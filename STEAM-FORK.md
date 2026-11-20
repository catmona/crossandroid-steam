# CrossAndroid with Steam

A fork of [rumpnisse/crossandroid](https://gitlab.com/rumpnisse/crossandroid) v1.7.1 (itself a fork of Namnodorel's CrossAndroid). It adds a Steam connection, so playing CrossCode on Android counts toward your Steam playtime and achievements.

## What it adds

- **Steam sign-in** (launcher: "Steam: Sign In"): you scan a QR code with the Steam mobile app. The app keeps the account name and refresh token in its private preferences (`steam.xml`), and backups exclude them.
- **Playtime**: while the game activity is in front, the app tells Steam you are playing app 368340. That shows "Playing CrossCode" to friends and adds to your hours. It clears on pause.
- **Achievements**: `SteamFeature` sets `ig.greenworks.greenworks` to a JS stub that calls `CrossAndroidSteam.unlock(id)`. `SteamBridge` queues each unlock, reads the user's stats and schema (ClientGetUserStats), sets the achievement's bit and stores it (ClientStoreUserStats2). On every load the game re-sends trophies already in the save, so earlier unlocks catch up too.
- **Full save import**: put a desktop `cc.save` at `Android/data/de.radicalfishgames.crosscode/files/import/cc.save`. On the next launch it replaces localStorage `cc.save`, which holds every slot and global data. The old save is kept next to it as `cc.save.before-import-<time>`.

Code: `app/src/main/java/de/radicalfishgames/crosscode/steam/` and `features/SteamFeature.kt`, `features/FullSaveImportFeature.kt`.

## Before making this repo public

The owner plans to publish this once it works well. Before that:

- [ ] **License**: neither upstream repo (rumpnisse or Namnodorel) declares a license, so by default we may not redistribute their code. Ask them for a license, or for permission, before going public.
- [ ] **No GPL code**: GameNative is GPL-3. The Steam code here is written from the protocol, not copied from GameNative. Keep it that way, or adopt GPL-3 for the whole project. JavaSteam is MIT and ZXing is Apache-2.0; both are fine to bundle and are listed on the About screen.
- [ ] **No secrets**: never commit `keystore.properties`, keystores, `local.properties`, refresh tokens or logs with tokens. Check the history (`git log -p`) before publishing.
- [ ] **Identity**: commits use the GitHub noreply address (`30540400+catmona@users.noreply.github.com`), set as repo-local git config.
- [ ] **Steam terms**: this signs in with the user's own account and only reports their own play. Say clearly in the README that it is unofficial and not affiliated with Valve or Radical Fish Games, and that you need to own the game on Steam.
- [ ] **Package name**: the app still uses upstream's `de.radicalfishgames.crosscode`. Consider an id of our own for a public build, so it can sit beside upstream installs.
- [ ] **minSdk**: upstream says 24, but JavaSteam uses `java.util.Base64` (API 26). Raise minSdk to 26, or enable core library desugaring.
