# ADB Transfer Guide

This guide covers transferring game files to your device using ADB (Android Debug Bridge) from your
computer.

## Prerequisites

1. **Enable USB debugging** on your Android device:
   - Go to **Settings > About phone** and tap **Build number** 7 times to enable Developer options.
   - Go to **Settings > Developer options** and enable **USB debugging**.

2. **Install ADB** on your computer:
   - **Windows/Mac/Linux**: Follow the [official Android guide](https://developer.android.com/tools/adb) to install the Android SDK Platform Tools.
   - After installing, make sure `adb` is available in your terminal/command prompt.

## Connect your device

1. Connect your phone to your computer via USB.
2. If prompted on your phone, tap **Allow** to authorize the USB debugging connection.
3. Verify the connection:
   ```
   adb devices
   ```
   You should see your device listed. If it shows as `unauthorized`, check for the authorization
   prompt on your phone.

## Launch the app first

Before transferring files, **open CrossAndroid once** on your device and grant storage permissions.
This creates the target directory. If you skip this step, the `adb push` command will fail with
"No such file or directory".

## Transfer game files

Push your `CrossCode` folder to the device:

```
adb push CrossCode/ /sdcard/Android/data/de.radicalfishgames.crosscode/files/CrossCode/
```

This may take several minutes depending on the size of your game directory and USB speed.

## Verify the transfer

Check that the files are in place:

```
adb shell ls /sdcard/Android/data/de.radicalfishgames.crosscode/files/CrossCode/
```

You should see `assets`, `ccloader`, `package.json`, and other game files.

## Transferring mods later

To add a mod after the initial setup, push it directly into the mods directory:

```
adb push my-mod-folder/ /sdcard/Android/data/de.radicalfishgames.crosscode/files/CrossCode/assets/mods/my-mod-folder/
```

For `.ccmod` files:

```
adb push my-mod.ccmod /sdcard/Android/data/de.radicalfishgames.crosscode/files/CrossCode/assets/mods/
```

## Troubleshooting

- **"device not found"** or **no devices listed**: Make sure USB debugging is enabled, try a
  different USB cable, and check that you have the correct ADB drivers installed (Windows may
  require OEM-specific drivers).

- **"Permission denied"**: Some devices require you to authorize the connection on the phone screen.
  Disconnect and reconnect the cable, then check for a popup.

- **"No such file or directory"**: You need to launch the CrossAndroid app at least once before
  transferring files so the directory gets created. Open the app, grant permissions, then try again.

- **Transfer is very slow**: Use a USB 3.0 cable and port if available. Avoid USB hubs.
