# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app project (`:app`).

- Root build/config: `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `gradlew(.bat)`.
- App code: `app/src/main/java/com/jjundev/oneclickeng` (feature-oriented packages like `activity`, `fragment`, `manager_gemini`, `summary`, `settings`).
- UI/resources: `app/src/main/res` (`layout`, `drawable`, `navigation`, `values`, `xml`) and prompt assets in `app/src/main/assets/prompts`.
- Tests: local JVM tests in `app/src/test`, instrumentation tests in `app/src/androidTest`.
- Design/engineering notes: `docs/`.

## Build, Test, and Development Commands
Use Gradle wrapper from repo root (Windows examples below):

- `.\gradlew.bat :app:assembleDebug` - build debug APK.
- `.\gradlew.bat :app:testDebugUnitTest` - run JVM unit tests.
- `.\gradlew.bat :app:connectedDebugAndroidTest` - run device/emulator instrumentation tests.
- `.\gradlew.bat :app:spotlessCheck` - verify formatting.
- `.\gradlew.bat :app:spotlessApply` - auto-format Java/XML.
- `.\gradlew.bat check` - full verification (includes `spotlessCheck`).

On macOS/Linux, replace with `./gradlew`.

## Coding Style & Naming Conventions
- Language/toolchain: Java 17 (`sourceCompatibility`/`targetCompatibility` 17).
- Formatting is enforced by Spotless:
  - Java: `googleJavaFormat`.
  - XML: 4-space indentation, trailing whitespace trimmed, newline at EOF.
- Naming:
  - Packages: lowercase (`com.jjundev.oneclickeng...`).
  - Classes: PascalCase (`DialogueLearningViewModel`).
  - Methods/fields: camelCase.
  - Android resources: snake_case (`fragment_dialogue_learning.xml`, `ic_nav_study.xml`).

## Testing Guidelines
- Frameworks: JUnit4 (`app/src/test`) and AndroidX test + Espresso (`app/src/androidTest`).
- Test files should end with `Test` (for example, `DialogueQuizViewModelTest.java`).
- Add or update tests whenever behavior changes in ViewModels, managers, parsers, or state handling.
- Run at least `:app:testDebugUnitTest` before opening a PR; include instrumentation results for UI/device-sensitive changes.

## Commit & Pull Request Guidelines
- Recent history favors short, scope-first subjects (often Korean/English mixed), e.g. `LoginActivity ...`, `History Fragment ...`.
- Recommended commit format: `<Scope>: <concise change summary>` (one logical change per commit).
- PRs should include:
  - What changed and why.
  - How it was tested (commands/results).
  - Screenshots or short recordings for UI updates.
  - Linked issue/task ID when available.

## Security & Configuration Tips
- `local.properties` is used for `GEMINI_API_KEY` and injected into `BuildConfig`; never expose real keys in commits, logs, or screenshots.
- `app/google-services.json` must match the intended Firebase project/environment.
