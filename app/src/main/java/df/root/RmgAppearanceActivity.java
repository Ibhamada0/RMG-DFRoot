package df.root;

import android.app.Activity;
import android.os.Bundle;
import android.widget.RadioGroup;

/** Six-theme appearance picker; does not interact with root code. */
public class RmgAppearanceActivity extends Activity {
    private static final int[] RADIOS={
        R.id.themeObsidian,R.id.themeAurora,R.id.themeGlacier,
        R.id.themeEmber,R.id.themeMatrix,R.id.themeHeritage
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        setTheme(RmgAppearance.theme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rmg_appearance);
        RadioGroup options=findViewById(R.id.themeGroup);
        options.check(RADIOS[RmgAppearance.index(this)]);
        options.setOnCheckedChangeListener((group,id)->{
            for(int i=0;i<RADIOS.length;i++){
                if(RADIOS[i]!=id)continue;
                if(i!=RmgAppearance.index(this)){
                    RmgAppearance.select(this,i);
                    recreate();
                }
                break;
            }
        });
    }
}
