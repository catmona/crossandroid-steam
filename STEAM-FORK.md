# CrossAndroid with Steam

A fork of [rumpnisse/crossandroid](https://gitlab.com/rumpnisse/crossandroid) v1.7.1 (itself a fork of Namnodorel's CrossAndroid). It adds a Steam connection, so playing CrossCode on Android counts toward your Steam playtime and achievements.

## What it adds

- **Steam sign-in** (launcher: "Steam: Sign In"), three ways: scan the QR code with the Steam app on another phone; "Open in Steam app" on the same device (opens the QR challenge URL in the Steam app); or account name and password, then a Steam Guard code or an approval in the Steam app (`beginAuthSessionViaCredentials`). The password is never stored; the app keeps only the account name and refresh token in its private preferences (`steam.xml`), and backups exclude them.
- **Status panel** (same screen, once signed in): whether Steam confirmed "Playing CrossCode", how many achievements Steam has, how many the game reported, verified and pending, and a "Check now" button that re-sends anything missing and re-reads the stats.
- **Playtime**: while the game activity is in front, the app tells Steam you are playing app 368340. That shows "Playing CrossCode" to friends and adds to your hours. It clears on pause. The app confirms it from Steam's playing-session and own-persona answers.
- **Achievements**: `SteamFeature` waits for `sc.trophies` and sets `ig.greenworks.greenworks` to a JS stub that calls `CrossAndroidSteam.unlock(id, live, name)`. A `replaying` flag around `onStorageGlobalLoad` tells new unlocks from the boot replay of the save. `SteamBridge` queues each unlock, reads the user's stats and schema (ClientGetUserStats), sets the achievement's bit, stores it (ClientStoreUserStats2), then reads the stats back and logs "Unlocked on Steam: [...] (verified...)". Replays of what Steam already has log "Already on Steam: X" and are skipped.
- **Achievement pop-ups**: a Steam-style card in the bottom-right corner (the achievement's own art from Steam's CDN, "Achievement Unlocked!", name, description) that rises out of the screen edge, glints and sinks back. Only for live unlocks; earlier unlocks synced at boot get one summary card. Setting: Settings > Steam > "Achievement pop-ups" (`steam_achievement_popups`, on by default).
- **Built-in extension-asset-preloader**: with CCLoader2, mods that require `extension-asset-preloader` (Azure's Adjustments, xenons-playable-classes) failed with "mod ... is missing". When no installed mod provides it, the app serves a stand-in from memory (`features/BuiltInMods.kt`) whose postload fills `ig.fileForwarding` from the app's extension scan.
- **Launcher tile**: the manifest declares `android:isGame="true"` and `android:appCategory="game"`. In Cocoon, add it from the App Drawer: press Y on CrossCode and choose "Treat it as a game".
- **Debug builds only**: `adb shell am broadcast -a de.radicalfishgames.crosscode.PREVIEW_POPUP [--es id <api name>]` previews the pop-up for an achievement Steam already has (nothing is sent to Steam); `... -a de.radicalfishgames.crosscode.FPS_PROBE` logs 10 s of in-page frame pacing to logcat (tag CCLoader).
- **Full save import**: put a desktop `cc.save` at `Android/data/de.radicalfishgames.crosscode/files/import/cc.save`. On the next launch it replaces localStorage `cc.save`, which holds every slot and global data. The old save is kept next to it as `cc.save.before-import-<time>`.

Code: `app/src/main/java/de/radicalfishgames/crosscode/steam/` and `features/SteamFeature.kt`, `features/BuiltInMods.kt`, `features/FullSaveImportFeature.kt`. Tests: `./gradlew testDebugUnitTest` (schema parsing, built-in mod serving).

## Before making this repo public

The owner plans to publish this once it works well. Before that:

- [ ] **License**: neither upstream repo (rumpnisse or Namnodorel) declares a license, so by default we may not redistribute their code. Ask them for a license, or for permission, before going public.
- [ ] **No GPL code**: GameNative is GPL-3. The Steam code here is written from the protocol, not copied from GameNative. Keep it that way, or adopt GPL-3 for the whole project. JavaSteam is MIT and ZXing is Apache-2.0; both are fine to bundle and are listed on the About screen.
- [ ] **No secrets**: never commit `keystore.properties`, keystores, `local.properties`, refresh tokens or logs with tokens. Check the history (`git log -p`) before publishing.
- [ ] **Identity**: commits use the GitHub noreply address (`30540400+catmona@users.noreply.github.com`), set as repo-local git config.
- [ ] **Steam terms**: this signs in with the user's own account and only reports their own play. Say clearly in the README that it is unofficial and not affiliated with Valve or Radical Fish Games, and that you need to own the game on Steam.
- [ ] **Package name**: the app still uses upstream's `de.radicalfishgames.crosscode`. Consider an id of our own for a public build, so it can sit beside upstream installs.
- [ ] **minSdk**: upstream says 24, but JavaSteam uses `java.util.Base64` (API 26). Raise minSdk to 26, or enable core library desugaring.
