package com.darexsh.appvault;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class AppCatalogManager {
    static final int FILTER_ALL = 0;
    static final int FILTER_USER = 1;
    static final int FILTER_SYSTEM = 2;

    static final int SORT_NAME = 0;
    static final int SORT_INSTALL_DATE = 1;
    static final int SORT_UPDATE_DATE = 2;
    static final int SORT_APP_SIZE = 3;

    private final List<AppRow> allAppRows = new ArrayList<>();
    private final List<AppRow> filteredAppRows = new ArrayList<>();

    private String currentSearchQuery = "";
    private int currentFilter = FILTER_USER;
    private int currentSort = SORT_NAME;

    List<AppRow> getFilteredAppRows() {
        return filteredAppRows;
    }

    int getCurrentFilter() {
        return currentFilter;
    }

    void setCurrentFilter(int currentFilter) {
        this.currentFilter = currentFilter;
    }

    int getCurrentSort() {
        return currentSort;
    }

    void setCurrentSort(int currentSort) {
        this.currentSort = currentSort;
    }

    void setCurrentSearchQuery(String query) {
        currentSearchQuery = query == null ? "" : query.trim();
    }

    int getFilteredCount() {
        return filteredAppRows.size();
    }

    int getAllCount() {
        return allAppRows.size();
    }

    void loadInstalledApps(PackageManager packageManager) {
        allAppRows.clear();
        allAppRows.addAll(queryInstalledApps(packageManager));
    }

    Set<String> reloadInstalledApps(PackageManager packageManager) {
        loadInstalledApps(packageManager);
        Set<String> existingPackages = new HashSet<>();
        for (AppRow row : allAppRows) {
            existingPackages.add(row.packageInfo.packageName);
        }
        return existingPackages;
    }

    void applyFilters(AppBackupManager appBackupManager) {
        String searchLower = currentSearchQuery.toLowerCase(Locale.US);
        filteredAppRows.clear();

        for (AppRow row : allAppRows) {
            if (!matchesTypeFilter(row, currentFilter)) {
                continue;
            }

            if (!searchLower.isEmpty()) {
                String appNameLower = row.appName.toLowerCase(Locale.US);
                String packageLower = row.packageInfo.packageName.toLowerCase(Locale.US);
                if (!appNameLower.contains(searchLower) && !packageLower.contains(searchLower)) {
                    continue;
                }
            }

            filteredAppRows.add(row);
        }

        sortFilteredRows(appBackupManager);
    }

    AppRow findAppRow(String packageName) {
        for (AppRow row : allAppRows) {
            if (row.packageInfo.packageName.equals(packageName)) {
                return row;
            }
        }
        return null;
    }

    String buildSelectedAppNamesSubtitle(Set<String> selectedPackages) {
        List<String> appNames = new ArrayList<>();
        for (String packageName : selectedPackages) {
            AppRow row = findAppRow(packageName);
            appNames.add(row == null ? packageName : row.appName);
        }
        Collections.sort(appNames, String.CASE_INSENSITIVE_ORDER);
        StringBuilder subtitleBuilder = new StringBuilder();
        for (int i = 0; i < appNames.size(); i++) {
            if (i > 0) {
                subtitleBuilder.append(", ");
            }
            subtitleBuilder.append(appNames.get(i));
        }
        return subtitleBuilder.toString();
    }

    private List<AppRow> queryInstalledApps(PackageManager packageManager) {
        List<PackageInfo> installedPackages;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            installedPackages = packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0));
        } else {
            installedPackages = packageManager.getInstalledPackages(0);
        }

        List<AppRow> rows = new ArrayList<>();
        for (PackageInfo packageInfo : installedPackages) {
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            if (appInfo == null) {
                continue;
            }

            String appName = String.valueOf(packageManager.getApplicationLabel(appInfo));
            Drawable appIcon = appInfo.loadIcon(packageManager);
            rows.add(new AppRow(appName, packageInfo, appIcon));
        }

        Collections.sort(rows, Comparator.comparing(row -> row.appName.toLowerCase(Locale.US)));
        return rows;
    }

    private void sortFilteredRows(AppBackupManager appBackupManager) {
        Comparator<AppRow> comparator;
        if (currentSort == SORT_INSTALL_DATE) {
            comparator = (left, right) -> Long.compare(
                    right.packageInfo.firstInstallTime,
                    left.packageInfo.firstInstallTime
            );
        } else if (currentSort == SORT_UPDATE_DATE) {
            comparator = (left, right) -> Long.compare(
                    right.packageInfo.lastUpdateTime,
                    left.packageInfo.lastUpdateTime
            );
        } else if (currentSort == SORT_APP_SIZE) {
            comparator = (left, right) -> Long.compare(
                    appBackupManager.getSourceApkSize(right),
                    appBackupManager.getSourceApkSize(left)
            );
        } else {
            comparator = Comparator.comparing(row -> row.appName.toLowerCase(Locale.US));
        }
        Collections.sort(filteredAppRows, comparator);
    }

    private boolean matchesTypeFilter(AppRow row, int filter) {
        boolean isSystemApp = (row.packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        if (filter == FILTER_USER) {
            return !isSystemApp;
        }
        if (filter == FILTER_SYSTEM) {
            return isSystemApp;
        }
        return true;
    }
}
