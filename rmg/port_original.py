#!/usr/bin/env python3
"""Apply RMG UI additions to PINNED original diabl0w/DFRoot.
Root engine/ExploitRunner/JNI/LKM and original SU manager selector untouched.
"""
from pathlib import Path
import shutil
R=Path(__file__).resolve().parent.parent
M=R/"app/src/main"
O=R/"rmg/overlay/app/src/main"
def replace(path,old,new):
    p=R/path
    s=p.read_text()
    if s.count(old)!=1: raise RuntimeError(f"Upstream source mismatch: {path}, {old[:40]}")
    p.write_text(s.replace(old,new,1))
def run():
    assert (M/"java/df/root/ExploitRunner.java").is_file()
    assert "RmgAppearance" not in (M/"java/df/root/MainActivity.java").read_text()
    for f in O.rglob("*"):
        if f.is_file() and (M/f.relative_to(O)).exists() and str(f.relative_to(O)) not in ("res/values/themes.xml","res/values-night/themes.xml"):
            raise RuntimeError("Overlay collision: "+str(f))
    a="app/src/main/java/df/root/MainActivity.java"
    replace(a,'    private static final String TAG = "dfroot";','    private static final String TAG = "dfroot";\n    private int rmgThemeIndex;')
    replace(a,"    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);",
              "    protected void onCreate(Bundle savedInstanceState) {\n        rmgThemeIndex=RmgAppearance.index(this);\n        setTheme(RmgAppearance.theme(this));\n        super.onCreate(savedInstanceState);")
    replace(a,"    @Override\n    public boolean onCreateOptionsMenu(",
              "    @Override\n    protected void onResume() {\n        super.onResume();\n        if(rmgThemeIndex!=RmgAppearance.index(this))recreate();\n    }\n    @Override\n    public boolean onCreateOptionsMenu(")
    b="app/src/main/java/df/root/SettingsActivity.java"
    replace(b,"public class SettingsActivity extends Activity {","public class SettingsActivity extends Activity {\n    private int rmgThemeIndex;")
    replace(b,"    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);",
              "    protected void onCreate(Bundle savedInstanceState) {\n        rmgThemeIndex=RmgAppearance.index(this);\n        setTheme(RmgAppearance.theme(this));\n        super.onCreate(savedInstanceState);")
    replace(b,"    @Override\n    public boolean onOptionsItemSelected(",
              "    @Override\n    protected void onResume() {\n        super.onResume();\n        if(rmgThemeIndex!=RmgAppearance.index(this))recreate();\n    }\n    @Override\n    public boolean onOptionsItemSelected(")
    f="app/src/main/java/df/root/SettingsFragment.java"
    replace(f,"import android.content.Context;","import android.content.Context;\nimport android.content.Intent;")
    replace(f,"        addPreferencesFromResource(R.xml.preferences);",
              '        addPreferencesFromResource(R.xml.preferences);\n'
              '        findPreference("rmg_appearance").setOnPreferenceClickListener(p -> {\n'
              '            startActivity(new Intent(getActivity(),RmgAppearanceActivity.class));return true;});\n'
              '        findPreference("rmg_install").setOnPreferenceClickListener(p -> {\n'
              '            RmgOfflineManagerInstaller.choose(getActivity());return true;});')
    replace("app/src/main/res/xml/preferences.xml","</PreferenceScreen>",
            '    <Preference android:key="rmg_appearance" android:title="RMG Appearance" android:summary="Choose six RMG themes"/>\n'
            '    <Preference android:key="rmg_install" android:title="Install SU Manager offline" android:summary="Install official verified Manager APK, Android approval required"/>\n'
            '</PreferenceScreen>')
    m="app/src/main/AndroidManifest.xml"
    replace(m,'android:label="DFRoot"','android:label="RMG"')
    replace(m,'    <uses-permission android:name="android.permission.WAKE_LOCK" />',
            '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n'
            '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')
    replace(m,'        <activity\n            android:name=".SettingsActivity"',
            '        <activity android:name=".RmgAppearanceActivity" android:exported="false"/>\n'
            '        <provider android:name="androidx.core.content.FileProvider"\n'
            '            android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">\n'
            '            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/rmg_file_paths"/>\n'
            '        </provider>\n        <activity\n            android:name=".SettingsActivity"')
    g="app/build.gradle.kts"
    replace(g,'dependencies {\n}','dependencies {\n    implementation("androidx.core:core:1.13.1")\n}')
    replace(g,'storeFile = file(signingProps.getProperty("KEYSTORE_FILE"))',
            'storeFile = file(signingProps.getProperty("KEYSTORE_FILE") ?: "../keystore.jks")')
    for f in O.rglob("*"):
        if f.is_file():
            p=M/f.relative_to(O);p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,p)
    p=M/"res/layout/activity_main.xml"
    s=p.read_text();key='android:id="@+id/rootLayout"'
    if s.count(key)!=1:raise RuntimeError("Main layout anchor changed")
    p.write_text(s.replace(key,key+'\n    android:background="?attr/rmgBg"',1))
    (R/"ORIGINAL_ENGINE_SHA.txt").write_text(
        "Original: diabl0w/DFRoot c274137e8c02b4f94f64d1b132d991b80c747340\n"
        "ExploitRunner, JNI, bootstrap and kernel module unchanged.\n")
    print("RMG UI port applied. Original engine unchanged.")
if __name__=="__main__":run()
