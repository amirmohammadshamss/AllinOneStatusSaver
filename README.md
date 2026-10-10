# All in One Status Saver

The active app is now written in **Kotlin** with **Jetpack Compose and Material 3**.
It compiles against **Android 17 / API 37.2** and targets API 37. The application ID remains
`com.cd.statussaver`; the version is 2.0 (version code 2).

## Toolchain

- Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17.
- AGP built-in Kotlin support and Kotlin Compose compiler plugin 2.4.10.
- Compose BOM 2026.09.00, Activity 1.13.0, Lifecycle 2.11.0, WorkManager 2.12.0.
- Minimum Android version: Android 7.0 / API 24, raised from API 21 for current Jetpack libraries.
- Google Maven and Maven Central replace JCenter and bundled native dependencies.

Release references: [Android API levels](https://developer.android.com/guide/topics/manifest/uses-sdk-element),
[AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## What works in the new implementation

- WhatsApp / WhatsApp Business status browsing and app shortcuts through Android's folder picker,
  with multiple connected folders and image/video filters.
- Import one or more images/videos through the document picker or Android's share menu.
- Download direct HTTPS media links and public pages exposing an MP4/WebM/3GP
  video source or Open Graph video URL, including extensionless CDN links with
  an explicit supported video MIME type. WorkManager keeps the job scheduled
  across app restarts and retries network failures.
- Saved-media gallery, image/video previews, sharing, document export, and deletion.
- English and Persian screen labels, selected automatically from the device language.
- System light/dark themes, dynamic colors on Android 12+, edge-to-edge layouts,
  adaptive media grid, RTL support, and confirmation before deleting a saved copy.
- No broad storage permissions, clipboard monitoring, account requirement, ads,
  or analytics. Clipboard access happens only when the user presses Paste.

### Connecting WhatsApp

View a status in WhatsApp first. In **Statuses**, press **Connect folder** and select:

```
Android/media/com.whatsapp/WhatsApp/Media/.Statuses
Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses
```

Enable **Show hidden files** in the system picker if necessary. Selecting the
`Media` parent also works; the app searches for `.Statuses` rather than including
unrelated WhatsApp media. Folder visibility depends on the device's document
provider. If access is unavailable, import or share the image/video instead.
Use **Disconnect folders** to revoke the app's persisted read grants.

Saved copies live in private app storage. Use **Export** to keep a copy outside
this app; uninstalling or clearing app data removes private saved files. Existing
legacy downloads can be imported from their original folders. Originals are
never deleted by the saved-gallery delete action.

## Migration limits

This is a replacement of the app's UI and storage/download foundation, **not full
feature parity with every old platform integration**. The original implementation
is preserved under `legacy/` and is excluded from the build.

The old Instagram cookie login/private story APIs, platform-specific TikTok/X/
SnackVideo scraping services, ads, Firebase push notifications, and legacy Arabic layouts have not been ported.
The Compose UI supports English and Persian; some download/server error details
are still displayed in English.
Instagram, Facebook, TikTok, X, Likee, ShareChat, Roposo, and SnackVideo links only
work when the public page exposes a supported direct video source. Sign-in pages,
private stories, embedded player pages, and protected pages report an unsupported
link. Open Graph cover images are intentionally not mistaken for post downloads.
Full platform support requires maintained official integrations or a supported
backend; updating Android dependencies cannot repair retired third-party APIs.

## Build and verify

Install JDK 17 and a current Android Studio. In SDK Manager install Android API 37.2
and Android SDK Build Tools 36.0.0. Configure `local.properties` with your SDK path
or set `ANDROID_HOME`, then run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

If `dl.google.com` is unavailable in your network, this project supports an
alternate Google Maven URL. The following official Google CDN worked here:

```sh
./gradlew -PgoogleMavenUrl=https://redirector.gvt1.com/edgedl/android/maven2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The default repositories remain Google Maven and Maven Central. This override
also applies to plugin resolution. The SDK installed for this verification is
under `/tmp/status-android-sdk`, configured in the ignored `local.properties`.
Install a permanent SDK through Android Studio for ongoing development.

The second command needs a connected device or emulator. For a release, retain
the original signing key to update existing installations and configure signing
locally. No signing credentials are included in this project.

Core JVM tests can run independently of the Android SDK and Google Maven:

```sh
./gradlew -p tools/jvm-tests test
```

### Verification in this environment

- Gradle 9.6.0 downloaded and runs on JDK 17; the wrapper JAR is updated to 9.6.0.
- **18 core Kotlin tests passed**, covering HTTPS link handling, public-video
  extraction, MIME handling, atomic file writes, size/empty-file rejection,
  cancellation, truncated-response rejection, extensionless video sources, and
  interrupted-write cleanup.
- **Android debug build, Android unit tests, and lint passed** using Google's
  alternate CDN. Lint has no errors; remaining warnings recommend newer Gradle,
  coroutine, and Jsoup versions.
- **Release build passed**, including R8 code shrinking and resource shrinking.
  The release APK is unsigned; configure your original signing key for distribution.
- **Four Compose device tests passed** on Android 17 / API 37.2 with 16 KB
  memory pages: main-screen navigation, incoming shared links, link retention
  after activity recreation, and rejection of insecure links.

Build artifacts are `app/build/outputs/apk/debug/app-debug.apk` (installable debug
APK) and `app/build/outputs/apk/release/app-release-unsigned.apk` (unsigned release).
