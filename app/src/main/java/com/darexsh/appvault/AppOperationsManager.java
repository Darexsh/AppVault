package com.darexsh.appvault;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AppOperationsManager {
    private static final String TAG = "AppVault";

    private final AppCompatActivity activity;
    private final AppBackupManager appBackupManager;

    AppOperationsManager(AppCompatActivity activity, AppBackupManager appBackupManager) {
        this.activity = activity;
        this.appBackupManager = appBackupManager;
    }

    void openSystemAppSettings(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", packageName, null));
        activity.startActivity(intent);
    }

    void uninstallApp(String packageName) {
        Uri packageUri = Uri.fromParts("package", packageName, null);
        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        uninstallIntent.setData(packageUri);

        Intent legacyDeleteIntent = new Intent(Intent.ACTION_DELETE);
        legacyDeleteIntent.setData(packageUri);

        try {
            if (uninstallIntent.resolveActivity(activity.getPackageManager()) != null) {
                Log.d(TAG, "Launching ACTION_UNINSTALL_PACKAGE for " + packageName);
                activity.startActivity(uninstallIntent);
                return;
            }
            if (legacyDeleteIntent.resolveActivity(activity.getPackageManager()) != null) {
                Log.d(TAG, "Launching ACTION_DELETE for " + packageName);
                activity.startActivity(legacyDeleteIntent);
                return;
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Uninstall activity not found for " + packageName, e);
        } catch (Exception e) {
            Log.e(TAG, "Uninstall intent failed for " + packageName, e);
        }

        Log.w(TAG, "No uninstall activity available, opening settings for " + packageName);
        Toast.makeText(activity, R.string.uninstall_not_supported, Toast.LENGTH_SHORT).show();
        openSystemAppSettings(packageName);
    }

    void backupAndShareApk(AppRow row) {
        if (!ensureBackupStorageAccess()) {
            return;
        }

        BackupResult backupResult = appBackupManager.copyApkToBackupFolder(row);
        if (backupResult == null || backupResult.savedFiles.isEmpty()) {
            Toast.makeText(activity, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> paths = new ArrayList<>();
        for (File savedFile : backupResult.savedFiles) {
            paths.add(savedFile.getAbsolutePath());
        }
        MediaScannerConnection.scanFile(activity, paths.toArray(new String[0]), null, null);

        if (backupResult.containsSplitApks) {
            Toast.makeText(
                    activity,
                    activity.getString(R.string.backup_saved_split, row.appName, backupResult.savedFiles.size()),
                    Toast.LENGTH_LONG
            ).show();
        } else {
            Toast.makeText(activity, activity.getString(R.string.backup_saved, backupResult.savedFiles.get(0).getName()), Toast.LENGTH_LONG).show();
        }
    }

    void backupSelectedApps(Set<String> selectedPackages, AppCatalogManager appCatalogManager) {
        if (selectedPackages.isEmpty()) {
            return;
        }
        if (!ensureBackupStorageAccess()) {
            return;
        }

        int success = 0;
        int failed = 0;
        int splitBackups = 0;
        for (String packageName : new ArrayList<>(selectedPackages)) {
            AppRow row = appCatalogManager.findAppRow(packageName);
            if (row == null) {
                failed++;
                continue;
            }

            BackupResult backupResult = appBackupManager.copyApkToBackupFolder(row);
            if (backupResult == null || backupResult.savedFiles.isEmpty()) {
                failed++;
                continue;
            }

            success++;
            if (backupResult.containsSplitApks) {
                splitBackups++;
            }
            List<String> paths = new ArrayList<>();
            for (File savedFile : backupResult.savedFiles) {
                paths.add(savedFile.getAbsolutePath());
            }
            MediaScannerConnection.scanFile(activity, paths.toArray(new String[0]), null, null);
        }

        Toast.makeText(activity, activity.getString(R.string.batch_backup_result, success, failed), Toast.LENGTH_LONG).show();
        if (splitBackups > 0) {
            Toast.makeText(activity, activity.getString(R.string.batch_backup_split_hint, splitBackups), Toast.LENGTH_LONG).show();
        }
    }

    void promptForBackupStoragePermissionIfNeeded(AppDialogManager appDialogManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            return;
        }
        appDialogManager.showStoragePermissionDialog(
                activity.getApplicationInfo().loadIcon(activity.getPackageManager()),
                this::openAllFilesAccessSettings
        );
    }

    private boolean ensureBackupStorageAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        if (Environment.isExternalStorageManager()) {
            return true;
        }

        Toast.makeText(activity, R.string.backup_permission_required, Toast.LENGTH_LONG).show();
        return openAllFilesAccessSettings();
    }

    private boolean openAllFilesAccessSettings() {
        Intent manageIntent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + activity.getPackageName())
        );
        try {
            activity.startActivity(manageIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return true;
            } catch (ActivityNotFoundException ignored) {
                return false;
            }
        }
    }
}
