package df.root;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

public class SettingsActivity extends Activity {
    private int rmgThemeIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        rmgThemeIndex=RmgAppearance.index(this);
        setTheme(RmgAppearance.theme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction()
                .replace(R.id.settingsContainer, new SettingsFragment())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(rmgThemeIndex!=RmgAppearance.index(this))recreate();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
