package com.darexsh.appvault;

import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;

class AppLocaleManager {

    String getCurrentLanguageMode() {
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

    void applyLanguageMode(String mode) {
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

    void styleLanguageButton(MaterialButton button, boolean selected) {
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
}
