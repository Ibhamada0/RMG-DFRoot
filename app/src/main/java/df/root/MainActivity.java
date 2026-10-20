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

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
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
        findViewById(R.id.btnOfflineManager).setOnClickListener(v ->
            RmgOfflineManagerInstaller.choose(this));
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
            if (mRunning || mValidSuManagerPos < 1 || new File("/dev/df").exists()) {
                updateRunButton();
                return;
            }
            mRunning = true;
            btnRun.setEnabled(false);
            btnRun.setText("RUNNING ORIGINAL DFROOT...");
            progressRoot.setVisibility(View.VISIBLE);
            showStatus("Executing session", "WORKING",
                "DFRoot is running. Avoid rebooting while it is in progress.",
                R.attr.rmgIce);
            outputView.setText("> Starting original DFRoot engine...\n");
            mExec.execute(this::runExploit);
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
        }
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
        List<SuManagerEntry> entries = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (!ExploitRunner.hasKsud(ai)) continue;
            entries.add(new SuManagerEntry(ai.packageName,
                pm.getApplicationLabel(ai), pm.getApplicationIcon(ai)));
        }
        entries.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        mManagerCount = entries.size();
        entries.add(0, new SuManagerEntry(null, "Select a compatible SU Manager", null));

        SharedPreferences prefs = mDeCtx.getSharedPreferences(
            ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(ExploitRunner.PREF_SU_MANAGER, null);
        int selected = 0;
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).packageName.equals(saved)) {
                selected = i;
                break;
            }
        }
        if (selected == 0 && saved != null)
            prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply();

        mValidSuManagerPos = selected;
        spinnerSuManager.setAdapter(new SuManagerAdapter(this, entries));
        spinnerSuManager.setSelection(selected);
        spinnerSuManager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                SuManagerEntry e = entries.get(pos);
                mValidSuManagerPos = e.packageName == null ? 0 : pos;
                if (e.packageName != null)
                    prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER, e.packageName).apply();
                updateRunButton();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
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
        btnRun.setEnabled(!active && selected);
        btnRun.setText(active ? "ROOT SESSION ACTIVE" :
            selected ? "LAUNCH ROOT SESSION" : "SELECT A MANAGER TO START");
        tvManagerHint.setText(mManagerCount == 0
            ? "No compatible SU Manager detected. Use the offline installer below."
            : mManagerCount + " compatible manager(s) detected. Select the one to use.");
        if (active) {
            showStatus("Root session found", "ACTIVE",
                "DFRoot's /dev/df session marker is present. This does not independently verify SU permissions.",
                R.attr.rmgGood);
        } else if (!selected) {
            showStatus("System on standby", "NOT ACTIVE",
                "Choose a compatible installed SU Manager before launching the original DFRoot engine.",
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

        SuManagerEntry(String pkg, CharSequence label, Drawable icon) {
            this.packageName = pkg;
            this.label = label;
            this.icon = icon;
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
            return getItem(pos).packageName != null;
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
