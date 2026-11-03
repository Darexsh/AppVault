package com.darexsh.appvault;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppListAdapter.Listener {
    private final AppBackupManager appBackupManager = new AppBackupManager();
    private final AppLocaleManager appLocaleManager = new AppLocaleManager();
    private final AppCatalogManager appCatalogManager = new AppCatalogManager();
    private final SelectionManager selectionManager = new SelectionManager();
    private final List<String> pendingBatchUninstallPackages = new ArrayList<>();
    private final ExecutorService catalogLoader = Executors.newSingleThreadExecutor();

    private AppDialogManager appDialogManager;
    private AppOperationsManager appOperationsManager;
    private PackageChangeWatcher packageChangeWatcher;
    private AppListAdapter appAdapter;
    private ActivityResultLauncher<Intent> installBackupPicker;

    private TextInputEditText searchInput;
    private AutoCompleteTextView filterDropdown;
    private AutoCompleteTextView sortDropdown;
    private TextView appCount;
    private TextView emptyView;
    private View batchActionsBar;
    private TextView batchSelectionCount;
    private MaterialButton batchClearButton;
    private MaterialButton batchBackupButton;
    private MaterialButton batchUninstallButton;

    private boolean batchUninstallInProgress = false;
    private boolean catalogLoadInProgress = false;
    private boolean catalogReloadPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installBackupPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                getContentResolver().takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                );
                            } catch (SecurityException ignored) {
                                // The picker may provide a temporary read grant instead.
                            }
                            ApkBackupInstaller.install(this, uri);
                        }
                    }
                }
        );
        setContentView(R.layout.activity_main);

        appDialogManager = new AppDialogManager(this, appLocaleManager);
        appOperationsManager = new AppOperationsManager(this, appBackupManager);
        packageChangeWatcher = new PackageChangeWatcher(this, this::reloadInstalledApps);

        ListView appListView = findViewById(R.id.app_list);
        emptyView = findViewById(R.id.empty_view);

        appListView.setEmptyView(emptyView);

        appCount = findViewById(R.id.app_count);
        searchInput = findViewById(R.id.search_input);
        filterDropdown = findViewById(R.id.filter_dropdown);
        sortDropdown = findViewById(R.id.sort_dropdown);
        TextView appInfoButton = findViewById(R.id.btn_app_info);
        MaterialButton installBackupButton = findViewById(R.id.btn_install_backup);
        batchActionsBar = findViewById(R.id.batch_actions_bar);
        batchSelectionCount = findViewById(R.id.batch_selection_count);
        MaterialButton batchSelectAllButton = findViewById(R.id.batch_select_all);
        batchClearButton = findViewById(R.id.batch_clear);
        batchBackupButton = findViewById(R.id.batch_backup);
        batchUninstallButton = findViewById(R.id.batch_uninstall);

        appAdapter = new AppListAdapter(this, appCatalogManager.getFilteredAppRows(), this);
        appListView.setAdapter(appAdapter);
        updateAppCount();

        setupSearchInput();
        setupFilterDropdown();
        setupSortDropdown();
        setupBatchActions(batchSelectAllButton);
        appInfoButton.setOnClickListener(v -> showAppInfoDialog());
        installBackupButton.setOnClickListener(v -> openInstallBackupPicker());

        promptForBackupStoragePermissionIfNeeded();
    }

    private void openInstallBackupPicker() {
        if (!appOperationsManager.ensureInstallPermission()) {
            return;
        }
        Intent pickerIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickerIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickerIntent.setType("*/*");
        pickerIntent.putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:App_Backups"
                )
        );
        installBackupPicker.launch(pickerIntent);
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
    protected void onDestroy() {
        catalogLoader.shutdownNow();
        if (appAdapter != null) {
            appAdapter.close();
        }
        super.onDestroy();
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
        filterDropdown.setText(getString(
                R.string.filter_selected_value,
                options[AppCatalogManager.FILTER_USER]
        ), false);
        filterDropdown.setOnClickListener(v -> filterDropdown.showDropDown());
        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            appCatalogManager.setCurrentFilter(position);
            filterDropdown.setText(getString(R.string.filter_selected_value, options[position]), false);
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
        sortDropdown.setText(getString(
                R.string.sort_selected_value,
                options[AppCatalogManager.SORT_NAME]
        ), false);
        sortDropdown.setOnClickListener(v -> sortDropdown.showDropDown());
        sortDropdown.setOnItemClickListener((parent, view, position, id) -> {
            appCatalogManager.setCurrentSort(position);
            sortDropdown.setText(getString(R.string.sort_selected_value, options[position]), false);
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
        updateAppCount();
    }

    private void reloadInstalledApps() {
        if (catalogLoadInProgress) {
            catalogReloadPending = true;
            return;
        }

        catalogLoadInProgress = true;
        if (appCatalogManager.getAllCount() == 0) {
            emptyView.setText(R.string.loading_apps);
        }

        PackageManager packageManager = getPackageManager();
        catalogLoader.execute(() -> {
            List<AppRow> installedRows;
            try {
                installedRows = AppCatalogManager.queryInstalledApps(packageManager);
            } catch (RuntimeException e) {
                installedRows = null;
            }

            List<AppRow> loadedRows = installedRows;
            runOnUiThread(() -> {
                catalogLoadInProgress = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (loadedRows != null) {
                    Set<String> existingPackages = appCatalogManager.setInstalledApps(loadedRows);
                    selectionManager.pruneToExistingPackages(existingPackages);
                    emptyView.setText(R.string.no_apps_found);
                    applyFiltersAndRefreshUi();
                    updateBatchActionsUi();
                } else if (appCatalogManager.getAllCount() == 0) {
                    emptyView.setText(R.string.apps_load_failed);
                }

                if (catalogReloadPending) {
                    catalogReloadPending = false;
                    reloadInstalledApps();
                }
            });
        });
    }

    private void refreshFilterAndSortSelectionLabels() {
        if (filterDropdown != null) {
            String[] filterOptions = getFilterOptions();
            int filterIndex = Math.max(0, Math.min(appCatalogManager.getCurrentFilter(), filterOptions.length - 1));
            filterDropdown.setText(getString(
                    R.string.filter_selected_value,
                    filterOptions[filterIndex]
            ), false);
        }
        if (sortDropdown != null) {
            String[] sortOptions = getSortOptions();
            int sortIndex = Math.max(0, Math.min(appCatalogManager.getCurrentSort(), sortOptions.length - 1));
            sortDropdown.setText(getString(
                    R.string.sort_selected_value,
                    sortOptions[sortIndex]
            ), false);
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
        if (batchActionsBar == null) {
            return;
        }
        boolean shouldShow = selectionManager.isSelectionMode() && selectionManager.hasSelection();
        boolean isVisible = batchActionsBar.getVisibility() == View.VISIBLE;

        batchActionsBar.animate().cancel();

        if (!shouldShow) {
            if (isVisible) {
                batchActionsBar.animate()
                        .alpha(0f)
                        .translationY(16f)
                        .setDuration(140)
                        .withEndAction(() -> {
                            batchActionsBar.setVisibility(View.GONE);
                            batchActionsBar.setAlpha(1f);
                            batchActionsBar.setTranslationY(0f);
                        })
                        .start();
            } else {
                batchActionsBar.setVisibility(View.GONE);
            }
            return;
        }

        if (!isVisible) {
            batchActionsBar.setAlpha(0f);
            batchActionsBar.setTranslationY(16f);
            batchActionsBar.setVisibility(View.VISIBLE);
            batchActionsBar.animate()
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

    private void updateAppCount() {
        appCount.setText(getString(
                R.string.app_count_state,
                appCatalogManager.getFilteredCount(),
                appCatalogManager.getAllCount()
        ));
    }

    private void openDetailsScreen(String packageName) {
        Intent intent = new Intent(this, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
        String versionName = String.valueOf(R.string.app_info_version);
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
