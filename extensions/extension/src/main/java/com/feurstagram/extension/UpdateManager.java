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
    private static final String LEGACY_UPDATE_URL = "https://feurstagram.onrender.com/updates.json";
    public static final String DEFAULT_UPDATE_URL = "https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/updates.json";

    private UpdateManager() {}

    public static String getUpdateUrl() {
        Context context = Config.getAppContext();
        if (context == null) return DEFAULT_UPDATE_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL);
        if (value == null || value.trim().isEmpty() || LEGACY_UPDATE_URL.equals(value.trim())) {
            prefs.edit().putString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL).apply();
            return DEFAULT_UPDATE_URL;
        }
        return value.trim();
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

    public static final class UpdateCheckResult {
        public final boolean updateAvailable;
        public final Release release;
        public final File downloadedApk;
        public final String message;

        public UpdateCheckResult(boolean updateAvailable, Release release, File downloadedApk, String message) {
            this.updateAvailable = updateAvailable;
            this.release = release;
            this.downloadedApk = downloadedApk;
            this.message = message == null ? "" : message;
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
                return new Release(null, url, "Update manifest unavailable.");
            }
            byte[] buffer = readFully(connection.getInputStream());
            String text = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
            return parseRelease(text);
        } catch (Throwable t) {
            return new Release(null, updateUrl == null || updateUrl.trim().isEmpty() ? DEFAULT_UPDATE_URL : updateUrl.trim(),
                    "Unable to fetch remote update metadata.");
        }
    }

    public static Release parseRelease(String jsonText) {
        try {
            if (jsonText == null || jsonText.trim().isEmpty()) {
                return new Release(null, DEFAULT_UPDATE_URL, "No release metadata available.");
            }
            String version = unescapeJsonString(findString(jsonText, "version"));
            String apkUrl = unescapeJsonString(findString(jsonText, "apkUrl"));
            if (apkUrl == null || apkUrl.isEmpty()) apkUrl = unescapeJsonString(findString(jsonText, "downloadUrl"));
            String notes = unescapeJsonString(findString(jsonText, "notes"));
            if (version == null || version.isEmpty()) version = null;
            if (apkUrl == null || apkUrl.isEmpty()) apkUrl = DEFAULT_UPDATE_URL;
            if (notes == null || notes.isEmpty()) notes = "Release notes unavailable.";
            return new Release(version, apkUrl, notes);
        } catch (Throwable t) {
            return new Release(null, DEFAULT_UPDATE_URL, "Unable to parse remote update metadata.");
        }
    }

    private static String findString(String jsonText, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"");
        Matcher matcher = pattern.matcher(jsonText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescapeJsonString(String value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"': c = '"'; break;
                    case '\\': c = '\\'; break;
                    case '/': c = '/'; break;
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'u':
                        if (i + 4 < value.length()) {
                            try {
                                String codePoint = value.substring(i + 1, i + 5);
                                c = (char) Integer.parseInt(codePoint, 16);
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                c = 'u';
                            }
                        }
                        break;
                    default: c = next; break;
                }
            }
            out.append(c);
        }
        return out.toString();
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

    public static UpdateCheckResult evaluateRelease(String currentVersion, Release release) {
        if (release == null) {
            return new UpdateCheckResult(false, null, null,
                    "Unable to read the update feed. The app could not confirm whether a newer version is available.");
        }
        if (release.version == null || release.version.trim().isEmpty()) {
            return new UpdateCheckResult(false, release, null,
                    "Unable to read the update feed. The app could not confirm whether a newer version is available.");
        }
        boolean available = shouldUpdate(currentVersion, release.version);
        String message = available
                ? "Update available: " + release.version + "\n" + release.notes
                : "No update required. Current version is already up to date.";
        return new UpdateCheckResult(available, release, null, message);
    }

    public static UpdateCheckResult checkForUpdate(Context context, String currentVersion, String updateUrl) {
        Release release = fetchRelease(updateUrl);
        UpdateCheckResult result = evaluateRelease(currentVersion, release);
        if (!result.updateAvailable || release == null || release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
            return result;
        }

        File downloadedApk = downloadApk(context, release.apkUrl);
        String message = result.message;
        if (downloadedApk != null) {
            message = message + "\nAPK downloaded to cache: " + downloadedApk.getAbsolutePath();
        } else {
            message = message + "\nThe APK could not be downloaded from the Cloudflare update feed.";
        }
        return new UpdateCheckResult(true, release, downloadedApk, message);
    }

    public static boolean shouldUpdate(String currentVersion, String remoteVersion) {
        if (currentVersion == null || remoteVersion == null) return false;
        int[] left = extractVersionParts(currentVersion);
        int[] right = extractVersionParts(remoteVersion);
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a < b) return true;
            if (a > b) return false;
        }
        return false;
    }

    private static int[] extractVersionParts(String value) {
        if (value == null) return new int[0];
        java.util.ArrayList<Integer> parts = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                // ignore malformed numeric fragments
            }
        }
        if (parts.isEmpty()) return new int[] {0};
        int[] result = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = parts.get(i);
        }
        return result;
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
