# RMP: stable APK signing and in-app updates

## Why stable signing is required

Android upgrades an installed APK without wiping its data only when
the **application ID**, **signing certificate**, and update versionCode
satisfy package-manager rules. This repository's application ID stays
`df.root`. The GitHub Actions Builds **1–13** published APKs signed by
the runner's **ephemeral debug certificate**; these certificates are not
guaranteed to match from run to run.

**Important one-time migration:** a new production keystore does **not**
match any old debug-signed install. Android cannot update those packages
in place. Before moving from an old debug APK to the first stable signed
release, save any important logs and data. You may need to uninstall the
old copy and install the new stable one. Uninstalling erases app-local
history/settings because the app disables Android Auto Backup. Never
promise no data loss across different signing identities.

Once the first stable signed version is installed, later builds can be
installed over it **without uninstalling** when they use the **same
keystore and alias** with increasing `versionCode`.

## 1. Generate a private release signing key ON YOUR COMPUTER

Install Java JDK, then use a terminal **outside this repository**:

```bash
keytool -genkeypair -v -storetype PKCS12 \
  -keystore rmp-release.jks -alias rmp-release \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong keystore password, store it privately, and back up the
`rmp-release.jks` file and passwords **offline**. Losing the key means
you can no longer update installations signed with that certificate.
Do NOT paste the key/password in chats, commit them to git, or upload
them as GitHub Actions artifacts.

## 2. Configure 4 GitHub Actions secrets

In the GitHub repository **Settings → Secrets and variables → Actions**
add the following **Repository secrets**. Secret names must match:

| Name | What to put there |
| --- | --- |
| `RMP_KEYSTORE_BASE64` | One-line Base64 text containing the complete .jks file |
| `RMP_KEYSTORE_PASSWORD` | The keystore password from keytool |
| `RMP_KEY_ALIAS` | `rmp-release` (or your actual alias) |
| `RMP_KEY_PASSWORD` | Password for that key alias (often same as keystore password for PKCS12) |

Make the Base64 value using your machine, **not an online converter**.

Linux:
```bash
base64 -w 0 rmp-release.jks
```

macOS:
```bash
base64 < rmp-release.jks | tr -d '\n'
```

Windows PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("rmp-release.jks"))
```

Paste the Base64 **only** into the GitHub Actions secret value on
GitHub's site; never into this chat.

## 3. Publish the first stable signed APK

Go to **Actions → Build RMP on the original DFRoot engine → Run
workflow**, using `main`, **after** saving all four secrets. The
workflow verifies the key and invokes `assembleRelease`, checks the
APK's Android signing certificate with `apksigner`, and only then
publishes the public GitHub Release with
`RMP-Original-DFRoot.apk` and its SHA-256.

When **any secret is missing**, GitHub Actions can still run its
debug-build smoke test, but it **does not publish a Latest Release**
for that run. Debug test artifacts are explicitly labeled
`RMP-debug-not-updatable`; don't distribute them as production APKs.

## 4. Use the updater INSIDE RMP

Open **Settings → Check for updates** or the
**Check RMP App Updates** link on the home dashboard. RMP contacts
`api.github.com/repos/Ibhamada0/RMG-DFRoot/releases/latest` over
HTTPS, reads the stable release's
`RMP_VERSION_CODE` and `RMP-Original-DFRoot.apk` asset digest,
and offers downloads only when the build number is newer.

After your approval, RMP downloads the APK to its private cache and
checks all of these before requesting installation:

1. Exact APK download URL host/path and upper size limit
2. Actual downloaded APK bytes against GitHub's SHA-256 digest
3. Same application ID `df.root`
4. Newer `versionCode`
5. Matching Android APK signing certificates on current and new APK

Then Android's package installer asks the user for consent. This is an
**in-app GitHub release updater for sideloaded APKs**, **not** Google's
Play Core in-app updates API and not silent background installation.
Android may additionally ask you to allow installs from RMP.

## 5. Every future update

Keep using the **same four GitHub Secrets and same .jks file**.
Increment both `versionCode` and `versionName` in
`app/build.gradle.kts`; update the
`RMP_VERSION_CODE` in the workflow Release body to match.

**Do not rotate the key**, replace it on different CI runners, or
delete the keystore backup. Every released APK intended as an update
must be signed with the same key. If you need a different private key
later, plan a separate, explicitly managed migration.

This update mechanism doesn't change or verify root exploit
compatibility; the original upstream DFRoot engine remains unmodified.
