# CatoTube 2.0 — Complete Desktop Music Player Redesign

You are working on my existing Kotlin/JVM project, **CatoTube**, located at:

`X:\botsncoding-1tb-ssd\catotube`

Your task is to transform CatoTube from a simple YouTube-to-MP3 downloader into a polished, fully functional desktop music player with YouTube search, local music management, playlists, playback, and a modern dark interface.

**This is an implementation task, not a planning-only task.** Inspect the repository, make the changes, build the application, run tests, and verify as much functionality as possible.

## 1. Read the existing project first

Before modifying code, read:

- `AGENTS.md`
- `README.md`
- `HANDOFF.md`
- `app/src/main/kotlin/App.kt`
- `app/src/main/kotlin/Theme.kt`
- `app/build.gradle.kts`
- Relevant shared utilities and tests

Follow the repository's existing conventions.

Important architectural details:

- Kotlin/JVM with Swing.
- `App.kt` currently handles CLI and GUI behavior.
- `Theme.kt` contains custom-painted components, `Palette`, `Fonts`, and visual utilities.
- Swing UI updates must occur on the Event Dispatch Thread.
- Existing custom components use `Eased` animations and `smoothCopy()` for antialiasing.
- The app builds into `app/build/libs/app-all.jar`.
- External yt-dlp, FFmpeg, and Deno tools are available through PATH.
- The existing yt-dlp configuration was carefully tuned after discovering broken IPv6 connectivity.

**Do not remove or break `--force-ipv4`.**

Preserve the existing working download pipeline, cancellation, progress reporting, CLI behavior, URL validation, and executable JAR packaging.

Do not add emojis to application UI text or source code.

## 2. Overall application design

Redesign the Swing interface into a larger, resizable music application.

Target initial window size: **1100 × 750 pixels**.

Minimum window size: approximately 900 × 600 pixels.

Maintain the existing dark visual identity, but improve spacing, navigation, typography, layout, and overall polish.

Create four main navigation tabs:

1. Home / Library
2. Discover
3. Playlists
4. Downloads

Add a persistent music player bar at the bottom of the application.

The bottom player must remain visible while navigating between tabs.

Use the existing `Palette`, `Fonts`, and custom theme infrastructure. Put new visual components and styling in `Theme.kt` or appropriately separated UI files rather than filling `App.kt` with drawing code.

Avoid introducing JavaFX or replacing Swing.

Use appropriate layout managers so the window resizes gracefully.

## 3. Home / Library tab

Create a proper local music library view.

Features:

- Display all imported and downloaded music.
- Show cover art, title, artist/channel, duration, and optional album information.
- Search local music by title, artist, or filename.
- Sort by title, artist, duration, and date added.
- Display a Recently Added section.
- Double-click a track to start playback.
- Right-click tracks for actions such as Play, Add to Playlist, Open File Location, and Remove from Library.
- Allow importing existing local audio files.
- Show an empty-state message when the library contains no music.

Use a responsive list or compact card layout suitable for a desktop music player.

Keep library searching fast by filtering indexed in-memory metadata rather than rescanning the filesystem on every keystroke.

## 4. Discover tab — YouTube search

Allow users to search YouTube videos directly from CatoTube.

Search interface:

- Search input at the top.
- Search button and Enter-key support.
- Loading indicator.
- Clear error messages.
- Scrollable search results.
- Cancel outstanding searches when appropriate.

Use yt-dlp's supported YouTube search functionality initially.

Avoid requiring a Google API key.

Preserve IPv4 preference for yt-dlp network operations.

Display each result in a compact horizontal card containing:

- YouTube thumbnail.
- Video title.
- Channel/uploader.
- Video duration.
- Short video description.
- Video URL or ID internally.

Descriptions should be truncated cleanly, with an option to view more information.

Load thumbnails asynchronously with reasonable connection timeouts. Do not block the Swing EDT.

When a result is clicked, open a dialog showing the selected video's information.

The dialog should provide:

- Add to existing playlist.
- Create a new playlist.
- Add to library without a playlist.
- Cancel.

When the user confirms, queue the audio download.

After successful download and conversion, automatically add the song to the library and selected playlists.

If downloading fails, do not create a broken library entry.

Do not silently download videos merely because they appear in search results.

Do not implement YouTube video streaming or attempt to bypass authentication, DRM, or access restrictions.

## 5. Persistent music library folder

Allow users to select their preferred music library directory.

Requirements:

- Prompt for a library folder on first launch.
- Allow changing the folder through Settings.
- Remember the chosen folder across restarts.
- Store the path using Java Preferences or an equivalent lightweight persistent settings mechanism.
- Validate that the directory exists and is writable.
- Never silently replace an unavailable library with an empty one.
- Provide a way to relocate a missing library.

Use a structure similar to:

```text
Music/
├── songs/
│   ├── track-1.mp3
│   └── track-2.mp3
├── artwork/
│   ├── track-1.jpg
│   └── track-2.jpg
└── library.json
```

The library metadata should include:

- Stable track ID.
- Title.
- Artist/uploader.
- Duration.
- File location.
- Date added.
- Source URL, if applicable.
- YouTube video ID, if applicable.
- Artwork location.
- Playlist membership and ordering.

Use a simple, maintainable JSON serialization approach.

Persist changes reliably using temporary-file writes followed by atomic replacement where supported, with safe fallback and recovery behavior.

Keep a backup of the last valid metadata file.

Use portable relative paths for files inside the library.

If the library folder changes, explicitly offer to move the existing library or open another existing library. Never silently abandon or overwrite music.

Support rescanning the library for manually added files.

Do not delete user audio files without explicit confirmation.

## 6. Playlist management

Create a dedicated Playlists tab.

Users must be able to:

- Create playlists.
- Rename playlists.
- Delete playlists.
- Add tracks from Discover.
- Add tracks from the local library.
- Remove tracks from playlists.
- Reorder playlist tracks.
- Play an entire playlist.
- Shuffle a playlist.
- View playlist duration and track count.

Display playlists in a sidebar or grid.

Selecting a playlist should display its tracks.

**Important data model rule:** One audio file can belong to multiple playlists.

Do not duplicate MP3 files when adding the same song to multiple playlists.

Deleting a playlist must never delete its audio files.

Removing a track from a playlist should only remove that playlist association.

If a YouTube video has already been downloaded into the library, adding it to another playlist should reuse the existing track instead of downloading it again.

This is duplicate prevention, not a general metadata caching system.

Persist playlist changes across application restarts.

## 7. Built-in music playback engine

Implement a real local music player.

Minimum playback features:

- Play.
- Pause.
- Stop.
- Next track.
- Previous track.
- Seek within a track.
- Adjustable volume.
- Mute.
- Shuffle.
- Repeat off.
- Repeat all.
- Repeat one.
- Track duration and current playback time.
- Automatic playback of the next queued track.

Use a reliable playback solution compatible with Kotlin/JVM and Windows.

Evaluate lightweight Java audio libraries or FFmpeg-based playback solutions before selecting one.

The player must correctly handle MP3 files.

Prefer supporting common imported formats such as WAV, FLAC, and Opus if practical.

Do not implement a fragile custom MP3 decoder.

Playback must not block the Swing EDT.

Ensure playback resources are released when switching tracks or exiting the application.

Handle missing, corrupt, and unsupported audio files gracefully.

## 8. Persistent bottom player

Create a bottom playback bar visible across every tab.

Display:

- Small cover image.
- Track title.
- Artist.
- Previous button.
- Play/pause button.
- Next button.
- Seek bar.
- Current time.
- Total duration.
- Volume control.
- Shuffle control.
- Repeat control.
- Queue button.

Use smooth but lightweight animations.

Avoid excessive repaints or expensive work on Swing timers.

The player should visually match the existing CatoTube theme.

## 9. Now Playing queue

Implement a proper playback queue.

Features:

- View upcoming tracks.
- Add a song to the queue.
- Play Next.
- Add to Queue.
- Remove queued items.
- Reorder queued tracks.
- Clear queue.
- Display the currently playing song.
- Automatically advance when playback finishes.

Distinguish between the persistent music library, playlists, and the temporary playback queue.

Do not accidentally modify playlist order when rearranging the playback queue.

Preserve queue state during normal tab navigation.

Persisting the queue across restarts is optional; prioritize reliable playback first.

## 10. Recently Added

Add a Recently Added section to the Home tab.

Show the latest imported or downloaded tracks.

Sort by the library's recorded date-added field.

Provide a way to open the full recently added list.

This feature must work for both YouTube downloads and manually imported files.

## 11. Automatic cover art

When downloading music from YouTube:

- Retrieve the video's thumbnail.
- Save artwork inside the library's artwork directory.
- Associate the image with the correct track.
- Display artwork in library lists, playlists, and the bottom player.
- Optionally embed artwork in MP3 ID3 metadata.

Use background workers for thumbnail retrieval and processing.

Resize large images for UI display to avoid unnecessary memory usage.

Handle missing thumbnails with a visually consistent placeholder.

For manually imported files, read embedded cover art when available.

Do not repeatedly fetch artwork that is already stored locally.

## 12. Keyboard media keys

Add support for standard media controls when feasible:

- Play/Pause.
- Next Track.
- Previous Track.
- Stop.

Also support application keyboard shortcuts, such as:

- Space: play/pause when not typing.
- Ctrl+F: focus the current search field.
- Ctrl+N: create playlist where appropriate.
- Ctrl+O: import local music.

Investigate Windows media-key support carefully.

Global media keys may require Windows-specific APIs or a native integration library.

Do not claim global media-key support unless it is actually implemented and verified.

If global support requires substantial native integration, implement application-level shortcuts first and document the limitation.

## 13. Audio visualizer

Add a compact, attractive audio visualizer to the player.

Preferred design:

- Animated frequency-spectrum bars.
- Colors matching the dark CatoTube theme.
- Smooth transitions.
- Low CPU overhead.
- Automatically stops animating when playback stops.

The visualization must be driven by real decoded audio data, not random values.

If the selected playback engine does not expose PCM data, implement a compatible analysis path or defer the visualizer rather than faking audio responsiveness.

Use FFT or an appropriate lightweight DSP implementation.

Keep analysis off the Swing EDT.

Render only the required visual data on the EDT.

Allow disabling the visualizer in Settings.

Prioritize stable playback over visualization complexity.

## 14. Downloads tab

Preserve the existing manual YouTube URL download workflow.

Add:

- URL input.
- Download button.
- Download queue.
- Per-item status.
- Progress percentage.
- Current operation description.
- Cancel current download.
- Clear completed entries.
- Open downloaded file location.
- Optional expandable verbose logs.

Download statuses should distinguish:

- Queued.
- Extracting information.
- Downloading.
- Converting.
- Completed.
- Failed.
- Cancelled.

Process downloads sequentially by default.

Do not introduce concurrent downloads unless there is a demonstrated benefit.

Use the selected music library as the default destination for library-managed downloads.

Preserve the existing yt-dlp audio selection, FFmpeg conversion quality, URL validation, cancellation, watchdog, and network settings.

**Do not remove `--force-ipv4`.**

Existing standalone CLI behavior should remain functional, even if the GUI gains additional library features.

## 15. Settings

Add a small Settings dialog or panel.

Include:

- Music library folder.
- Change library folder.
- Rescan library.
- Default MP3 quality.
- Enable/disable visualizer.
- Preferred theme, if easily supported.
- Basic information about external tool availability.

Persist settings across restarts.

Avoid creating a large settings framework.

## 16. Architecture and maintainability

Do not put the entire redesign into `App.kt`.

Separate responsibilities into appropriately named Kotlin files and packages.

Suggested components:

```text
app/src/main/kotlin/
├── App.kt
├── Theme.kt
├── library/
│   ├── MusicLibrary.kt
│   ├── Track.kt
│   ├── Playlist.kt
│   └── LibraryStorage.kt
├── playback/
│   ├── AudioPlayer.kt
│   ├── PlaybackQueue.kt
│   └── AudioVisualizer.kt
├── download/
│   ├── DownloadManager.kt
│   └── YouTubeSearch.kt
└── ui/
    ├── MainWindow.kt
    ├── LibraryPanel.kt
    ├── DiscoverPanel.kt
    ├── PlaylistsPanel.kt
    ├── DownloadsPanel.kt
    ├── PlayerBar.kt
    └── SettingsDialog.kt
```

This is a suggested structure, not a rigid requirement.

Keep components cohesive and avoid unnecessary abstraction.

Use background executors or appropriate concurrency primitives for networking, decoding, downloads, metadata extraction, and filesystem scans.

All Swing state changes must happen on the EDT.

Handle cancellation, application shutdown, process termination, and resource cleanup correctly.

## 17. Reliability and data safety

The application must:

- Never overwrite existing audio unexpectedly.
- Never delete user files without confirmation.
- Never corrupt playlist metadata during normal operations.
- Recover gracefully from missing files.
- Avoid duplicate downloads when an existing library track can be reused.
- Handle network errors without freezing.
- Handle missing FFmpeg, yt-dlp, or Deno with useful messages.
- Prevent two downloads from writing to the same destination simultaneously.
- Keep the UI responsive during large library scans.
- Avoid leaving partial MP3 files registered as completed tracks.
- Handle duplicate video titles and filename collisions safely.
- Save library state before exiting.
- Avoid losing completed library updates if the app crashes.

Do not implement cloud synchronization, user accounts, telemetry, or online services.

All music library and playlist data should remain local.

## 18. Implementation phases

Implement this incrementally, keeping the application buildable after each phase.

**Phase 1 — Foundation**

- Refactor UI structure where necessary.
- Larger resizable main window.
- Tab navigation.
- Persistent library folder.
- Library metadata model.
- Library scanning and import.
- Local search and Recently Added.

**Phase 2 — Playlists**

- Playlist creation, renaming, deletion.
- Track membership.
- Playlist ordering.
- Persistent playlist storage.
- Multiple playlists sharing one audio file.

**Phase 3 — YouTube discovery**

- Search UI.
- Asynchronous result retrieval.
- Thumbnails and descriptions.
- Playlist selection dialog.
- Integration with the existing download pipeline.
- Automatic library registration after successful download.

**Phase 4 — Playback**

- Playback engine.
- Persistent bottom player.
- Playback queue.
- Shuffle and repeat.
- Seeking and volume.
- Track advancement.

**Phase 5 — Polish**

- Automatic cover art.
- Keyboard shortcuts and media-key support.
- Audio visualizer.
- Settings.
- Improved loading, empty, and error states.
- UI performance and cleanup.

If a phase becomes blocked, document the blocker and continue with independent work where safe.

Do not replace a working implementation with nonfunctional placeholders.

## 19. Testing and verification

After each phase, compile and fix errors.

Run:

```powershell
.\gradlew.bat :app:jar
.\gradlew.bat test
```

Launch the Swing GUI using:

```powershell
javaw -jar app\build\libs\app-all.jar
```

Do not rely solely on compilation to verify the UI.

Check visually that:

- Tabs render correctly.
- Window resizing works.
- Text is readable.
- Scrollable content behaves correctly.
- Dialogs remain usable.
- Player controls are accessible.
- No components overlap.

Test library persistence by closing and reopening the application.

Test playlist creation, deletion, reordering, and cross-playlist membership.

Test playback using real local audio files.

Test the download pipeline with a short authorized YouTube video when network access permits.

Verify that the resulting MP3 is valid and playable.

Test cancellation during extraction and downloading.

Test behavior when the library directory is missing or unwritable.

Test filename collisions and metadata recovery.

Add automated tests for library serialization, playlist operations, duplicate detection, queue behavior, and other non-UI logic.

If a feature cannot be tested in the environment, clearly report the limitation.

Do not invent benchmark results or claim functionality works without verification.

## 20. Documentation

Update:

- `README.md`
- `HANDOFF.md`
- `AGENTS.md` if architectural guidance needs updating

Document:

- New application features.
- Build instructions.
- Runtime requirements.
- Library folder behavior.
- Playlist persistence.
- Playback format support.
- Keyboard shortcuts.
- Any new dependencies.
- Known limitations.

Preserve the existing authorized-use guidance.

Do not commit or push changes unless explicitly instructed.

## 21. Final deliverables

When implementation is complete, provide:

1. Summary of implemented features.
2. List of changed and added files.
3. New dependencies and why they were selected.
4. Build and test results.
5. GUI verification results.
6. Playback verification results.
7. YouTube search/download verification results.
8. Any features that remain incomplete.
9. Instructions for launching the finished application.

The final deliverable must include a successfully built executable JAR at:

`app\build\libs\app-all.jar`

## Final priorities

In order of importance:

1. Preserve the existing fast and reliable downloader.
2. Prevent music library and playlist data loss.
3. Deliver functional local music playback.
4. Deliver usable YouTube discovery and playlist management.
5. Maintain a responsive, polished dark Swing interface.
6. Add media controls, cover art, and visualization without sacrificing stability.

**Do not optimize for the smallest amount of code. Optimize for a maintainable, reliable, enjoyable desktop music player.**

**Do not stop after creating a design or architecture proposal. Implement the application, verify it, and build the JAR.**

The goal is to make CatoTube feel like a lightweight personal Spotify-style desktop music player, powered by a local music library and YouTube discovery, without accounts, subscriptions, or unnecessary complexity.