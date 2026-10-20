<div align="center">

<img src="./.github/assets/logo.png" alt="Yomihon logo" width="128" height="128" />

# Yomihon
### Full-Featured Reader for Android
Discover and read manga, webtoons, comics, and more – easier than ever on your Android device.


[![Discord server](https://img.shields.io/discord/1464063546257182876.svg?label=&labelColor=6A7EC2&color=7389D8&logo=discord&logoColor=FFFFFF)](https://discord.com/invite/TXvTZdBuQa)
[![GitHub downloads](https://img.shields.io/github/downloads/yomihon/yomihon/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://yomihon.github.io/download)

[![CI](https://img.shields.io/github/actions/workflow/status/yomihon/yomihon/build.yml?labelColor=27303D)](https://github.com/yomihon/yomihon/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/mihonapp/mihon?labelColor=27303D&color=0877d2)](/LICENSE)
<!--
[![Translation status](https://img.shields.io/weblate/progress/mihon?labelColor=27303D&color=946300)](https://hosted.weblate.org/engage/mihon/)
-->

## Download


[![Yomihon Stable](https://img.shields.io/github/release/yomihon/yomihon.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://yomihon.github.io/download)
<!--
[![Mihon Beta](https://img.shields.io/github/v/release/mihonapp/mihon-preview.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39)](https://mihon.app/download)
-->

*Requires Android 8.0 or higher.*

New to Yomihon? Check out our [**Getting Started Guide**](https://yomihon.github.io/docs/guides/getting-started) to set up your library.

---
</div>

## Features

Yomihon takes the features of Mihon and enhances them with advanced tools for language learners.

| Feature | Description |
| :--- | :--- |
| 🔍 **Built-in Text Recognition** | Fast, on-device OCR to extract text from images in real-time. Online models are also available for more languages. |
| 📖 **Yomitan Dictionary Support** | Seamless dictionary lookups for language learners across multiple languages. |
| **One-click Anki Cards** | Instantly create flashcards while reading and looking up new words. |

Core Reader Features:

* Experimental offline panel-by-panel reading.
* Local reading of content.
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/) and [Hikka](https://hikka.io/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.
* Plus much more...

## Simplified Chinese (offline)

This fork can recognize Simplified Chinese on the phone. The model is bundled in the APK. Selecting it does not download weights and does not upload the page.

1. Install the arm64 APK (see below).
2. Open **More → Text recognition**.
3. Set **Text recognition model** to **Chinese (offline)**.
4. Leave **Strict offline** on. Google Lens and OwOCR are hidden, and images are not sent to them even when **Use fallback models** is on.
5. Import the Yomitan [CC-CEDICT](https://github.com/MarvNC/cc-cedict-yomitan) dictionary (`CC-CEDICT.zip`). With **Chinese (offline)** selected, **Settings → Advanced → Parser language = Auto** treats pure hanzi as Chinese. You can also set the parser to **Chinese**. Kana still uses the Japanese parser.
6. Read in **webtoon** mode (long-strip) or paged mode. Long-press a bubble and drag a box around the text. A box that crosses a split between two strips is stitched back together before recognition. Turn on **Activate OCR on long tap** in the reader settings if a long-press opens the page menu instead.

With **Chinese (offline)** selected, each page is scanned on the phone as it loads, and a downloaded chapter is scanned too. Bubbles are outlined. Tap one to open the same result as a box selection. A tap that misses text still turns the page or opens the menu. Detection uses ML Kit Chinese text blocks grouped on the device. It does not use the panel detector.

Build an arm64 release without Firebase. Do not pass `-Pinclude-telemetry`.

```sh
# Japanese manga-ocr weights are still required for the Legacy and Fast engines.
# Chinese recognition does not use those files. See CONTRIBUTING.md for the file names and checksums.
cat > keystore.properties << 'EOF'
storeFile=../signing/zh-fork-release.jks
storePassword=android
keyAlias=zh-fork
keyPassword=android
EOF
./gradlew assembleRelease -PabiSplits=arm64-v8a
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

`keystore.properties` is gitignored. With those four lines, release builds are signed with the keystore committed at `signing/zh-fork-release.jks`. The store password, key password, and alias are the public debug-style values above (`android` / `android` / `zh-fork`). GitHub Actions secrets could not be set from this environment, so the key lives in the repo instead of `SIGNING_KEY`. The certificate SHA-256 is `87:24:F7:B6:0C:AD:08:1E:BF:46:F5:0E:0F:ED:D9:A6:78:70:0F:25:87:74:32:BD:E6:7C:C2:51:F7:1A:CB:E4`.

A pushed tag `zh-v0.4.3-N` runs `.github/workflows/zh-release.yml`, which writes the same `keystore.properties`, builds only arm64, and publishes `yomihon-arm64-v8a.apk` on a GitHub Release. `versionCode` is `2800 + N` and `versionName` is `0.4.3-zh.N`, so a later tag installs over an earlier one. It does not install over upstream Yomihon or over an APK signed with a machine debug key; uninstall that copy once.

Without `keystore.properties`, Gradle still falls back to the machine debug keystore. Those APKs will not update the release builds.

### Staying in sync with upstream

Yomihon upstream is <https://github.com/yomihon/yomihon>. Chinese-only code lives in new files where possible (`data/.../ocr/zh/`, the offline-policy types). After an upstream release:

```sh
git remote add upstream https://github.com/yomihon/yomihon   # once
git fetch upstream
git rebase upstream/main
```

Rebuild with `./gradlew assembleRelease` and without `-Pinclude-telemetry`.

## 🤝 Contributing

Feature requests, bug reports, and pull requests are welcome. For major changes, please open an issue or join our [discord](https://discord.com/invite/TXvTZdBuQa) to discuss what you would like to change.

Before opening an issue, please check the [FAQ](https://yomihon.github.io/docs/faq/general) and search [existing issues](https://github.com/yomihon/yomihon/issues).

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Model and dataset attribution for externally fetched ML assets is documented in [MODEL_ATTRIBUTION.md](./MODEL_ATTRIBUTION.md).

## Credits

Yomihon is a community-driven fork of the [Mihon](https://github.com/mihonapp/mihon) project. Thank you to all the people who have contributed.

> Note: this project is unaffiliated with the official Mihon Organization.

---

### Disclaimer
The developer(s) of this application have no affiliation with the content providers available. This application hosts zero content. Users are responsible for the content they bring or access.

### License
Copyright © 2015 Javier Tomás  
Copyright © 2024 Mihon Open Source Project  
Copyright © 2025 Yomihon  

Licensed under the Apache License, Version 2.0. See the [LICENSE](/LICENSE) file for more details.

