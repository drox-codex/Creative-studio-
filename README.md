# Creative Studio

**Creative Studio** is an all-in-one Android creative suite built with **Kotlin** and **Jetpack Compose**, combining non-destructive photo editing, multi-layer graphic design, multi-track video editing, and a Gemini-powered AI Creative Assistant inside a unified monochrome obsidian (`#090A0C`) and metallic silver (`#C4C8CE`) workspace.

---

## Core Capabilities

- **Home & Workspace Hub**: Personalized greeting, live project/template search, quick studio launchers, Continue Editing spotlight, recent projects, and curated templates.
- **Photo Studio**: Zero-permission Photo Picker import, non-destructive color & tonal adjustments (brightness, contrast, saturation, temperature, exposure, highlights, shadows, sharpness, vignette), 7 editorial filter presets, crop ratios, 90° rotation and flip, multi-layer text/shape/image/circle-mask overlays, freehand drawing strokes, Gemini AI image editing & background removal, Undo/Redo, cloud autosave, and MediaStore PNG/JPEG export & share.
- **Design Studio**: Interactive multi-layer canvas with preset and custom dimensions (1:1 Social, 4:5 Poster, 16:9 Presentation, 16:9 Thumbnail, 7:4 Business Card), alignment guides, draggable elements, Brand Kit swatch integration, layer locking/visibility/reordering/duplication, Undo/Redo, and PNG/JPEG/PDF export.
- **Video Studio**: Multi-track timeline editor (`VIDEO`, `TEXT`, `AUDIO`) for imported video and audio clips featuring `Media3 ExoPlayer` live preview, timed caption overlays, keyframe zoom/opacity interpolation, trim, split at playhead, clip reordering, playback speed (`0.25x`–`2.0x`), transitions, color filter presets, audio mute/volume/fade controls, and `Media3 Transformer` `720p`/`1080p` MP4 export.
- **AI Creative Assistant**: Powered by **Firebase AI Logic** (`Firebase.ai` + App Check) for context-aware structured editing commands and image generation/editing with Undo/Redo and Firestore AI history.
- **Assets Library, Projects & Settings**: Searchable templates, icons, shapes, bundled fonts, filters, text presets, backdrops, and transitions; user-isolated Firestore project management; Brand Kit editor; Obsidian/Slate theme toggle, reduced motion, language preference, export defaults, and storage cache management.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| **Language & UI** | Kotlin `2.2.10`, Jetpack Compose, Material 3, Adaptive Navigation (`NavigationBar` / `NavigationRail`) |
| **Navigation & Serialization** | Navigation Compose (`@Serializable` type-safe routes), `kotlinx.serialization` |
| **Media & Video** | AndroidX Media3 `1.6.1` (`ExoPlayer`, `PlayerView`, `Transformer`, `Effect`), Coil Compose |
| **Backend & Auth** | Firebase Authentication (Google Sign-In via Jetpack `CredentialManager`), Cloud Firestore, Firebase App Check |
| **AI Integration** | Firebase AI Logic (`com.google.firebase:firebase-ai`) |
| **Local Preferences** | AndroidX DataStore Preferences |
| **Build System** | Gradle `9.3.1` (Kotlin DSL), AGP `9.1.1`, Android SDK `36` (`minSdk 24`, `targetSdk 36`), JDK `21` |

---

## Project Structure

```text
.
├── .github/workflows/
│   └── android-release.yml      # GitHub Actions CI/CD workflow for Release APK & AAB builds
├── app/
│   ├── build.gradle.kts         # App dependencies, SDK config, release & debug signingConfigs
│   ├── google-services.json     # Firebase Android configuration
│   └── src/
│       ├── main/java/com/example/
│       │   ├── core/            # Auth state machine, design system, database models, navigation, storage
│       │   └── feature/         # Home, Photo Studio, Design Studio, Video Studio, AI Assistant, Assets, Projects, Profile, Settings
│       └── test/java/com/example/
│           ├── AuthStateMachineTest.kt
│           ├── ExampleRobolectricTest.kt
│           └── StudioLogicAndSerializationTest.kt
├── build.gradle.kts
├── gradle/libs.versions.toml    # Version catalog
├── firebase-blueprint.json      # Firestore collection schema blueprint
├── firestore.rules              # Zero-trust Firestore security rules
└── firestore.test.js            # Local Firestore emulator security rules tests
```

---

## Local Development & Building

> **Note**: This repository uses Gradle **`9.3.1`** directly (configured in `gradle/wrapper/gradle-wrapper.properties`) without a checked-in `gradlew` script. Ensure **JDK 21** and **Gradle 9.3.1** are installed on your `PATH`.

### 1. Environment Configuration
The Secrets Gradle Plugin reads `.env` (or falls back to `.env.example`). Firebase AI Logic authenticates through Firebase App Check and `app/google-services.json` rather than a hardcoded Gemini key.

### 2. Run Unit & Security Tests
```bash
# Run JVM & Robolectric unit tests
gradle :app:testDebugUnitTest

# Run Firestore security rules tests (requires Firebase Emulator running on 127.0.0.1:8085)
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### 3. Build Release APK & App Bundle (AAB) Locally
`app/build.gradle.kts` configures `signingConfigs.release` with `keyAlias = "upload"` and reads signing credentials from environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):

```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your_keystore_password
export KEY_PASSWORD=your_key_password

# Build signed Release APK
gradle :app:assembleRelease

# Build signed Release Android App Bundle (for Google Play)
gradle :app:bundleRelease
```

Outputs are generated at:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

---

## GitHub Actions Release CI/CD

The workflow in `.github/workflows/android-release.yml` is configured specifically for **Release builds** (`assembleRelease` and `bundleRelease`, never debug):

- **Triggers**: Pushes to `main` / `master`, version tags (`v*`), pull requests targeting `main` / `master`, and manual runs (`workflow_dispatch`).
- **Toolchain**: `ubuntu-24.04`, Temurin JDK `21` (`actions/setup-java@v5`), Android SDK `36` (`android-actions/setup-android@v4`), and Gradle (`gradle/actions/setup-gradle@v6` with `gradle-version: 'current'`).
- **Artifacts**: Uploads both `creative-studio-release-apk` (`.apk`) and `creative-studio-release-aab` (`.aab`), and automatically attaches them to a GitHub Release when pushing a `v*` tag.

### Setting Up Production Signing Secrets in GitHub

1. Create an upload keystore (with alias `upload`, matching `app/build.gradle.kts`):
   ```bash
   keytool -genkeypair -v \
     -keystore my-upload-key.jks \
     -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode the keystore:
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
3. In your GitHub repository, open **Settings → Secrets and variables → Actions** and add:
   - `ANDROID_KEYSTORE_BASE64`: Base64 string of `my-upload-key.jks`
   - `STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password for alias `upload`
   - *(Optional)* `GOOGLE_SERVICES_JSON_BASE64`: Base64 string of `app/google-services.json` if not committed to your repository

> If signing secrets are not yet configured, the workflow generates an ephemeral CI keystore (`alias: upload`) so Release compilation and artifact checks can still be verified, and posts a reminder in the build summary before publishing to Google Play.
