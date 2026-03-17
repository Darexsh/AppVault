package com.darexsh.appvault;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        File appBackupFolder = new File(backupRoot, sanitizedName + " (" + row.packageInfo.packageName + ")");
        if (!appBackupFolder.exists() && !appBackupFolder.mkdirs()) {
            return null;
        }

        File baseSource = new File(sourceDir);
        if (!baseSource.exists()) {
            return null;
        }
        File baseTarget = new File(appBackupFolder, "base.apk");
        if (!copyFile(baseSource, baseTarget)) {
            return null;
        }
        savedFiles.add(baseTarget);

        for (String splitPath : splitSourceDirs) {
            if (splitPath == null || splitPath.trim().isEmpty()) {
                continue;
            }
            File splitSource = new File(splitPath);
            if (!splitSource.exists()) {
                continue;
            }
            String splitName = splitSource.getName();
            if (!splitName.toLowerCase(Locale.US).endsWith(".apk")) {
                splitName = splitName + ".apk";
            }
            File splitTarget = new File(appBackupFolder, splitName);
            if (!copyFile(splitSource, splitTarget)) {
                return null;
            }
            savedFiles.add(splitTarget);
        }

        return new BackupResult(savedFiles, true);
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
