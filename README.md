# Prime One — Android APK project

Prime One is packaged as a native Android WebView app. The original Prime One HTML app is bundled locally in `app/src/main/assets/index.html`, and the supplied fantasy-knight artwork is used for the launcher icon.

## Build the APK on GitHub

1. Create a new GitHub repository.
2. Upload the **contents** of this project folder (include the hidden `.github` folder at the repository root).
3. Open the repository's **Actions** tab.
4. Select **Build Prime One APK** and press **Run workflow**, or push to `main` / `master`.
5. When the run finishes, open it and download the `Prime-One-APK` artifact.
6. Extract the artifact and install `app-debug.apk` on your Android phone.

## Updated interface

- The crowded feature shortcut row is hidden.
- The Files / Recycle Bin floating shortcuts are hidden.
- The main navigation row is collapsed to one **gear-shaped Settings** control.
- Open Settings → **Player sections** to reach Queue, Now Playing, Playlists & Library, Folders, Artists, Tags, Albums and Search.
- Open Settings → **Storage & Recovery** to open Device Files & Photos, the Recycle Bin, or the Android full-storage permission screen.
- Equalizer, themes, cloud, import, notifications and other settings remain available from the Settings panel.

## Android media controls

The native Android MediaSession publishes the current track title, artist, album art (when the artwork is embedded as a data URL), playback state and progress. Android can then show Prime One in its media controls / notification shade and expose play, pause, previous, next and seek controls. Android decides exactly where the media card appears based on its OS version, media-control settings and other active players.

If the notification shade is showing another player's card, start playback in Prime One and check the system's media-player carousel; Android may keep several active media sessions available.

## Device permissions and file tools

- On first launch, Android may ask for notification permission.
- Open Settings → Storage & Recovery → **Grant Full Storage Access**. Android Settings opens; enable **Allow access to manage all files**, then return to Prime One.
- Common image files can be previewed (up to 12 MB each). Text files can be edited and saved. Deleted files can be moved to Prime One's private recycle bin, restored to their original path, or permanently deleted.
- Restore is safely refused if the original path already contains a file with the same name.
- Android protects some locations, including `Android/data` and `Android/obb`; Prime One does not bypass those restrictions.
- The recycle bin is private to this app installation. Clearing app data or uninstalling Prime One can remove its contents, so it is not a replacement for a separate backup.

Android does not permit an app to silently grant itself permissions. `MANAGE_EXTERNAL_STORAGE` is special access and Google Play heavily restricts its use; this project is intended for personal installation/testing or another distribution path where the permission is permitted.

## Existing Prime One features

The original bundled music-player features remain, including equalizer, playlists/library, tags and artwork editing, themes, voice tools, and cloud connection flows. Google Drive / Google Photos still require supported Google sign-in and API configuration; this wrapper does not create Google credentials or bypass provider permissions.

## Technical notes

- Package: `com.primeone.player`
- Minimum Android: 8.0 (API 26)
- Build: Android Gradle Plugin 8.7.3 / Gradle 8.9 / Java 17
- APK output: `app/build/outputs/apk/debug/app-debug.apk`
