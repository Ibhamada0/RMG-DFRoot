package df.root;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements IReporter {
    private static final String TAG = "dfroot";

    private int rmgThemeIndex;
    private Button btnRun;
    private ScrollView outputScroll;
    private TextView outputView;
    private Spinner spinnerSuManager;
    private ProgressBar progressRoot;
    private TextView tvStatus;
    private TextView tvStatusTag;
    private TextView tvStatusDetail;
    private TextView tvManagerHint;
    private Context mDeCtx;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private int mValidSuManagerPos = 0;
    private int mManagerCount = 0;
    private boolean mRunning = false;
    private volatile String mLogSessionId;
    // Kept in memory only: reopening the app later must not unexpectedly run root.
    private String mPendingManagerPackage;
    private int mPendingPermissionIndex = -1;
    private long mInstallRequestedAt;
    private boolean mContinueStartAfterInstall;
    private static final long INSTALL_FLOW_TIMEOUT_MS = 10 * 60 * 1000L;
    private final RmgOfflineManagerInstaller.InstallListener mStartInstallListener =
        new RmgOfflineManagerInstaller.InstallListener() {
            @Override
            public void onPermissionRequired(int managerIndex) {
                mPendingPermissionIndex = managerIndex;
            }

            @Override
            public void onInstallerOpened(String packageName) {
                mPendingManagerPackage = packageName;
                mInstallRequestedAt = SystemClock.elapsedRealtime();
                showStatus("Waiting for Manager installation", "INSTALLING",
                    "Confirm installation in Android. RMP will continue only if a compatible Manager is installed.",
                    R.attr.rmgIce);
            }
        };

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        String session = mLogSessionId;
        if (session != null) RmpSessionHistory.append(this, session, msg);
        mMain.post(() -> {
            if (isFinishing() || isDestroyed() || outputView == null) return;
            outputView.append(msg);
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        rmgThemeIndex = RmgAppearance.index(this);
        setTheme(RmgAppearance.theme(this));
        super.onCreate(savedInstanceState);
        mDeCtx = createDeviceProtectedStorageContext();
        setContentView(R.layout.activity_main);
        if (getActionBar() != null) getActionBar().hide();
        RmpBottomNavigation.attach(this, RmpBottomNavigation.HOME);

        btnRun = findViewById(R.id.btnRun);
        outputScroll = findViewById(R.id.outputScroll);
        outputView = findViewById(R.id.outputView);
        spinnerSuManager = findViewById(R.id.spinnerSuManager);
        progressRoot = findViewById(R.id.progressRoot);
        tvStatus = findViewById(R.id.tvStatus);
        tvStatusTag = findViewById(R.id.tvStatusTag);
        tvStatusDetail = findViewById(R.id.tvStatusDetail);
        tvManagerHint = findViewById(R.id.tvManagerHint);

        ((TextView) findViewById(R.id.tvDeviceModel)).setText(
            Build.MANUFACTURER + " " + Build.MODEL);
        ((TextView) findViewById(R.id.tvAndroidVersion)).setText(
            "Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        ((TextView) findViewById(R.id.tvDeviceBuild)).setText(Build.DISPLAY);
        ((TextView) findViewById(R.id.tvKernel)).setText(
            System.getProperty("os.version", "Unavailable"));
        refreshSelinux();

        findViewById(R.id.btnSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnAppUpdate).setOnClickListener(v ->
            startActivity(new Intent(this, RmpUpdateActivity.class)));
        findViewById(R.id.btnInfo).setOnClickListener(v ->
            startActivity(new Intent(this, AppInformationActivity.class)));
        findViewById(R.id.btnOfflineManager).setOnClickListener(v -> {
            mContinueStartAfterInstall = false;
            RmgOfflineManagerInstaller.choose(this, mStartInstallListener);
        });
        findViewById(R.id.btnCopyLogs).setOnClickListener(v -> {
            ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText(
                    "RMP session logs", outputView.getText().toString()));
                Toast.makeText(this, "Console copied", Toast.LENGTH_SHORT).show();
            }
        });

        btnRun.setOnClickListener(v -> {
            if (mRunning) return;
            // Do not rely exclusively on manager count. An installed but
            // unselected Manager previously skipped the offline install chooser.
            if (mValidSuManagerPos < 1 || mManagerCount == 0) {
                mContinueStartAfterInstall = true;
                // An explicit new Start tap replaces an abandoned install flow.
                mPendingPermissionIndex = -1;
                mPendingManagerPackage = null;
                mInstallRequestedAt = 0L;
                RmgOfflineManagerInstaller.chooseForStart(
                    this, mStartInstallListener, mManagerCount > 0,
                    () -> spinnerSuManager.performClick());
                return;
            }
            if (new File("/dev/df").exists()) {
                updateRunButton();
                return;
            }
            startRootSession();
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rmgThemeIndex != RmgAppearance.index(this)) {
            recreate();
            return;
        }
        if (!mRunning && spinnerSuManager != null) {
            refreshSelinux();
            loadManagers();
            updateRunButton();

            if (mPendingPermissionIndex >= 0) {
                final int chosen = mPendingPermissionIndex;
                mPendingPermissionIndex = -1;
                if (getPackageManager().canRequestPackageInstalls()) {
                    mMain.post(() -> RmgOfflineManagerInstaller.install(
                        this, chosen, mStartInstallListener));
                } else {
                    Toast.makeText(this, "Manager install permission was not granted.",
                        Toast.LENGTH_LONG).show();
                    updateRunButton();
                }
            } else if (mPendingManagerPackage != null) {
                mMain.postDelayed(this::checkPendingManagerInstallation, 650);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RmgOfflineManagerInstaller.REQUEST_INSTALL) return;

        // ACTION_VIEW installation activities may return RESULT_CANCELED even
        // when the package install is in progress or subsequently succeeds.
        // Never clear the pending Start request based solely on resultCode.
        // Confirm presence of the chosen package and its ksud library instead.
        for (long delay : new long[]{450L, 1300L, 3200L, 6800L}) {
            mMain.postDelayed(this::checkPendingManagerInstallation, delay);
        }
    }

    private void checkPendingManagerInstallation() {
        String requested = mPendingManagerPackage;
        if (requested == null || mRunning || isFinishing() || isDestroyed()) return;
        if (SystemClock.elapsedRealtime() - mInstallRequestedAt > INSTALL_FLOW_TIMEOUT_MS) {
            mPendingManagerPackage = null;
            Toast.makeText(this, "Installer session expired. Tap Start to try again.",
                Toast.LENGTH_LONG).show();
            updateRunButton();
            return;
        }
        ApplicationInfo installed;
        try {
            installed = getPackageManager().getApplicationInfo(requested, 0);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Not installed or installation canceled. Never launch the root engine.
            updateRunButton();
            return;
        }
        if (!ExploitRunner.hasKsud(installed)) {
            mPendingManagerPackage = null;
            showStatus("Manager is not compatible", "CHECK MANAGER",
                "Android installed the app, but DFRoot cannot find its required libksud.so. No root session was launched.",
                R.attr.rmgWarning);
            Toast.makeText(this, "Manager installed without a compatible ksud library.",
                Toast.LENGTH_LONG).show();
            updateRunButton();
            return;
        }

        // The exact APK chosen by the user has been installed and passed the
        // same eligibility check as original DFRoot's Manager selector.
        mPendingManagerPackage = null;
        boolean continueStart = mContinueStartAfterInstall;
        mContinueStartAfterInstall = false;
        mDeCtx.getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(ExploitRunner.PREF_SU_MANAGER, requested).apply();
        loadManagers();
        if (mValidSuManagerPos < 1 || new File("/dev/df").exists()) {
            updateRunButton();
            return;
        }
        if (continueStart) {
            Toast.makeText(this, "Manager verified. Continuing your Start request.",
                Toast.LENGTH_LONG).show();
            startRootSession();
        } else {
            updateRunButton();
            Toast.makeText(this, "Built-in Manager verified and selected.",
                Toast.LENGTH_LONG).show();
        }
    }

    private void startRootSession() {
        if (mRunning || mValidSuManagerPos < 1 || new File("/dev/df").exists()) {
            updateRunButton();
            return;
        }
        String selectedManager = mDeCtx.getSharedPreferences(
            ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE).getString(
                ExploitRunner.PREF_SU_MANAGER, "Unknown");
        mLogSessionId = RmpSessionHistory.begin(this, selectedManager);
        RmpSessionHistory.append(this, mLogSessionId,
            "> Starting original DFRoot engine...\n");
        mRunning = true;
        btnRun.setEnabled(false);
        btnRun.setText("RUNNING ORIGINAL DFROOT...");
        progressRoot.setVisibility(View.VISIBLE);
        showStatus("Executing session", "WORKING",
            "DFRoot is running. Avoid rebooting while it is in progress.",
            R.attr.rmgIce);
        outputView.setText("> Starting original DFRoot engine...\n");
        mExec.execute(this::runExploit);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadManagers() {
        PackageManager pm = getPackageManager();
        List<SuManagerEntry> installed = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (!ExploitRunner.hasKsud(ai)) continue;
            installed.add(new SuManagerEntry(ai.packageName,
                pm.getApplicationLabel(ai), pm.getApplicationIcon(ai)));
        }
        installed.sort((a,b)->a.label.toString().compareToIgnoreCase(b.label.toString()));
        mManagerCount = installed.size();
        List<SuManagerEntry> entries = new ArrayList<>();
        entries.add(new SuManagerEntry(null,"Select SU Manager / Built-in",null));
        // Always available from the bundled APK assets, even on a fresh device.
        entries.add(new SuManagerEntry(1,"KernelSU Classic (Samsung) - BUILT-IN / INSTALL"));
        entries.add(new SuManagerEntry(0,"KernelSU Next v3.4.2 - BUILT-IN / INSTALL"));
        entries.addAll(installed);
        SharedPreferences prefs=mDeCtx.getSharedPreferences(
            ExploitRunner.PREFS_NAME,Context.MODE_PRIVATE);
        String saved=prefs.getString(ExploitRunner.PREF_SU_MANAGER,null);
        int selected=0;
        for(int i=3;i<entries.size();i++)
            if(entries.get(i).packageName.equals(saved)){selected=i;break;}
        if(selected==0 && saved!=null)
            prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply();
        mValidSuManagerPos=selected;
        spinnerSuManager.setOnItemSelectedListener(null);
        spinnerSuManager.setAdapter(new SuManagerAdapter(this,entries));
        spinnerSuManager.setSelection(selected,false);
        spinnerSuManager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){
                if(pos<0||pos>=entries.size())return;
                SuManagerEntry e=entries.get(pos);
                if(e.builtInIndex>=0){
                    // Choosing a bundled APK installs it; it is NOT yet a ready Manager.
                    spinnerSuManager.setSelection(findSelectedManager(entries),false);
                    mContinueStartAfterInstall=false;
                    mPendingPermissionIndex=-1;
                    mPendingManagerPackage=null;
                    RmgOfflineManagerInstaller.install(MainActivity.this,
                        e.builtInIndex,mStartInstallListener);
                    updateRunButton();
                    return;
                }
                mValidSuManagerPos=e.packageName==null?0:pos;
                if(e.packageName!=null)
                    prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER,e.packageName).apply();
                else prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply();
                updateRunButton();
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        updateRunButton();
    }

    private int findSelectedManager(List<SuManagerEntry> entries){
        String saved=mDeCtx.getSharedPreferences(
            ExploitRunner.PREFS_NAME,Context.MODE_PRIVATE)
            .getString(ExploitRunner.PREF_SU_MANAGER,null);
        if(saved==null)return 0;
        for(int i=3;i<entries.size();i++)
            if(saved.equals(entries.get(i).packageName))return i;
        return 0;
    }

    private void refreshSelinux() {
        String mode = "Restricted / unavailable";
        try (BufferedReader reader = new BufferedReader(
                new FileReader("/sys/fs/selinux/enforce"))) {
            String value = reader.readLine();
            if ("1".equals(value)) mode = "Enforcing";
            else if ("0".equals(value)) mode = "Permissive";
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.tvSelinux)).setText(mode);
    }

    private void showStatus(String title, String tag, String detail, int colorAttr) {
        tvStatus.setText(title);
        tvStatusTag.setText(tag);
        tvStatusDetail.setText(detail);
        android.util.TypedValue color = new android.util.TypedValue();
        if (getTheme().resolveAttribute(colorAttr, color, true))
            tvStatusTag.setTextColor(color.resourceId != 0 ?
                getColor(color.resourceId) : color.data);
    }

    private void updateRunButton() {
        if (mRunning) return;
        boolean active = new File("/dev/df").exists();
        boolean selected = mValidSuManagerPos >= 1;
        boolean waiting = mPendingPermissionIndex >= 0 || mPendingManagerPackage != null;
        boolean needsSetup = !selected || mManagerCount == 0;
        // Even if a stale /dev/df marker exists, users must be able to open
        // the Manager setup chooser. Do not run an exploit in that case.
        btnRun.setEnabled(!active || needsSetup);
        btnRun.setText(needsSetup
            ? waiting ? "RETRY MANAGER SETUP" : "START / CHOOSE SU MANAGER"
            : active ? "ROOT SESSION ACTIVE" : "LAUNCH ROOT SESSION");
        tvManagerHint.setText(waiting
            ? "Confirm the bundled Manager installation in Android, then return."
            : selected
                ? mManagerCount + " compatible Manager(s). Built-in installers available above."
                : "Choose a built-in Classic / Next Manager above to install it, or tap Start.");
        if (active) {
            showStatus("Root session found", "ACTIVE",
                "DFRoot's /dev/df session marker is present. This does not independently verify SU permissions.",
                R.attr.rmgGood);
        } else if (!selected) {
            showStatus("System on standby", "NOT ACTIVE",
                mManagerCount == 0
                    ? "Tap Start to pick a bundled SU Manager for offline installation."
                    : "Tap Start to select a Manager or install Classic / Next offline.",
                R.attr.rmgWarning);
        } else {
            showStatus("Ready to launch", "READY",
                "Manager selected. Root compatibility depends on your device firmware and kernel.",
                R.attr.rmgIce);
        }
    }

    private void runExploit() {
        int result = -1;
        String error = null;
        try {
            // Preserve the unmodified original upstream exploit entry point.
            result = ExploitRunner.run(mDeCtx, this);
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            error = e.toString();
            report("\nException: " + error + "\n");
        }
        final int rc = result;
        final String detail = error;
        String session = mLogSessionId;
        RmpSessionHistory.finish(this, session,
            (new File("/dev/df").exists() ? "Session marker detected" : "Session not confirmed")
                + " / code " + rc + (detail == null ? "" : " / " + detail));
        mLogSessionId = null;
        mMain.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            mRunning = false;
            progressRoot.setVisibility(View.GONE);
            refreshSelinux();
            if (new File("/dev/df").exists()) {
                updateRunButton();
                Toast.makeText(this, "DFRoot session marker detected", Toast.LENGTH_LONG).show();
            } else {
                updateRunButton();
                showStatus("Session not confirmed", "CHECK LOGS",
                    detail != null ? detail : rc == 0
                        ? "The engine returned success, but no root session marker was found."
                        : rc == 1 ? "SU daemon returned a nonzero status; check console."
                        : rc == 2 ? "The engine reported an error; check logs and dmesg."
                        : "The original engine did not complete the operation.",
                    R.attr.rmgError);
                Toast.makeText(this, "No active session confirmed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private static class SuManagerEntry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;
        final int builtInIndex;

        SuManagerEntry(String pkg, CharSequence label, Drawable icon) {
            this.packageName = pkg;
            this.label = label;
            this.icon = icon;
            this.builtInIndex = -1;
        }
        SuManagerEntry(int builtInIndex, String label) {
            this.packageName = null;
            this.label = label;
            this.icon = null;
            this.builtInIndex = builtInIndex;
        }
    }

    private static class SuManagerAdapter extends ArrayAdapter<SuManagerEntry> {
        SuManagerAdapter(Context ctx, List<SuManagerEntry> items) {
            super(ctx, R.layout.item_su_manager, items);
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.FALSE)
                v = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_su_manager_closed, parent, false);
            v.setTag(Boolean.FALSE);
            return bindItem(pos, v);
        }

        @Override
        public View getDropDownView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.TRUE)
                v = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_su_manager, parent, false);
            v.setTag(Boolean.TRUE);
            return bindItem(pos, v);
        }

        @Override
        public boolean isEnabled(int pos) {
            return getItem(pos).packageName != null || getItem(pos).builtInIndex >= 0;
        }

        private View bindItem(int pos, View v) {
            SuManagerEntry e = getItem(pos);
            ImageView icon = v.findViewById(R.id.iconApp);
            TextView label = v.findViewById(R.id.labelApp);
            icon.setVisibility(e.icon == null ? View.GONE : View.VISIBLE);
            if (e.icon != null) icon.setImageDrawable(e.icon);
            label.setText(e.label);
            return v;
        }
    }
}
