# Migrating Shuffleframe v0.6 to v0.7 without redownloading photos

**Do not uninstall v0.6 before backing up.**

The original v0.6 Android package was signed using an ephemeral keystore under GitHub's XDG configuration directory, not the separate key that the workflow cached. That original private key is no longer available. Android therefore cannot install a new differently signed APK as a normal update. The v0.7 signing configuration has been explicitly pinned to its cached key so subsequent releases can retain their signing identity.

These Windows scripts use Android Debug Bridge and the existing debug app's run-as capability to save media and preferences to your computer, then restore those files after installation. They do not send photos to any web service.

1. Install official Android SDK Platform Tools from https://developer.android.com/tools/releases/platform-tools and ensure adb.exe and its accompanying DLLs are available on PATH.
2. Enable USB debugging on the phone; connect with USB and authorise the PC.
3. Run backup-cache.cmd **while v0.6 is still installed**. Verify that Shuffleframe-v0.6-backup.tar is created and contains your files. Make a second copy before migration.
4. Download the v0.7 APK and name it Shuffleframe-v0.7.0.apk. Put it in the same directory as the scripts and backup tar.
5. Run restore-cache.cmd. It first tries a normal install. If Android rejects it, you must explicitly type RESTORE before it uninstalls v0.6, installs v0.7 and restores media and preferences.
6. Open Shuffleframe and check your album counts and saved photos. Keep the tar backup until you have verified the app.

This workaround needs a Windows PC with enough free disk space to store thousands of photos and may take time over USB. Do not unplug your phone during backup or restore. If validation fails, do not uninstall. Restoration cannot be guaranteed on every device; retain your backup. For long-term stable signing, store the v0.7 private signing keystore in a GitHub Actions secret instead of relying indefinitely on Actions cache retention.
