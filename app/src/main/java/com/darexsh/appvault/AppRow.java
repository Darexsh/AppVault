package com.darexsh.appvault;

import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;

class AppRow {
    final String appName;
    final String normalizedAppName;
    final String normalizedPackageName;
    final PackageInfo packageInfo;
    volatile Drawable appIcon;

    AppRow(String appName, PackageInfo packageInfo, Drawable appIcon) {
        this.appName = appName;
        this.normalizedAppName = appName.toLowerCase(java.util.Locale.US);
        this.packageInfo = packageInfo;
        this.normalizedPackageName = packageInfo.packageName.toLowerCase(java.util.Locale.US);
        this.appIcon = appIcon;
    }

    @Override
    public String toString() {
        return appName;
    }
}
