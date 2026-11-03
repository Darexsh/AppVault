package com.darexsh.appvault;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

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
                packageInfo = pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS)
                );
            } else {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
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
            TextView appSizeTotalView = findViewById(R.id.detail_app_size_total_value);
            TextView minSdkView = findViewById(R.id.detail_min_sdk_value);
            TextView dataDirView = findViewById(R.id.detail_data_dir_value);
            TextView sourceDirView = findViewById(R.id.detail_source_dir_value);
            TextView permissionsView = findViewById(R.id.detail_permissions_value);

            long apkSizeBytes = calculateApkSizeBytes(appInfo);

            iconView.setImageDrawable(appInfo.loadIcon(pm));
            appNameView.setText(appName);
            packageView.setText(packageName);
            versionView.setText(versionName);
            versionCodeView.setText(versionCode);
            targetSdkView.setText(targetSdk);
            firstInstallView.setText(firstInstall);
            appSizeTotalView.setText(formatBytes(apkSizeBytes));
            minSdkView.setText(minSdk);
            dataDirView.setText(dataDir);
            sourceDirView.setText(sourceDir);
            permissionsView.setText(buildPermissionsText(packageInfo));

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

    private long calculateApkSizeBytes(ApplicationInfo appInfo) {
        long total = 0L;
        total += getFileSizeSafe(appInfo.sourceDir);
        if (appInfo.splitSourceDirs != null) {
            for (String splitPath : appInfo.splitSourceDirs) {
                total += getFileSizeSafe(splitPath);
            }
        }
        return total;
    }

    private long getFileSizeSafe(String path) {
        if (path == null || path.trim().isEmpty()) {
            return 0L;
        }
        File file = new File(path);
        if (!file.exists()) {
            return 0L;
        }
        return file.length();
    }

    private String formatBytes(long bytes) {
        return Formatter.formatFileSize(this, Math.max(0L, bytes));
    }

    private String buildPermissionsText(PackageInfo packageInfo) {
        String[] permissions = packageInfo.requestedPermissions;
        if (permissions == null || permissions.length == 0) {
            return getString(R.string.permissions_none);
        }
        int[] permissionFlags = packageInfo.requestedPermissionsFlags;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < permissions.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            boolean granted = permissionFlags != null
                    && permissionFlags.length > i
                    && (permissionFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            String permissionName = permissions[i] == null ? "" : permissions[i];
            builder.append("- ")
                    .append(formatPermissionLabel(permissionName))
                    .append(" (")
                    .append(getString(granted ? R.string.permission_granted : R.string.permission_not_granted))
                    .append(")");
        }
        return builder.toString();
    }

    private String formatPermissionLabel(String permissionName) {
        if (permissionName == null || permissionName.trim().isEmpty()) {
            return getString(R.string.permission_unknown);
        }
        if ("android.permission.CAMERA".equals(permissionName)) return getString(R.string.perm_camera);
        if ("android.permission.RECORD_AUDIO".equals(permissionName)) return getString(R.string.perm_microphone);
        if ("android.permission.READ_CONTACTS".equals(permissionName)) return getString(R.string.perm_contacts_read);
        if ("android.permission.WRITE_CONTACTS".equals(permissionName)) return getString(R.string.perm_contacts_edit);
        if ("android.permission.GET_ACCOUNTS".equals(permissionName)) return getString(R.string.perm_accounts);
        if ("android.permission.ACCESS_FINE_LOCATION".equals(permissionName)) return getString(R.string.perm_location_precise);
        if ("android.permission.ACCESS_COARSE_LOCATION".equals(permissionName)) return getString(R.string.perm_location_approx);
        if ("android.permission.ACCESS_BACKGROUND_LOCATION".equals(permissionName)) return getString(R.string.perm_location_background);
        if ("android.permission.ACCESS_MEDIA_LOCATION".equals(permissionName)) return getString(R.string.perm_media_location);
        if ("android.permission.ACCESS_LOCAL_NETWORK".equals(permissionName)) return getString(R.string.perm_local_network);
        if ("android.permission.READ_EXTERNAL_STORAGE".equals(permissionName)) return getString(R.string.perm_media_read);
        if ("android.permission.WRITE_EXTERNAL_STORAGE".equals(permissionName)) return getString(R.string.perm_media_write);
        if ("android.permission.READ_MEDIA_IMAGES".equals(permissionName)) return getString(R.string.perm_images_read);
        if ("android.permission.READ_MEDIA_VIDEO".equals(permissionName)) return getString(R.string.perm_videos_read);
        if ("android.permission.READ_MEDIA_AUDIO".equals(permissionName)) return getString(R.string.perm_audio_read);
        if ("android.permission.POST_NOTIFICATIONS".equals(permissionName)) return getString(R.string.perm_notifications);
        if ("android.permission.READ_CALENDAR".equals(permissionName)) return getString(R.string.perm_calendar_read);
        if ("android.permission.WRITE_CALENDAR".equals(permissionName)) return getString(R.string.perm_calendar_edit);
        if ("android.permission.READ_PHONE_STATE".equals(permissionName)) return getString(R.string.perm_phone_status);
        if ("android.permission.READ_BASIC_PHONE_STATE".equals(permissionName)) return getString(R.string.perm_basic_phone_status);
        if ("android.permission.CALL_PHONE".equals(permissionName)) return getString(R.string.perm_phone_calls);
        if ("android.permission.READ_CALL_LOG".equals(permissionName)) return getString(R.string.perm_call_history_read);
        if ("android.permission.WRITE_CALL_LOG".equals(permissionName)) return getString(R.string.perm_call_history_edit);
        if ("android.permission.SEND_SMS".equals(permissionName)) return getString(R.string.perm_sms_send);
        if ("android.permission.RECEIVE_SMS".equals(permissionName)) return getString(R.string.perm_sms_receive);
        if ("android.permission.READ_SMS".equals(permissionName)) return getString(R.string.perm_sms_read);
        if ("android.permission.BLUETOOTH_CONNECT".equals(permissionName)) return getString(R.string.perm_bluetooth_devices);
        if ("android.permission.BLUETOOTH_SCAN".equals(permissionName)) return getString(R.string.perm_bluetooth_scan);
        if ("android.permission.BLUETOOTH".equals(permissionName)) return getString(R.string.perm_bluetooth_legacy);
        if ("android.permission.BLUETOOTH_ADMIN".equals(permissionName)) return getString(R.string.perm_bluetooth_settings);
        if ("android.permission.NEARBY_WIFI_DEVICES".equals(permissionName)) return getString(R.string.perm_wifi_nearby);
        if ("android.permission.INTERNET".equals(permissionName)) return getString(R.string.perm_internet);
        if ("android.permission.ACCESS_NETWORK_STATE".equals(permissionName)) return getString(R.string.perm_network_status);
        if ("android.permission.ACCESS_WIFI_STATE".equals(permissionName)) return getString(R.string.perm_wifi_status);
        if ("android.permission.CHANGE_NETWORK_STATE".equals(permissionName)) return getString(R.string.perm_change_network);
        if ("android.permission.CHANGE_WIFI_STATE".equals(permissionName)) return getString(R.string.perm_change_wifi);
        if ("android.permission.WAKE_LOCK".equals(permissionName)) return getString(R.string.perm_keep_awake);
        if ("android.permission.VIBRATE".equals(permissionName)) return getString(R.string.perm_vibration);
        if ("android.permission.RECEIVE_BOOT_COMPLETED".equals(permissionName)) return getString(R.string.perm_start_on_boot);
        if ("android.permission.USE_BIOMETRIC".equals(permissionName)) return getString(R.string.perm_biometric);
        if ("android.permission.USE_FINGERPRINT".equals(permissionName)) return getString(R.string.perm_fingerprint);
        if ("android.permission.MANAGE_EXTERNAL_STORAGE".equals(permissionName)) return getString(R.string.perm_manage_all_files);
        if ("android.permission.SCHEDULE_EXACT_ALARM".equals(permissionName)
                || "android.permission.USE_EXACT_ALARM".equals(permissionName)) {
            return getString(R.string.perm_exact_alarms);
        }
        if ("android.permission.REQUEST_INSTALL_PACKAGES".equals(permissionName)) return getString(R.string.perm_install_apps);
        if ("android.permission.REQUEST_DELETE_PACKAGES".equals(permissionName)) return getString(R.string.perm_uninstall_apps);
        if ("android.permission.QUERY_ALL_PACKAGES".equals(permissionName)) return getString(R.string.perm_view_installed_apps);
        if ("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS".equals(permissionName)) {
            return getString(R.string.perm_ignore_battery_optimizations);
        }
        if ("android.permission.FOREGROUND_SERVICE".equals(permissionName)) return getString(R.string.perm_foreground_service);
        if ("android.permission.FOREGROUND_SERVICE_CAMERA".equals(permissionName)) return getString(R.string.perm_foreground_camera);
        if ("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE".equals(permissionName)) return getString(R.string.perm_foreground_connected_device);
        if ("android.permission.FOREGROUND_SERVICE_DATA_SYNC".equals(permissionName)) return getString(R.string.perm_foreground_data_sync);
        if ("android.permission.FOREGROUND_SERVICE_HEALTH".equals(permissionName)) return getString(R.string.perm_foreground_health);
        if ("android.permission.FOREGROUND_SERVICE_LOCATION".equals(permissionName)) return getString(R.string.perm_foreground_location);
        if ("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK".equals(permissionName)) return getString(R.string.perm_foreground_media_playback);
        if ("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING".equals(permissionName)) return getString(R.string.perm_foreground_media_processing);
        if ("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION".equals(permissionName)) return getString(R.string.perm_foreground_screen_capture);
        if ("android.permission.FOREGROUND_SERVICE_MICROPHONE".equals(permissionName)) return getString(R.string.perm_foreground_microphone);
        if ("android.permission.FOREGROUND_SERVICE_PHONE_CALL".equals(permissionName)) return getString(R.string.perm_foreground_phone_call);
        if ("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING".equals(permissionName)) return getString(R.string.perm_foreground_remote_messaging);
        if ("android.permission.FOREGROUND_SERVICE_SPECIAL_USE".equals(permissionName)) return getString(R.string.perm_foreground_special_use);
        if ("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED".equals(permissionName)) return getString(R.string.perm_foreground_system_exempted);
        if ("android.permission.READ_MEDIA_VISUAL_USER_SELECTED".equals(permissionName)) {
            return getString(R.string.perm_selected_media_read);
        }
        if ("android.permission.READ_PHONE_NUMBERS".equals(permissionName)) return getString(R.string.perm_phone_numbers);
        if ("android.permission.ANSWER_PHONE_CALLS".equals(permissionName)) return getString(R.string.perm_answer_calls);
        if ("android.permission.ACCEPT_HANDOVER".equals(permissionName)) return getString(R.string.perm_call_handover);
        if ("android.permission.ACTIVITY_RECOGNITION".equals(permissionName)) return getString(R.string.perm_activity_recognition);
        if ("android.permission.BODY_SENSORS".equals(permissionName)
                || "android.permission.BODY_SENSORS_BACKGROUND".equals(permissionName)) {
            return getString(R.string.perm_body_sensors);
        }
        if ("android.permission.ACCESS_NOTIFICATION_POLICY".equals(permissionName)) {
            return getString(R.string.perm_notification_policy);
        }
        if ("android.permission.SYSTEM_ALERT_WINDOW".equals(permissionName)) return getString(R.string.perm_display_over_apps);
        if ("android.permission.PACKAGE_USAGE_STATS".equals(permissionName)) return getString(R.string.perm_app_usage);
        if ("android.permission.BLUETOOTH_ADVERTISE".equals(permissionName)) return getString(R.string.perm_bluetooth_advertise);
        if ("android.permission.REQUEST_COMPANION_PROFILE_GLASSES".equals(permissionName)) return getString(R.string.perm_companion_glasses);
        if ("android.permission.REQUEST_COMPANION_PROFILE_WATCH".equals(permissionName)) return getString(R.string.perm_companion_watch);
        if ("android.permission.CHANGE_WIFI_MULTICAST_STATE".equals(permissionName)) return getString(R.string.perm_wifi_multicast);
        if ("android.permission.GET_PACKAGE_SIZE".equals(permissionName)) return getString(R.string.perm_package_storage_size);
        if ("android.permission.INSTALL_SHORTCUT".equals(permissionName)) return getString(R.string.perm_install_shortcut);
        if ("android.permission.KILL_BACKGROUND_PROCESSES".equals(permissionName)) return getString(R.string.perm_stop_background_apps);
        if ("android.permission.MANAGE_DOCUMENTS".equals(permissionName)) return getString(R.string.perm_manage_documents);
        if ("android.permission.MANAGE_MEDIA".equals(permissionName)) return getString(R.string.perm_manage_media);
        if ("android.permission.MANAGE_ONGOING_CALLS".equals(permissionName)
                || "android.permission.MANAGE_OWN_CALLS".equals(permissionName)) {
            return getString(R.string.perm_manage_calls);
        }
        if ("android.permission.MODIFY_AUDIO_SETTINGS".equals(permissionName)) return getString(R.string.perm_audio_settings);
        if ("android.permission.NFC".equals(permissionName)) return getString(R.string.perm_nfc);
        if ("android.permission.NFC_PREFERRED_PAYMENT_INFO".equals(permissionName)) return getString(R.string.perm_nfc_payment_info);
        if ("android.permission.NFC_TRANSACTION_EVENT".equals(permissionName)) return getString(R.string.perm_nfc_payment_events);
        if ("android.permission.POST_PROMOTED_NOTIFICATIONS".equals(permissionName)) return getString(R.string.perm_promoted_notifications);
        if ("android.permission.READ_HOME_TIME_ZONE".equals(permissionName)) return getString(R.string.perm_home_time_zone);
        if ("android.permission.READ_SYNC_SETTINGS".equals(permissionName)) return getString(R.string.perm_sync_settings_read);
        if ("android.permission.WRITE_SYNC_SETTINGS".equals(permissionName)) return getString(R.string.perm_sync_settings_write);
        if ("android.permission.READ_SYNC_STATS".equals(permissionName)) return getString(R.string.perm_sync_activity);
        if ("android.permission.READ_VOICEMAIL".equals(permissionName)) return getString(R.string.perm_voicemail_read);
        if ("android.permission.WRITE_VOICEMAIL".equals(permissionName)) return getString(R.string.perm_voicemail_manage);
        if ("android.permission.RECEIVE_MMS".equals(permissionName)) return getString(R.string.perm_mms_receive);
        if ("android.permission.RECEIVE_WAP_PUSH".equals(permissionName)) return getString(R.string.perm_wap_push_receive);
        if ("android.permission.SET_ALARM".equals(permissionName)) return getString(R.string.perm_set_alarm);
        if ("android.permission.SET_WALLPAPER".equals(permissionName)) return getString(R.string.perm_set_wallpaper);
        if ("android.permission.TRANSMIT_IR".equals(permissionName)) return getString(R.string.perm_infrared);
        if ("android.permission.TURN_SCREEN_ON".equals(permissionName)) return getString(R.string.perm_turn_screen_on);
        if ("android.permission.USE_FULL_SCREEN_INTENT".equals(permissionName)) return getString(R.string.perm_full_screen_intent);
        if ("android.permission.USE_SIP".equals(permissionName)) return getString(R.string.perm_sip);
        if ("android.permission.UWB_RANGING".equals(permissionName)) return getString(R.string.perm_uwb_ranging);
        if ("com.google.android.c2dm.permission.RECEIVE".equals(permissionName)) return getString(R.string.perm_push_messages);
        if ("com.google.android.gms.permission.AD_ID".equals(permissionName)) return getString(R.string.perm_advertising_id);
        if ("com.google.android.providers.gsf.permission.READ_GSERVICES".equals(permissionName)) {
            return getString(R.string.perm_google_services_settings);
        }
        if ("com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE".equals(permissionName)) {
            return getString(R.string.perm_install_referrer);
        }
        if ("com.android.vending.BILLING".equals(permissionName)) return getString(R.string.perm_in_app_purchases);

        // Use a declared label when available, but reject labels that are still raw identifiers.
        try {
            CharSequence label = getPackageManager()
                    .getPermissionInfo(permissionName, 0)
                    .loadLabel(getPackageManager());
            if (label != null) {
                String localizedLabel = label.toString().trim();
                if (isReadablePermissionLabel(localizedLabel, permissionName)) {
                    return localizedLabel;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep fallback below.
        }
        return humanizePermissionConstant(permissionName);
    }

    private boolean isReadablePermissionLabel(String label, String permissionName) {
        if (label == null || label.isEmpty() || label.equalsIgnoreCase(permissionName)) {
            return false;
        }
        if (label.indexOf('.') >= 0 && !label.contains(" ")) {
            return false;
        }
        if (label.indexOf('_') >= 0 && !label.contains(" ")) {
            return false;
        }
        if (!label.contains(" ") && label.matches("[a-z]+([A-Z][a-zA-Z0-9]*)+")) {
            return false;
        }
        return !label.matches("[A-Z0-9_.]+") && !label.matches("[A-Z0-9_]+(\\.[A-Z0-9_]+)+");
    }

    private String humanizePermissionConstant(String permissionName) {
        String suffix = permissionName;
        int lastDot = suffix.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < suffix.length()) {
            suffix = suffix.substring(lastDot + 1);
        }
        String[] rawTokens = suffix.split("_");
        if (rawTokens.length == 0) {
            return getString(R.string.permission_unknown);
        }

        boolean german = isGermanLocaleActive();
        StringBuilder builder = new StringBuilder();
        for (String rawToken : rawTokens) {
            if (rawToken == null || rawToken.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String token = rawToken.toUpperCase(Locale.US);
            builder.append(german
                    ? germanPermissionToken(token)
                    : englishPermissionToken(token));
        }
        String text = builder.toString().trim();
        if (text.isEmpty()) {
            return getString(R.string.permission_unknown);
        }
        return text;
    }

    private boolean isGermanLocaleActive() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = getResources().getConfiguration().getLocales().isEmpty()
                    ? Locale.getDefault()
                    : getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = getResources().getConfiguration().locale;
        }
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    private String englishPermissionToken(String token) {
        switch (token) {
            case "SMS":
            case "MMS":
            case "NFC":
            case "UWB":
            case "USB":
            case "API":
            case "URI":
            case "ID":
            case "MIDI":
            case "VPN":
                return token;
            case "WIFI":
                return "Wi-Fi";
            default:
                break;
        }
        String lower = token.toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String germanPermissionToken(String token) {
        switch (token) {
            case "READ": return "Lesen";
            case "WRITE": return "Schreiben";
            case "ACCESS": return "Zugriff";
            case "COARSE": return "Ungefährer";
            case "FINE": return "Genauer";
            case "BACKGROUND": return "Hintergrund";
            case "LOCATION": return "Standort";
            case "CONTACTS": return "Kontakte";
            case "GET": return "Abrufen";
            case "ACCOUNTS": return "Konten";
            case "CALENDAR": return "Kalender";
            case "CALL": return "Anruf";
            case "LOG": return "Verlauf";
            case "PHONE": return "Telefon";
            case "SMS": return "SMS";
            case "MMS": return "MMS";
            case "NFC": return "NFC";
            case "UWB": return "UWB";
            case "USB": return "USB";
            case "API": return "API";
            case "URI": return "URI";
            case "ID": return "Kennung";
            case "MIDI": return "MIDI";
            case "VPN": return "VPN";
            case "SEND": return "Senden";
            case "RECEIVE": return "Empfangen";
            case "AUDIO": return "Audio";
            case "VIDEO": return "Video";
            case "IMAGE":
            case "IMAGES": return "Bilder";
            case "MEDIA": return "Medien";
            case "EXTERNAL": return "Externer";
            case "STORAGE": return "Speicher";
            case "NOTIFICATIONS": return "Benachrichtigungen";
            case "POST": return "Senden";
            case "BLUETOOTH": return "Bluetooth";
            case "WIFI": return "WLAN";
            case "NEARBY": return "Nahe";
            case "INTERNET": return "Internet";
            case "NETWORK": return "Netzwerk";
            case "STATE": return "Status";
            case "CHANGE": return "Ändern";
            case "MODIFY": return "Ändern";
            case "SYSTEM": return "System";
            case "ALERT": return "Warnung";
            case "WINDOW": return "Fenster";
            case "PACKAGES": return "Pakete";
            case "INSTALL": return "Installieren";
            case "DELETE": return "Löschen";
            case "REQUEST": return "Anfrage";
            case "QUERY": return "Abfrage";
            case "MANAGE": return "Verwalten";
            case "USE": return "Verwenden";
            case "SENSORS": return "Sensoren";
            case "BODY": return "Körper";
            case "ACTIVITY": return "Aktivität";
            case "RECOGNITION": return "Erkennung";
            case "CAMERA": return "Kamera";
            case "MICROPHONE": return "Mikrofon";
            case "RECORD": return "Aufzeichnen";
            case "EXACT": return "Exakt";
            case "ALARMS": return "Alarme";
            case "BOOT": return "Start";
            case "COMPLETED": return "Abgeschlossen";
            case "VIBRATE": return "Vibration";
            case "WAKE": return "Aufwecken";
            case "LOCK": return "Sperre";
            case "SCREEN": return "Bildschirm";
            case "BRIGHTNESS": return "Helligkeit";
            case "FLASHLIGHT": return "Taschenlampe";
            case "HEALTH": return "Gesundheit";
            case "STEP": return "Schritt";
            case "COUNT": return "Zähler";
            case "SERVICE": return "Dienst";
            case "FOREGROUND": return "Vordergrund";
            case "RUN": return "Ausführen";
            case "SHORTCUT": return "Verknüpfung";
            case "SETTINGS": return "Einstellungen";
            case "SCHEDULE": return "Planen";
            case "IGNORE": return "Ignorieren";
            case "BATTERY": return "Batterie";
            case "OPTIMIZATIONS": return "Optimierungen";
            default:
                String lower = token.toLowerCase(Locale.GERMAN);
                return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }
}
