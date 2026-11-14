# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

CatoTube is a Kotlin/JVM app that saves the best available audio from a single YouTube video as an MP3 in a local music library. It has two front ends in one JAR:

- **Swing window**: opens when `System.console()` is null (for example, double-clicking the JAR).
- **CLI**: runs when a console is attached.

Downloads run through external `yt-dlp` and FFmpeg processes. The app does not bundle either tool.

Use only for audio the user owns or is authorized to use. Do not add features that bypass YouTube access controls.

## Layout

- `app/src/main/kotlin/App.kt`: entry point, CLI, UIManager defaults, and window launch.
- `app/src/main/kotlin/ui/`: main window, sidebar navigation, library and downloads panels, startup and library-folder dialogs, and themed dialogs.
- `app/src/main/kotlin/library/`: library model, storage with atomic save and backup, file names, media probing, and scanning.
- `app/src/main/kotlin/download/`: yt-dlp command construction, YouTube URL validation, and the 2-minute no-output watchdog.
- `app/src/main/kotlin/Theme.kt`: custom-painted Swing components and the `Palette`/`Fonts` objects.
- `utils/src/main/kotlin/Utilities.kt`: shared helper module (`org.catosaur.utils`). Tests in `utils/src/test/kotlin/`.
- `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`: convention plugin. Sets the Java toolchain (currently 25) and JUnit Platform for tests.
- `DOCS/music-player-devplan.md`: scope, locked decisions, and phase gates. It holds the yt-dlp flag rationale; read it before changing download arguments or network behavior.
- `README.md`: user-facing install and run instructions.

Package: `org.catosaur.app` for `app`, `org.catosaur.utils` for `utils`.

## Build, run, test

Run from the repository root on Windows PowerShell:

```powershell
./gradlew.bat :app:jar      # build app/build/libs/app-all.jar
./gradlew.bat :app:run      # run from Gradle (CLI when a console is attached)
./gradlew.bat test          # run all tests (JUnit Platform)
./gradlew.bat :utils:test   # utils tests only
```

Launching the GUI from a terminal: `java -jar` attaches to the console and starts the CLI. To test the window, launch with `javaw -jar app\build\libs\app-all.jar`.

External tools must be on `PATH` for real downloads: `yt-dlp`, `ffmpeg`, and `deno` (the JS challenge solver).

## Code conventions

- Kotlin only. Keep the existing style: 4-space indent, trailing commas where the file already uses them, `private` top-level functions by default.
- Swing UI work happens on the EDT. Background work (process reading, watchdog) must hand UI updates back through `SwingUtilities.invokeLater`.
- Put new visual components and styling in `Theme.kt`. Keep `App.kt` for behavior and wiring.
- Use `Palette` and `Fonts` for colors and fonts. Do not hard-code new colors in `App.kt`.
- Custom components use `Eased` for animation and `smoothCopy()` for anti-aliased painting. Keep Swing `Timer` callbacks cheap and repaint only when state changes.
- Do not change the yt-dlp argument list (`startDownload`) without reading the yt-dlp decisions in `DOCS/music-player-devplan.md`. Flags like `--force-ipv4`, `--socket-timeout`, and the `player_client` override were tuned on purpose.
- Keep the download output path as `<chosen folder>/%(title)s.%(ext)s`.
- Don't add emojis to UI text or code.

## Verification

- Run `./gradlew.bat :app:jar` after any Kotlin change. It must compile without new warnings.
- For UI changes, start the JAR with `javaw` and check the window visually. Don't rely on code review alone.
- For download changes, test with a short authorized clip. Confirm progress lines and the final MP3 in `downloads/`.
- Run `./gradlew.bat test` when touching `utils` or shared logic.

## Repository rules

- `downloads/`, `*.mp3`, `*.jar` (except the Gradle wrapper JAR), and `build/` are git-ignored. Never commit downloaded audio.
- Do not commit or push unless the user asks.
- Don't edit `gradle/wrapper/` or the Gradle version without a reason.
- The JDK toolchain is pinned in `buildSrc` (25). Keep the JDK requirement in `README.md` in sync when you change it.
- Keep `README.md` and `DOCS/music-player-devplan.md` accurate when behavior, flags, or the window change.
