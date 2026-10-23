/*
 * Copyright (C) 2026 amisuke
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amisuke.widevine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import vendor.xiaomi.hardware.mtdservice.V1_2.IMTService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MIUI の CloudService (alipay/provision) と同じ手順で Widevine の鍵を取り直す。
 *
 * 1. mtdservice の widevine_get_version でローカルのバージョンを読む
 * 2. clientData を空にしてサーバーに問い合わせ、サーバー側のバージョンを読む
 * 3. 違っていれば widevine_prepare → clientData 付きで問い合わせ → widevine_load
 *
 * 1 と 2 は読み取りだけで、TEE に書き込むのは 3 の widevine_load だけ。
 */
public class ProvisionActivity extends Activity {
    private static final String TAG = "WidevineProvision";

    private static final String URL_EXTERNAL_KEY =
            "https://find.api.micloud.xiaomi.net/mic/find/v4/anonymous/device/externalkey";
    private static final String KEY_TYPE = "widevine";
    private static final int TIMEOUT_MS = 30000;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private TextView mLog;
    private ScrollView mScroll;
    private Button mCheck;
    private Button mQuery;
    private Button mUpdate;

    private int mLocalVersion = Integer.MIN_VALUE;
    private int mServerVersion = Integer.MIN_VALUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setFitsSystemWindows(true);

        mCheck = new Button(this);
        mCheck.setText("1. 端末の状態を確認 (読み取りのみ)");
        mCheck.setOnClickListener(v -> run(this::check));
        root.addView(mCheck);

        mQuery = new Button(this);
        mQuery.setText("2. サーバーのバージョンを確認 (通信のみ)");
        mQuery.setEnabled(false);
        mQuery.setOnClickListener(v -> run(this::queryVersion));
        root.addView(mQuery);

        mUpdate = new Button(this);
        mUpdate.setText("3. 鍵を更新 (TEE に書き込み)");
        mUpdate.setEnabled(false);
        mUpdate.setOnClickListener(v -> confirmUpdate());
        root.addView(mUpdate);

        mScroll = new ScrollView(this);
        mLog = new TextView(this);
        mLog.setTextIsSelectable(true);
        mScroll.addView(mLog);
        root.addView(mScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private interface Step {
        void run() throws Exception;
    }

    private void run(Step step) {
        setButtons(false);
        mExecutor.execute(() -> {
            try {
                step.run();
            } catch (Exception e) {
                Log.e(TAG, "failed", e);
                log("エラー: " + e);
            } finally {
                runOnUiThread(this::refreshButtons);
            }
        });
    }

    private void setButtons(boolean enabled) {
        mCheck.setEnabled(enabled);
        mQuery.setEnabled(enabled);
        mUpdate.setEnabled(enabled);
    }

    private void refreshButtons() {
        mCheck.setEnabled(true);
        mQuery.setEnabled(mLocalVersion != Integer.MIN_VALUE);
        mUpdate.setEnabled(mServerVersion != Integer.MIN_VALUE);
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        runOnUiThread(() -> {
            mLog.append(msg + "\n");
            mScroll.post(() -> mScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private static IMTService service() throws Exception {
        IMTService service = IMTService.getService(true);
        if (service == null) {
            throw new IllegalStateException("IMTService が見つかりません");
        }
        return service;
    }

    private static String mask(String s) {
        if (s == null || s.length() <= 8) return s;
        return s.substring(0, 4) + "…" + s.substring(s.length() - 4) + " (" + s.length() + "文字)";
    }

    private void check() throws Exception {
        IMTService service = service();
        mLocalVersion = service.widevine_get_version();
        log("ローカルのバージョン: " + mLocalVersion);
        String fid = service.getFid();
        log("fid: " + (TextUtils.isEmpty(fid) ? "(空)" : mask(fid)));
        if (TextUtils.isEmpty(fid)) {
            mLocalVersion = Integer.MIN_VALUE;
            throw new IllegalStateException("fid を取得できません");
        }
    }

    private void queryVersion() throws Exception {
        String fid = service().getFid();
        JSONObject data = request(fid, "", true);
        mServerVersion = data.getInt("version");
        log("サーバーのバージョン: " + mServerVersion);
        log(mServerVersion == mLocalVersion
                ? "ローカルと同じです。更新は不要です。"
                : "ローカル (" + mLocalVersion + ") と違います。");
    }

    private void confirmUpdate() {
        String msg = "TEE に Widevine の鍵を書き込みます。\n"
                + "ローカル: " + mLocalVersion + " / サーバー: " + mServerVersion;
        if (mServerVersion == mLocalVersion) {
            msg += "\n\nバージョンは同じです。それでも実行しますか?";
        }
        new AlertDialog.Builder(this)
                .setTitle("鍵を更新")
                .setMessage(msg)
                .setPositiveButton("実行", (d, w) -> run(this::update))
                .setNegativeButton("やめる", null)
                .show();
    }

    private void update() throws Exception {
        IMTService service = service();
        String fid = service.getFid();
        String clientData = service.widevine_prepare();
        if (TextUtils.isEmpty(clientData)) {
            throw new IllegalStateException("widevine_prepare が空を返しました");
        }
        log("clientData を生成しました (" + clientData.length() + "文字)");

        JSONObject data = request(fid, clientData, false);
        String key = data.optString("encryptedPrivateKey");
        String sign = data.optString("sign");
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(sign)) {
            throw new IllegalStateException("サーバーの応答に鍵がありません");
        }
        log("鍵を受け取りました (version " + data.optInt("version") + ")");

        int ret = service.widevine_load(key, sign);
        log("widevine_load: " + ret);
        mLocalVersion = service.widevine_get_version();
        log("更新後のローカルのバージョン: " + mLocalVersion);
    }

    /** 鍵と署名を長さだけに置き換えた応答 (表示用) */
    private static String redact(JSONObject json) throws Exception {
        JSONObject copy = new JSONObject(json.toString());
        JSONObject data = copy.optJSONObject("data");
        if (data != null) {
            for (String k : new String[] {"encryptedPrivateKey", "sign"}) {
                if (data.has(k)) {
                    data.put(k, "(" + data.optString(k).length() + "文字)");
                }
            }
        }
        return copy.toString(2);
    }

    private JSONObject request(String fid, String clientData, boolean showResponse)
            throws Exception {
        String query = "fid=" + URLEncoder.encode(fid, "utf-8")
                + "&clientData=" + URLEncoder.encode(clientData, "utf-8")
                + "&keyType=" + KEY_TYPE;
        HttpURLConnection conn =
                (HttpURLConnection) new URL(URL_EXTERNAL_KEY + "?" + query).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            String body = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + status + ": " + body);
            }
            JSONObject json = new JSONObject(body);
            if (showResponse) {
                log("サーバーの応答:\n" + redact(json));
            }
            int code = json.getInt("code");
            if (code != 0) {
                throw new IllegalStateException("サーバーのエラー: " + body);
            }
            return json.getJSONObject("data");
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
