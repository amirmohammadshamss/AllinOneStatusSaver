# Legacy implementation

This directory preserves the original Java activities, API models, XML layouts,
native libraries, tests, and Gradle/manifest configuration for reference.
It is outside the Android source sets and is **not compiled or packaged**.

The active application lives in `app/src/main/kotlin` and uses Jetpack Compose.
The old private Instagram API/cookie login, platform scraping endpoints, ads,
Firebase notifications, Arabic layouts, and native SnackVideo helper have not been
ported. See the root README for the current supported behavior and verification
limits. Do not re-enable the legacy source directory in the active build: its
storage model and dependencies are incompatible with the current target SDK.
