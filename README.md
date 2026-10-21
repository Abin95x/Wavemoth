# Wavemoth

A music player for Android that streams from YouTube Music. No account, no ads, no sign-in. Your library lives on your phone.

<p align="center">
  <img src="docs/screenshots/home.png" width="22%" alt="Home" />
  <img src="docs/screenshots/search.png" width="22%" alt="Search" />
  <img src="docs/screenshots/playlist.png" width="22%" alt="Playlist" />
  <img src="docs/screenshots/settings.png" width="22%" alt="Settings" />
</p>

## Features

**Listening**
- Search songs, artists, albums and playlists, or browse by genre and mood
- Plays in the background and on the lock screen, with media notification controls
- Best audio quality available (Opus up to 160 kbps), cached as it plays
- Queue, shuffle and repeat; play next / add to queue from any song
- Mini player: tap to open the full player, swipe left or right to skip songs
- Time-synced lyrics from [LRCLIB](https://lrclib.net). Tap a line to jump to it
- Autoplay radio when the queue ends. It picks related songs and weighs in the artists you like and play, songs you skipped, and what you played recently

**Library**
- Liked Songs, recently played, and your own playlists (create, rename, delete)
- Import a playlist from a `.csv` file (title, artist). Each row is matched on YouTube Music
- Export a playlist as `.csv`
- Back up and restore your whole library as a JSON file

**Settings**
- Stop music when the app is swiped away from Recents (on by default)
- Clear the song and artwork cache. Your library is kept

## Download

**[⬇ Download Wavemoth.apk](release/Wavemoth.apk?raw=1)** (Android 8.0 or newer)

1. Open the link on your phone and download the APK.
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Tap **Install**.

The APK is signed with a debug key, so Play Protect may warn about an unknown developer. Tap **Install anyway** to continue. To update, install the new APK over the old one. Your library is kept.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```bash
# Point Gradle at your SDK once
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (minified, debug-signed)
```

Install on a connected phone:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Tests

Unit and UI tests run on your computer with Robolectric, so no phone is needed. The UI tests use a fake player and fake search results, so they don't touch the network.

```bash
./gradlew testDebugUnitTest
```

`YouTubeSourceOnlineTest` calls YouTube for real and only runs with `WAVEMOTH_ONLINE_TESTS=1`.

## How it works

| Part | Code |
|---|---|
| Catalogue and stream URLs ([NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)) | `data/YouTubeSource.kt` |
| Playback service and media session ([Media3](https://developer.android.com/media/media3)) | `playback/PlaybackService.kt` |
| Audio fetched in short ranged requests, so YouTube doesn't throttle it | `playback/ChunkedDataSource.kt` |
| Autoplay radio scoring | `data/Radio.kt`, `data/SkipHistory.kt` |
| Lyrics (LRCLIB + LRC parser) | `data/Lyrics.kt` |
| Library, playlists, backup | `data/LibraryRepository.kt` |
| CSV import / export | `data/PlaylistFile.kt`, `data/PlaylistImporter.kt` |
| UI (Jetpack Compose, Material 3) | `ui/` |

Paths are under `app/src/main/java/com/wavemoth/app/`.

On first launch, the app adds a starter playlist from `app/src/main/assets/my-playlist.csv`.

## Disclaimer

Wavemoth is a personal project and is not affiliated with YouTube or Google. It uses NewPipeExtractor, which is licensed under GPL-3.0. Streaming content may be subject to YouTube's Terms of Service. Use it at your own risk.
