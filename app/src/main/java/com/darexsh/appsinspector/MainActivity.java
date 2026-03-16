package com.darexsh.appsinspector;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
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
import androidx.core.content.ContextCompat;

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
    // TODO roadmap:
    // 1) Sort options (name, install date, update date, app size)
    // 2) Export app list (CSV/TXT with core metadata)
    // 3) Backup manager screen (list/open/share/delete backups)
    // 4) App size breakdown (APK/data/cache where available)
    // 5) Permissions viewer in details page
    // 6) Favorites / pinning important apps
    // 7) "What's new" section in app info

    private static final int FILTER_ALL = 0;
    private static final int FILTER_USER = 1;
    private static final int FILTER_SYSTEM = 2;

    private final List<AppRow> allAppRows = new ArrayList<>();
    private final List<AppRow> filteredAppRows = new ArrayList<>();
    private final Set<String> expandedPackages = new HashSet<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private final List<String> pendingBatchUninstallPackages = new ArrayList<>();
    private AppListAdapter appAdapter;
    private String currentSearchQuery = "";
    private int currentFilter = FILTER_USER;
    private boolean refreshAppsOnResume = false;
    private boolean selectionMode = false;
    private boolean batchUninstallInProgress = false;

    private TextInputEditText searchInput;
    private AutoCompleteTextView filterDropdown;
    private TextView headerSubtitle;
    private View batchActionsCard;
    private TextView batchSelectionCount;
    private MaterialButton batchSelectAllButton;
    private MaterialButton batchClearButton;
    private MaterialButton batchBackupButton;
    private MaterialButton batchUninstallButton;
    private MaterialButton batchDoneButton;
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
        TextView appInfoButton = findViewById(R.id.btn_app_info);
        batchActionsCard = findViewById(R.id.batch_actions_card);
        batchSelectionCount = findViewById(R.id.batch_selection_count);
        batchSelectAllButton = findViewById(R.id.batch_select_all);
        batchClearButton = findViewById(R.id.batch_clear);
        batchBackupButton = findViewById(R.id.batch_backup);
        batchUninstallButton = findViewById(R.id.batch_uninstall);
        batchDoneButton = findViewById(R.id.batch_done);

        appAdapter = new AppListAdapter(
                this,
                filteredAppRows
        );
        appListView.setAdapter(appAdapter);

        setupSearchInput();
        setupFilterDropdown();
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
        if (refreshAppsOnResume) {
            refreshAppsOnResume = false;
            reloadInstalledApps();
        }
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
        String[] options = {
                getString(R.string.filter_all),
                getString(R.string.filter_user),
                getString(R.string.filter_system)
        };

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
        batchBackupButton.setOnClickListener(v -> backupSelectedApps());
        batchUninstallButton.setOnClickListener(v -> confirmBatchUninstall());
        batchDoneButton.setOnClickListener(v -> exitSelectionMode());
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
        if (!selectionMode || selectedPackages.isEmpty()) {
            batchActionsCard.setVisibility(View.GONE);
            return;
        }
        batchActionsCard.setVisibility(View.VISIBLE);
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

        appAdapter.notifyDataSetChanged();
        updateHeaderSubtitle();
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
                refreshAppsOnResume = true;
                startActivity(uninstallIntent);
                return;
            }
            if (legacyDeleteIntent.resolveActivity(getPackageManager()) != null) {
                Log.d(TAG, "Launching ACTION_DELETE for " + packageName);
                refreshAppsOnResume = true;
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
        File apkCopy = copyApkToBackupFolder(row);
        if (apkCopy == null) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        MediaScannerConnection.scanFile(
                this,
                new String[]{apkCopy.getAbsolutePath()},
                new String[]{"application/vnd.android.package-archive"},
                null
        );
        Toast.makeText(this, getString(R.string.backup_saved, apkCopy.getName()), Toast.LENGTH_LONG).show();
    }

    private File copyApkToBackupFolder(AppRow row) {
        String sourceDir = row.packageInfo.applicationInfo.sourceDir;
        if (sourceDir == null) {
            return null;
        }

        File sourceFile = new File(sourceDir);
        if (!sourceFile.exists()) {
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
        String fileName = sanitizedName + " (" + row.packageInfo.packageName + ").apk";
        File apkCopy = new File(backupRoot, fileName);
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(apkCopy)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return apkCopy;
        } catch (IOException e) {
            return null;
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
        for (String packageName : new ArrayList<>(selectedPackages)) {
            AppRow row = findAppRow(packageName);
            if (row == null) {
                failed++;
                continue;
            }
            File savedFile = copyApkToBackupFolder(row);
            if (savedFile == null) {
                failed++;
                continue;
            }
            success++;
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{savedFile.getAbsolutePath()},
                    new String[]{"application/vnd.android.package-archive"},
                    null
            );
        }
        Toast.makeText(this, getString(R.string.batch_backup_result, success, failed), Toast.LENGTH_LONG).show();
    }

    private void confirmBatchUninstall() {
        if (selectedPackages.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.batch_uninstall_title)
                .setMessage(getString(R.string.batch_uninstall_message, selectedPackages.size()))
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> startBatchUninstall())
                .show();
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

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(this, R.color.header_tone_accent));
        }
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
            uninstallButton.setOnClickListener(v -> uninstallApp(row.packageInfo.packageName));
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
