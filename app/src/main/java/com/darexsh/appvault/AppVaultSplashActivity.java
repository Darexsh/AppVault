package com.darexsh.appvault;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppVaultSplashActivity extends Activity {
    private static final long MIN_SPLASH_DURATION_MS = 2400L;
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable launchCheck = this::maybeLaunchMain;
    private final Runnable cardDrop = this::dropApkCardIntoTray;

    private FrameLayout splashRoot;
    private ImageView backgroundView;
    private ImageView appTilesView;
    private ImageView backupTrayView;
    private ImageView apkCardView;
    private LinearLayout brandView;
    private TextView appNameView;
    private TextView subtitleView;
    private long splashStartTime;
    private boolean artworkLoadFinished;
    private boolean animationStarted;
    private boolean mainActivityLaunched;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        splashStartTime = SystemClock.elapsedRealtime();
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 32));
        setContentView(R.layout.activity_appvault_splash);

        splashRoot = findViewById(R.id.splash_root);
        backgroundView = findViewById(R.id.splash_background);
        appTilesView = findViewById(R.id.splash_app_tiles);
        backupTrayView = findViewById(R.id.splash_backup_tray);
        apkCardView = findViewById(R.id.splash_apk_card);
        brandView = findViewById(R.id.splash_brand);
        appNameView = findViewById(R.id.splash_app_name);
        subtitleView = findViewById(R.id.splash_subtitle);

        backgroundView.setAlpha(0f);
        appNameView.setAlpha(0f);
        appNameView.setTranslationY(dp(10));
        subtitleView.setAlpha(0f);
        subtitleView.setTranslationY(dp(10));
        DecelerateInterpolator easing = new DecelerateInterpolator();
        appNameView.animate().alpha(1f).translationY(0f).setStartDelay(90L).setDuration(420L).setInterpolator(easing).start();
        subtitleView.animate().alpha(1f).translationY(0f).setStartDelay(180L).setDuration(420L).setInterpolator(easing).start();

        loadSplashArtwork();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(launchCheck);
        mainHandler.removeCallbacks(cardDrop);
        super.onDestroy();
    }

    private void loadSplashArtwork() {
        IMAGE_EXECUTOR.execute(() -> {
            Bitmap background = decodeAsset("appvault_splash_background.png", 1);
            Bitmap tiles = decodeAsset("appvault_splash_tiles.png", 1);
            Bitmap apkCard = decodeAsset("appvault_splash_apk_card.png", 2);
            Bitmap backupTray = decodeAsset("appvault_splash_backup_tray.png", 2);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                backgroundView.setImageBitmap(background);
                backgroundView.animate().alpha(1f).setDuration(320L).start();
                appTilesView.setImageBitmap(tiles);
                apkCardView.setImageBitmap(apkCard);
                backupTrayView.setImageBitmap(backupTray);
                artworkLoadFinished = true;
                splashRoot.post(this::startSplashAnimationIfReady);
            });
        });
    }

    private Bitmap decodeAsset(String assetName, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream input = getAssets().open(assetName)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void startSplashAnimationIfReady() {
        if (!artworkLoadFinished || animationStarted || isFinishing() || isDestroyed()) {
            return;
        }

        int width = splashRoot.getWidth();
        int height = splashRoot.getHeight();
        if (width <= 0 || height <= 0) {
            splashRoot.post(this::startSplashAnimationIfReady);
            return;
        }

        float artScale = Math.min(width / (float) dp(393), height / (float) dp(849));
        int tilesSize = dp(330f * artScale);
        positionArtLayer(appTilesView, tilesSize, tilesSize, (int) (height * 0.23f));
        int trayWidth = dp(330f * artScale);
        int trayHeight = (int) (trayWidth * 0.5f);
        positionArtLayer(backupTrayView, trayWidth, trayHeight, (int) (height * 0.68f));
        int cardWidth = dp(125f * artScale);
        int cardHeight = (int) (cardWidth * 1.2f);
        positionArtLayer(apkCardView, cardWidth, cardHeight, (int) (height * 0.49f));
        int brandTopPadding = Math.min(dp(72), (int) (height * 0.07f));
        brandView.setPadding(dp(24), brandTopPadding, dp(24), 0);

        appTilesView.setAlpha(0f);
        appTilesView.setScaleX(0.82f);
        appTilesView.setScaleY(0.82f);
        appTilesView.setTranslationY(dp(18));
        backupTrayView.setAlpha(0f);
        backupTrayView.setScaleX(0.9f);
        backupTrayView.setScaleY(0.9f);
        backupTrayView.setTranslationY(dp(34));
        apkCardView.setAlpha(0f);
        apkCardView.setScaleX(0.82f);
        apkCardView.setScaleY(0.82f);
        apkCardView.setTranslationY(-dp(28));

        appNameView.setVisibility(View.VISIBLE);
        subtitleView.setVisibility(View.VISIBLE);
        appTilesView.setVisibility(View.VISIBLE);
        backupTrayView.setVisibility(View.VISIBLE);
        apkCardView.setVisibility(View.VISIBLE);

        animationStarted = true;
        splashStartTime = SystemClock.elapsedRealtime();
        DecelerateInterpolator easing = new DecelerateInterpolator();
        appTilesView.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(150L).setDuration(620L).setInterpolator(new OvershootInterpolator(0.7f)).start();
        backupTrayView.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(570L).setDuration(560L).setInterpolator(easing).start();
        apkCardView.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(980L).setDuration(520L).setInterpolator(easing).start();

        mainHandler.postDelayed(cardDrop, 1740L);
        mainHandler.postDelayed(launchCheck, MIN_SPLASH_DURATION_MS);
    }

    private void positionArtLayer(ImageView imageView, int width, int height, int topMargin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width,
                height,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        params.topMargin = topMargin;
        imageView.setLayoutParams(params);
    }

    private void dropApkCardIntoTray() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        apkCardView.animate()
                .translationY(dp(58))
                .scaleX(0.78f)
                .scaleY(0.78f)
                .alpha(0.12f)
                .setDuration(420L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> backupTrayView.animate()
                        .scaleX(1.035f)
                        .scaleY(0.94f)
                        .setDuration(130L)
                        .withEndAction(() -> backupTrayView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(190L)
                                .start())
                        .start())
                .start();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void maybeLaunchMain() {
        if (mainActivityLaunched || !animationStarted || isFinishing() || isDestroyed()) {
            return;
        }

        long elapsed = SystemClock.elapsedRealtime() - splashStartTime;
        long remaining = MIN_SPLASH_DURATION_MS - elapsed;
        if (!artworkLoadFinished || remaining > 0L) {
            mainHandler.postDelayed(launchCheck, Math.max(50L, remaining));
            return;
        }

        mainActivityLaunched = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
