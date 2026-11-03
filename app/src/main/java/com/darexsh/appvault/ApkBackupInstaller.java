package com.darexsh.appvault;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

final class ApkBackupInstaller {
    private ApkBackupInstaller() {
    }

    static void install(Context context, Uri backupUri) {
        Context appContext = context.getApplicationContext();
        Toast.makeText(appContext, R.string.install_preparing, Toast.LENGTH_SHORT).show();
        new Thread(() -> installOnWorker(appContext, backupUri), "appvault-apk-installer").start();
    }

    private static void installOnWorker(Context context, Uri backupUri) {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            String displayName = getDisplayName(context.getContentResolver(), backupUri);
            String lowerName = displayName.toLowerCase(Locale.US);
            boolean isXapk = lowerName.endsWith(".xapk");
            boolean isArchive = lowerName.endsWith(".apks")
                    || lowerName.endsWith(".apkm")
                    || isXapk
                    || lowerName.endsWith(".zip");
            if (!isArchive && !lowerName.endsWith(".apk")) {
                showToast(context, R.string.install_invalid_file);
                return;
            }

            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
            );
            sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                if (isArchive) {
                    addArchiveApks(context.getContentResolver(), backupUri, session, isXapk);
                } else {
                    try (InputStream input = context.getContentResolver().openInputStream(backupUri)) {
                        if (input == null) {
                            throw new IOException("Could not open APK file");
                        }
                        writeApk(session, "base.apk", input, -1L);
                    }
                }

                Intent statusIntent = new Intent(context, ApkInstallStatusReceiver.class)
                        .setAction(context.getPackageName() + ".APK_INSTALL_STATUS");
                int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pendingFlags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent statusPendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        pendingFlags
                );
                session.commit(statusPendingIntent.getIntentSender());
            }
        } catch (Exception e) {
            if (sessionId >= 0) {
                installer.abandonSession(sessionId);
            }
            showToast(context, R.string.install_failed);
        }
    }

    private static void addArchiveApks(
            ContentResolver resolver,
            Uri archiveUri,
            PackageInstaller.Session session,
            boolean isXapk
    ) throws IOException {
        String xapkBaseName = isXapk ? findXapkBaseName(resolver, archiveUri) : null;
        InputStream archiveInput = resolver.openInputStream(archiveUri);
        if (archiveInput == null) {
            throw new IOException("Could not open APK archive");
        }

        boolean hasBaseApk = false;
        Set<String> usedNames = new HashSet<>();
        usedNames.add("base.apk");
        try (ZipInputStream zip = new ZipInputStream(archiveInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || entry.getName().indexOf('/') >= 0
                        || entry.getName().indexOf('\\') >= 0) {
                    zip.closeEntry();
                    continue;
                }

                String entryName = getFileName(entry.getName());
                if (!entryName.toLowerCase(Locale.US).endsWith(".apk")) {
                    zip.closeEntry();
                    continue;
                }

                boolean isBase = "base.apk".equalsIgnoreCase(entryName)
                        || "base-master.apk".equalsIgnoreCase(entryName)
                        || (xapkBaseName != null && xapkBaseName.equalsIgnoreCase(entryName));
                String sessionName;
                if (isBase && !hasBaseApk) {
                    sessionName = "base.apk";
                    hasBaseApk = true;
                } else {
                    sessionName = uniqueApkName(entryName, usedNames);
                }
                writeApk(session, sessionName, zip, entry.getSize());
                zip.closeEntry();
            }
        }

        if (!hasBaseApk) {
            throw new IOException("APK archive does not contain base.apk");
        }
    }

    private static String findXapkBaseName(ContentResolver resolver, Uri archiveUri) throws IOException {
        InputStream archiveInput = resolver.openInputStream(archiveUri);
        if (archiveInput == null) {
            throw new IOException("Could not open XAPK archive");
        }

        try (ZipInputStream zip = new ZipInputStream(archiveInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && "manifest.json".equalsIgnoreCase(getFileName(entry.getName()))) {
                    byte[] manifestBytes = readEntryUpTo(zip, 1024 * 1024);
                    try {
                        JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
                        JSONArray splitApks = manifest.optJSONArray("split_apks");
                        if (splitApks != null) {
                            for (int i = 0; i < splitApks.length(); i++) {
                                JSONObject split = splitApks.optJSONObject(i);
                                if (split != null && "base".equalsIgnoreCase(split.optString("id"))) {
                                    String fileName = split.optString("file");
                                    if (!fileName.isEmpty()) {
                                        return getFileName(fileName);
                                    }
                                }
                            }
                        }
                        String packageName = manifest.optString("package_name");
                        if (!packageName.isEmpty()) {
                            return packageName + ".apk";
                        }
                    } catch (Exception ignored) {
                        // Fall back to the conventional base.apk name below.
                    }
                    return null;
                }
                zip.closeEntry();
            }
        }
        return null;
    }

    private static byte[] readEntryUpTo(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = maxBytes;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count == -1) {
                break;
            }
            output.write(buffer, 0, count);
            remaining -= count;
        }
        return output.toByteArray();
    }

    private static String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) {
                    String name = cursor.getString(nameColumn);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to the URI path below.
        }
        String path = uri.getLastPathSegment();
        return path == null ? "" : path;
    }

    private static String getFileName(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String uniqueApkName(String desiredName, Set<String> usedNames) {
        String candidate = desiredName;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.US))) {
            int extensionIndex = candidate.lastIndexOf('.');
            String name = extensionIndex > 0 ? candidate.substring(0, extensionIndex) : candidate;
            String extension = extensionIndex > 0 ? candidate.substring(extensionIndex) : "";
            candidate = name + "_" + suffix++ + extension;
        }
        return candidate;
    }

    private static void writeApk(
            PackageInstaller.Session session,
            String name,
            InputStream input,
            long size
    ) throws IOException {
        try (OutputStream output = session.openWrite(name, 0L, size)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            session.fsync(output);
        }
    }

    private static void showToast(Context context, int messageResId) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, messageResId, Toast.LENGTH_LONG).show()
        );
    }
}
