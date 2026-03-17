package com.darexsh.appvault;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.List;

class AppListAdapter extends ArrayAdapter<AppRow> {

    interface Listener {
        boolean isSelectionMode();

        boolean isExpanded(String packageName);

        boolean isSelected(String packageName);

        boolean toggleExpanded(String packageName);

        void onSettingsClicked(AppRow row);

        void onBackupClicked(AppRow row);

        void onUninstallClicked(AppRow row);

        void onAppClicked(String packageName);

        void onAppLongPressed(String packageName);
    }

    private final LayoutInflater inflater;
    private final Listener listener;

    AppListAdapter(MainActivity context, List<AppRow> apps, Listener listener) {
        super(context, R.layout.item_app_row, apps);
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_app_row, parent, false);
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
        boolean expanded = listener.isExpanded(packageName);
        boolean selected = listener.isSelected(packageName);
        actionsContainer.animate().cancel();
        expandButton.animate().cancel();
        selectCheckBox.setChecked(selected);
        view.setBackgroundColor(selected ? 0x1A6F89B8 : Color.TRANSPARENT);

        if (listener.isSelectionMode()) {
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
            expandButton.setContentDescription(getContext().getString(
                    expanded ? R.string.app_actions_collapse : R.string.app_actions_expand
            ));
            expandButton.setOnClickListener(v -> {
                boolean isExpanded = listener.toggleExpanded(packageName);
                expandButton.setContentDescription(getContext().getString(
                        isExpanded ? R.string.app_actions_collapse : R.string.app_actions_expand
                ));
                animateActionsToggle(actionsContainer, expandButton, isExpanded);
            });
        }

        settingsButton.setOnClickListener(v -> listener.onSettingsClicked(row));
        backupButton.setOnClickListener(v -> listener.onBackupClicked(row));
        uninstallButton.setOnClickListener(v -> listener.onUninstallClicked(row));
        view.setOnClickListener(v -> listener.onAppClicked(packageName));
        view.setOnLongClickListener(v -> {
            listener.onAppLongPressed(packageName);
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
}
