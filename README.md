# CatoTube

A Kotlin/JVM desktop app that saves the best available audio from a single YouTube video as an MP3. It has a Swing window (opened by double-clicking the JAR) and a command-line mode (used when a console is attached). MP3 encoding uses FFmpeg's highest quality VBR setting (`-V 0`). The audio source quality still depends on what YouTube provides.

## Requirements

- JDK 25 (the Gradle build pins toolchain 25)
- `yt-dlp` available on `PATH`
- `ffmpeg` available on `PATH` (required by yt-dlp for MP3 conversion)
- `deno` available on `PATH` (the JavaScript challenge solver used by yt-dlp)

On Windows, install yt-dlp and FFmpeg with WinGet from PowerShell:

```powershell
winget install --source winget --id yt-dlp.yt-dlp
winget install --source winget --id Gyan.FFmpeg
```

Close and reopen CatoTube after installation so Windows supplies the updated `PATH`.

## Run

Build a standalone executable JAR from the project directory:

```powershell
./gradlew.bat :app:jar
```

The file will be `app/build/libs/app-all.jar`. The JAR bundles the app and its JVM dependencies. `yt-dlp`, `ffmpeg`, and `deno` remain separate tools and must be available on `PATH`.

Double-click `app-all.jar` to open the window, or run it with Java:

```powershell
java -jar .\app\build\libs\app-all.jar
```

`java -jar` attaches to the console and starts the command-line mode. To open the window from a terminal, use `javaw -jar .\app\build\libs\app-all.jar`.

For development, you can also run it through Gradle:

```powershell
./gradlew.bat :app:run
```

## Using the window

- **Home** lists songs in your library with search, sort, and a Recent/All scope. Right-click a song to open its file location, edit its details, or remove it from the library.
- **Downloads** takes a single-video URL from `youtube.com` or `youtu.be`. The app rejects playlist expansion. The MP3 is saved in the folder shown under "Save to", which defaults to the project's `downloads` directory.
- **Accent** (bottom of the sidebar) changes the highlight color and is remembered between runs.

The first time you open the app, you choose a library folder. The library keeps its index in `library.json` and copies imported songs into a `songs` folder. Original files are never moved or changed.

CatoTube forces IPv4 for yt-dlp requests. On the development machine, YouTube and npm returned quickly over IPv4 while IPv6 connections timed out; using IPv4 reduced extraction of a test clip from a long stall to about six seconds. This setting is local to CatoTube and does not change Windows networking.

Use this only for videos and audio you own or have permission to download and use. Check YouTube's terms before downloading or using any audio.
