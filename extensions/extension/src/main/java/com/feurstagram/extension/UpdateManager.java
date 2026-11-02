package com.feurstagram.extension;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateManager {
    private static final String PREFS = "feurstagram_prefs";
    private static final String KEY_UPDATE_URL = "update_url";
    public static final String DEFAULT_UPDATE_URL = "https://10.212.4.235:8443/updates.json";

    private UpdateManager() {}

    public static String getUpdateUrl() {
        Context context = Config.getAppContext();
        if (context == null) return DEFAULT_UPDATE_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL);
        return value == null || value.trim().isEmpty() ? DEFAULT_UPDATE_URL : value.trim();
    }

    public static void setUpdateUrl(String value) {
        if (value == null) return;
        Context context = Config.getAppContext();
        if (context == null) return;
        String normalized = value.trim();
        if (normalized.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_UPDATE_URL, normalized)
                .apply();
    }

    public static final class Release {
        public final String version;
        public final String apkUrl;
        public final String notes;

        public Release(String version, String apkUrl, String notes) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    public static Release fetchRelease(String updateUrl) {
        try {
            String url = updateUrl == null || updateUrl.trim().isEmpty() ? getUpdateUrl() : updateUrl.trim();
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new Release(Config.getAppVersionName(), DEFAULT_UPDATE_URL, "Update manifest unavailable.");
            }
            byte[] buffer = readFully(connection.getInputStream());
            String text = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
            return parseRelease(text);
        } catch (Throwable t) {
            return new Release(Config.getAppVersionName(), DEFAULT_UPDATE_URL, "Unable to fetch remote update metadata.");
        }
    }

    public static Release parseRelease(String jsonText) {
        try {
            if (jsonText == null || jsonText.trim().isEmpty()) {
                return new Release("0.0.0", DEFAULT_UPDATE_URL, "No release metadata available.");
            }
            String version = findString(jsonText, "version");
            String apkUrl = findString(jsonText, "apkUrl");
            if (apkUrl == null || apkUrl.isEmpty()) apkUrl = findString(jsonText, "downloadUrl");
            String notes = findString(jsonText, "notes");
            if (version == null || version.isEmpty()) version = Config.getAppVersionName();
            if (apkUrl == null || apkUrl.isEmpty()) apkUrl = DEFAULT_UPDATE_URL;
            if (notes == null || notes.isEmpty()) notes = "Release notes unavailable.";
            return new Release(version, apkUrl, notes);
        } catch (Throwable t) {
            return new Release(Config.getAppVersionName(), DEFAULT_UPDATE_URL, "Unable to parse remote update metadata.");
        }
    }

    private static String findString(String jsonText, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\\\"]*)\"");
        Matcher matcher = pattern.matcher(jsonText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static byte[] readFully(InputStream inputStream) throws java.io.IOException {
        if (inputStream == null) return new byte[0];
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    public static boolean shouldUpdate(String currentVersion, String remoteVersion) {
        if (currentVersion == null || remoteVersion == null) return false;
        String[] left = currentVersion.split("[.-]");
        String[] right = remoteVersion.split("[.-]");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = parseVersionPart(i < left.length ? left[i] : "0");
            int b = parseVersionPart(i < right.length ? right[i] : "0");
            if (a < b) return true;
            if (a > b) return false;
        }
        return false;
    }

    private static int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Throwable t) {
            return 0;
        }
    }

    public static File downloadApk(Context context, String apkUrl) {
        if (context == null || apkUrl == null || apkUrl.trim().isEmpty()) return null;
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir == null) return null;
            File apkFile = new File(cacheDir, "feurstagram-update.apk");
            HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(apkFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            return apkFile.exists() ? apkFile : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Intent installIntent(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) return null;
        try {
            Uri apkUri = Uri.fromFile(apkFile);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getPackageName());
            }
            return intent;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean canInstallPackage(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        return pm != null && pm.canRequestPackageInstalls();
    }
}
