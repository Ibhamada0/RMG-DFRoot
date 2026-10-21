package df.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GitHub Release based in-app updater for a sideloaded, consistently signed RMP.
 * Android's package installer retains final control and obtains explicit consent.
 */
public final class RmpUpdateActivity extends Activity {
    private static final String API =
        "https://api.github.com/repos/Ibhamada0/RMG-DFRoot/releases/latest";
    private static final String APK_NAME = "RMP-Original-DFRoot.apk";
    private static final String DOWNLOAD_PREFIX =
        "https://github.com/Ibhamada0/RMG-DFRoot/releases/download/";
    private static final long MAX_APK_BYTES = 160L * 1024L * 1024L;
    private static final int MAX_API_BYTES = 2 * 1024 * 1024;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Button checkButton;
    private Button downloadButton;
    private ProgressBar progress;
    private TextView status;
    private TextView releaseDetails;
    private Release latest;
    private File downloadedFile;
    private boolean awaitingInstallerPermission;
    private boolean working;
    private int originalTheme;

    private static final class Release {
        String name;
        String tag;
        String notes;
        String apkUrl;
        String sha256;
        long size;
        long versionCode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        originalTheme = RmgAppearance.index(this);
        setTheme(RmgAppearance.theme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rmp_update);
        if (getActionBar() != null) getActionBar().hide();
        RmpBottomNavigation.attach(this, RmpBottomNavigation.SETTINGS);
        findViewById(R.id.updateBack).setOnClickListener(v -> finish());
        checkButton = findViewById(R.id.updateCheck);
        downloadButton = findViewById(R.id.updateDownload);
        status = findViewById(R.id.updateStatus);
        releaseDetails = findViewById(R.id.updateRelease);
        progress = findViewById(R.id.updateProgress);
        TextView current = findViewById(R.id.updateCurrent);
        current.setText("INSTALLED: RMP v" + BuildConfig.VERSION_NAME
            + "  •  BUILD " + BuildConfig.VERSION_CODE);
        checkButton.setOnClickListener(v -> checkForUpdates());
        downloadButton.setOnClickListener(v -> confirmDownload());
        checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (originalTheme != RmgAppearance.index(this)) {
            recreate();
            return;
        }
        if (awaitingInstallerPermission && getPackageManager().canRequestPackageInstalls()
            && downloadedFile != null && downloadedFile.isFile()) {
            awaitingInstallerPermission = false;
            ui.post(this::launchInstaller);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    private void busy(boolean value, String message) {
        working = value;
        checkButton.setEnabled(!value);
        downloadButton.setEnabled(!value && latest != null
            && latest.versionCode > BuildConfig.VERSION_CODE);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    private void checkForUpdates() {
        if (working) return;
        latest = null;
        downloadedFile = null;
        downloadButton.setVisibility(View.GONE);
        releaseDetails.setText("Fetching latest published GitHub release...");
        busy(true, "Checking GitHub Releases...");
        worker.execute(() -> {
            try {
                Release release = fetchRelease();
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (release.versionCode <= BuildConfig.VERSION_CODE) {
                        releaseDetails.setText(release.name
                            + "\nYou're on the same or a newer version.");
                        busy(false, "RMP is up to date.");
                    } else {
                        latest = release;
                        releaseDetails.setText(release.name + "\nPublished release: "
                            + release.tag + "\nVerified SHA-256 metadata is available."
                            + "\nEstimated download: " + (release.size / 1024 / 1024) + " MiB");
                        downloadButton.setVisibility(View.VISIBLE);
                        busy(false, "Update available. Review and download when ready.");
                    }
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    releaseDetails.setText("Couldn't validate the latest release.");
                    busy(false, friendlyError(ex));
                });
            }
        });
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isEmpty() ? "Connection or verification error."
            : "Update check: " + msg;
    }

    private static byte[] boundedRead(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (out.size() + n > limit) throw new SecurityException("Release metadata too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String openText(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "RMP-App-Updater");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        try {
            if (conn.getResponseCode() != 200)
                throw new IllegalStateException("GitHub API HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream()) {
                return new String(boundedRead(in, MAX_API_BYTES), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static Release fetchRelease() throws Exception {
        JSONObject root = new JSONObject(openText(API));
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true))
            throw new SecurityException("Release is not a stable published build");
        Release result = new Release();
        result.tag = root.getString("tag_name");
        result.name = root.optString("name", result.tag);
        result.notes = root.optString("body", "");
        // Explicit build number in the CI release notes (not the GitHub run number).
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("RMP_VERSION_CODE\\s*[:=]\\s*(\\d+)")
            .matcher(result.notes);
        if (!matcher.find()) throw new SecurityException(
            "Release lacks RMP_VERSION_CODE. This build cannot be updated safely.");
        result.versionCode = Long.parseLong(matcher.group(1));
        JSONArray assets = root.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!APK_NAME.equals(asset.optString("name"))) continue;
            result.apkUrl = asset.getString("browser_download_url");
            result.size = asset.getLong("size");
            String digest = asset.optString("digest", "");
            if (!digest.startsWith("sha256:") || !digest.substring(7).matches("[a-fA-F0-9]{64}"))
                throw new SecurityException("Release APK has no valid SHA-256 digest");
            result.sha256 = digest.substring(7).toLowerCase(java.util.Locale.ROOT);
            break;
        }
        if (result.apkUrl == null || !result.apkUrl.startsWith(DOWNLOAD_PREFIX)
            || !result.apkUrl.endsWith("/" + APK_NAME)
            || result.size < 1024 || result.size > MAX_APK_BYTES)
            throw new SecurityException("Release APK asset is missing or invalid");
        if (result.versionCode <= 0) throw new SecurityException("Invalid build number");
        return result;
    }

    private void confirmDownload() {
        if (working || latest == null || latest.versionCode <= BuildConfig.VERSION_CODE) return;
        String note = latest.notes.replaceAll("(?m)^RMP_VERSION_CODE.*$", "").trim();
        if (note.length() > 650) note = note.substring(0, 650) + "...";
        new AlertDialog.Builder(this)
            .setTitle("Install " + latest.name + "?")
            .setMessage("RMP will download the APK from the official GitHub Release, "
                + "verify SHA-256 and its Android signing certificate, then ask "
                + "Android to install the update.\n\n" + note)
            .setPositiveButton("DOWNLOAD UPDATE", (dialog, which) -> download())
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void download() {
        if (working || latest == null) return;
        final Release requested = latest;
        busy(true, "Downloading signed APK...");
        worker.execute(() -> {
            File temp = null;
            try {
                File folder = new File(getCacheDir(), "rmp_updates");
                if (!folder.isDirectory() && !folder.mkdirs())
                    throw new IllegalStateException("Update cache is unavailable");
                temp = new File(folder, "rmp-update-partial.apk");
                File finalFile = new File(folder, "rmp-update-verified.apk");
                if (finalFile.exists() && !finalFile.delete())
                    throw new IllegalStateException("Unable to replace cached update");
                downloadAndVerify(requested, temp);
                checkInstallPackage(temp, requested.versionCode);
                if (!temp.renameTo(finalFile))
                    throw new IllegalStateException("Couldn't finalize verified APK");
                final File verified = finalFile;
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    downloadedFile = verified;
                    busy(false, "APK checksum and signing certificate verified.");
                    launchInstaller();
                });
            } catch (Exception ex) {
                if (temp != null) temp.delete();
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy(false, "Update blocked: " + friendlyError(ex));
                });
            }
        });
    }

    private void downloadAndVerify(Release release, File outFile) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)new URL(release.apkUrl).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(45000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RMP-App-Updater");
        try {
            if (conn.getResponseCode() != 200)
                throw new IllegalStateException("Download HTTP " + conn.getResponseCode());
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            byte[] block = new byte[32768];
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                int n;
                int lastPercent = -1;
                while ((n = in.read(block)) != -1) {
                    total += n;
                    if (total > MAX_APK_BYTES || total > release.size)
                        throw new SecurityException("Unexpected downloaded APK size");
                    sha.update(block, 0, n);
                    out.write(block, 0, n);
                    int percent = (int) (100 * total / release.size);
                    if (percent >= lastPercent + 10) {
                        lastPercent = percent;
                        final int progressPercent = percent;
                        ui.post(() -> {
                            if (!isFinishing() && !isDestroyed())
                                status.setText("Downloading signed APK: " + progressPercent + "%");
                        });
                    }
                }
                out.getFD().sync();
            }
            if (total != release.size) throw new SecurityException("APK length mismatch");
            byte[] actual = sha.digest();
            if (!MessageDigest.isEqual(actual, hexBytes(release.sha256)))
                throw new SecurityException("APK SHA-256 mismatch");
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] hexBytes(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++)
            result[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private static byte[][] signerHashes(PackageInfo info) throws Exception {
        if (info == null || info.signingInfo == null)
            throw new SecurityException("No APK signing information");
        Signature[] signers = info.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length == 0)
            throw new SecurityException("Missing APK certificate");
        byte[][] hashes = new byte[signers.length][];
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < signers.length; i++)
            hashes[i] = sha.digest(signers[i].toByteArray());
        Arrays.sort(hashes, (a,b)->Arrays.compareUnsigned(a,b));
        return hashes;
    }

    private void checkInstallPackage(File apk, long minimumVersion) throws Exception {
        PackageManager pm = getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(
            apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = pm.getPackageInfo(
            getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || !getPackageName().equals(archive.packageName))
            throw new SecurityException("Update has a different application ID");
        if (archive.getLongVersionCode() < minimumVersion
            || archive.getLongVersionCode() <= installed.getLongVersionCode())
            throw new SecurityException("APK build number isn't newer than installed");
        if (!Arrays.deepEquals(signerHashes(archive), signerHashes(installed)))
            throw new SecurityException("Signing key differs. Android cannot update this "
                + "installation in place. Use a consistently signed release; don't uninstall "
                + "until you've backed up any local data.");
    }

    private void launchInstaller() {
        if (downloadedFile == null || !downloadedFile.isFile()) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            awaitingInstallerPermission = true;
            new AlertDialog.Builder(this)
                .setTitle("Allow APK updates")
                .setMessage("Android needs your permission to install updates from RMP. "
                    + "Enable 'Allow from this source', then return to continue. "
                    + "Installation will still require confirmation.")
                .setPositiveButton("OPEN SETTINGS", (d,w) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(settings);
                })
                .setNegativeButton("NOT NOW", (d,w) -> awaitingInstallerPermission = false)
                .show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", downloadedFile);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.setClipData(ClipData.newRawUri("Verified RMP update", uri));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
            status.setText("Android installer opened. Confirm the update to keep app data.");
        } catch (Exception e) {
            status.setText("Unable to launch Android installer: " + friendlyError(e));
        }
    }
}
