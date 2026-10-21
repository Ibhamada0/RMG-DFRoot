package df.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Read-only private log history with explicit per-session and clear-all controls. */
public final class RmpLogHistoryActivity extends Activity {
    private LinearLayout list;
    private TextView empty;
    private TextView count;
    private TextView clearAll;
    private int shownTheme;

    @Override
    protected void onCreate(Bundle state) {
        shownTheme = RmgAppearance.index(this);
        setTheme(RmgAppearance.theme(this));
        super.onCreate(state);
        setContentView(R.layout.activity_rmp_log_history);
        if (getActionBar() != null) getActionBar().hide();
        list = findViewById(R.id.historyList);
        empty = findViewById(R.id.historyEmpty);
        count = findViewById(R.id.historyCount);
        clearAll = findViewById(R.id.historyClearAll);
        clearAll.setOnClickListener(v -> confirmClear());
        RmpBottomNavigation.attach(this, RmpBottomNavigation.HISTORY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shownTheme != RmgAppearance.index(this)) {
            recreate();
            return;
        }
        refresh();
    }

    private int dp(int value) {
        return (int)(getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private int color(int attr) {
        TypedValue typed = new TypedValue();
        if (!getTheme().resolveAttribute(attr, typed, true)) return 0xFFFFFFFF;
        return typed.resourceId == 0 ? typed.data : getColor(typed.resourceId);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(sp);
        result.setTextColor(color(color));
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private void refresh() {
        count.setText("LOADING SESSIONS...");
        RmpSessionHistory.sessions(this, sessions -> {
            if (isFinishing() || isDestroyed()) return;
            list.removeAllViews();
            empty.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
            clearAll.setEnabled(!sessions.isEmpty());
            clearAll.setAlpha(sessions.isEmpty() ? 0.4f : 1f);
            count.setText(sessions.size() + " / 20 SAVED SESSIONS");
            for (RmpSessionHistory.Session session : sessions)
                addSession(session);
        });
    }

    private void addSession(RmpSessionHistory.Session session) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(R.attr.rmgPanel));
        bg.setStroke(dp(1), color(R.attr.rmgBorder));
        bg.setCornerRadius(dp(20));
        outer.setBackground(bg);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        list.addView(outer, p);

        TextView title = text(session.title, 17, R.attr.rmgText, true);
        outer.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView secondary = text(session.subtitle, 12, R.attr.rmgSecondary, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(9);
        outer.addView(secondary, subParams);
        TextView action = text("VIEW SESSION LOG   \u2192", 11, R.attr.rmgIce, true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(16);
        outer.addView(action, actionParams);
        outer.setClickable(true);
        outer.setFocusable(true);
        outer.setContentDescription("View RMP log session from " + session.title);
        outer.setOnClickListener(v -> openSession(session));
    }

    private void openSession(RmpSessionHistory.Session session) {
        RmpSessionHistory.read(this, session.id, value -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this)
                .setTitle("RMP session / " + session.title)
                .setMessage(value)
                .setPositiveButton("COPY LOG", (dialog, which) -> copy(value))
                .setNeutralButton("DELETE", (dialog, which) -> confirmDelete(session))
                .setNegativeButton("CLOSE", null)
                .show();
        });
    }

    private void copy(String data) {
        ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("RMP session history", data));
            Toast.makeText(this, "Session log copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(RmpSessionHistory.Session session) {
        new AlertDialog.Builder(this)
            .setTitle("Delete this session?")
            .setMessage("This will permanently delete the selected session's locally stored log.")
            .setPositiveButton("DELETE", (dialog, which) ->
                RmpSessionHistory.remove(this, session.id, () -> {
                    if (!isFinishing() && !isDestroyed()) refresh();
                }))
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("Clear all history?")
            .setMessage("All locally saved RMP session logs will be permanently deleted. " +
                "This does not affect Android or the root engine.")
            .setPositiveButton("CLEAR ALL", (dialog, which) ->
                RmpSessionHistory.clear(this, () -> {
                    if (!isFinishing() && !isDestroyed()) refresh();
                }))
            .setNegativeButton("CANCEL", null)
            .show();
    }
}
