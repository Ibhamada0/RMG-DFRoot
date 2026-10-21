# RMP — Root Management Pro

[![Latest Release](https://img.shields.io/github/v/release/Ibhamada0/RMG-DFRoot?label=Latest%20Release)](https://github.com/Ibhamada0/RMG-DFRoot/releases/latest)
[![Build Status](https://github.com/Ibhamada0/RMG-DFRoot/actions/workflows/rmg-build.yml/badge.svg)](https://github.com/Ibhamada0/RMG-DFRoot/actions/workflows/rmg-build.yml)

### 📱 Download RMP APK

**[⬇️ Download Latest RMP APK — GitHub Releases](https://github.com/Ibhamada0/RMG-DFRoot/releases/latest)**

Open the latest release above and download `RMP-Original-DFRoot.apk` under **Assets**. This is a GitHub Release on the repository home page, **not** a temporary Actions artifact or signing-key file. SHA-256 is included alongside the APK.

> **Experimental and debug-signed.** Successful compilation is not proof of root support on your firmware. An APK signed with the default debug key cannot necessarily update a differently signed install. Back up your device.

## About RMP / original DFRoot engine

**Owner:** [Ibhamada0](https://github.com/Ibhamada0)  
**Upstream source:** [diabl0w/DFRoot](https://github.com/diabl0w/DFRoot) at
`c274137e8c02b4f94f64d1b132d991b80c747340`

This is an independently created derivative repository (not a formal GitHub network Fork).
The RMP port uses the original DFRoot source and preserves its `ExploitRunner.java`,
`app/src/main/jni/` native exploit, `lkm/` kernel module, and upstream manager
selection code without changes.

### App Information, How to Use and FAQ

Open the **information icon** in the home screen header, or go to
**Settings → App Information**. The new center has three tabs:
- **Kernel Targets:** eight KMI module build families from the CI build matrix,
  plus your reported device kernel version. **A matching KMI does not guarantee
  the device is vulnerable, compatible, or successfully rooted.**
- **How to Use:** step-by-step safety and interface guidance, SU Manager selection,
  offline installer and log interpretation.
- **FAQ:** expandable answers about KernelSU variants, root session markers,
  signing, updates, permissions and boot settings.

### Smart Start — Manager installation assistance

If no compatible SU Manager is installed, **Start** opens a choice between the
two bundled offline Manager APKs. Android asks for installation approval; the
app verifies the installed package and the same `libksud.so` check as original
DFRoot. If verification succeeds, RMP selects the manager and **continues
the same Start request automatically** without another tap.

If installation is canceled, permission is denied, or the Manager is incompatible,
no root session starts. You can return and tap Start again. Installation and root
success are both dependent on the device and firmware; **this does not bypass
Android's installer confirmation**. The native DFRoot exploit remains unchanged.

### Bottom navigation and session log history

The fixed bottom bar provides four destinations: **Home, Info, History, Settings**.
On the **History** page, tap any root session to review its captured console messages,
copy the text, or delete it. The **Clear All** action asks for confirmation.

- Manual root-session logs are stored privately on-device in the app's own files directory.
- Only the latest **20 sessions** are retained; each is capped at **256 KiB**.
- No history is uploaded. Logs may include firmware identifiers and other output
  printed by DFRoot. Share copied logs only when you trust the recipient.
- Logs from before this update are **not recoverable**. Removing the app or clearing
  app data erases these local records. Boot-initiated sessions are not captured.

### Smart Start / Manager selection

When **Start** is tapped without a selected SU Manager, a setup dialog
immediately offers **KernelSU Classic (Samsung)** or **KernelSU Next** offline
installation. This works even when RMP detects another eligible Manager but none
is selected. If an installed Manager is available, a third choice selects it.

After approving Android's installer and returning to RMP, the application checks
the installed package and required `libksud.so` before continuing the
original Start request. If the user cancels, fails installation or the Manager
is incompatible, RMP does **not** start the root engine. A stale root session
marker does not prevent opening the Manager chooser.

### RMP customizations

The main screen is a fully redesigned **Premium Control Center** with a
status hero, SU Manager selection, progress feedback, quick access to Settings
and offline installation, device model/build/kernel/SELinux diagnostics, and
a copyable live console. All six existing themes style the complete dashboard.

- Application display name **RMP**.
- **Six themes**: Obsidian, Aurora, Glacier, Ember, Matrix, RMP Heritage (warm ivory).
- **Appearance** item in Settings saves and applies the chosen theme.
- **Install SU Manager offline** installs the selected verified official Manager APK via the Android installer: KernelSU Next v3.4.2 or diabl0w KernelSU Samsung v2.2.
- Original DFRoot SU manager selector remains the source of the active manager preference. Select a compatible installed manager in the original main-screen spinner. Installing a Manager is not proof its daemon/kernel module will function on this device.

### Offline APK asset verification
APKs are fetched at **CI build time** (not checked into git) and embedded:
- Next v3.4.2 SHA-256: `774a5511f7034a4930e881114e202319cbdc34c5aaaa5c7c6b6d2902364e4c58`.
- Classic Samsung v2.2 SHA-256: `761dffad54ae080a4977971db7b04e2fc65c2d9b5e2336accca4a85661290a69`.

No network access is needed for installing a bundled manager **from a built APK**. Android prompts for user permission to install. Developer builds that do not embed the assets will not support offline install.

### Build setup
The bootstrap workflow clones the pinned upstream code and adds the UI overlay, committing the result to `main`. The build workflow compiles the original KMI kernel modules first and then the Android debug APK. Successful builds are published as visible **GitHub Releases**, with the most recent build marked as **Latest**. Releases remain experimental even though the GitHub prerelease flag is disabled so the build appears in the repository's Releases section. Debug-signed APKs are **not production-signed** and may not update apps with a different signature.


Code credits: [diabl0w](https://github.com/diabl0w/DFRoot),
[soumarcelino](https://github.com/soumarcelino/DFRoot), and original exploit contributors.
Check upstream licenses before distributing compiled APKs.
