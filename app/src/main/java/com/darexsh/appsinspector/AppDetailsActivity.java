package com.darexsh.appsinspector;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.DateFormat;
import java.util.Date;

public class AppDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_details);

        Toolbar toolbar = findViewById(R.id.details_toolbar);
        toolbar.setOnClickListener(v -> finish());
        toolbar.setNavigationOnClickListener(v -> finish());

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null || packageName.trim().isEmpty()) {
            finish();
            return;
        }

        loadAppDetails(packageName.trim());
    }

    private void loadAppDetails(String packageName) {
        PackageManager pm = getPackageManager();

        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = pm.getPackageInfo(packageName, 0);
            }

            ApplicationInfo appInfo = packageInfo.applicationInfo;
            if (appInfo == null) {
                finish();
                return;
            }

            String appName = String.valueOf(pm.getApplicationLabel(appInfo));
            String versionName = packageInfo.versionName == null ? "N/A" : packageInfo.versionName;
            String versionCode = String.valueOf(getVersionCodeCompat(packageInfo));
            String targetSdk = String.valueOf(appInfo.targetSdkVersion);
            String minSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? String.valueOf(appInfo.minSdkVersion)
                    : "N/A";
            String dataDir = appInfo.dataDir == null ? "N/A" : appInfo.dataDir;
            String sourceDir = appInfo.sourceDir == null ? "N/A" : appInfo.sourceDir;
            String firstInstall = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(packageInfo.firstInstallTime));

            ImageView iconView = findViewById(R.id.detail_icon);
            TextView appNameView = findViewById(R.id.detail_app_name);
            TextView packageView = findViewById(R.id.detail_package_name);
            TextView versionView = findViewById(R.id.detail_version_value);
            TextView versionCodeView = findViewById(R.id.detail_version_code_value);
            TextView targetSdkView = findViewById(R.id.detail_target_sdk_value);
            TextView firstInstallView = findViewById(R.id.detail_first_install_value);
            TextView minSdkView = findViewById(R.id.detail_min_sdk_value);
            TextView dataDirView = findViewById(R.id.detail_data_dir_value);
            TextView sourceDirView = findViewById(R.id.detail_source_dir_value);

            iconView.setImageDrawable(appInfo.loadIcon(pm));
            appNameView.setText(appName);
            packageView.setText(packageName);
            versionView.setText(versionName);
            versionCodeView.setText(versionCode);
            targetSdkView.setText(targetSdk);
            firstInstallView.setText(firstInstall);
            minSdkView.setText(minSdk);
            dataDirView.setText(dataDir);
            sourceDirView.setText(sourceDir);

        } catch (PackageManager.NameNotFoundException e) {
            finish();
        }
    }

    private long getVersionCodeCompat(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }
}
