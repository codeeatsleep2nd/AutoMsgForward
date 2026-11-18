# Agent instructions

Android app (Kotlin, Jetpack Compose) that forwards incoming SMS to Gmail. See `README.md` for what it does
and how it is built.

## Documentation

- `README.md` (English) and `README.zh-CN.md` (简体中文) are translations of each other. Any change to one
  must be made to the other in the same commit, keeping the same sections, tables and code blocks.
- App UI labels are English only; the Chinese README keeps them in English (bold), with a Chinese gloss
  where useful.

## Before creating a release

Do every step, in order. Don't tag or publish until all of them pass.

1. **READMEs match the release.** For every user-visible change since the previous tag
   (`git log <previous-tag>..HEAD`), check both READMEs and update them:
   - Features list
   - Setup steps, and UI labels and button names exactly as the app shows them
   - Troubleshooting and Limits
   - The "How it works" file table (new or renamed source files)
2. **Screenshot is current.** `docs/screenshot.png` shows the current main screen. Retake it if the
   screen changed. Use placeholder data only (`you@gmail.com`, `+15551234`), never a real account
   or phone number.
3. **Version bumped.** Increase `versionCode` and set `versionName` in `app/build.gradle.kts`.
4. **Checks pass.** Run `./gradlew testDebugUnitTest lintDebug assembleDebug` with no new lint warnings.
5. **APK installs as an update.** Releases are signed with the debug key (`~/.android/debug.keystore`).
   Confirm with `apksigner verify --print-certs` that the certificate matches the previous release's APK.
6. **Tag the exact commit the APK was built from.** The working tree must be clean when you build.
7. **Release notes.** Cover what's new, what to do after updating, and known limits. Attach
   `AutoMsgForward-<versionName>.apk`.

Ask the user before publishing a release. Pushing code to `main` doesn't mean they've approved a release.

## Build environment

- Use JDK 17 or 21. Early-access JDKs (for example 25-loom) break Gradle's version parsing.
- If `local.properties` doesn't exist, set `ANDROID_HOME` to an Android SDK with `platforms;android-35`
  and `build-tools;35.0.0`.
