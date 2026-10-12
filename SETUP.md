# Installing and updating the game files

## 1. Install the app

Download the latest APK file at https://gitlab.com/rumpnisse/crossandroid/-/releases and install on your device.

## 2. Find the game asset files

The location of them can vary depending on how you installed CrossCode.
If you used Steam, the location should be:

- Windows: `C:\Program Files (x86)\Steam\steamapps\common\CrossCode`
- Linux: `~/.steam/steam/steamapps/common/CrossCode`
- Mac: `~/Library/Application Support/Steam/steamapps/common/CrossCode/CrossCode.app/Contents/Resources/app.nw`

If you used a different method ([GOG](https://www.gog.com/), itch.io, etc.), search on the internet where games for that are usually saved.

## 3. Copy the folder to your phone

### Recommended: import from within the app

1. Copy the `CrossCode` folder to any regular location on your device, e.g. `Download`, using MTP/USB.
2. Open CrossAndroid and tap **Import game folder…** in the setup guide.
3. Select the `CrossCode` folder. The app copies it into its own data directory.

This is the most reliable method: the app writes the files itself, so they always have the correct
permissions. The copy in `Download` can be deleted afterwards.

### Alternative: copy manually

Launch the app at least once so the target directory is created. Then grab the folder called
`CrossCode` and copy it to `Android/data/de.radicalfishgames.crosscode/files`

Both internal storage and SD card are supported. The app will use whichever location already has the `CrossCode` folder, checking internal storage first.

You can move the folder there with a file manager that supports app-specific directories, such as
[DocumentsUI](https://play.google.com/store/apps/details?id=su.axi.documentsui) or
[Material Files](https://play.google.com/store/apps/details?id=me.zhanghai.android.files),
or see the [ADB Transfer Guide](ADB_TRANSFER.md) to transfer files from a computer.

When the copy is done, tap **Finish** in the setup guide.

> **Warning:** files written into `Android/data` by adb or by file managers that use Shizuku/shell
> access end up owned by a different user, and the app may not be allowed to read them. The app will
> then report *"A game folder exists but the app is not allowed to read it"*. If that happens, use the
> in-app import above, or copy the folder through the system **Files** app into the **CrossCode**
> location it lists.

**Verify that your file structure looks like this:**
```
Android
└── data
    └── de.radicalfishgames.crosscode
        └── files
            └── CrossCode
                ├── assets
                │   ├── data
                │   ├── media
                │   └── ...
                ├── package.json
                └── ...
```

## 4. OPTIONAL: Install mods

Tap **Mods** on the launcher screen.

1. If no modloader is installed yet, pick **CCLoader2** or **CCLoader3** to install it. The app
   offers updates for it later.
2. Browse the **Browse** tab and tap **Install** on the mods you want. Required dependencies are
   installed along with them.
3. Update or uninstall mods from the **Installed** tab.

Mods you already have in your PC installation are copied along with the game folder, and you can also
add mods manually to `CrossCode/assets/mods/`.

Most mods work on Android, including many that were written for the desktop (nw.js) version.
Known exception: CCModManager's online mod list does not work, use the **Mods** screen instead.
The `extension-asset-preloader` mod is built in and not needed on Android.
Create an [issue](https://gitlab.com/rumpnisse/crossandroid/issues) if you find a mod that does not work.

### That's it. You are done!

To update the game, copy the game folder again with the steps above. The app itself can be updated independently.
