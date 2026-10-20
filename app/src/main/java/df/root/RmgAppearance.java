package df.root;

import android.content.Context;

/** Local appearance preferences only; never changes the rooting engine. */
public final class RmgAppearance {
    private static final int[] THEMES = {
        R.style.Theme_RMG_Obsidian, R.style.Theme_RMG_Aurora,
        R.style.Theme_RMG_Glacier, R.style.Theme_RMG_Ember,
        R.style.Theme_RMG_Matrix, R.style.Theme_RMG_Heritage
    };
    private RmgAppearance() {}
    public static int index(Context ctx) {
        int v=ctx.getSharedPreferences("rmg_appearance",Context.MODE_PRIVATE)
            .getInt("selected",0);
        return (v>=0 && v<THEMES.length)?v:0;
    }
    public static void select(Context ctx,int index) {
        if(index<0 || index>=THEMES.length)throw new IllegalArgumentException("Invalid RMP theme");
        ctx.getSharedPreferences("rmg_appearance",Context.MODE_PRIVATE)
            .edit().putInt("selected",index).apply();
    }
    public static int theme(Context ctx) {return THEMES[index(ctx)];}
}
