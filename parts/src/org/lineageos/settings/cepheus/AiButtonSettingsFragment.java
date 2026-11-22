/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cepheus;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import lineageos.providers.LineageSettings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AiButtonSettingsFragment extends SettingsBasePreferenceFragment {

    private Preference mShortPressPref;
    private Preference mLongPressPref;

    private static class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.ai_button_settings, rootKey);

        mShortPressPref = findPreference(Constants.KEY_SHORT_PRESS_APP);
        mLongPressPref = findPreference(Constants.KEY_LONG_PRESS_APP);

        if (mShortPressPref != null) {
            mShortPressPref.setOnPreferenceClickListener(pref -> {
                showAppPickerDialog(Constants.KEY_SHORT_PRESS_APP, mShortPressPref);
                return true;
            });
        }

        if (mLongPressPref != null) {
            mLongPressPref.setOnPreferenceClickListener(pref -> {
                showAppPickerDialog(Constants.KEY_LONG_PRESS_APP, mLongPressPref);
                return true;
            });
        }

        updateSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    private void updateSummaries() {
        updatePreferenceSummary(mShortPressPref, Constants.KEY_SHORT_PRESS_APP);
        updatePreferenceSummary(mLongPressPref, Constants.KEY_LONG_PRESS_APP);
    }

    private void updatePreferenceSummary(Preference pref, String settingKey) {
        if (pref == null) {
            return;
        }
        String pkg = Settings.System.getStringForUser(
                requireContext().getContentResolver(),
                settingKey,
                UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(pkg)) {
            pref.setSummary(R.string.ai_button_default_action);
            return;
        }
        try {
            PackageManager pm = requireContext().getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            pref.setSummary(label);
        } catch (PackageManager.NameNotFoundException e) {
            pref.setSummary(pkg);
        }
    }

    private void syncSystemAssistSetting(
            ContentResolver resolver, String settingKey, String selectedPkg) {
        final boolean isShort = Constants.KEY_SHORT_PRESS_APP.equals(settingKey);
        final String lineageKey = isShort
                ? LineageSettings.System.KEY_ASSIST_ACTION
                : LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION;
        final String backupKey = isShort
                ? Constants.KEY_PREV_ASSIST_ACTION
                : Constants.KEY_PREV_ASSIST_LONG_PRESS_ACTION;
        final int defaultAction = isShort
                ? Constants.ACTION_SEARCH
                : Constants.ACTION_VOICE_SEARCH;

        if (!TextUtils.isEmpty(selectedPkg)) {
            int currentAction = LineageSettings.System.getIntForUser(
                    resolver, lineageKey, defaultAction, UserHandle.USER_CURRENT);
            if (currentAction != Constants.ACTION_NOTHING) {
                Settings.System.putIntForUser(
                        resolver, backupKey, currentAction, UserHandle.USER_CURRENT);
            }
            LineageSettings.System.putIntForUser(
                    resolver, lineageKey, Constants.ACTION_NOTHING, UserHandle.USER_CURRENT);
        } else {
            int currentAction = LineageSettings.System.getIntForUser(
                    resolver, lineageKey, defaultAction, UserHandle.USER_CURRENT);
            if (currentAction == Constants.ACTION_NOTHING) {
                int prevAction = Settings.System.getIntForUser(
                        resolver, backupKey, defaultAction, UserHandle.USER_CURRENT);
                if (prevAction == Constants.ACTION_NOTHING) {
                    prevAction = defaultAction;
                }
                LineageSettings.System.putIntForUser(
                        resolver, lineageKey, prevAction, UserHandle.USER_CURRENT);
            }
        }
    }

    private void showAppPickerDialog(String settingKey, Preference targetPref) {
        Context context = requireContext();
        PackageManager pm = context.getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        List<AppEntry> apps = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (!seenPackages.add(pkg)) {
                continue;
            }
            String label = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);
            apps.add(new AppEntry(pkg, label, icon));
        }

        Collator collator = Collator.getInstance();
        apps.sort(Comparator.comparing((AppEntry a) -> a.label, collator));

        // Prepend default option
        apps.add(0, new AppEntry("", context.getString(R.string.ai_button_default_action), null));

        AppListAdapter adapter = new AppListAdapter(context, apps);
        new AlertDialog.Builder(context)
                .setTitle(R.string.ai_button_select_app_title)
                .setAdapter(adapter, (dialog, which) -> {
                    AppEntry selected = apps.get(which);
                    ContentResolver resolver = context.getContentResolver();
                    Settings.System.putStringForUser(
                            resolver,
                            settingKey,
                            selected.packageName,
                            UserHandle.USER_CURRENT);
                    syncSystemAssistSetting(resolver, settingKey, selected.packageName);
                    updatePreferenceSummary(targetPref, settingKey);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class AppListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final List<AppEntry> mItems;

        AppListAdapter(Context context, List<AppEntry> items) {
            mInflater = LayoutInflater.from(context);
            mItems = items;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.layout.app_list_item, parent, false);
            }
            AppEntry item = getItem(position);
            ImageView iconView = view.findViewById(R.id.app_icon);
            TextView textView = view.findViewById(R.id.app_label);

            if (item.icon != null) {
                iconView.setImageDrawable(item.icon);
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setImageDrawable(null);
                iconView.setVisibility(View.INVISIBLE);
            }
            textView.setText(item.label);
            return view;
        }
    }
}
