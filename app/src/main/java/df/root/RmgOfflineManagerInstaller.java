package df.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Verified offline APK installer; the system handles package installation consent. */
public final class RmgOfflineManagerInstaller {
    private static final String[] NAMES={"ksu-next-v3.4.2.apk","ksu-samsung-v2.2.apk"};
    private static final String[] CHECKSUMS={
       "774a5511f7034a4930e881114e202319cbdc34c5aaaa5c7c6b6d2902364e4c58",
       "761dffad54ae080a4977971db7b04e2fc65c2d9b5e2336accca4a85661290a69"
    };
    private RmgOfflineManagerInstaller(){}
    public static void choose(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("Install official Manager (offline)")
            .setItems(new String[]{"KernelSU Next v3.4.2","KernelSU Samsung v2.2"},
                (dialog,index)->install(activity,index))
            .setNegativeButton("Cancel",null).show();
    }
    private static void install(Activity activity,int index) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O &&
            !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity,"Allow RMP to install apps, then tap Install again.",
                Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+activity.getPackageName())));
            return;
        }
        File output=null;
        try {
            File dir=new File(activity.getCacheDir(),"rmg_offline");
            if(!dir.isDirectory() && !dir.mkdirs())throw new IllegalStateException("Cache unavailable");
            output=new File(dir,NAMES[index]);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=activity.getAssets().open("managers/"+NAMES[index]);
                FileOutputStream out=new FileOutputStream(output)){
                byte[] b=new byte[65536];
                int n;
                while((n=in.read(b))!=-1){digest.update(b,0,n);out.write(b,0,n);}
                out.getFD().sync();
            }
            StringBuilder sha=new StringBuilder();
            for(byte b:digest.digest())sha.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!CHECKSUMS[index].contentEquals(sha)){
                output.delete();
                throw new SecurityException("SHA-256 verification failed");
            }
            Uri file=FileProvider.getUriForFile(activity,
                activity.getPackageName()+".fileprovider",output);
            Intent view=new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(file,"application/vnd.android.package-archive");
            view.setClipData(ClipData.newRawUri("Verified official Manager",file));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(view);
        }catch(Exception ex){
            Toast.makeText(activity,"Offline install error: "+ex.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
}
