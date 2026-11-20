# CrossCode (Steam) - a CrossAndroid fork

This is a fork of [CrossAndroid](https://gitlab.com/rumpnisse/crossandroid) that connects the game to your Steam account:

- **Playtime**: while the game is in front, Steam shows you as "Playing CrossCode" and counts your hours.
- **Achievements**: trophies you earn unlock on Steam, and ones already in your save catch up the next time you play online. Each unlock is checked by reading your stats back from Steam.
- **Steam sign-in** with a QR code, the Steam app on the same device, or account name and password with Steam Guard. The password is never stored; the sign-in token is kept encrypted on the device.
- **Achievement pop-ups**, full `cc.save` import from a PC, and a few launcher fixes. See [STEAM-FORK.md](STEAM-FORK.md) for details.

**You must own CrossCode on Steam.** The app doesn't include the game: you copy the game files from your own Steam copy, see [SETUP.md](SETUP.md).

**Install**: download the APK from [the releases page](https://github.com/catmona/crossandroid-steam/releases), or build it yourself (`./gradlew assembleRelease`). It uses its own package (`de.radicalfishgames.crosscode.steam`), so it installs next to upstream CrossAndroid instead of replacing it, with its own game files and saves.

**Tip**: a recent Android System WebView makes a big difference to how smoothly the game runs (it fixes most stutter at map changes). On phones with Google apps, update "Android System WebView" from the Play Store. Some AOSP-based devices only accept the `com.android.webview` package, so the Play Store version is never used there; with root, a Magisk module that replaces `/product/app/webview` with LineageOS's [Chromium WebView build](https://github.com/LineageOS/android_external_chromium-webview_prebuilt_arm64) works. Back up your saves first: they live in the app's WebView data.

Built on CrossAndroid by Namnodorel, rumpnisse and its contributors; their full history and credits are kept here. If you're an upstream author and want something changed or taken down, please open an issue.

Not affiliated with Valve, Steam or Radical Fish Games. CrossCode is a trademark of Radical Fish Games.

Most of this fork's code was written with AI assistance (Anthropic's Claude) and reviewed and tested by the maintainer. Use at your own risk.

---

## Upstream CrossAndroid

The rest of this README is upstream's, kept as it was. Its links (gitlab, Obtainium, Discord) are upstream's, not this fork's.

[![Discord](https://img.shields.io/discord/382339402338402315?label=Discord&logo=discord)](https://discord.gg/3Xw69VjXfW)

Upstream's Obtainium link (installs upstream CrossAndroid from gitlab, not this fork):

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get upstream CrossAndroid on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://gitlab.com/rumpnisse/CrossAndroid)

Welcome to CrossAndroid! This is an app that allows you to play
CrossCode on your Android device.

Current features:
- Play CrossCode on the go!
- Browse, install, update and uninstall mods in the app, including the
  modloader (CCLoader2 or CCLoader3)
- Import your game folder straight from your device's storage
- Support for controllers connected to your phone, also in the
  launcher and Mods screen
- Includes a virtual controller layout _(for details see below)_
- Customize the virtual controller with size and transparency settings
  (you can even let it disable itself automatically during cutscenes)
- Sharp pixel scaling, Always Sprint and other gameplay settings
- Haptic feedback when the screen shakes _(experimental)_
- Import/Export saves between your PC and this app! _(for details, see
  below)_

Help improve the app by reporting bugs or making
suggestions for improvements.

## Credits
Main parts of CrossAndroid made by Namnodorel, Pixelart by Ichiki Hayaite aka TheSparkstarScope

## Getting the app
Install the [latest APK](https://gitlab.com/rumpnisse/crossandroid/-/releases) (or use Obtainium), then follow the [setup guide](SETUP.md) to copy the game files to your device.

You can update the app independently of the game files. To update the game, follow the [setup guide](SETUP.md) again. Mods are installed from the **Mods** screen in the app.

## Using the virtual controller
Here's an overview of the combat controls overlay:

![Control scheme reference](crossandroid_overlay_reference.png)

**Important:** Do not change the controller binding settings in the
in-game options when you want to use the virtual controller! Doing so
will result in the virtual controller behaving in some weird and
unexpected ways.

Turning off the overlay will let you interact directly with the game
(click on buttons, etc.)

You can adjust the size and transparency of the virtual controller in
the settings of the app.

# Import/Export of saves
CrossAndroid lets you transfer save games between devices or between
your PC and this app. You can access the import and export features
from the two buttons on the launcher screen.

### Exporting saves from CrossAndroid
Tap "Export Save Games" on the launcher. You'll see a list of your save
slots with a preview of each (level, chapter, playtime, location, and
credits). Tap the export button on the slot you want, and choose where
to save the file on your device.

### Importing saves into CrossAndroid
Tap "Import Save Games" on the launcher. You have two options:
- **Import from files:** Select one or more previously exported save
  files from your device.
- **Import from string:** Paste a Save String directly.

Imported saves are queued and will be added to your saves list the next
time you launch the game.

### Exporting/Importing a Save String from/into CrossCode on your PC/Mac
On your PC/Mac, open the game, and press `F10` in the title screen/menu. A window will pop up
that lets you both copy the latest Save String and paste a Save String
to import into the game.

## Feedback
Speaking of suggestions: If you have any, suggest them either
[here](https://gitlab.com/rumpnisse/crossandroid/issues) or in the #crossandroid channel 
on the [Community Modding Discord Server](https://discord.gg/3Xw69VjXfW)
