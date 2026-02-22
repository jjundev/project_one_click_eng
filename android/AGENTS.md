# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app (`:app`) using Java + XML.

- Root build/config files: `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`.
- App source: `app/src/main/java/com/jjundev/oneclickeng` (organized by feature areas such as `activity`, `fragment`, `manager_gemini`, `settings`, `summary`).
- UI/resources: `app/src/main/res` (`layout`, `drawable`, `navigation`, `values`, `xml`) and prompt assets in `app/src/main/assets/prompts`.
- Tests: `app/src/test` (JVM) and `app/src/androidTest` (instrumentation/Espresso).
- Supporting docs and schemas: `docs/`.

## Build, Test, and Development Commands
Run commands from repo root. On Windows use `.\gradlew.bat`; on macOS/Linux use `./gradlew`.

- `.\gradlew.bat :app:assembleDebug`: build debug APK.
- `.\gradlew.bat :app:testDebugUnitTest`: run JVM unit tests.
- `.\gradlew.bat :app:connectedDebugAndroidTest`: run instrumentation tests on emulator/device.
- `.\gradlew.bat :app:spotlessCheck`: verify formatting rules.
- `.\gradlew.bat :app:spotlessApply`: auto-format Java/XML files.
- `.\gradlew.bat check`: project verification (includes `spotlessCheck`).

## Coding Style & Naming Conventions
- Java toolchain is version 17.
- Formatting is enforced with Spotless:
- Java uses `googleJavaFormat`.
- XML uses 4-space indentation, trims trailing whitespace, and requires newline at EOF.
- Naming patterns:
- Packages: lowercase (`com.jjundev.oneclickeng...`).
- Classes: PascalCase (`RefinerGameViewModel`).
- Methods/fields: camelCase.
- Resources: snake_case (`fragment_dialogue_summary.xml`, `ic_nav_study.xml`).

## Testing Guidelines
- Frameworks: JUnit4 + AndroidX Arch Core for unit tests, AndroidX Test + Espresso for instrumentation.
- Test classes should end with `Test` (example: `MinefieldGameViewModelTest.java`).
- Add/update tests with behavior changes in ViewModels, managers, parsers, and state reducers.
- Minimum pre-PR check: `:app:testDebugUnitTest`; include instrumentation results for UI/device-specific changes.

## Commit & Pull Request Guidelines
- Recent commits use short, scope-first subjects, often in Korean (example: `LoginActivity 구글 로그인 구현`).
- Recommended commit format: `<Scope>: <concise summary>` and keep one logical change per commit.
- PRs should include:
- Why the change was made.
- What was tested (commands + outcomes).
- Screenshots/recordings for UI changes.
- Related issue/task link when available.

## Security & Configuration Tips
- Keep secrets in `local.properties` only (for example `GEMINI_API_KEY` used by `BuildConfig`).
- Never commit API keys, Firebase credentials, or sensitive logs/screenshots.
- Ensure `app/google-services.json` matches the target Firebase environment.
