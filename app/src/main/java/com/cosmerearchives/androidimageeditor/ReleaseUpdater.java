package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;

final class ReleaseUpdater {
    private static final String REPO = "cosmerearchives-creator/Android-Image-Editor";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long MAX_APK_BYTES = 100 * 1024 * 1024;
    private final Context context;
    private final PackageInfo installed;

    ReleaseUpdater(Context context, PackageInfo installed) {
        this.context = context;
        this.installed = installed;
    }

    File downloadUpdate(Consumer<String> status) throws Exception {
        JSONObject release = latestRelease();
        if (release == null) return null;
        String tag = release.getString("tag_name");
        int code = StableVersion.codeFromTag(tag);
        if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)
                || code <= installed.getLongVersionCode()) return null;

        String assetName = "android-image-editor-" + tag + ".apk";
        var assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            var asset = assets.getJSONObject(i);
            if (!assetName.equals(asset.optString("name"))) continue;
            String url = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + assetName;
            String digest = asset.optString("digest");
            long size = asset.optLong("size", -1);
            if (!url.equals(asset.optString("browser_download_url"))
                    || !"uploaded".equals(asset.optString("state"))
                    || !digest.matches("sha256:[a-f0-9]{64}") || size <= 0 || size > MAX_APK_BYTES) {
                throw new UpdateException("GitHub returned invalid APK metadata.");
            }
            status.accept("Update " + tag + " is available. Downloading…");
            return fetchApk(url, tag, code, size, digest, status);
        }
        throw new UpdateException("The latest release has no Android APK.");
    }

    private JSONObject latestRelease() throws Exception {
        var connection = connect(LATEST);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        try {
            int response = connection.getResponseCode();
            if (response == 404) return null;
            if (response != 200) throw new UpdateException("GitHub returned HTTP " + response + ".");
            try (var in = connection.getInputStream(); var out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (out.size() + n > 1024 * 1024) {
                        throw new UpdateException("GitHub release metadata is too large.");
                    }
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally {
            connection.disconnect();
        }
    }

    private File fetchApk(String url, String tag, int code, long size, String expectedDigest,
            Consumer<String> status) throws Exception {
        var directory = new File(context.getCacheDir(), "verified-updates");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new UpdateException("Couldn't prepare storage for the update.");
        }
        var apk = new File(directory, "update-" + code + ".apk");
        var partial = File.createTempFile("update-", ".partial.apk", context.getCacheDir());
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var connection = connect(url);
            long total = 0;
            int lastPercent = -5;
            try {
                if (connection.getResponseCode() != 200) {
                    throw new UpdateException("GitHub couldn't serve the APK.");
                }
                try (var in = connection.getInputStream(); var out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32768];
                    for (int n; (n = in.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                        total += n;
                        if (total > size) throw new UpdateException("The APK exceeds its published size.");
                        out.write(buffer, 0, n);
                        digest.update(buffer, 0, n);
                        int percent = (int) (total * 100 / size);
                        if (percent >= lastPercent + 5 || percent == 100) {
                            status.accept("Downloading " + tag + "… " + percent + "%");
                            lastPercent = percent;
                        }
                    }
                }
            } finally {
                connection.disconnect();
            }
            var hash = new StringBuilder("sha256:");
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value));
            if (total != size || !expectedDigest.contentEquals(hash)) {
                throw new UpdateException("The download didn't match the published APK.");
            }
            status.accept("Verifying " + tag + "…");
            verifyPackage(partial, tag, code);
            if (!partial.renameTo(apk) && !(apk.delete() && partial.renameTo(apk))) {
                throw new UpdateException("Couldn't save the verified APK.");
            }
            return apk;
        } finally {
            partial.delete();
        }
    }

    private void verifyPackage(File file, String tag, int code) throws UpdateException {
        PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(
                file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !installed.packageName.equals(candidate.packageName)
                || candidate.getLongVersionCode() != code
                || !tag.substring(1).equals(candidate.versionName)
                || candidate.signingInfo == null || installed.signingInfo == null) {
            throw new UpdateException("The APK identity doesn't match the release.");
        }
        Signature[] current = installed.signingInfo.getApkContentsSigners();
        Signature[] next = candidate.signingInfo.getApkContentsSigners();
        if (current == null || current.length != 1 || !Arrays.equals(current, next)) {
            throw new UpdateException("The APK signing certificate doesn't match this installation.");
        }
    }

    private static HttpsURLConnection connect(String url) throws IOException {
        var connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "Android-Image-Editor");
        return connection;
    }

    static final class UpdateException extends IOException {
        UpdateException(String message) {
            super(message);
        }
    }
}
