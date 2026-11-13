package com.cosmerearchives.androidimageeditor;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INSTALL_PERMISSION = 1;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button check;
    private PackageInfo installed;
    private File pending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            installed = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }

        var panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getSystemWindowInsets();
            view.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });

        var title = new TextView(this);
        title.setText("Android Image Editor");
        title.setTextSize(28);
        panel.addView(title);
        var version = new TextView(this);
        version.setText("Installed version " + installed.versionName);
        panel.addView(version);
        if (installed.getLongVersionCode() >= 1001) {
            var milestone = new TextView(this);
            milestone.setText("Updater test build");
            panel.addView(milestone);
        }
        status = new TextView(this);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(status);
        check = new Button(this);
        check.setText("Check for Updates");
        check.setOnClickListener(view -> checkForUpdates());
        panel.addView(check);
        setContentView(panel);
        checkForUpdates();
    }

    private void checkForUpdates() {
        check.setEnabled(false);
        status.setText("Checking for updates…");
        var updater = new ReleaseUpdater(getApplicationContext(), installed);
        worker.execute(() -> {
            try {
                File apk = updater.downloadUpdate(message -> runOnUiThread(() -> {
                    if (!isDestroyed()) status.setText(message);
                }));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (apk == null) {
                        finishCheck("You're up to date.");
                    } else {
                        pending = apk;
                        requestInstallation();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        String message = error instanceof ReleaseUpdater.UpdateException
                                ? error.getMessage()
                                : "Check your internet connection and try again.";
                        finishCheck("Update failed. " + message);
                    }
                });
            }
        });
    }

    private void requestInstallation() {
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                status.setText("Allow installs from Android Image Editor in Settings, then return.");
                var settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settings, INSTALL_PERMISSION);
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", pending);
            var install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
            finishCheck("Android installer opened. Follow the system prompt to install.");
        } catch (RuntimeException error) {
            finishCheck("Android couldn't open the installer. Try checking for updates again.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != INSTALL_PERMISSION || pending == null) return;
        if (getPackageManager().canRequestPackageInstalls()) {
            requestInstallation();
        } else {
            finishCheck("Installation permission was declined. Check again to retry.");
        }
    }

    private void finishCheck(String message) {
        pending = null;
        status.setText(message);
        check.setEnabled(true);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
