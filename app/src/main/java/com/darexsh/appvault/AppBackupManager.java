package com.darexsh.appvault;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class AppBackupManager {

    long getSourceApkSize(AppRow row) {
        String sourceDir = row.packageInfo.applicationInfo.sourceDir;
        if (sourceDir == null) {
            return 0L;
        }
        File apkFile = new File(sourceDir);
        if (!apkFile.exists()) {
            return 0L;
        }
        return apkFile.length();
    }

    BackupResult copyApkToBackupFolder(AppRow row) {
        String sourceDir = row.packageInfo.applicationInfo.sourceDir;
        if (sourceDir == null) {
            return null;
        }

        File backupRoot = new File(Environment.getExternalStorageDirectory(), "App_Backups");
        if (!backupRoot.exists() && !backupRoot.mkdirs()) {
            return null;
        }

        String sanitizedName = sanitizeAppName(row);

        String[] splitSourceDirs = row.packageInfo.applicationInfo.splitSourceDirs;
        boolean hasSplits = splitSourceDirs != null && splitSourceDirs.length > 0;
        List<File> savedFiles = new ArrayList<>();

        if (!hasSplits) {
            File sourceFile = new File(sourceDir);
            if (!sourceFile.exists()) {
                return null;
            }
            String fileName = sanitizedName + " (" + row.packageInfo.packageName + ").apk";
            File apkCopy = new File(backupRoot, fileName);
            if (!copyFile(sourceFile, apkCopy)) {
                return null;
            }
            savedFiles.add(apkCopy);
            return new BackupResult(savedFiles, false);
        }

        File appBackupArchive = new File(backupRoot, sanitizedName + " (" + row.packageInfo.packageName + ").apks");
        File tempArchive = new File(backupRoot, appBackupArchive.getName() + ".tmp");
        File baseSource = new File(sourceDir);
        if (!baseSource.exists()) {
            return null;
        }

        Set<String> entryNames = new HashSet<>();
        entryNames.add("base.apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tempArchive))) {
            addFileToArchive(baseSource, "base.apk", zip);
            for (String splitPath : splitSourceDirs) {
                if (splitPath == null || splitPath.trim().isEmpty()) {
                    continue;
                }
                File splitSource = new File(splitPath);
                if (!splitSource.exists()) {
                    throw new IOException("Missing split APK: " + splitPath);
                }
                String splitName = splitSource.getName();
                if (!splitName.toLowerCase(Locale.US).endsWith(".apk")) {
                    splitName = splitName + ".apk";
                }
                String entryName = uniqueArchiveEntryName(splitName, entryNames);
                addFileToArchive(splitSource, entryName, zip);
            }
        } catch (IOException e) {
            tempArchive.delete();
            return null;
        }
        if (appBackupArchive.exists() && !appBackupArchive.delete()) {
            tempArchive.delete();
            return null;
        }
        if (!tempArchive.renameTo(appBackupArchive)) {
            tempArchive.delete();
            return null;
        }

        savedFiles.add(appBackupArchive);
        return new BackupResult(savedFiles, true);
    }

    private void addFileToArchive(File sourceFile, String entryName, ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream in = new FileInputStream(sourceFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
    }

    private String uniqueArchiveEntryName(String desiredName, Set<String> usedNames) {
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

    private String sanitizeAppName(AppRow row) {
        String sanitizedName = row.appName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitizedName.isEmpty()) {
            return row.packageInfo.packageName;
        }
        return sanitizedName;
    }

    private boolean copyFile(File sourceFile, File targetFile) {
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
