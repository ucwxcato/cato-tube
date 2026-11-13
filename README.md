# CatoTube

A small Kotlin command-line app that prompts for one YouTube video URL and saves its best available audio as an MP3 in `downloads/`. MP3 encoding uses FFmpeg's highest quality VBR setting (`-V 0`). The audio source quality still depends on what YouTube provides.

## Requirements

- JDK 17 or newer
- `yt-dlp` available on `PATH`
- `ffmpeg` available on `PATH` (required by yt-dlp for MP3 conversion)

On Windows, install both with WinGet from PowerShell:

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

The file will be `app/build/libs/app-all.jar`. Run it with Java:

```powershell
java -jar .\app\build\libs\app-all.jar
```

The JAR bundles the app and its JVM dependencies. `yt-dlp` and `ffmpeg` remain separate system tools and must be available on `PATH`.

On Windows, double-click `app-all.jar` to open the dark URL entry window. It includes a save-folder picker and download progress bar. If Windows does not associate JAR files with Java, run it with the `java -jar` command above. The app still needs `yt-dlp` and FFmpeg installed.

For development, you can also run it through Gradle:

```powershell
./gradlew.bat :app:run
```

Enter a single-video URL from `youtube.com` or `youtu.be` when prompted. The app rejects playlist expansion and stores the resulting MP3 in the project’s `downloads` directory. Keep the output filename when importing the track into RoboBeat.

Use this only for videos and audio you own or have permission to download and use. Check YouTube’s terms and the game’s custom music requirements before importing audio.
