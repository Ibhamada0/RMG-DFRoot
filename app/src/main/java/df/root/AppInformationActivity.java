package df.root;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

import java.util.Locale;

/**
 * Read-only app information. The KMI list corresponds to CI module build targets,
 * NOT verified device/kernel exploit compatibility.
 */
public final class AppInformationActivity extends Activity {
    public static final String EXTRA_TAB = "info_tab";
    public static final int KERNELS = 0;
    public static final int GUIDE = 1;
    public static final int FAQ = 2;

    private static final String[] KMI_TARGETS = {
        "android12-5.10", "android13-5.10", "android13-5.15",
        "android14-5.15", "android14-6.1", "android15-6.6",
        "android16-6.12", "android17-6.18"
    };
    private static final String[] TAB_NAMES = {"KERNEL TARGETS", "HOW TO USE", "FAQ"};

    private int selected = 0;
    private int shownTheme;
    private LinearLayout content;
    private LinearLayout tabs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        shownTheme = RmgAppearance.index(this);
        setTheme(RmgAppearance.theme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_information);
        if (getActionBar() != null) getActionBar().hide();
        RmpBottomNavigation.attach(this, RmpBottomNavigation.INFO);
        content = findViewById(R.id.infoContent);
        tabs = findViewById(R.id.infoTabs);
        findViewById(R.id.infoBack).setOnClickListener(v -> finish());

        selected = Math.max(0, Math.min(2, getIntent().getIntExtra(EXTRA_TAB, KERNELS)));
        switchTo(selected);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RmgAppearance.index(this) != shownTheme) recreate();
    }

    private int color(int attr) {
        TypedValue v = new TypedValue();
        if (!getTheme().resolveAttribute(attr, v, true)) return 0xFFFFFFFF;
        return v.resourceId != 0 ? getColor(v.resourceId) : v.data;
    }
    private int dp(float dp) { return (int)(getResources().getDisplayMetrics().density * dp + 0.5f); }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private TextView label(String text, int size, int colorAttr, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color(colorAttr));
        tv.setTextSize(size);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(18), dp(18), dp(18), dp(18));
        p.setBackground(background(color(R.attr.rmgPanel), color(R.attr.rmgBorder), 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        content.addView(p, params);
        return p;
    }

    private void add(LinearLayout parent, TextView view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private void heading(String overline, String title, String subtitle) {
        TextView eye = label(overline, 11, R.attr.rmgIce, true);
        eye.setLetterSpacing(0.14f);
        add(content, eye, 16);
        add(content, label(title, 27, R.attr.rmgText, true), 10);
        TextView desc = label(subtitle, 13, R.attr.rmgSecondary, false);
        desc.setLineSpacing(dp(3), 1f);
        add(content, desc, 8);
    }

    private void switchTo(int tab) {
        selected = tab;
        tabs.removeAllViews();
        content.removeAllViews();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int target = i;
            TextView btn = label(TAB_NAMES[i], 11, i == tab ? R.attr.rmgBg : R.attr.rmgSecondary, true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(8), dp(12), dp(8), dp(12));
            btn.setBackground(background(
                color(i == tab ? R.attr.rmgCyan : R.attr.rmgPanel),
                i == tab ? 0 : color(R.attr.rmgBorder), 13));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.leftMargin = dp(i == 0 ? 0 : 4);
            p.rightMargin = dp(i == 2 ? 0 : 4);
            tabs.addView(btn, p);
            btn.setContentDescription(TAB_NAMES[i] + (tab == i ? " selected" : ""));
            btn.setOnClickListener(v -> switchTo(target));
        }
        ((ScrollView)findViewById(R.id.infoScroll)).scrollTo(0, 0);
        if (tab == KERNELS) drawKernels();
        else if (tab == GUIDE) drawGuide();
        else drawFaq();
    }

    private void drawKernels() {
        heading("RMP / DEVICE SUPPORT", "Kernel build targets",
            "These are kernel-module build families included by CI, not a list of confirmed vulnerable devices.");
        LinearLayout notice = panel();
        add(notice, label("IMPORTANT COMPATIBILITY NOTICE", 12, R.attr.rmgWarning, true), 0);
        add(notice, label("Matching a KMI family does NOT confirm DirtyFrag vulnerability, " +
            "SU compatibility, or successful root. Vendor patches, security updates, " +
            "firmware and hardware configuration can change the result.",
            13, R.attr.rmgSecondary, false), 10);

        String detected = System.getProperty("os.version", "Unavailable");
        LinearLayout device = panel();
        add(device, label("THIS DEVICE / KERNEL RELEASE", 11, R.attr.rmgIce, true), 0);
        add(device, label(Build.MANUFACTURER + " " + Build.MODEL, 17, R.attr.rmgText, true), 8);
        add(device, label(detected, 12, R.attr.rmgSecondary, false), 5);
        String known = getFamily(detected);
        add(device, label(known == null ? "Exact KMI family not detected"
            : "Possible CI build family: " + known, 12,
            known == null ? R.attr.rmgMuted : R.attr.rmgGood, true), 10);
        add(device, label("Family identification is informative only; it is not a compatibility check.",
            11, R.attr.rmgMuted, false), 5);

        LinearLayout targets = panel();
        add(targets, label("AVAILABLE MODULE BUILD FAMILIES", 12, R.attr.rmgIce, true), 0);
        for (String kmi : KMI_TARGETS) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(-1, -2);
            rowP.topMargin = dp(11);
            targets.addView(row, rowP);

            TextView bullet = label("\u25cf", 11, R.attr.rmgCyan, true);
            row.addView(bullet, new LinearLayout.LayoutParams(dp(22), -2));
            TextView item = label(kmi, 15, R.attr.rmgText, true);
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            TextView stage = label("CI build", 10, R.attr.rmgMuted, false);
            row.addView(stage);
        }
        add(targets, label("8 build targets  •  arm64-v8a  •  Original DFRoot kernel module",
            11, R.attr.rmgMuted, false), 18);
        final TextView action = label("VIEW HOW TO USE  \u2192", 12, R.attr.rmgIce, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(16), dp(12), dp(16));
        action.setBackground(background(color(R.attr.rmgPanel), color(R.attr.rmgBorder), 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(18);
        content.addView(action, p);
        action.setOnClickListener(v -> switchTo(GUIDE));
    }

    /**
     * Conservatively identify explicit androidXX-x.y KMI tokens only.
     * Do not guess from just Linux 5.15 or Android framework release.
     */
    private String getFamily(String release) {
        String kernel = release.toLowerCase(Locale.ROOT);
        for (String kmi : KMI_TARGETS) if (kernel.contains(kmi)) return kmi;
        return null;
    }

    private void drawGuide() {
        heading("RMP / GETTING STARTED", "How to use",
            "A practical guide to RMP's controls and compatibility information.");
        guideStep("01", "Check device information",
            "Open Kernel Targets to see your kernel release and module build families. A matching family alone cannot establish exploit compatibility.");
        guideStep("02", "Back up first",
            "Before using any experimental root software, back up important data and have a recovery plan for your exact firmware.");
        guideStep("03", "Install a compatible SU Manager",
            "Go to Settings and choose Install SU Manager offline. RMP bundles verified official Manager APKs. Android will ask for approval.");
        guideStep("04", "Choose the installed manager",
            "Return to the dashboard and choose an installed compatible SU Manager from the drop-down. The original DFRoot selector stores your selection.");
        guideStep("05", "Read status and logs",
            "The dashboard displays status, device details and console messages. The root-session marker alone does not prove SU works; verify permission using your SU Manager.");
        guideStep("06", "Use advanced settings carefully",
            "Settings has appearance options and experimental boot and module controls. Leave boot automation off until you have independently confirmed compatibility.");
        LinearLayout tip = panel();
        add(tip, label("SAFETY FIRST", 12, R.attr.rmgWarning, true), 0);
        add(tip, label("If your firmware is unsupported or a session fails, do not repeatedly retry blindly. Read the console and avoid changing security settings as a workaround.",
            13, R.attr.rmgSecondary, false), 8);
    }

    private void guideStep(String number, String title, String answer) {
        LinearLayout p = panel();
        add(p, label(number + "  /  " + title, 16, R.attr.rmgText, true), 0);
        add(p, label(answer, 13, R.attr.rmgSecondary, false), 9);
    }

    private void drawFaq() {
        heading("RMP / HELP CENTER", "Frequently asked",
            "Answers to the most common questions about supported kernels, managers and installation.");
        faq("Does the KMI list guarantee root support?",
            "No. It only shows the eight kernel-module families built by RMP's GitHub Actions. Device firmware and kernel security patches determine actual compatibility.");
        faq("Does RMP support KernelSU Classic and Next?",
            "RMP bundles offline installers for both. The original DFRoot selector lists installed Managers with a suitable libksud.so. Availability does not guarantee root success.");
        faq("Why is my Manager missing from the list?",
            "The app only displays eligible installed apps that expose the ksud library expected by original DFRoot. An installed Manager may be an incompatible build.");
        faq("Can I install a Manager with no internet?",
            "Yes, the RMP APK from GitHub Releases embeds two pinned Manager APKs verified by SHA-256. Android must still approve their installation.");
        faq("Is root permanent?",
            "Do not assume so. Runtime root/session state can change after reboot. Boot automation is experimental and depends on your firmware and manager.");
        faq("What if RMP says root is active?",
            "The dashboard checks the original /dev/df marker; this is not an independent SU verification. Confirm root requests with your chosen SU Manager.");
        faq("What if the launch fails?",
            "Use Copy Logs in the dashboard to collect the console output. Check your exact build, security patch and Manager compatibility. Avoid repeated blind attempts.");
        faq("Why can't I update an older APK?",
            "APK upgrades require matching application ID and signing certificate. RMP builds are signed using the GitHub runner's default debug key, so a previous differently signed install can block the update.");
        faq("Does changing themes affect the root engine?",
            "No. The six themes affect appearance only; the original DFRoot native engine is unchanged.");
        faq("Where can I find previous session logs?",
            "Open History using the bottom bar. RMP stores only manual sessions started since this update, privately on your device. Copy or delete individual logs, or clear all saved sessions.");
    }

    private void faq(String question, String answer) {
        LinearLayout p = panel();
        TextView q = label(question + "    \u2304", 15, R.attr.rmgText, true);
        q.setPadding(0, dp(3), 0, dp(3));
        add(p, q, 0);
        TextView a = label(answer, 13, R.attr.rmgSecondary, false);
        a.setLineSpacing(dp(3), 1f);
        add(p, a, 10);
        a.setVisibility(View.GONE);
        q.setOnClickListener(v -> {
            boolean expand = a.getVisibility() != View.VISIBLE;
            a.setVisibility(expand ? View.VISIBLE : View.GONE);
            q.setText(question + (expand ? "    \u2303" : "    \u2304"));
        });
        q.setContentDescription("FAQ " + question + ". Tap to expand or collapse");
    }
}
