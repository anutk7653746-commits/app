# Findora — Android media search

Findora is a bilingual (Hebrew/English) Android app for discovering openly licensed images, video clips and films, downloading the actual media file, and saving it to a location chosen through Android's document picker (including Google Drive when its provider is installed and signed in).

## Features

- Hebrew and English interface with RTL/LTR layout.
- Expands a Hebrew, Arabic, Cyrillic, CJK or English search phrase into translations for 11 common languages (English, Arabic, Spanish, French, German, Russian, Chinese, Portuguese, Italian, Turkish and Japanese), using MyMemory's public translation endpoint. The original phrase is always searched too.
- Searches Wikimedia Commons for images and freely licensed media files.
- Searches Internet Archive for movies/videos, and only enables downloads for items whose metadata identifies a Creative Commons or public-domain license.
- Downloads the original file to temporary app storage, then opens Android's **Create document** picker. Choose Google Drive and a folder to transfer the real file into Drive; the app does not save only a URL.
- Displays source and license information. Check the license page and follow attribution/share-alike requirements before reuse.

## Google Drive behavior

No Drive password, private key or OAuth secret is embedded in the app. The save flow uses Android's Storage Access Framework. On the save dialog, select Google Drive (the Drive app/provider must be installed and signed in), then choose the destination folder. Android/Drive handles the authenticated file transfer.

## Build an APK

The repository includes `.github/workflows/android.yml`. On GitHub, open **Actions → Build Findora APK → Run workflow**. When the job succeeds, open its run and download the `Findora-Android-APK` artifact. It contains `app-debug.apk`, an installable debug build.

Local build requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.9. Run:

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Data and limitations

- Search requires an internet connection. Third-party services (MyMemory, Wikimedia Commons and Internet Archive) have their own availability, rate limits and terms. MyMemory documents a 500-byte maximum per translation request and a 5,000-character/day anonymous limit; extensive translation searches may therefore fall back to the original query after the free quota is reached.
- Translation expansion covers the languages listed above; it is not literally every language in existence. The original query is always included too.
- Internet Archive items without an explicit recognized Creative Commons/public-domain license are excluded from downloadable results. A displayed license still needs to be checked at its source page.
- Google Drive must be available as a document provider on the Android device. If it is not shown, install/sign in to Google Drive or choose another storage provider.
- Downloads larger than 1.5 GB are blocked to protect device storage.

## Upload this project to the requested empty GitHub repository (Windows)

1. Extract this ZIP and open PowerShell inside the extracted `findora` folder.
2. Make sure Git for Windows is installed and configured with your GitHub account.
3. Run `./upload-to-github.ps1` (or follow the commands below manually).

```powershell
git init -b main
git remote add origin https://github.com/anutk7653746-commits/app.git
git add -A
git commit -m "Create Findora Android app"
git push -u origin main
```

GitHub will request authentication if it is not already configured. Do not paste a personal access token into the source files. After the push, the workflow in `.github/workflows/android.yml` starts automatically; download the APK from the successful run's artifact.
