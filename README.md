<div align="center">

**Futon is a free and open-source manga reader for Android with built-in online content sources.**

![Android 8.0](https://img.shields.io/badge/android-8.0+-brightgreen) [![Sources count](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2FKotatsu-Redo%2Fkotatsu-parsers-redo%2Frefs%2Fheads%2Fmaster%2F.github%2Fsummary.yaml&query=total&label=manga%20sources&color=%23E9321C)](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) [![License](https://img.shields.io/github/license/madebycli/Futon-MihonFIx)](https://github.com/madebycli/Futon-MihonFIx/blob/devel/LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/madebycli/Futon-MihonFIx?sort=date&display_name=tag&style=flat)](https://github.com/madebycli/Futon-MihonFIx/releases/latest) [![Discord](https://img.shields.io/discord/1452862077134700628)
](https://discord.gg/9sqBHXhwzz)


### Main Features

<div align="left">

* Online [manga catalogues](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) (with 1200+ manga sources)
* Support for externally installed [Mihon / Tachiyomi / Keiyoushi](https://github.com/keiyoushi/extensions) extensions
* A continuous chapter list for external Comix when chapter numbers are unique; group views remain available for duplicates
* Search manga by name, genres and more filters
* Favorites organized by user-defined categories
* Reading history, bookmarks and incognito mode support
* Download manga and read it offline. Third-party CBZ archives are also supported
* Clean and convenient Material You UI, optimized for phones, tablets and desktop
* Standard and Webtoon-optimized customizable reader, gesture support on reading interface
* Notifications about new chapters with updates feed, manga recommendations (with filters)
* Integration with manga tracking services: Shikimori, AniList, MyAnimeList, Kitsu
* Password / fingerprint-protected access to the app
* Automatically sync app data with other devices on the same account
* Support for devices running Android 8.0+

</div>
<div align="left">

## Development Setup

### Prerequisites

- **JDK 17** (recommended: [Temurin](https://adoptium.net/temurin/releases/) distribution)
- **Android SDK** (compile SDK 37, minimum SDK 26; Gradle selects the required build tools)
- **Android Studio** (recommended) or Android SDK command-line tools

### Building the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/madebycli/Futon-MihonFIx.git
   cd Futon-MihonFIx
   ```

2. **Build debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`
   
   *Note: Requires `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables. Keep signing material outside the repository.*

4. **Build nightly APK:**
   ```bash
   ./gradlew assembleNightly
   ```
   Output: `app/build/outputs/apk/nightly/app-nightly.apk`

### Running Tests

- **Unit tests:**
  ```bash
  ./gradlew test
  ```

- **Instrumented tests:**
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

### Code Quality

- **Lint check:**
  ```bash
  ./gradlew lint
  ```

- **Full check (lint + tests):**
  ```bash
  ./gradlew check
  ```

For detailed contribution guidelines, see [CONTRIBUTING.md](./CONTRIBUTING.md).
</div>
### In-App Screenshots

<div align="center">
    <img src="./metadata/en-US/images/phoneScreenshots/1.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/2.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/3.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/4.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/5.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/6.png" alt="Mobile view" width="250"/>
</div>

<br>

<div align="center">
    <img src="./metadata/en-US/images/tenInchScreenshots/1.png" alt="Tablet view" width="400"/>
    <img src="./metadata/en-US/images/tenInchScreenshots/2.png" alt="Tablet view" width="400"/>
</div>

### Downloads

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?logo=github)](https://github.com/madebycli/Futon-MihonFIx/releases/latest)

Download the signed APK from [GitHub Releases](https://github.com/madebycli/Futon-MihonFIx/releases/latest).
Each stable release includes an APK, its SHA-256 checksum and build information.

### Localization
Help translate the upstream Futon project:

<a href="https://hosted.weblate.org/engage/futon/"><img src="https://hosted.weblate.org/widget/futon/open-graph.png" width="500"></a>
<a href="https://hosted.weblate.org/engage/futon/"><img src="https://hosted.weblate.org/widget/futon/horizontal-auto.svg" width="500"></a>

### Contributing

<br>

**📌 Pull requests are welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.**

### Community

Join the upstream Futon community on Discord for support and discussion: https://discord.gg/9sqBHXhwzz

### Certificate fingerprints

SHA-256 of the signing certificate used for this fork's published releases 9.8.3 and 9.8.4:

```plaintext
12:FE:17:24:5D:EC:B8:4C:98:98:5D:DC:C0:88:84:B8:74:D1:0A:78:E4:FB:74:7A:6E:D3:E2:A4:2D:77:B6:25
```

### License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

<div align="left">

You may copy, distribute and modify the software as long as you track changes/dates in source files. Any modifications
to or software including (via compiler) GPL-licensed code must also be made available under the GPL along with build &
install instructions.

</div>

### DMCA disclaimer

<div align="left">

The developers of this application do not have any affiliation with the content available in the app and do not store
or distribute any content. This application should be considered a web browser, all content that can be found using this
application is freely available on the Internet. All DMCA takedown requests should be sent to the owners of the website
where the content is hosted.

</div>

---

### Acknowledgments

<div align="left">

**Futon is built upon the exceptional work of the [Kotatsu](https://github.com/KotatsuApp/Kotatsu) project.**

We are deeply grateful to:

* **The original Kotatsu developers** for creating such an outstanding manga reader and making it open source
* **The Kotatsu community** for their contributions, testing, and support
* **All translators** who helped localize Kotatsu through [Weblate](https://hosted.weblate.org/engage/kotatsu/)
* **[Kotatsu-Redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo)** for continuing parser development and maintenance
* **[Kototoro](https://github.com/Kototoro-app/Kototoro)** for the Tachiyomi extension integration guide

This project stands on the shoulders of giants. The Kotatsu team's dedication to creating a feature-rich, user-friendly manga reader has provided an incredible foundation for Futon to build upon.

**Thank you to everyone who contributed to Kotatsu — your work continues to benefit the manga reading community!**

For the original Kotatsu project, please visit: [github.com/KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu)

</div>
