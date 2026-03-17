package com.darexsh.appvault;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;

class PackageChangeWatcher {
    private final AppCompatActivity activity;
    private final Runnable onPackagesChanged;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    || Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                onPackagesChanged.run();
            }
        }
    };

    PackageChangeWatcher(AppCompatActivity activity, Runnable onPackagesChanged) {
        this.activity = activity;
        this.onPackagesChanged = onPackagesChanged;
    }

    void register() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(packageChangeReceiver, filter, AppCompatActivity.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(packageChangeReceiver, filter);
        }
        receiverRegistered = true;
    }

    void unregister() {
        if (!receiverRegistered) {
            return;
        }
        activity.unregisterReceiver(packageChangeReceiver);
        receiverRegistered = false;
    }
}
