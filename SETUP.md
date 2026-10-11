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

## 3. Install CCLoader

Follow the steps at [https://wiki.c2dl.info/CCLoader#Installation](https://wiki.c2dl.info/CCLoader#Installation)

**.ccmod files are supported in CrossAndroid from CCLoader version 2.26.0.**
For earlier versions, replace `CrossCode/ccloader/js/ccloader.js` with this modified [ccloader.js](https://raw.githubusercontent.com/SiggeMcKvack/CCLoader/refs/heads/master/ccloader/js/ccloader.js)

## 4. OPTIONAL: Install your favorite mods

Browse and download mods at [c2dl.info/cc/mods](https://c2dl.info/cc/mods).
All mods should work on Android. Create an issue if you find a mod that is not supported.
The `extension-asset-preloader` mod is built in and not needed on Android.

## 5. Copy the folder to your phone

Make sure you have launched the app at least once so the target directory is created.

### Recommended: import from within the app

1. Copy the `CrossCode` folder to any regular location on your device, e.g. `Download`, using MTP/USB.
2. Open CrossAndroid and tap **Import game folder…** in the setup guide.
3. Select the `CrossCode` folder. The app copies it into its own data directory.

This is the most reliable method: the app writes the files itself, so they always have the correct
permissions. The copy in `Download` can be deleted afterwards.

### Alternative: copy manually

Grab the folder called `CrossCode` and copy it to
`Android/data/de.radicalfishgames.crosscode/files`

Both internal storage and SD card are supported. The app will use whichever location already has the `CrossCode` folder, checking internal storage first.

You can move the folder there with a file manager that supports app-specific directories, such as
[DocumentsUI](https://play.google.com/store/apps/details?id=su.axi.documentsui) or
[Material Files](https://play.google.com/store/apps/details?id=me.zhanghai.android.files),
or see the [ADB Transfer Guide](ADB_TRANSFER.md) to transfer files from a computer.

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
                │   ├── mods
                │   └── ...
                ├── ccloader
                ├── package.json
                └── ...
```

### That's it. You are done!
