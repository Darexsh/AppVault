package com.darexsh.appvault;

import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;

class AppRow {
    final String appName;
    final PackageInfo packageInfo;
    final Drawable appIcon;

    AppRow(String appName, PackageInfo packageInfo, Drawable appIcon) {
        this.appName = appName;
        this.packageInfo = packageInfo;
        this.appIcon = appIcon;
    }

    @Override
    public String toString() {
        return appName;
    }
}
