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

For support for .ccmod files, replace `CrossCode/ccloader/js/ccloader.js` with this modified [ccloader.js](https://raw.githubusercontent.com/SiggeMcKvack/CCLoader/refs/heads/master/ccloader/js/ccloader.js)

## 4. Install the [cc-font-fix](https://github.com/krypciak/cc-font-fix) mod.

Download the zip from [here](https://github.com/krypciak/cc-font-fix/releases/latest).
Unpack the zip and copy the `cc-font-fix` folder into `CrossCode/assets/mods/`.

## 5. OPTIONAL: Install your favorite mods

Browse and download mods at [c2dl.info/cc/mods](https://c2dl.info/cc/mods).
All mods should work on Android.
The `extension-asset-preloader` mod's functionality is built in and the mod is not needed on Android.

## 6. Copy the folder to your phone

Make sure you have launched the app at least once so the target directory is created.

Grab the folder called `CrossCode` and copy it to
`Android/data/de.radicalfishgames.crosscode/files`

Both internal storage and SD card are supported. The app will use whichever location already has the `CrossCode` folder, checking internal storage first.

You can copy the folder to your device's `Download` folder using MTP and then move it to the Android/data location by using a file manager that supports app-specific directories, such as
[DocumentsUI](https://play.google.com/store/apps/details?id=su.axi.documentsui) or
[Material Files](https://play.google.com/store/apps/details?id=me.zhanghai.android.files).
Alternatively, see the [ADB Transfer Guide](ADB_TRANSFER.md) to transfer files from a computer.

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
