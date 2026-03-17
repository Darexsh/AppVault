package com.darexsh.appsinspector;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AppsInspector";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_USER = 1;
    private static final int FILTER_SYSTEM = 2;
    private static final int SORT_NAME = 0;
    private static final int SORT_INSTALL_DATE = 1;
    private static final int SORT_UPDATE_DATE = 2;
    private static final int SORT_APP_SIZE = 3;

    private final List<AppRow> allAppRows = new ArrayList<>();
    private final List<AppRow> filteredAppRows = new ArrayList<>();
    private final Set<String> expandedPackages = new HashSet<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private final List<String> pendingBatchUninstallPackages = new ArrayList<>();
    private AppListAdapter appAdapter;
    private String currentSearchQuery = "";
    private int currentFilter = FILTER_USER;
    private int currentSort = SORT_NAME;
    private boolean selectionMode = false;
    private boolean batchUninstallInProgress = false;

    private TextInputEditText searchInput;
    private AutoCompleteTextView filterDropdown;
    private AutoCompleteTextView sortDropdown;
    private TextView headerSubtitle;
    private View batchActionsCard;
    private TextView batchSelectionCount;
    private MaterialButton batchSelectAllButton;
    private MaterialButton batchClearButton;
    private MaterialButton batchBackupButton;
    private MaterialButton batchUninstallButton;
    private boolean packageReceiverRegistered = false;

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
                reloadInstalledApps();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView appListView = findViewById(R.id.app_list);
        TextView emptyView = findViewById(R.id.empty_view);
        View headerCard = findViewById(R.id.header_card);

        appListView.setEmptyView(emptyView);

        headerSubtitle = findViewById(R.id.header_subtitle);
        searchInput = findViewById(R.id.search_input);
        filterDropdown = findViewById(R.id.filter_dropdown);
        sortDropdown = findViewById(R.id.sort_dropdown);
        TextView appInfoButton = findViewById(R.id.btn_app_info);
        batchActionsCard = findViewById(R.id.batch_actions_card);
        batchSelectionCount = findViewById(R.id.batch_selection_count);
        batchSelectAllButton = findViewById(R.id.batch_select_all);
        batchClearButton = findViewById(R.id.batch_clear);
        batchBackupButton = findViewById(R.id.batch_backup);
        batchUninstallButton = findViewById(R.id.batch_uninstall);

        appAdapter = new AppListAdapter(
                this,
                filteredAppRows
        );
        appListView.setAdapter(appAdapter);

        setupSearchInput();
        setupFilterDropdown();
        setupSortDropdown();
        setupBatchActions();
        appInfoButton.setOnClickListener(v -> showAppInfoDialog());

        promptForBackupStoragePermissionIfNeeded();
        animateScreenEntrance(headerCard, appListView);

        allAppRows.clear();
        allAppRows.addAll(loadInstalledApps());
        applyFilters();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFilterAndSortSelectionLabels();
        reloadInstalledApps();
        if (batchUninstallInProgress && !pendingBatchUninstallPackages.isEmpty()) {
            launchNextBatchUninstall();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerPackageChangeReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterPackageChangeReceiver();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && searchInput != null && searchInput.hasFocus()) {
            Rect outRect = new Rect();
            searchInput.getGlobalVisibleRect(outRect);
            if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                searchInput.clearFocus();
                InputMethodManager imm = getSystemService(InputMethodManager.class);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private List<AppRow> loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> installedPackages;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            installedPackages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0));
        } else {
            installedPackages = pm.getInstalledPackages(0);
        }

        List<AppRow> rows = new ArrayList<>();
        for (PackageInfo packageInfo : installedPackages) {
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            if (appInfo == null) {
                continue;
            }

            String appName = String.valueOf(pm.getApplicationLabel(appInfo));
            Drawable appIcon = appInfo.loadIcon(pm);
            rows.add(new AppRow(appName, packageInfo, appIcon));
        }

        Collections.sort(rows, Comparator.comparing(row -> row.appName.toLowerCase(Locale.US)));
        return rows;
    }

    private void setupSearchInput() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // no-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString().trim();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // no-op
            }
        });
    }

    private void setupFilterDropdown() {
        String[] options = getFilterOptions();

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_filter_dropdown,
                options
        );
        filterAdapter.setDropDownViewResource(R.layout.item_filter_dropdown);
        filterDropdown.setAdapter(filterAdapter);
        filterDropdown.setDropDownBackgroundResource(R.drawable.bg_dropdown_menu);
        filterDropdown.setText(options[FILTER_USER], false);
        filterDropdown.setOnClickListener(v -> filterDropdown.showDropDown());
        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            currentFilter = position;
            applyFilters();
        });
    }

    private void setupSortDropdown() {
        String[] options = getSortOptions();

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_filter_dropdown,
                options
        );
        sortAdapter.setDropDownViewResource(R.layout.item_filter_dropdown);
        sortDropdown.setAdapter(sortAdapter);
        sortDropdown.setDropDownBackgroundResource(R.drawable.bg_dropdown_menu);
        sortDropdown.setText(options[SORT_NAME], false);
        sortDropdown.setOnClickListener(v -> sortDropdown.showDropDown());
        sortDropdown.setOnItemClickListener((parent, view, position, id) -> {
            currentSort = position;
            applyFilters();
        });
    }

    private void refreshFilterAndSortSelectionLabels() {
        if (filterDropdown != null) {
            String[] filterOptions = getFilterOptions();
            int filterIndex = Math.max(0, Math.min(currentFilter, filterOptions.length - 1));
            filterDropdown.setText(filterOptions[filterIndex], false);
        }
        if (sortDropdown != null) {
            String[] sortOptions = getSortOptions();
            int sortIndex = Math.max(0, Math.min(currentSort, sortOptions.length - 1));
            sortDropdown.setText(sortOptions[sortIndex], false);
        }
    }

    private String[] getFilterOptions() {
        return new String[]{
                getString(R.string.filter_all),
                getString(R.string.filter_user),
                getString(R.string.filter_system)
        };
    }

    private String[] getSortOptions() {
        return new String[]{
                getString(R.string.sort_name),
                getString(R.string.sort_install_date),
                getString(R.string.sort_update_date),
                getString(R.string.sort_app_size)
        };
    }

    private void setupBatchActions() {
        batchSelectAllButton.setOnClickListener(v -> {
            selectedPackages.clear();
            for (AppRow row : filteredAppRows) {
                selectedPackages.add(row.packageInfo.packageName);
            }
            appAdapter.notifyDataSetChanged();
            updateBatchActionsUi();
        });
        batchClearButton.setOnClickListener(v -> {
            exitSelectionMode();
        });
        batchBackupButton.setOnClickListener(v -> confirmBatchBackup());
        batchUninstallButton.setOnClickListener(v -> confirmBatchUninstall());
        updateBatchActionsUi();
    }

    private void toggleAppSelection(String packageName) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName);
        } else {
            selectedPackages.add(packageName);
        }
        if (selectedPackages.isEmpty()) {
            exitSelectionMode();
            return;
        }
        appAdapter.notifyDataSetChanged();
        updateBatchActionsUi();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedPackages.clear();
        updateBatchActionsUi();
        appAdapter.notifyDataSetChanged();
    }

    private void updateBatchActionsUi() {
        if (batchActionsCard == null) {
            return;
        }
        boolean shouldShow = selectionMode && !selectedPackages.isEmpty();
        boolean isVisible = batchActionsCard.getVisibility() == View.VISIBLE;

        batchActionsCard.animate().cancel();

        if (!shouldShow) {
            if (isVisible) {
                batchActionsCard.animate()
                        .alpha(0f)
                        .translationY(16f)
                        .setDuration(140)
                        .withEndAction(() -> {
                            batchActionsCard.setVisibility(View.GONE);
                            batchActionsCard.setAlpha(1f);
                            batchActionsCard.setTranslationY(0f);
                        })
                        .start();
            } else {
                batchActionsCard.setVisibility(View.GONE);
            }
            return;
        }
        if (!isVisible) {
            batchActionsCard.setAlpha(0f);
            batchActionsCard.setTranslationY(16f);
            batchActionsCard.setVisibility(View.VISIBLE);
            batchActionsCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start();
        }
        batchSelectionCount.setText(getString(R.string.batch_selected_count, selectedPackages.size()));
        boolean hasSelection = !selectedPackages.isEmpty();
        batchClearButton.setEnabled(hasSelection);
        batchBackupButton.setEnabled(hasSelection);
        batchUninstallButton.setEnabled(hasSelection);
    }

    private void applyFilters() {
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
        sortFilteredRows();

        appAdapter.notifyDataSetChanged();
        updateHeaderSubtitle();
    }

    private void sortFilteredRows() {
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
                    getSourceApkSize(right),
                    getSourceApkSize(left)
            );
        } else {
            comparator = Comparator.comparing(row -> row.appName.toLowerCase(Locale.US));
        }
        Collections.sort(filteredAppRows, comparator);
    }

    private long getSourceApkSize(AppRow row) {
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

    private void reloadInstalledApps() {
        allAppRows.clear();
        allAppRows.addAll(loadInstalledApps());
        Set<String> existingPackages = new HashSet<>();
        for (AppRow row : allAppRows) {
            existingPackages.add(row.packageInfo.packageName);
        }
        Iterator<String> iterator = expandedPackages.iterator();
        while (iterator.hasNext()) {
            if (!existingPackages.contains(iterator.next())) {
                iterator.remove();
            }
        }
        Iterator<String> selectionIterator = selectedPackages.iterator();
        while (selectionIterator.hasNext()) {
            if (!existingPackages.contains(selectionIterator.next())) {
                selectionIterator.remove();
            }
        }
        if (selectionMode && selectedPackages.isEmpty()) {
            selectionMode = false;
        }
        applyFilters();
        updateBatchActionsUi();
    }

    private void registerPackageChangeReceiver() {
        if (packageReceiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageChangeReceiver, filter);
        }
        packageReceiverRegistered = true;
    }

    private void unregisterPackageChangeReceiver() {
        if (!packageReceiverRegistered) {
            return;
        }
        unregisterReceiver(packageChangeReceiver);
        packageReceiverRegistered = false;
    }

    private void updateHeaderSubtitle() {
        String modeLabel;
        if (currentFilter == FILTER_USER) {
            modeLabel = getString(R.string.filter_user);
        } else if (currentFilter == FILTER_SYSTEM) {
            modeLabel = getString(R.string.filter_system);
        } else {
            modeLabel = getString(R.string.filter_all);
        }

        String subtitle = getString(
                R.string.header_subtitle_state,
                filteredAppRows.size(),
                allAppRows.size(),
                modeLabel
        );
        headerSubtitle.setText(subtitle);
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

    private void openDetailsScreen(String packageName) {
        Intent intent = new Intent(this, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private boolean toggleExpanded(String packageName) {
        if (expandedPackages.contains(packageName)) {
            expandedPackages.remove(packageName);
            return false;
        }
        expandedPackages.add(packageName);
        return true;
    }

    private void animateScreenEntrance(View headerCard, View appListView) {
        headerCard.setAlpha(0f);
        headerCard.setTranslationY(-18f);
        headerCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .start();

        appListView.setAlpha(0f);
        appListView.setTranslationY(20f);
        appListView.post(() -> appListView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(80)
                .setDuration(280)
                .start());
    }

    private void animateActionsToggle(View actionsContainer, ImageButton expandButton, boolean expand) {
        expandButton.animate().cancel();
        actionsContainer.animate().cancel();

        expandButton.animate()
                .rotation(expand ? 180f : 0f)
                .setDuration(200)
                .start();

        if (expand) {
            actionsContainer.setVisibility(View.VISIBLE);
            actionsContainer.setAlpha(0f);
            actionsContainer.setTranslationY(-10f);
            actionsContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start();
            return;
        }

        actionsContainer.animate()
                .alpha(0f)
                .translationY(-10f)
                .setDuration(170)
                .withEndAction(() -> {
                    actionsContainer.setVisibility(View.GONE);
                    actionsContainer.setAlpha(1f);
                    actionsContainer.setTranslationY(0f);
                })
                .start();
    }

    private void openSystemAppSettings(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", packageName, null));
        startActivity(intent);
    }

    private void uninstallApp(String packageName) {
        Uri packageUri = Uri.fromParts("package", packageName, null);
        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        uninstallIntent.setData(packageUri);

        Intent legacyDeleteIntent = new Intent(Intent.ACTION_DELETE);
        legacyDeleteIntent.setData(packageUri);

        try {
            if (uninstallIntent.resolveActivity(getPackageManager()) != null) {
                Log.d(TAG, "Launching ACTION_UNINSTALL_PACKAGE for " + packageName);
                startActivity(uninstallIntent);
                return;
            }
            if (legacyDeleteIntent.resolveActivity(getPackageManager()) != null) {
                Log.d(TAG, "Launching ACTION_DELETE for " + packageName);
                startActivity(legacyDeleteIntent);
                return;
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Uninstall activity not found for " + packageName, e);
        } catch (Exception e) {
            Log.e(TAG, "Uninstall intent failed for " + packageName, e);
        }

        Log.w(TAG, "No uninstall activity available, opening settings for " + packageName);
        Toast.makeText(this, R.string.uninstall_not_supported, Toast.LENGTH_SHORT).show();
        openSystemAppSettings(packageName);
    }

    private void confirmUninstall(AppRow row) {
        showActionConfirmDialog(
                row.appIcon,
                getString(R.string.uninstall_confirm_title),
                row.appName,
                getString(R.string.uninstall_confirm_message, row.appName),
                getString(R.string.uninstall_confirm_yes),
                true,
                () -> uninstallApp(row.packageInfo.packageName)
        );
    }

    private void showActionConfirmDialog(
            Drawable icon,
            String title,
            String subtitle,
            String message,
            String confirmButtonText,
            boolean destructiveAction,
            Runnable onConfirm
    ) {
        View content = getLayoutInflater().inflate(R.layout.dialog_uninstall_confirm, null);
        ImageView iconView = content.findViewById(R.id.uninstall_app_icon);
        TextView titleView = content.findViewById(R.id.uninstall_title);
        TextView subtitleView = content.findViewById(R.id.uninstall_subtitle);
        TextView messageView = content.findViewById(R.id.uninstall_message);
        MaterialButton noButton = content.findViewById(R.id.uninstall_no_button);
        MaterialButton yesButton = content.findViewById(R.id.uninstall_yes_button);

        iconView.setImageDrawable(icon);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        messageView.setText(message);
        yesButton.setText(confirmButtonText);
        if (destructiveAction) {
            yesButton.setTextColor(Color.parseColor("#FFD3D3"));
            yesButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5A2323")));
            yesButton.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#A55A5A")));
        } else {
            yesButton.setTextColor(Color.parseColor("#CFE0FF"));
            yesButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2F4D73")));
            yesButton.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#5C789E")));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        noButton.setOnClickListener(v -> dialog.dismiss());
        yesButton.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void confirmBackup(AppRow row) {
        View content = getLayoutInflater().inflate(R.layout.dialog_backup_confirm, null);
        ImageView appIcon = content.findViewById(R.id.backup_app_icon);
        TextView appName = content.findViewById(R.id.backup_app_name);
        TextView backupMessage = content.findViewById(R.id.backup_message);
        MaterialButton noButton = content.findViewById(R.id.backup_no_button);
        MaterialButton yesButton = content.findViewById(R.id.backup_yes_button);

        appIcon.setImageDrawable(row.appIcon);
        appName.setText(row.appName);
        backupMessage.setText(getString(R.string.backup_confirm_message, row.appName));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        noButton.setOnClickListener(v -> dialog.dismiss());
        yesButton.setOnClickListener(v -> {
            dialog.dismiss();
            backupAndShareApk(row);
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void backupAndShareApk(AppRow row) {
        if (!ensureBackupStorageAccess()) {
            return;
        }
        BackupResult backupResult = copyApkToBackupFolder(row);
        if (backupResult == null || backupResult.savedFiles.isEmpty()) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> paths = new ArrayList<>();
        for (File savedFile : backupResult.savedFiles) {
            paths.add(savedFile.getAbsolutePath());
        }
        MediaScannerConnection.scanFile(this, paths.toArray(new String[0]), null, null);

        if (backupResult.containsSplitApks) {
            Toast.makeText(
                    this,
                    getString(R.string.backup_saved_split, row.appName, backupResult.savedFiles.size()),
                    Toast.LENGTH_LONG
            ).show();
        } else {
            Toast.makeText(this, getString(R.string.backup_saved, backupResult.savedFiles.get(0).getName()), Toast.LENGTH_LONG).show();
        }
    }

    private BackupResult copyApkToBackupFolder(AppRow row) {
        String sourceDir = row.packageInfo.applicationInfo.sourceDir;
        if (sourceDir == null) {
            return null;
        }

        File backupRoot = new File(Environment.getExternalStorageDirectory(), "App_Backups");
        if (!backupRoot.exists() && !backupRoot.mkdirs()) {
            return null;
        }

        String sanitizedName = row.appName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitizedName.isEmpty()) {
            sanitizedName = row.packageInfo.packageName;
        }

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

    private void backupSelectedApps() {
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
            AppRow row = findAppRow(packageName);
            if (row == null) {
                failed++;
                continue;
            }
            BackupResult backupResult = copyApkToBackupFolder(row);
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
            MediaScannerConnection.scanFile(this, paths.toArray(new String[0]), null, null);
        }
        Toast.makeText(this, getString(R.string.batch_backup_result, success, failed), Toast.LENGTH_LONG).show();
        if (splitBackups > 0) {
            Toast.makeText(this, getString(R.string.batch_backup_split_hint, splitBackups), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmBatchUninstall() {
        if (selectedPackages.isEmpty()) {
            return;
        }
        showActionConfirmDialog(
                getApplicationInfo().loadIcon(getPackageManager()),
                getString(R.string.batch_uninstall_title),
                buildSelectedAppNamesSubtitle(),
                getString(R.string.batch_uninstall_message, selectedPackages.size()),
                getString(R.string.uninstall_confirm_yes),
                true,
                this::startBatchUninstall
        );
    }

    private void confirmBatchBackup() {
        if (selectedPackages.isEmpty()) {
            return;
        }
        showActionConfirmDialog(
                getApplicationInfo().loadIcon(getPackageManager()),
                getString(R.string.batch_backup_title),
                buildSelectedAppNamesSubtitle(),
                getString(R.string.batch_backup_message, selectedPackages.size()),
                getString(R.string.batch_backup_confirm_yes),
                false,
                this::backupSelectedApps
        );
    }

    private String buildSelectedAppNamesSubtitle() {
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

    private void startBatchUninstall() {
        pendingBatchUninstallPackages.clear();
        pendingBatchUninstallPackages.addAll(selectedPackages);
        batchUninstallInProgress = true;
        exitSelectionMode();
        launchNextBatchUninstall();
    }

    private void launchNextBatchUninstall() {
        if (pendingBatchUninstallPackages.isEmpty()) {
            batchUninstallInProgress = false;
            Toast.makeText(this, R.string.batch_uninstall_done, Toast.LENGTH_SHORT).show();
            reloadInstalledApps();
            return;
        }
        String nextPackage = pendingBatchUninstallPackages.remove(0);
        uninstallApp(nextPackage);
    }

    private AppRow findAppRow(String packageName) {
        for (AppRow row : allAppRows) {
            if (row.packageInfo.packageName.equals(packageName)) {
                return row;
            }
        }
        return null;
    }

    private boolean ensureBackupStorageAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        if (Environment.isExternalStorageManager()) {
            return true;
        }

        Toast.makeText(this, R.string.backup_permission_required, Toast.LENGTH_LONG).show();
        return openAllFilesAccessSettings();
    }

    private void promptForBackupStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_storage_permission, null);
        ImageView appIcon = content.findViewById(R.id.permission_app_icon);
        TextView appName = content.findViewById(R.id.permission_app_name);
        MaterialButton laterButton = content.findViewById(R.id.permission_later_button);
        MaterialButton openSettingsButton = content.findViewById(R.id.permission_open_settings_button);

        appName.setText(R.string.app_name);
        appIcon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        laterButton.setOnClickListener(v -> dialog.dismiss());
        openSettingsButton.setOnClickListener(v -> {
            dialog.dismiss();
            openAllFilesAccessSettings();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private boolean openAllFilesAccessSettings() {
        Intent manageIntent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(manageIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return true;
            } catch (ActivityNotFoundException ignored) {
                return false;
            }
        }
    }

    private void showAppInfoDialog() {
        String versionName = "1.0";
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            if (info.versionName != null) {
                versionName = info.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_app_info, null);
        TextView appName = content.findViewById(R.id.tv_app_name);
        TextView appVersion = content.findViewById(R.id.tv_app_version);
        TextView appDescription = content.findViewById(R.id.tv_app_description);
        TextView appDeveloper = content.findViewById(R.id.tv_app_developer);
        MaterialButton openEmail = content.findViewById(R.id.btn_open_email);
        MaterialButton changeLanguage = content.findViewById(R.id.btn_change_language);
        MaterialButton openGithub = content.findViewById(R.id.btn_open_github);
        MaterialButton openTelegramBot = content.findViewById(R.id.btn_open_telegram_bot);
        MaterialButton openGithubProfile = content.findViewById(R.id.btn_open_github_profile);
        MaterialButton openCoffee = content.findViewById(R.id.btn_open_coffee);

        appName.setText(R.string.app_info_name);
        appVersion.setText(getString(R.string.app_info_version, versionName));
        appDescription.setText(R.string.app_info_description);

        String developerLabel = getString(R.string.app_info_developer_label);
        String developerName = getString(R.string.app_info_developer_name);
        SpannableString developerText = new SpannableString(
                String.format(Locale.getDefault(), "%s %s", developerLabel, developerName)
        );
        int labelEnd = developerLabel.length();
        developerText.setSpan(
                new ForegroundColorSpan(0xFF7F9CCB),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        developerText.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        appDeveloper.setText(developerText);

        openEmail.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sichler.daniel@gmail.com"))));
        openGithub.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/darexsh"))));
        openTelegramBot.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/darexsh_bot"))));
        openGithubProfile.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Darexsh"))));
        openCoffee.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/darexsh"))));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.app_info_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        changeLanguage.setOnClickListener(v -> showLanguageDialog(dialog));

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(this, R.color.header_tone_accent));
        }
    }

    private void showLanguageDialog(AlertDialog appInfoDialog) {
        View content = getLayoutInflater().inflate(R.layout.dialog_language_picker, null);
        MaterialButton systemButton = content.findViewById(R.id.language_system_button);
        MaterialButton englishButton = content.findViewById(R.id.language_english_button);
        MaterialButton germanButton = content.findViewById(R.id.language_german_button);
        MaterialButton cancelButton = content.findViewById(R.id.language_cancel_button);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        String currentMode = getCurrentLanguageMode();
        styleLanguageButton(systemButton, "system".equals(currentMode));
        styleLanguageButton(englishButton, "en".equals(currentMode));
        styleLanguageButton(germanButton, "de".equals(currentMode));

        systemButton.setOnClickListener(v -> {
            applyLanguageMode("system");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            recreate();
        });
        englishButton.setOnClickListener(v -> {
            applyLanguageMode("en");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            recreate();
        });
        germanButton.setOnClickListener(v -> {
            applyLanguageMode("de");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            recreate();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private String getCurrentLanguageMode() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            return "system";
        }
        String language = locales.get(0).getLanguage();
        if ("de".equalsIgnoreCase(language)) {
            return "de";
        }
        return "en";
    }

    private void applyLanguageMode(String mode) {
        if ("de".equals(mode)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de"));
            return;
        }
        if ("en".equals(mode)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
            return;
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
    }

    private void styleLanguageButton(MaterialButton button, boolean selected) {
        if (selected) {
            button.setTextColor(Color.parseColor("#CFE0FF"));
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2F4D73")));
            button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#5C789E")));
            return;
        }
        button.setTextColor(Color.parseColor("#D7E1F6"));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#50607D")));
    }

    private static class AppRow {
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

    private static class BackupResult {
        final List<File> savedFiles;
        final boolean containsSplitApks;

        BackupResult(List<File> savedFiles, boolean containsSplitApks) {
            this.savedFiles = savedFiles;
            this.containsSplitApks = containsSplitApks;
        }
    }

    private class AppListAdapter extends ArrayAdapter<AppRow> {
        AppListAdapter(MainActivity context, List<AppRow> apps) {
            super(context, R.layout.item_app_row, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_app_row, parent, false);
            }

            AppRow row = getItem(position);
            if (row == null) {
                return view;
            }

            ImageView iconView = view.findViewById(R.id.app_icon);
            TextView appNameView = view.findViewById(R.id.app_name);
            TextView packageView = view.findViewById(R.id.app_package);
            ImageButton expandButton = view.findViewById(R.id.app_expand_button);
            CheckBox selectCheckBox = view.findViewById(R.id.app_select_checkbox);
            View actionsContainer = view.findViewById(R.id.app_actions_container);
            MaterialButton settingsButton = view.findViewById(R.id.action_settings);
            MaterialButton backupButton = view.findViewById(R.id.action_backup);
            MaterialButton uninstallButton = view.findViewById(R.id.action_uninstall);

            iconView.setImageDrawable(row.appIcon);
            appNameView.setText(row.appName);
            packageView.setText(row.packageInfo.packageName);

            String packageName = row.packageInfo.packageName;
            boolean expanded = expandedPackages.contains(packageName);
            boolean selected = selectedPackages.contains(packageName);
            actionsContainer.animate().cancel();
            expandButton.animate().cancel();
            selectCheckBox.setChecked(selected);
            view.setBackgroundColor(selected ? 0x1A6F89B8 : Color.TRANSPARENT);

            if (selectionMode) {
                actionsContainer.setVisibility(View.GONE);
                expandButton.setVisibility(View.GONE);
                selectCheckBox.setVisibility(View.VISIBLE);
                expandButton.setOnClickListener(null);
            } else {
                selectCheckBox.setVisibility(View.GONE);
                expandButton.setVisibility(View.VISIBLE);
                actionsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                actionsContainer.setAlpha(1f);
                actionsContainer.setTranslationY(0f);
                expandButton.setImageResource(android.R.drawable.arrow_down_float);
                expandButton.setRotation(expanded ? 180f : 0f);
                expandButton.setContentDescription(getString(
                        expanded ? R.string.app_actions_collapse : R.string.app_actions_expand
                ));
                expandButton.setOnClickListener(v -> {
                    boolean isExpanded = toggleExpanded(packageName);
                    expandButton.setContentDescription(getString(
                            isExpanded ? R.string.app_actions_collapse : R.string.app_actions_expand
                    ));
                    animateActionsToggle(actionsContainer, expandButton, isExpanded);
                });
            }

            settingsButton.setOnClickListener(v -> openSystemAppSettings(row.packageInfo.packageName));
            backupButton.setOnClickListener(v -> confirmBackup(row));
            uninstallButton.setOnClickListener(v -> confirmUninstall(row));
            view.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleAppSelection(packageName);
                    return;
                }
                openDetailsScreen(packageName);
            });
            view.setOnLongClickListener(v -> {
                if (!selectionMode) {
                    selectionMode = true;
                    selectedPackages.clear();
                }
                if (!selectedPackages.contains(packageName)) {
                    selectedPackages.add(packageName);
                    appAdapter.notifyDataSetChanged();
                    updateBatchActionsUi();
                }
                return true;
            });

            if (convertView == null) {
                view.setAlpha(0f);
                view.setTranslationY(14f);
                view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(Math.min(position * 12L, 120L))
                        .setDuration(220)
                        .start();
            }

            return view;
        }
    }
}
