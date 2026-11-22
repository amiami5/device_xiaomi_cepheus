/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cepheus;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.android.internal.os.DeviceKeyHandler;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = "CepheusKeyHandler";

    private final Context mContext;
    private final Handler mHandler;
    private final PowerManager mPowerManager;
    private final Vibrator mVibrator;

    private String mShortPressApp;
    private String mLongPressApp;
    private boolean mLongPressTriggered;
    private boolean mIgnoreCurrentGesture;

    private final Runnable mLongPressRunnable = this::onLongPressTimeout;

    private void onLongPressTimeout() {
        mLongPressTriggered = true;
        if (!TextUtils.isEmpty(mLongPressApp)) {
            if (mVibrator != null && mVibrator.hasVibrator()) {
                mVibrator.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            }
            launchApp(mLongPressApp);
        }
    }

    public KeyHandler(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mPowerManager = context.getSystemService(PowerManager.class);
        mVibrator = context.getSystemService(Vibrator.class);

        loadSettings();

        ContentResolver resolver = mContext.getContentResolver();
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                loadSettings();
            }
        };
        resolver.registerContentObserver(
                Settings.System.getUriFor(Constants.KEY_SHORT_PRESS_APP),
                false, observer, UserHandle.USER_ALL);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Constants.KEY_LONG_PRESS_APP),
                false, observer, UserHandle.USER_ALL);
    }

    private void loadSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShortPressApp = Settings.System.getStringForUser(
                resolver, Constants.KEY_SHORT_PRESS_APP, UserHandle.USER_CURRENT);
        mLongPressApp = Settings.System.getStringForUser(
                resolver, Constants.KEY_LONG_PRESS_APP, UserHandle.USER_CURRENT);
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_ASSIST) {
            return event;
        }

        if (TextUtils.isEmpty(mShortPressApp) && TextUtils.isEmpty(mLongPressApp)) {
            return event;
        }

        final int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                mLongPressTriggered = false;
                mIgnoreCurrentGesture = false;

                if (mPowerManager != null && !mPowerManager.isInteractive()) {
                    boolean wakeEnabled = Settings.System.getIntForUser(
                            mContext.getContentResolver(),
                            Constants.KEY_ASSIST_WAKE_SCREEN,
                            0,
                            UserHandle.USER_CURRENT) == 1;
                    if (!wakeEnabled) {
                        mIgnoreCurrentGesture = true;
                        return null;
                    }
                    mPowerManager.wakeUp(
                            SystemClock.uptimeMillis(),
                            PowerManager.WAKE_REASON_WAKE_KEY,
                            "cepheus:ai_button");
                }

                if (!TextUtils.isEmpty(mLongPressApp)) {
                    mHandler.postDelayed(
                            mLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
            }
            return null;
        }

        if (action == KeyEvent.ACTION_UP) {
            mHandler.removeCallbacks(mLongPressRunnable);
            if (!mIgnoreCurrentGesture
                    && !mLongPressTriggered
                    && (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0
                    && !TextUtils.isEmpty(mShortPressApp)) {
                launchApp(mShortPressApp);
            }
            mIgnoreCurrentGesture = false;
            return null;
        }

        return null;
    }

    private void launchApp(String packageName) {
        try {
            PackageManager pm = mContext.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                Log.w(TAG, "No launch intent for package: " + packageName);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + packageName, e);
        }
    }
}
