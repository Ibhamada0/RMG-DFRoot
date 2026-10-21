package df.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
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
    /** Result is checked by the calling Activity only after Android's installer returns. */
    public interface InstallListener {
        void onPermissionRequired(int managerIndex);
        void onInstallerOpened(String packageName);
    }

    public static final int REQUEST_INSTALL = 6041;

    private RmgOfflineManagerInstaller(){}

    public static void choose(Activity activity) { choose(activity, null); }

    public static void choose(Activity activity, InstallListener listener) {
        new AlertDialog.Builder(activity).setTitle("RMP / Built-in Managers")
            .setMessage("Choose one of the two verified Manager APKs bundled "
                + "inside RMP. Android will request your permission to install.")
            .setItems(new String[]{
                "KernelSU Classic (Samsung) - BUILT-IN",
                "KernelSU Next v3.4.2 - BUILT-IN"},
                (dialog,index)->install(activity,index == 0 ? 1 : 0,listener))
            .setNegativeButton("Cancel",null).show();
    }

    /**
     * Always offer an explicit offline choice when Start has no selected SU
     * Manager. With existing managers, retain access to the installed chooser.
     */
    public static void chooseForStart(Activity activity, InstallListener listener,
                                      boolean hasInstalledManager, Runnable selectInstalled) {
        String[] options = hasInstalledManager
            ? new String[]{
                "Install KernelSU Classic (Samsung) - offline",
                "Install KernelSU Next - offline",
                "Select an installed Manager"
              }
            : new String[]{
                "Install KernelSU Classic (Samsung) - offline",
                "Install KernelSU Next - offline"
              };
        new AlertDialog.Builder(activity)
            .setTitle("RMP / Choose your SU Manager")
            .setMessage("No SU Manager is selected. Choose an offline installer. "
                + "Android will request permission before installation, and "
                + "RMP will continue your Start request only after verification.")
            .setItems(options, (dialog, index) -> {
                if (index == 0) install(activity, 1, listener);
                else if (index == 1) install(activity, 0, listener);
                else if (selectInstalled != null) selectInstalled.run();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public static void install(Activity activity, int index, InstallListener listener) {
        if (index < 0 || index >= NAMES.length) throw new IllegalArgumentException("Manager index");
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O &&
            !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity,"Allow RMP to install apps, then tap Install again.",
                Toast.LENGTH_LONG).show();
            if (listener != null) listener.onPermissionRequired(index);
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
            // Obtain package identity from the verified APK itself, not a guessed package name.
            PackageInfo archive = activity.getPackageManager()
                .getPackageArchiveInfo(output.getAbsolutePath(), 0);
            if (archive == null || archive.packageName == null || archive.packageName.isEmpty())
                throw new IllegalStateException("Invalid Manager package identity");
            Uri file=FileProvider.getUriForFile(activity,
                activity.getPackageName()+".fileprovider",output);
            Intent view=new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(file,"application/vnd.android.package-archive");
            view.setClipData(ClipData.newRawUri("Verified official Manager",file));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (listener == null) activity.startActivity(view);
            else {
                activity.startActivityForResult(view, REQUEST_INSTALL);
                listener.onInstallerOpened(archive.packageName);
            }
        }catch(Exception ex){
            Toast.makeText(activity,"Offline install error: "+ex.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
}
