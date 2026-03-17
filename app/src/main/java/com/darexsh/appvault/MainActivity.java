package com.darexsh.appvault;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AppListAdapter.Listener {
    private final AppBackupManager appBackupManager = new AppBackupManager();
    private final AppLocaleManager appLocaleManager = new AppLocaleManager();
    private final AppCatalogManager appCatalogManager = new AppCatalogManager();
    private final SelectionManager selectionManager = new SelectionManager();
    private final List<String> pendingBatchUninstallPackages = new ArrayList<>();

    private AppDialogManager appDialogManager;
    private AppOperationsManager appOperationsManager;
    private PackageChangeWatcher packageChangeWatcher;
    private AppListAdapter appAdapter;

    private TextInputEditText searchInput;
    private AutoCompleteTextView filterDropdown;
    private AutoCompleteTextView sortDropdown;
    private TextView headerSubtitle;
    private View batchActionsCard;
    private TextView batchSelectionCount;
    private MaterialButton batchClearButton;
    private MaterialButton batchBackupButton;
    private MaterialButton batchUninstallButton;

    private boolean batchUninstallInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appDialogManager = new AppDialogManager(this, appLocaleManager);
        appOperationsManager = new AppOperationsManager(this, appBackupManager);
        packageChangeWatcher = new PackageChangeWatcher(this, this::reloadInstalledApps);

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
        MaterialButton batchSelectAllButton = findViewById(R.id.batch_select_all);
        batchClearButton = findViewById(R.id.batch_clear);
        batchBackupButton = findViewById(R.id.batch_backup);
        batchUninstallButton = findViewById(R.id.batch_uninstall);

        appAdapter = new AppListAdapter(this, appCatalogManager.getFilteredAppRows(), this);
        appListView.setAdapter(appAdapter);

        setupSearchInput();
        setupFilterDropdown();
        setupSortDropdown();
        setupBatchActions(batchSelectAllButton);
        appInfoButton.setOnClickListener(v -> showAppInfoDialog());

        promptForBackupStoragePermissionIfNeeded();
        animateScreenEntrance(headerCard, appListView);

        appCatalogManager.loadInstalledApps(getPackageManager());
        applyFiltersAndRefreshUi();
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
        packageChangeWatcher.register();
    }

    @Override
    protected void onStop() {
        super.onStop();
        packageChangeWatcher.unregister();
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

    private void setupSearchInput() {
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // no-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                appCatalogManager.setCurrentSearchQuery(s == null ? "" : s.toString());
                applyFiltersAndRefreshUi();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
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
        filterDropdown.setText(options[AppCatalogManager.FILTER_USER], false);
        filterDropdown.setOnClickListener(v -> filterDropdown.showDropDown());
        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            appCatalogManager.setCurrentFilter(position);
            applyFiltersAndRefreshUi();
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
        sortDropdown.setText(options[AppCatalogManager.SORT_NAME], false);
        sortDropdown.setOnClickListener(v -> sortDropdown.showDropDown());
        sortDropdown.setOnItemClickListener((parent, view, position, id) -> {
            appCatalogManager.setCurrentSort(position);
            applyFiltersAndRefreshUi();
        });
    }

    private void setupBatchActions(MaterialButton batchSelectAllButton) {
        batchSelectAllButton.setOnClickListener(v -> {
            selectionManager.selectAll(appCatalogManager.getFilteredAppRows());
            appAdapter.notifyDataSetChanged();
            updateBatchActionsUi();
        });
        batchClearButton.setOnClickListener(v -> exitSelectionMode());
        batchBackupButton.setOnClickListener(v -> confirmBatchBackup());
        batchUninstallButton.setOnClickListener(v -> confirmBatchUninstall());
        updateBatchActionsUi();
    }

    private void applyFiltersAndRefreshUi() {
        appCatalogManager.applyFilters(appBackupManager);
        appAdapter.notifyDataSetChanged();
        updateHeaderSubtitle();
    }

    private void reloadInstalledApps() {
        Set<String> existingPackages = appCatalogManager.reloadInstalledApps(getPackageManager());
        selectionManager.pruneToExistingPackages(existingPackages);
        applyFiltersAndRefreshUi();
        updateBatchActionsUi();
    }

    private void refreshFilterAndSortSelectionLabels() {
        if (filterDropdown != null) {
            String[] filterOptions = getFilterOptions();
            int filterIndex = Math.max(0, Math.min(appCatalogManager.getCurrentFilter(), filterOptions.length - 1));
            filterDropdown.setText(filterOptions[filterIndex], false);
        }
        if (sortDropdown != null) {
            String[] sortOptions = getSortOptions();
            int sortIndex = Math.max(0, Math.min(appCatalogManager.getCurrentSort(), sortOptions.length - 1));
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

    private void updateBatchActionsUi() {
        if (batchActionsCard == null) {
            return;
        }
        boolean shouldShow = selectionManager.isSelectionMode() && selectionManager.hasSelection();
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

        batchSelectionCount.setText(getString(R.string.batch_selected_count, selectionManager.getSelectedCount()));
        boolean hasSelection = selectionManager.hasSelection();
        batchClearButton.setEnabled(hasSelection);
        batchBackupButton.setEnabled(hasSelection);
        batchUninstallButton.setEnabled(hasSelection);
    }

    private void updateHeaderSubtitle() {
        String modeLabel;
        int currentFilter = appCatalogManager.getCurrentFilter();
        if (currentFilter == AppCatalogManager.FILTER_USER) {
            modeLabel = getString(R.string.filter_user);
        } else if (currentFilter == AppCatalogManager.FILTER_SYSTEM) {
            modeLabel = getString(R.string.filter_system);
        } else {
            modeLabel = getString(R.string.filter_all);
        }

        String subtitle = getString(
                R.string.header_subtitle_state,
                appCatalogManager.getFilteredCount(),
                appCatalogManager.getAllCount(),
                modeLabel
        );
        headerSubtitle.setText(subtitle);
    }

    private void openDetailsScreen(String packageName) {
        Intent intent = new Intent(this, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

    private void confirmUninstall(AppRow row) {
        appDialogManager.showActionConfirmDialog(
                row.appIcon,
                getString(R.string.uninstall_confirm_title),
                row.appName,
                getString(R.string.uninstall_confirm_message, row.appName),
                getString(R.string.uninstall_confirm_yes),
                true,
                () -> appOperationsManager.uninstallApp(row.packageInfo.packageName)
        );
    }

    private void confirmBackup(AppRow row) {
        appDialogManager.showBackupConfirmDialog(row, () -> appOperationsManager.backupAndShareApk(row));
    }

    private void confirmBatchUninstall() {
        if (!selectionManager.hasSelection()) {
            return;
        }
        appDialogManager.showActionConfirmDialog(
                getApplicationInfo().loadIcon(getPackageManager()),
                getString(R.string.batch_uninstall_title),
                appCatalogManager.buildSelectedAppNamesSubtitle(selectionManager.getSelectedPackages()),
                getString(R.string.batch_uninstall_message, selectionManager.getSelectedCount()),
                getString(R.string.uninstall_confirm_yes),
                true,
                this::startBatchUninstall
        );
    }

    private void confirmBatchBackup() {
        if (!selectionManager.hasSelection()) {
            return;
        }
        appDialogManager.showActionConfirmDialog(
                getApplicationInfo().loadIcon(getPackageManager()),
                getString(R.string.batch_backup_title),
                appCatalogManager.buildSelectedAppNamesSubtitle(selectionManager.getSelectedPackages()),
                getString(R.string.batch_backup_message, selectionManager.getSelectedCount()),
                getString(R.string.batch_backup_confirm_yes),
                false,
                () -> appOperationsManager.backupSelectedApps(selectionManager.getSelectedPackages(), appCatalogManager)
        );
    }

    private void startBatchUninstall() {
        pendingBatchUninstallPackages.clear();
        pendingBatchUninstallPackages.addAll(selectionManager.getSelectedPackages());
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
        appOperationsManager.uninstallApp(nextPackage);
    }

    private void promptForBackupStoragePermissionIfNeeded() {
        appOperationsManager.promptForBackupStoragePermissionIfNeeded(appDialogManager);
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

        appDialogManager.showAppInfoDialog(versionName, this::recreate);
    }

    private void exitSelectionMode() {
        selectionManager.exitSelectionMode();
        updateBatchActionsUi();
        appAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean isSelectionMode() {
        return selectionManager.isSelectionMode();
    }

    @Override
    public boolean isExpanded(String packageName) {
        return selectionManager.isExpanded(packageName);
    }

    @Override
    public boolean isSelected(String packageName) {
        return selectionManager.isSelected(packageName);
    }

    @Override
    public boolean toggleExpanded(String packageName) {
        return selectionManager.toggleExpanded(packageName);
    }

    @Override
    public void onSettingsClicked(AppRow row) {
        appOperationsManager.openSystemAppSettings(row.packageInfo.packageName);
    }

    @Override
    public void onBackupClicked(AppRow row) {
        confirmBackup(row);
    }

    @Override
    public void onUninstallClicked(AppRow row) {
        confirmUninstall(row);
    }

    @Override
    public void onAppClicked(String packageName) {
        if (selectionManager.isSelectionMode()) {
            selectionManager.toggleSelection(packageName);
            if (!selectionManager.hasSelection()) {
                exitSelectionMode();
                return;
            }
            appAdapter.notifyDataSetChanged();
            updateBatchActionsUi();
            return;
        }
        openDetailsScreen(packageName);
    }

    @Override
    public void onAppLongPressed(String packageName) {
        selectionManager.enterSelectionAndSelect(packageName);
        appAdapter.notifyDataSetChanged();
        updateBatchActionsUi();
    }
}
