# DiZien Launcher

A dark "Amber Slate" Android home-screen (launcher) app with a Debian-style
look: selectable themes, icon styles and wallpapers, swipeable Home pages with
a fixed dock, Android widgets, a search bar, an all-apps drawer, a themed
notification panel, and Settings / About.

> **Status: early review build (0.1.5).** It has been tested on an Android
> emulator (x86_64, Android 9) and on the owner's own phone. It has not been
> tested widely, so expect rough edges. Bug reports and pull requests are
> welcome.

**Not affiliated with Debian. Debian is a registered trademark owned by
Software in the Public Interest, Inc.**

## Features

- Home pages you swipe left and right, page dots, fixed dock, drag icons to move them
- Swipe up for the all-apps drawer, swipe down for a notification panel
- Look screen: themes, icon styles, original wallpapers (drawn by code, no image files)
- Android widgets on a widget page left of Home
- Optional double-tap on empty Home to lock the screen
- Optional shortcut to open a separate terminal app, if you have one installed

## Privacy and permissions

- **No INTERNET permission.** The launcher itself cannot go online. When you
  use "Search the web for ..." it only hands the text you typed to your
  default browser app, which does the searching.
- Notification panel: reads notifications only after you enable Notification
  access in Settings. Nothing leaves the device.
- Double-tap to lock: optional, you choose either the Accessibility service
  "DiZien screen lock" (it receives no events and only presses Android's lock
  action) or the Device Admin "lock screen" right. Both are off by default.
- No root, no accounts, no analytics.

## Install

1. Download `DZianLauncher-0.1.5-review.apk` from the
   [Releases](../../releases) page.
2. Allow "install unknown apps" for your browser or file manager, then open the APK.
   Requires Android 7.0+ (API 24). The APK contains arm64-v8a and x86_64 builds.
3. The release APK is signed with a review key, so it can only be updated by
   builds signed with the same key. Builds you compile yourself need to be
   uninstalled first.

## Set as your default Home app

1. Press the Home button after installing. Android asks which Home app to use;
   choose **DiZien Launcher** and tap **Always**.
2. Or go to Android Settings > Apps > Default apps > Home app (wording varies
   by phone) and choose **DiZien Launcher**.
3. To go back, choose your previous launcher in the same place.

## Not included

No terminal, rootfs, NetHunter or Kali. The Debian-style terminal is a separate
app. The launcher can only open it if it is installed.

## Build

```
./gradlew assembleDebug      # needs JDK 17+ and Android SDK 34
./gradlew assembleRelease    # signs with signing.properties if you create one
```

`signing.properties` (not in the repo) looks like:

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## License

GPL-3.0-or-later, see `LICENSE` and `NOTICE`. Third-party material:

- DejaVu Sans Mono font (Bitstream Vera / DejaVu license)
- Debian Open Use Logo, shown only as a reference tile in Settings > About,
  (c) SPI, used under LGPL-3.0-or-later or CC BY-SA 3.0, see `NOTICE-debian-logo.txt`
- AndroidX / Jetpack Compose (Apache-2.0), see `LICENSES-Apache-2.0.txt`

The app name and icon do not use the Debian name or the Debian swirl.
