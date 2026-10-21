package df.root;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Shared bottom navigation for RMP's four top-level pages. */
public final class RmpBottomNavigation {
    public static final int HOME = 0;
    public static final int INFO = 1;
    public static final int HISTORY = 2;
    public static final int SETTINGS = 3;
    private static final int[] CONTAINERS = {
        R.id.navHome, R.id.navInfo, R.id.navHistory, R.id.navSettings
    };
    private static final int[] ICONS = {
        R.id.navIconHome, R.id.navIconInfo, R.id.navIconHistory, R.id.navIconSettings
    };
    private static final int[] LABELS = {
        R.id.navLabelHome, R.id.navLabelInfo, R.id.navLabelHistory, R.id.navLabelSettings
    };
    private static final Class<?>[] PAGES = {
        MainActivity.class, AppInformationActivity.class, RmpLogHistoryActivity.class,
        SettingsActivity.class
    };

    private RmpBottomNavigation() {}

    private static int themeColor(Activity activity, int attr) {
        TypedValue v = new TypedValue();
        if (!activity.getTheme().resolveAttribute(attr, v, true)) return 0xFFFFFFFF;
        return v.resourceId == 0 ? v.data : activity.getColor(v.resourceId);
    }

    public static void attach(Activity activity, int selected) {
        int on = themeColor(activity, R.attr.rmgCyan);
        int off = themeColor(activity, R.attr.rmgMuted);
        for (int i = 0; i < CONTAINERS.length; i++) {
            final int tab = i;
            View item = activity.findViewById(CONTAINERS[i]);
            if (item == null) continue;
            ImageView icon = activity.findViewById(ICONS[i]);
            TextView label = activity.findViewById(LABELS[i]);
            int color = selected == i ? on : off;
            icon.setImageTintList(ColorStateList.valueOf(color));
            label.setTextColor(color);
            label.setTypeface(null, selected == i
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            item.setSelected(selected == i);
            item.setContentDescription(label.getText()
                + (selected == i ? ", selected" : ""));
            item.setOnClickListener(v -> {
                if (tab == selected) return;
                Intent intent = new Intent(activity, PAGES[tab]);
                // Reuse existing screens and never restart an active root session on tab changes.
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                                Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (tab == HOME) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                }
                activity.startActivity(intent);
            });
        }
    }
}
