package com.darexsh.appvault;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

class AppDialogManager {
    interface LanguageChangedCallback {
        void onLanguageChanged();
    }

    private final MainActivity activity;
    private final AppLocaleManager appLocaleManager;

    AppDialogManager(MainActivity activity, AppLocaleManager appLocaleManager) {
        this.activity = activity;
        this.appLocaleManager = appLocaleManager;
    }

    void showActionConfirmDialog(
            Drawable icon,
            String title,
            String subtitle,
            String message,
            String confirmButtonText,
            boolean destructiveAction,
            Runnable onConfirm
    ) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_uninstall_confirm, null);
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

        AlertDialog dialog = new AlertDialog.Builder(activity)
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

    void showBackupConfirmDialog(AppRow row, Runnable onConfirmBackup) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_backup_confirm, null);
        ImageView appIcon = content.findViewById(R.id.backup_app_icon);
        TextView appName = content.findViewById(R.id.backup_app_name);
        TextView backupMessage = content.findViewById(R.id.backup_message);
        MaterialButton noButton = content.findViewById(R.id.backup_no_button);
        MaterialButton yesButton = content.findViewById(R.id.backup_yes_button);

        appIcon.setImageDrawable(row.appIcon);
        appName.setText(row.appName);
        backupMessage.setText(activity.getString(R.string.backup_confirm_message, row.appName));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .create();

        noButton.setOnClickListener(v -> dialog.dismiss());
        yesButton.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirmBackup.run();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    void showStoragePermissionDialog(Drawable appIcon, Runnable onOpenSettings) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_storage_permission, null);
        ImageView icon = content.findViewById(R.id.permission_app_icon);
        TextView appName = content.findViewById(R.id.permission_app_name);
        MaterialButton laterButton = content.findViewById(R.id.permission_later_button);
        MaterialButton openSettingsButton = content.findViewById(R.id.permission_open_settings_button);

        appName.setText(R.string.app_name);
        icon.setImageDrawable(appIcon);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .create();

        laterButton.setOnClickListener(v -> dialog.dismiss());
        openSettingsButton.setOnClickListener(v -> {
            dialog.dismiss();
            onOpenSettings.run();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    void showAppInfoDialog(String versionName, LanguageChangedCallback languageChangedCallback) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_app_info, null);
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
        appVersion.setText(activity.getString(R.string.app_info_version, versionName));
        appDescription.setText(R.string.app_info_description);

        String developerLabel = activity.getString(R.string.app_info_developer_label);
        String developerName = activity.getString(R.string.app_info_developer_name);
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
                activity.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sichler.daniel@gmail.com"))));
        openGithub.setOnClickListener(v ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/darexsh"))));
        openTelegramBot.setOnClickListener(v ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/darexsh_bot"))));
        openGithubProfile.setOnClickListener(v ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Darexsh"))));
        openCoffee.setOnClickListener(v ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/darexsh"))));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.app_info_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();

        changeLanguage.setOnClickListener(v -> showLanguageDialog(dialog, languageChangedCallback));

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(activity, R.color.header_tone_accent));
        }
    }

    private void showLanguageDialog(AlertDialog appInfoDialog, LanguageChangedCallback languageChangedCallback) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_language_picker, null);
        MaterialButton systemButton = content.findViewById(R.id.language_system_button);
        MaterialButton englishButton = content.findViewById(R.id.language_english_button);
        MaterialButton germanButton = content.findViewById(R.id.language_german_button);
        MaterialButton cancelButton = content.findViewById(R.id.language_cancel_button);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .create();

        String currentMode = appLocaleManager.getCurrentLanguageMode();
        appLocaleManager.styleLanguageButton(systemButton, "system".equals(currentMode));
        appLocaleManager.styleLanguageButton(englishButton, "en".equals(currentMode));
        appLocaleManager.styleLanguageButton(germanButton, "de".equals(currentMode));

        systemButton.setOnClickListener(v -> {
            appLocaleManager.applyLanguageMode("system");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            languageChangedCallback.onLanguageChanged();
        });
        englishButton.setOnClickListener(v -> {
            appLocaleManager.applyLanguageMode("en");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            languageChangedCallback.onLanguageChanged();
        });
        germanButton.setOnClickListener(v -> {
            appLocaleManager.applyLanguageMode("de");
            dialog.dismiss();
            if (appInfoDialog != null && appInfoDialog.isShowing()) {
                appInfoDialog.dismiss();
            }
            languageChangedCallback.onLanguageChanged();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
