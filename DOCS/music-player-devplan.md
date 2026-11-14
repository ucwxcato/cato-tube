# CatoTube 2.0 — Desktop Music Player — Development Plan

> **Status:** Phases 1 and 2 are complete. Phase 0 is complete except one finding carried into Phase 3. Phases 3 to 5 are partly done, with open items marked in section 9. Phase 6 is not started. Phase 7 (packaging and onboarding) is implemented, with two verification items open. Baseline: the Swing dark theme in `Theme.kt` and the restyled window in `App.kt` were launched with `javaw` and captured visually (window renders; logo glyph was then changed to `C` and rebuilt, not re-inspected).
>
> **Purpose:** Turn CatoTube from a YouTube-to-MP3 downloader into a local desktop music player with a persistent library, playlists, YouTube discovery, and playback, without accounts or online services.
>
> **Authority:** This document owns the CatoTube 2.0 scope, locked decisions, data model, and phase gates. Source request: `PROMPTS/musicplayerprompt01.md`. Repository rules that still apply: `AGENTS.md`, `README.md`.
>
> **Target:** Windows 10/11, Kotlin/JVM with Swing, JDK toolchain 25 (`buildSrc`), external `yt-dlp`, `ffmpeg`, `ffprobe`, and `deno` on `PATH`. Build output `app/build/libs/app-all.jar`. Distribution: a `jpackage` app-image in `app/build/installer/CatoTube/` (section 1, packaging decision).

Evidence labels used below: **Verified** (source plus active runtime check), **Installed** (artifact exists, not gameplay-checked), **Authored** (source exists, runtime not proven), **Planned** (future work or design-only), **TBD** (needs an owner or spike decision).

## 0. Outcome

CatoTube opens as a resizable 1100 × 750 window with Home, Discover, Playlists, and Downloads tabs and a bottom player that stays visible across tabs. A user can pick a music library folder, search YouTube, add a result to a playlist, download it, see it in the library, play it from the bottom bar, and find the same library after restarting.

Complete when:

```text
first launch -> choose library folder -> Discover search -> open result -> Add to playlist "Focus"
  -> download completes (MP3 valid, registered once) -> track appears in Library and in "Focus"
  -> double-click track -> bottom player plays it with seek, volume, and auto-advance
  -> close app -> reopen -> library, playlists, and folder setting are unchanged
```

## 1. Locked decisions

These decisions are binding for implementation. Changing one requires editing this document first.

- **Swing only.** No JavaFX and no replacement of Swing. Source: prompt section 2.
- **yt-dlp download arguments are preserved.** `--force-ipv4`, `--audio-format mp3`, `--audio-quality 0`, `--format bestaudio/best`, `--socket-timeout 15`, `--retries 1`, `--extractor-retries 1`, `--extractor-args youtube:player_client=tv_samsung`, `--newline`, `--verbose`, `--no-playlist` stay byte-identical. Only the output template changes, and only as described in the output-path decision below. Reason: these flags were tuned on purpose during the slow-extraction investigation (see the IPv4 note in `README.md`).
- **Output path (Locked in Phase 0 by owner choice: staging, then `songs/`).** `AGENTS.md` currently requires the download output to be `<chosen folder>/%(title)s.%(ext)s` because users import these filenames into RoboBeat. The prompt's library layout uses `songs/` and stable IDs. Decision: downloads write into `<library>/.staging/<trackId>/` first, then the finished MP3 is moved to `<library>/songs/<sanitized title>.mp3` (collision suffix ` (2)`, never overwrite). The final filename keeps the `%(title)s` convention. The `AGENTS.md` and `README.md` rule updates land with the Phase 3 code change, not before, so those docs stay accurate about the code that exists today.
- **Playback engine (Confirmed by Phase 0 spike).** Decode with the FFmpeg process the app already requires (`ffmpeg -ss <sec> -i <file> -f s16le -ar 44100 -ac 2 pipe:1`), write PCM to `javax.sound.sampled.SourceDataLine`. Reason: handles MP3, WAV, FLAC, and Opus through existing tooling, gives seek by restarting from `-ss`, and provides real PCM for the visualizer. Spike evidence is in section 9. No custom MP3 decoder. JLayer (MP3 only) remains the fallback if later playback testing shows a problem.
- **Volume is software gain on PCM.** Reason: one behavior across formats and devices; `FloatControl` is not guaranteed on every line.
- **Library layout.** `<root>/songs/`, `<root>/artwork/`, `<root>/library.json`, `<root>/library.json.bak`, `<root>/.staging/`. Paths inside `library.json` are relative to `<root>` with `/` separators.
- **Imported local files are copied into `songs/`.** Reason: the library stays self-contained and relocatable. Originals are never moved or deleted.
- **Playlists store track IDs, never file paths.** One audio file can belong to many playlists. A track appears at most once per playlist.
- **Playlist metadata lives in `library.json`.** One file, one atomic-save path. Reason: membership and track data must stay consistent after a crash.
- **Settings use `java.util.prefs.Preferences`.** Keys: `libraryRoot`, `visualizerEnabled`, `schemaMigrationDone`. Node: `org/catosaur/app`.
- **Serialization uses kotlinx.serialization JSON.** Already in `gradle/libs.versions.toml` (`kotlinxSerialization`, 1.11.0) and used by `utils`. The app module must declare the dependency itself because `utils` uses `implementation`, which does not expose it to `app`.
- **Downloads are sequential.** One worker. No concurrent downloads.
- **Search uses yt-dlp, no Google API key.** Search subprocesses also receive `--force-ipv4`.
- **Application-level shortcuts only in v2.0.** Global Windows media keys are TBD (see section 10). The docs must not claim global media-key support unless implemented and verified.
- **Queue is not persisted in v2.0.** Reason: prompt marks it optional; playback reliability comes first.
- **Deleting a playlist never deletes audio.** Removing a track from a playlist removes only the association. Deleting a track from the library requires an explicit confirmation, and deleting the audio file is a separate, default-off checkbox in that confirmation.
- **Distribution is a `jpackage` app-image, and external tools are never bundled.** `./gradlew.bat :app:packageWindows` builds `app/build/installer/CatoTube/` with the JDK 25 toolchain's `jpackage` (`--type app-image`, no WiX, no new Gradle plugin, icon at `assets/app-icon.ico`). The image carries the JVM and the app only. `yt-dlp`, `ffmpeg`, `ffprobe`, and `deno` stay external and are installed by the user. Reason: yt-dlp needs frequent updates to keep up with YouTube, so a bundled copy would go stale, and this keeps the `AGENTS.md` no-bundle rule. MSI and EXE installers are deferred until signing and WiX decisions exist (section 10).
- **The startup dependency check runs on every launch, and its dialog appears only on problems.** `tools/ToolProbe.kt` runs `yt-dlp --version`, `ffmpeg -version`, `ffprobe -version`, and `deno --version` on a background thread with 10 s timeouts. When every tool is found, nothing is shown. When one is missing or fails to run, the setup dialog names each tool, shows its WinGet install command, and offers Re-check and Continue. The check never blocks startup and never prevents the app from opening. The CLI prints the same status before the URL prompt.
- **Existing `downloads/` folder is not moved.** A one-time offer imports its MP3s into the chosen library. Reason: prompt section 17 forbids silent data moves.

## 2. Goals and non-goals

### Goals

- Persistent local library with stable track IDs, search, sort, and Recently Added.
- Playlists that share audio files, with reorder and persistence.
- YouTube search with asynchronous results, and download-on-confirm only.
- Local playback of MP3, WAV, FLAC, and Opus with queue, shuffle, repeat, seek, and volume.
- Persistent bottom player and a spectrum visualizer driven by real PCM data.
- Settings for library folder, visualizer, and tool status. The CLI keeps working.

### Explicitly out of scope

- Video streaming, DRM or access-control bypass, or any authentication workaround.
- Google API keys, YouTube Data API, user accounts, cloud sync, telemetry, or online services.
- Concurrent downloads, a custom MP3 decoder, JavaFX, or a replacement for Swing.
- Automatic download of search results.
- Persisted playback queue (optional later work).
- Global media-key capture in v2.0 (unless the Phase 5 spike proves a native path; see section 10).
- Editing ID3 tags or album metadata beyond what yt-dlp and ffprobe report.

## 3. User experience and operational flow

1. **First launch:** no `libraryRoot` preference, so a dialog asks for a folder. Choosing a folder that is empty creates `songs/`, `artwork/`, and `library.json`. Choosing a folder that already has `library.json` opens it. Cancel exits to the main window with the library disabled and a clear banner.
2. **Library folder check at every start:** if the saved root is missing or not writable, show a modal with three choices: Relocate (pick a folder), Open another library, or Retry. The app never creates an empty library over a missing one.
3. **Home / Library:** search box (filters an in-memory index), sort menu (title, artist, duration, date added), Recently Added strip, and the track list. Double-click plays. Right-click offers Play, Add to Playlist, Open File Location, Remove from Library.
4. **Discover:** query field with Enter and Search button. Results are cards with thumbnail, title, channel, duration, truncated description with a "more" toggle. Clicking a card opens an info dialog with Add to existing playlist, New playlist, Add to library only, and Cancel. Confirm queues the download on the Downloads tab.
5. **Downloads:** the existing URL field and Download button, plus a queue list with per-item state, percentage, current operation text, Cancel current, Clear completed, Open file location, and an expandable verbose log.
6. **Playlists:** sidebar of playlists (count and total duration), selected playlist's track list, Play, Shuffle, Move up/down, Remove from playlist, Rename, Delete (association only).
7. **Player:** the bottom bar shows cover, title, artist, transport, seek, time, volume, mute, shuffle, repeat, visualizer, and Queue. The Queue panel shows Now Playing, upcoming tracks, Play Next, Add to Queue, Remove, Move, and Clear.
8. **Settings:** library folder (change, rescan), visualizer on/off, tool availability (yt-dlp, ffmpeg, ffprobe, deno versions or "not found"), and fixed MP3 quality notice.

## 4. Architecture and ownership

Current tree (verified): `app/src/main/kotlin/App.kt` (CLI, Swing wiring, download process, URL validation) and `app/src/main/kotlin/Theme.kt` (Palette, Fonts, Eased, Backdrop, Card, GlowField, PillButton, GlowProgressBar, LogoMark, SlimScrollBarUI). `utils/` holds shared helpers with tests in `utils/src/test/kotlin/`. The `app` module has no test source set and no test dependency yet (Phase 0).

Proposed tree (Planned):

```text
app/src/main/kotlin/
├── App.kt                      entry point, CLI path, window bootstrap only
├── Theme.kt                    Palette, Fonts, shared components (unchanged role)
├── library/
│   ├── Track.kt                data model (Track, Playlist, LibrarySnapshot)
│   ├── MusicLibrary.kt         in-memory index, queries, mutations, change events
│   ├── LibraryStorage.kt       library.json load/save, backup, atomic replace, recovery
│   ├── LibraryFolder.kt        root validation, first-run, relocate, preferences
│   └── LibraryScanner.kt       rescan songs/, import, ffprobe duration
├── download/
│   ├── YtDlpCommands.kt        startDownload() argument list (moved, unchanged args)
│   ├── DownloadManager.kt      sequential worker, states, staging, registration
│   ├── DownloadJob.kt          job model and state transitions
│   └── YouTubeSearch.kt        ytsearchN runner, JSON parsing, cancellation
├── playback/
│   ├── AudioPlayer.kt          FFmpeg PCM -> SourceDataLine, single playback thread
│   ├── PlaybackQueue.kt        queue, shuffle, repeat; independent of playlists
│   ├── PcmAnalyzer.kt          FFT band computation (off EDT)
│   └── AudioVisualizer.kt      throttled band delivery to EDT
├── tools/
│   └── ToolProbe.kt            version probes for yt-dlp, ffmpeg, ffprobe, and deno
└── ui/
    ├── MainWindow.kt           frame, tab bar, bottom player host
    ├── LibraryPanel.kt
    ├── DiscoverPanel.kt
    ├── PlaylistsPanel.kt
    ├── DownloadsPanel.kt
    ├── PlayerBar.kt
    ├── QueuePanel.kt
    ├── SettingsDialog.kt
    └── ToolSetup.kt            startup missing-tools dialog
```

Ownership rules:

- `App.kt` keeps `main`, the CLI, and window bootstrap. It must not grow drawing or library logic.
- `library/` owns data and persistence. It has no Swing imports.
- `download/` owns processes. Process lifetime is tied to job cancellation and app shutdown.
- `tools/` owns the external tool probes (yt-dlp, ffmpeg, ffprobe, deno). It has no Swing imports and never starts a download.
- `playback/` owns audio threads and FFmpeg processes. It has no Swing imports; it publishes state through callbacks that the UI marshals to the EDT.
- `ui/` owns components and state display. It calls `library/`, `download/`, and `playback/` through their public APIs and never touches `library.json` directly.
- `utils/` stays free of Swing. Pure helpers such as filename sanitizing and the FFT may live there if tested.

Proposed contracts (Kotlin, design-only names):

```kotlin
data class Track(
    val id: String,                 // UUID, stable across renames and moves
    val title: String,
    val artist: String,             // channel or uploader; "" when unknown
    val album: String?,
    val durationMs: Long?,          // null until ffprobe succeeds
    val relativePath: String,       // "songs/<file>.mp3"
    val dateAddedEpochMs: Long,
    val sourceUrl: String?,
    val youtubeId: String?,         // dedupe key for YouTube-origin tracks
    val artworkRelativePath: String?,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,     // ordered; no duplicates within one playlist
    val createdEpochMs: Long,
)
```

## 5. Data, lifecycle, and failure handling

### library.json schema (version 1, Proposed)

```json
{
  "schemaVersion": 1,
  "savedAtEpochMs": 0,
  "tracks": [
    {
      "id": "3f1c...",
      "title": "Example",
      "artist": "Channel",
      "album": null,
      "durationMs": 215000,
      "relativePath": "songs/Example.mp3",
      "dateAddedEpochMs": 0,
      "sourceUrl": "https://www.youtube.com/watch?v=...",
      "youtubeId": "...",
      "artworkRelativePath": null
    }
  ],
  "playlists": [
    { "id": "...", "name": "Focus", "trackIds": ["3f1c..."], "createdEpochMs": 0 }
  ]
}
```

### Atomic save

1. Serialize the snapshot to `library.json.tmp` in the library root and flush it to disk.
2. If `library.json` exists, copy it to `library.json.bak` (replace the previous backup only after step 1 succeeds).
3. Move `library.json.tmp` over `library.json` with `ATOMIC_MOVE` and `REPLACE_EXISTING`. If the filesystem rejects atomic move, fall back to `REPLACE_EXISTING` and log the fallback.
4. Saves run on one background writer. Rapid mutations coalesce into one save. Shutdown flushes any pending save before exit.

### Load and recovery

- Parse `library.json`. On failure, parse `library.json.bak`, show a recovery message naming both files, and keep the corrupt file renamed as `library.json.corrupt-<timestamp>`. Never start with an empty library silently.
- If neither file parses, refuse to start the library and offer Relocate or Open another. Never overwrite either file.
- Unknown `schemaVersion` (newer than supported) is read-only: the app shows the library but does not save.

### Download lifecycle

States: `Queued -> Extracting -> Downloading -> Converting -> Completed`, with `Failed` and `Cancelled` as terminal side exits.

- Each job writes into `.staging/<trackId>/` only.
- Success: locate the produced MP3, move it to `songs/` under the sanitized final name (collision suffix, no overwrite), run ffprobe for duration, then register the track and its playlist memberships in one snapshot update, then save.
- Failure or cancel: delete the job's staging directory and create no library entry.
- Startup cleanup: remove stale `.staging/` directories left by a crash. These are app-owned partial files, not user audio.
- Duplicate guard: a set of active `youtubeId` and destination keys prevents two jobs from writing the same target.
- Dedupe: if `youtubeId` already exists in the library, reuse that track, add memberships, and skip the download.

### Playback lifecycle

- One playback thread owns the FFmpeg process and the `SourceDataLine`.
- Switching tracks, stopping, reaching end of track, and app exit each destroy the FFmpeg process, drain and close the line, and join the thread with a timeout.
- Missing, corrupt, or unsupported files raise an error event with the file name, then the queue advances or stops per repeat mode.

### Concurrency

- All Swing state changes go through `SwingUtilities.invokeLater`.
- Library mutations run on one single-thread executor. Readers use an immutable snapshot published to the EDT.
- Scans, thumbnail fetches, FFT analysis, and ffprobe calls run on background executors. Scans report progress and cancel on window close.

## 6. Configuration, permissions, and integrations

Preferences (`java.util.prefs`, node `org/catosaur/app`):

```text
libraryRoot              absolute path string; absent until first-run choice
visualizerEnabled        boolean, default true
schemaMigrationDone      boolean, set after the one-time downloads/ import offer
```

External tools (must remain optional-with-message, never a hard crash):

| Tool | Used for | Probe |
|---|---|---|
| yt-dlp | downloads, search | `yt-dlp --version` with 10 s timeout |
| ffmpeg | download conversion, playback decode, artwork extraction | `ffmpeg -version` |
| ffprobe | duration, metadata | `ffprobe -version` |
| deno | yt-dlp JS challenge solver | `deno --version` |

The startup check uses these probes and runs on every launch (section 1). A missing tool shows the setup dialog with its WinGet install command and never blocks startup.

Build changes (Phase 0):

```kotlin
// app/build.gradle.kts (Proposed)
dependencies {
    implementation(project(":utils"))
    implementation(libs.kotlinxSerialization)
    testImplementation(kotlin("test"))
}
```

The `kotlinPluginSerialization` plugin must also be applied to `app` when `@Serializable` classes live there.

yt-dlp search contract (Proposed, verify in spike):

```text
yt-dlp --force-ipv4 --flat-playlist --dump-json --no-warnings "ytsearch10:<query>"
```

One JSON object per output line. Parse each line defensively; skip malformed lines and log them. Fields to confirm in the spike: `id`, `title`, `uploader` or `channel`, `duration`, `description`, `thumbnails`.

Thumbnail fetches: HTTPS only, host allowlist (`i.ytimg.com` and other `ytimg.com` subdomains), 5 s connect timeout, 10 s read timeout, in-memory LRU cache, no writes to disk until Phase 5 artwork work.

## 7. Safety, security, and product constraints

- Download only on explicit confirmation. Search results never start a download.
- Keep the existing URL validation: only `youtube.com`, `m.youtube.com`, and `youtu.be`, HTTP(S), single video.
- Never overwrite an existing audio file. Filename collisions get a suffix.
- Never delete user audio without a confirmation dialog. Deleting a playlist, removing a track from a playlist, and removing a track from the library never delete audio by default.
- Never silently replace or abandon a library. Changing the library folder offers Move existing library, Open another existing library, or Cancel.
- Never register a track before its file is in its final location and verified by ffprobe.
- Fail closed on library parse errors (no silent empty library).
- No network calls except yt-dlp, yt-dlp search, and the allowlisted thumbnail hosts. No telemetry.
- Title and filename sanitizing removes path separators and Windows-reserved names (`CON`, `NUL`, and similar).
- Authorized-use text stays in `README.md`, and the UI keeps its existing "Only download audio you own or are authorized to use" line.

## 8. Verification matrix

| Scenario | Expected result | Evidence required |
|---|---|---|
| Build | `:app:jar` succeeds without new warnings | Gradle output, `app-all.jar` timestamp |
| Unit tests | `./gradlew.bat test` passes for `utils` and `app` | Test report output |
| Library round trip | Save then load yields an equal snapshot | Automated test |
| Corrupt `library.json` | Loads `.bak`, renames corrupt file, shows recovery message | Automated test plus manual check |
| Crash during save | Either old or new `library.json` survives intact | Fault-injection test (temp file present, move not done) |
| Missing library folder at start | Modal with Relocate, Open another, Retry; no empty library created | Manual check with a renamed folder |
| Unwritable library folder | Read-only mode with message; no writes attempted | Manual check with a read-only ACL |
| Playlist multi-membership | One file, two playlists, one `songs/` entry | Automated test plus file listing |
| Delete playlist | Playlists removed, audio files still present | Automated test plus file listing |
| Duplicate YouTube video | Second add reuses track, no second download | Automated test plus log showing no yt-dlp job |
| Download success | MP3 valid (ffprobe), registered once, in chosen playlists | Real authorized clip, ffprobe output |
| Download cancel during extraction | No staging dir left, no library entry | Manual check, file listing |
| Download cancel during conversion | No partial MP3 in `songs/`, no entry | Manual check, file listing |
| Missing yt-dlp / ffmpeg / deno | Clear message naming the tool; app stays usable | Manual check with `PATH` override |
| App image build | `:app:packageWindows` produces `build/installer/CatoTube/CatoTube.exe`; launching it opens the window with no console | Gradle output plus a launch check |
| Missing tool at start | Setup dialog names each missing tool with its install command; the app stays usable | Manual check with a stripped `PATH` |
| Tool probe timeout | A hanging probe reports "no response" and startup is not blocked | Automated test with a hanging command |
| Playback of MP3, WAV, FLAC, Opus | Audible output, correct duration, seek works | Manual listening plus timed position log |
| Corrupt audio file | Error message naming file, queue advances | Manual check with a truncated file |
| Stop or switch track | FFmpeg process exits, line closed | `Get-Process ffmpeg` before and after |
| App exit during playback | No orphan `ffmpeg` or `java` process | `Get-Process` after closing |
| Queue behavior | Play Next, Add, Remove, Move, Clear, auto-advance, repeat modes | Automated tests of `PlaybackQueue` |
| Shuffle | Deterministic with a fixed seed in tests; no repeats within one pass | Automated test |
| Visualizer | Bars move with audio, stop when stopped, off when disabled | Manual visual check |
| CLI | Existing prompt flow downloads a clip and reports path | Manual run of `:app:run` |
| Restart persistence | Library, playlists, folder setting identical after reopen | Manual close and reopen |
| Window layout | 1100 × 750 default, 900 × 600 minimum, no overlap at minimum | Screenshot at both sizes |
| Abusive input | Invalid URL rejected; path-unsafe titles sanitized | Automated test plus manual |

## 9. Phased checklist

Phase numbers follow the prompt's section 18 where it names them. Phase 6 covers documentation and release, which the prompt requires in sections 20 and 21.

### Phase 0 — Design lock and spikes

- [x] Baseline: restyled Swing window with `Theme.kt` components renders on `javaw` launch. Evidence: captured screenshots of the window; Theme.kt and App.kt changes are uncommitted in the working tree.
- [x] Owner decision: output path locked as staging, then `songs/<sanitized title>.mp3`. Evidence: owner selected "Staging, then songs/ (Recommended)" in the Phase 0 question. Section 1 records the lock.
- [x] Spike: FFmpeg PCM pipe playback on MP3, WAV, FLAC, and Opus. Evidence: `ffmpeg -ss <t> -i <file> -f s16le -ar 44100 -ac 2 pipe:1` into `SourceDataLine` (throwaway harness outside the repo, `%TEMP%\catotube-spike\Spike.java`). First PCM byte about 93–98 ms for all four formats. Played durations matched requests: 4.000 s from 0 s, and 3.000 s from 10 s on a WAV. A 4 s slice from 120 s of the real 151 s MP3 played 4.000 s. Limit: audibility was not checked by ear, and seek position was checked by duration, not by sample content. Decision confirmed for the planned engine.
- [x] Spike: yt-dlp search with `ytsearch3:<query>` plus `--flat-playlist --dump-json --force-ipv4`. Evidence: exit 0, 3 result lines in 2.75 s. Fields present: `id`, `title`, `description`, `channel`, `uploader`, `channel_id`, `thumbnails`, `webpage_url`, `duration`. Limit: `duration` was null for a live-stream result, so the UI must handle a missing duration.
- [x] Add `kotlinxSerialization` and `kotlin("test")` to `app/build.gradle.kts`, and apply the serialization plugin to `app`. Evidence: `:app:jar` and `test` succeed.
- [x] Move the existing `startDownload` argument list into `download/YtDlpCommands.kt` without changing any argument. Evidence: `YtDlpCommandsTest.downloadArgumentsMatchTunedConfiguration` pins the complete list, including `--force-ipv4`, and passes. The list was copied from the pre-change source. The pre-change command line was not captured by a live run.
- [x] Verify: `./gradlew.bat :app:jar` and `./gradlew.bat test` both succeed with no compiler warnings. Evidence: BUILD SUCCESSFUL; `YtDlpCommandsTest` 2/2 PASSED.
- [x] Verify: GUI from the rebuilt JAR still starts. Evidence: `javaw -jar app\build\libs\app-all.jar` stayed alive 5 s with window title "CatoTube for RoboBeat".
- [x] Verify: an authorized clip downloads with the unchanged yt-dlp argument list, and the MP3 plays. Evidence: `https://youtu.be/Pnp-vIrNaYc` ("Play It Loud - S3RL & Remzcore DEFI BRILATOR") downloaded with exit 0. Output is `downloads/<title>.mp3`, format `mp3`, duration 153.797 s, 5,341,100 bytes. The spike played a 4 s slice from 30 s with first PCM at 95 ms. Limit: this ran yt-dlp directly with the same argument list. The Java CLI itself was not exercised (see the next item).
- [x] Verify: run the Java CLI end to end (`java -jar app-all.jar` prompts for a URL, then reports the MP3 path). Evidence: owner ran it in an interactive terminal and confirmed it works after the `downloads/` and argument-list refactor.
- [ ] Finding (Phase 3 risk): the title above contains a character outside cp1252. The file on disk is correct, but Java passes process arguments in the Windows locale encoding (`sun.jnu.encoding`, cp1252 here), so a path with that character cannot be handed back to a child process such as ffmpeg. Phase 3 must verify non-cp1252 filenames end to end, and sanitize final names if that proves unreliable.

### Phase 1 — Foundation

- [x] `MainWindow.kt`: default size 1100 × 750, minimum 900 × 600, sidebar navigation with four pages (Home, Discover, Playlists, Downloads). Evidence: screenshots at 1100 × 750 and 900 × 600 (no overlap; title column clips without an ellipsis at 900 wide).
- [x] `PlayerBar` host in the bottom region, empty state "Nothing playing", no fake controls. Evidence: screenshots above.
- [x] `LibraryFolder.kt`: saved root, validation (exists, is directory, writable via temp-file probe), Preferences persistence. Evidence: unit-level logic plus missing-folder modal screenshot. The first-run chooser was not driven by automation (see Verify notes).
- [x] Missing or unwritable folder modal with Relocate, Open another, Retry, and Continue without library. Evidence: screenshot of the missing-folder modal. Unwritable-folder path is covered by `MusicLibraryTest.unwritableFolderWithLibraryOpensReadOnly`, not by GUI.
- [x] Library folder creation writes `songs/`, `artwork/`, and `library.json` only after the user confirms. Evidence: folder listing after confirmation.
- [x] `Track.kt` model and `LibraryStorage.kt` with atomic save, backup, and recovery. Evidence: `LibraryStorageTest` 6/6 pass (round trip, corrupt main to backup, interrupted save, newer schema read-only, both files corrupt).
- [x] `MusicLibrary.kt` in-memory index with title, artist, and filename search, and sorting by title, artist, duration, and date added. Evidence: `MusicLibraryTest` search and sort tests pass.
- [x] Scanning `songs/` for untracked audio (rescan) with completion feedback. Deviation: implemented in `MusicLibrary.scanForNewFiles` rather than a separate `LibraryScanner.kt`. Cancel-on-close is not implemented; the scan is short and the worker is joined on close.
- [x] Local import: copies into `songs/` with collision suffix, originals untouched. Evidence: `MusicLibraryTest.importCopiesIntoSongsAndSkipsUnsupportedFiles`, `FileNamesTest.copyAsNewSongNeverOverwritesAndNumbersCollisions`. Ctrl+O is deferred to Phase 5 as planned.
- [x] One-time offer to import `downloads/*.mp3`, guarded by `schemaMigrationDone`, files copied not moved. Evidence: the offer appeared and two tracks were imported from `downloads/`; originals remain.
- [x] Library tab: search, sort, track list, empty state, right-click menu (Open File Location, Edit Details, Remove from Library with default-off audio deletion), F2 and Delete shortcuts. Evidence: list screenshots. Right-click menu and dialogs were not exercised by automation; Remove and Edit logic are covered by `MusicLibraryTest`.
- [x] Recently Added (10 most recent) with a Recent/All segmented control in the list header. Deviation: shown as the table's default scope, not a separate horizontal strip. Evidence: screenshots.
- [x] Verify: `LibraryStorage` tests (round trip, corrupt main falls back to `.bak`, unknown schema read-only, save interruption leaves a valid file). Evidence: 6/6 pass.
- [x] Verify: close and reopen keeps the library (songs listed after relaunch); missing folder shows the modal; layouts at 1100 × 750 and 900 × 600 have no overlap. Unwritable read-only mode is covered by unit tests only.

Added beyond the original Phase 1 list: title and artist editing (`MusicLibrary.updateTrack`, Edit Details dialog), frosted-glass card styling, a gliding hover and selection highlight in the track list, hairline row separators, a solid accent pill for the active sidebar item, themed dialogs and right-click menu replacing the stock Swing popups, and an accent picker (ten palette colors, saved in preferences under key `accent`).

### Phase 2 — Playlists

- [x] `Playlist` model stored inside `library.json` with ordered `trackIds`. Evidence: `Track.kt` `Playlist` and `LibraryData.playlists`; the reorder test reopens the library and reads the order back.
- [x] Create playlist, rename playlist, and delete playlist (association only). Evidence: `MusicLibraryTest.playlistNamesAreTrimmedRequiredAndUniqueIgnoringCase`, `renameAllowsKeepingTheSameNameButNotTakingAnotherPlaylistsName`, `trackCanBeInSeveralPlaylistsAndDeletingOneKeepsAudioAndTheOther`. Delete asks for confirmation through a themed question dialog; the dialog was not exercised by automation.
- [x] Add library tracks to a playlist from the Home context menu ("Add to Playlist...", pick an existing playlist or "New playlist..."). Duplicates within one playlist are skipped and the status line says the song is already there (`MusicLibraryTest.addToPlaylistSkipsDuplicatesAndUnknownTracks`). Deviation: multi-select add is not built. Home's track table is single-select, so this item is partly done.
- [x] Remove a track from a playlist (association only) with no file change. Evidence: `MusicLibraryTest.reorderAndRemoveArePersistedAndKeepTheTrackAudio`.
- [x] Reorder tracks with Move up and Move down buttons (and right-click menu). Evidence: `MusicLibraryTest.reorderAndRemoveArePersistedAndKeepTheTrackAudio`; the first and last rows cannot move past the ends.
- [x] Playlists tab: list with name and song count, selected playlist's track list. Deviation: total duration is shown in the detail header, not in the sidebar list.
- [x] Add-from-Discover (Phase 3) and Play and Shuffle playlist (Phase 4) are wired. Discover adds a result to a playlist when it downloads, and Play and Shuffle use the playback queue.
- [x] Verify: automated tests for multi-membership (one file, two playlists, one `songs/` entry), delete playlist keeps audio, remove-from-playlist keeps audio, reorder persistence, duplicate rejection. Evidence: `MusicLibraryTest` 6 new playlist tests pass; `./gradlew.bat test` BUILD SUCCESSFUL.
- [x] Verify: restart keeps playlists, memberships, and order (manual close and reopen). Confirmed manually by the user. The reorder test also reopens the library file.
- [x] Verify: Playlists page and Add to Playlist dialogs visually from the JAR (`javaw`). Confirmed manually by the user.

Added beyond the original Phase 2 list: playlist names must be non-blank and unique ignoring case (`playlistNameProblem`), checked in the dialog and again in the library worker; the Add to Playlist dialog can create a playlist on the spot.

### Phase 3 — YouTube discovery and downloads

- [x] `YouTubeSearch.kt`: `ytsearchN:` runner with `--force-ipv4`, timeout, process cancellation, and defensive per-line JSON parsing. Evidence: `YouTubeSearchTest` (7 tests) covers the command flags, field parsing, fallbacks, malformed lines, the thumbnail allowlist, and the blank-query guard. Not yet verified: a live search against YouTube, and the timeout and cancel paths against a real yt-dlp process.
- [ ] Discover tab: query field with Enter support, Search button, loading indicator, error messages (no network, yt-dlp missing, no results), scrollable results, and cancellation of the previous search when a new one starts. Partly done: `ui/DiscoverPanel.kt` has the query field with Enter, Search button, error and no-result messages, a scrollable list, and cancellation of the previous search (stale results are ignored). Open: the loading state is text only (no spinner), results are plain rows (cards come next), and the UI has not been checked in the running app.
- [ ] Result card: async thumbnail (host allowlist, timeouts, in-memory LRU), title, channel, duration, truncated description with "more" toggle.
- [x] Result right-click menu with "Download" and "Download and add to playlist...". Deviation from section 3 item 4: the right-click menu replaces the info dialog, and the menu item is the explicit confirmation. Choosing "Download and add to playlist..." opens a picker for an existing playlist or a new one. A plus button at the right of each result opens the same menu. Evidence: `ui/DiscoverPanel.kt`, and a live right-click download confirmed by the user.
- [x] `DownloadManager.kt`: sequential single worker (`download/DownloadManager.kt`), using `ytDlpDownloadCommand` (arguments unchanged) into `<library>/.staging/<job>/`. States are reported as status lines, not a queue list. Cancel works from the download ring (click, then confirm). The ring shows the percentage while a download runs. The 2-minute no-output watchdog is enforced. Staging is removed in every outcome.
- [x] On success: the MP3 is moved to `songs/` with a collision-safe name (`moveAsNewSong`), ffprobe must report a duration before anything is registered, the track is saved with `youtubeId` and `sourceUrl`, and the chosen playlists are updated in the same save. Evidence: `DownloadRegistrationTest` (4 tests, real ffmpeg-made MP3). Confirmed by a live YouTube download in the running app.
- [x] Dedupe: an existing `youtubeId` is not downloaded again; the existing track joins the requested playlists. Evidence: `DownloadManager.submit`. Not yet exercised live.
- [ ] On failure or cancel: delete the job's staging directory and create no library entry.
- [ ] Active-job guard prevents two jobs writing the same target.
- [ ] Downloads tab: URL input and Download button (existing flow preserved), queue list with per-item state and percentage, current operation text, Cancel current, Clear completed, Open file location, expandable verbose log.
- [ ] Missing yt-dlp, ffmpeg, or deno produces a specific message naming the tool; the app stays usable.
- [ ] Verify non-cp1252 filenames end to end (download, register, play, Open File Location). If Java argument encoding breaks them, sanitize final names to a safe character set (Phase 0 finding).
- [ ] Verify: real download of a short authorized clip produces a valid MP3 (ffprobe output), registered once, in the chosen playlist.
- [ ] Verify: cancel during extraction and cancel during conversion each leave no staging directory, no partial MP3 in `songs/`, and no library entry.
- [ ] Verify: search returns results, thumbnails load without blocking the EDT, and the dedupe path downloads nothing.
- [ ] Verify: `--force-ipv4` is still present in every yt-dlp invocation (diff of command lines).

### Phase 4 — Playback

- [x] `AudioPlayer.kt`: FFmpeg PCM decode into `SourceDataLine` on a dedicated thread; play, pause, stop, seek (restart with `-ss`), position reporting, end-of-track event. Evidence: code in `playback/AudioPlayer.kt`; the owner confirmed playback works in the app. Per-format and seek checks are still open (see the verification item below).
- [x] Software volume and mute on the PCM stream. Evidence: `AudioPlayer.setVolume`/`setMuted`, wired to the bar. Audible check still open.
- [ ] Missing, corrupt, and unsupported files raise a named error and the queue advances or stops per repeat mode. Partly done: `PlaybackController.onError` shows the track name and error, then advances. Not tested with a corrupt file.
- [ ] Resource release on track switch, stop, end of track, and app exit (process destroyed, line closed, thread joined with timeout). `AudioPlayer.close()` runs on window close. The process check is still open (see the verification item below).
- [x] `PlaybackQueue.kt`: Now Playing, upcoming list, Play Next, Add to Queue, Remove, Move, Clear, auto-advance; shuffle; repeat off, all, and one. Queue state is independent of playlist order. Evidence: `PlaybackQueueTest` (9 tests). The queue editing operations have no UI yet (see the queue panel item).
- [x] Queue state survives tab navigation. The controller is owned by `MainWindow`, not by a panel.
- [ ] `PlayerBar.kt`: done: title, artist, previous, play and pause, next, seek bar, current and total time, volume, mute, shuffle, repeat, and the download ring. Not built: cover placeholder, queue button, visualizer toggle.
- [x] Queue panel showing Now Playing and upcoming tracks with reorder and remove. Built: Queue tab with Now playing card, Up next list, Move up, Move down, Remove, Clear queue (confirmed), double-click or Enter to play a row. Right-click Play Next and Add to Queue on Home and Playlists. Discover is not included, because its results are not in the library yet. Evidence: `PlaybackQueueTest` skipTo tests.
- [x] Wire Play from library double-click (and Enter), Play playlist, and Shuffle playlist (completes Phase 2 items).
- [x] Now-playing row highlight on Home and Playlists: a soft accent halo, a faint fill, and a left bar on the current track, kept separate from the selection pill. The halo pulses with the peak output level from `AudioPlayer.takeLevel()` (sampled each position tick) and eases back to rest on pause or stop. The highlight follows the track through sorting and filtering. Evidence: `SmoothTable.setPlaying`, `PlaybackController` level, `MainWindow` state listener. Visual check still open.
- [x] Verify: automated tests for queue behavior (next, previous, repeat modes, seeded shuffle, reorder leaves playlist unchanged). Evidence: `PlaybackQueueTest` 9/9 pass.
- [ ] Verify: real playback of MP3, WAV, FLAC, and Opus files; seek lands at the expected position; volume and mute audible; auto-advance works. Open: the owner confirmed playback works; the per-format, seek, volume, and auto-advance checks are not recorded yet.
- [ ] Verify: after Stop, after track switch, and after app exit, `Get-Process ffmpeg` shows no leftover process.

### Phase 5 — Polish

- [ ] Automatic cover art: during a YouTube download, fetch the thumbnail in the background, resize to a bounded size, save to `artwork/<trackId>.jpg`, and set `artworkRelativePath`. Do not refetch when the local file exists.
- [ ] Local import reads embedded cover art with ffmpeg when present; otherwise uses the placeholder.
- [ ] Artwork shown in library rows, playlist rows, and the bottom player; consistent placeholder when missing.
- [ ] Optional: embed artwork into MP3 ID3 tags (TBD; requires a re-mux step and its own verification).
- [ ] Application shortcuts: Space (play and pause when focus is not in a text field), Ctrl+F (focus search), Ctrl+N (new playlist), Ctrl+O (import local music).
- [ ] Transport media keys: Play and Pause, Next, Previous, and Stop in application scope only, unless the native spike in section 10 succeeds.
- [ ] `PcmAnalyzer.kt`: radix-2 FFT with a Hann window over PCM blocks from the playback thread, reduced to a fixed number of bands.
- [x] Full-window visualizer: `playback/SpectrumAnalyzer.kt` splits the PCM into 32 log-spaced bands (Goertzel, Hann window, mono mix) on the playback thread. `PlaybackController.spectrum()` returns zeros unless audio is playing. `Backdrop` in `Theme.kt` draws the bars behind the glass cards on a 33 ms timer, with eased attack and release. Owner decisions: one accent color (no rainbow), bars glow with that accent and are bright, and bars move only with real audio. Not built: the Settings toggle for `visualizerEnabled`. Visual check still open.
- [ ] Settings dialog: library folder (change, Move existing library or Open another), rescan, visualizer toggle, tool availability probes, fixed MP3 quality notice.
- [ ] Loading, empty, and error states reviewed across all tabs.
- [ ] Performance check: library list with 1,000 synthetic tracks stays responsive while scrolling and searching. Record measured numbers in the verification notes; no invented benchmarks.
- [ ] Cleanup: window close saves the library, stops playback, destroys download and search processes, and cancels background workers.
- [ ] Verify: visual pass against the prompt's section 19 checklist (tabs render, resize works, text readable, scroll behaves, dialogs usable, player controls accessible, no overlaps).
- [ ] Verify: visualizer responds to real playback and is off when disabled.

### Phase 6 — Documentation and release

- [ ] Update `README.md`: new features, build instructions, runtime requirements (yt-dlp, ffmpeg, ffprobe, deno), library folder behavior, playlist persistence, supported playback formats, keyboard shortcuts, new dependencies, known limitations. Keep the authorized-use guidance.
- [ ] Update `README.md` with the new window structure, command lines, and the output-path decision.
- [ ] Update `AGENTS.md` where the output-path rule, package layout, or dependency rules changed.
- [ ] Verify: `./gradlew.bat :app:jar` produces `app\build\libs\app-all.jar`; `javaw -jar app\build\libs\app-all.jar` opens the window; the final report lists every verification from phases 0 to 5 with the evidence type used.
- [ ] Do not commit or push unless explicitly requested.

### Phase 7 — Packaging and onboarding

Owner decisions are recorded in section 1. Planned.

- [x] `tools/ToolProbe.kt`: version probes for yt-dlp, ffmpeg, ffprobe, and deno with 10 s timeouts, drained output, and Found, Missing, or Failed results. Evidence: `ToolProbeTest` 4/4 pass (found tool, missing command, nonzero exit, hanging command reported as "no response").
- [x] `ui/ToolSetup.kt`: startup check on a background thread; the dialog appears only when a tool is missing, lists each tool with its status and WinGet install command, and offers Re-check and Continue. Evidence: screenshot of the app image launched with `PATH` set to System32 only; the dialog lists all four tools as not found, each with purpose, install command, and Copy button. Limit: Re-check and Copy were not clicked in automation. The all-found case was checked by screenshot with the WinGet Links folder on `PATH`: no dialog.
- [ ] CLI: print the tool status before the URL prompt. Code is in `App.kt` (`printToolStatus`). Not verified: `System.console()` returns null when stdin is redirected, so the CLI branch could not be run non-interactively. Needs a run in a real terminal.
- [x] `:app:packageWindows`: jpackage app-image from the JDK 25 toolchain into `app/build/installer/CatoTube/`, with `--enable-native-access=ALL-UNNAMED` for the native title bar and the icon from `assets/app-icon.ico` (generated once from `assets/app-icon-01.png`, multi-size PNG entries). Evidence: task BUILD SUCCESSFUL; output holds `CatoTube.exe`, `app/`, and `runtime/` (about 83 MB). Runtime modules from `jdeps`: java.base, java.desktop, java.instrument, java.prefs, jdk.unsupported. Limit: on two runs Windows held files in the output folder, so the task retries cleanup five times before failing (cause not identified).
- [x] `README.md`: add `ffprobe` and `deno` to Requirements, document the app-image build, and add a troubleshooting note about `PATH` after a WinGet install. Evidence: README diff.
- [x] `AGENTS.md`: add the `tools/` package, the `ui/` setup dialog, `ffprobe` in the PATH note, and the `packageWindows` command. Evidence: AGENTS.md diff.
- [x] Verify: `./gradlew.bat :app:jar` and `./gradlew.bat test` succeed with no new warnings. Evidence: `:app:jar test --rerun-tasks` BUILD SUCCESSFUL; no `w:` compiler lines.
- [x] Verify: `CatoTube.exe` from the app image opens the window with no console. Evidence: process stayed alive for 10 s on the final build. Earlier screenshots of the same dialog code show the window with its dark title bar. Limit: the last screenshot caught IntelliJ in front, so the final build was not re-inspected visually.
- [ ] Verify: with the tools removed from `PATH`, the setup dialog names each missing tool and shows its install command, the app stays usable, and Re-check picks the tools up after they are restored. Not verified: Re-check after restoring a tool. A running app keeps the `PATH` it started with, so Re-check cannot see a tool installed while CatoTube is open; the dialog text says to reopen the app after a fresh install. Revisit whether Re-check should re-read the registry `PATH`.
- [ ] Verify: the CLI prints the tool status and the install commands before the URL prompt. See the CLI item above.

## 10. Open tuning points

- **Global media keys on Windows.** Owner: Phase 5 spike. Options: JNA with `RegisterHotKey` for Play/Pause, Next, Previous, Stop, or defer. Trigger: after Phase 4 is stable. Until then, document as not supported.
- **FFmpeg playback latency.** If the Phase 0 spike shows unacceptable start latency, evaluate keeping a warm FFmpeg process per track or switching to JLayer for MP3-only playback. Owner: user decision with the spike numbers.
- **Visualizer band count and frame rate.** Starting values: 32 bands, about 30 fps delivery. Tune after the Phase 5 visual check.
- **Default library location.** Proposed: first-run dialog with no default. Owner: user.
- **Search result count.** Proposed: 10 per query, with a "load more" action deferred. Owner: user.
- **Theme option.** Only dark theme in v2.0 unless a light palette is requested. The Settings "Preferred theme" item stays deferred.
- **MP3 quality setting.** Fixed to `--audio-quality 0` in v2.0 to preserve the tuned pipeline. Revisit only with the download owner's sign-off after testing.
- **Optional ID3 artwork embedding.** Deferred until the core cover-art path is verified.
- **Installer types and code signing.** The first release ships the app-image folder only. MSI and EXE (both need the WiX Toolset) and Authenticode signing are deferred; unsigned launchers show a SmartScreen warning. Owner: user. Trigger: when a distribution channel is chosen.
