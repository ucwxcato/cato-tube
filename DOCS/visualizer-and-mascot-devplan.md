# CatoTube Visualizer Playground and Cato Mascot — Development Plan

> **Status:** Planned. No code has changed. Baseline commit `7d8304a`; the working tree has no source changes. Section 4 lists facts read from the repository; everything else is design.
>
> **Purpose:** Add a customizable, audio-reactive visualizer and an optional animated Cato mascot that reacts to playback. Both read one shared analysis feed.
>
> **Authority:** This document owns the visualizer settings model, the Visualizer page, the mascot state machine, and the playback feature feed. Related plan: `DOCS/music-player-devplan.md`, which owns playback, library, and downloads. This plan changes `playback/` and `Backdrop` in `Theme.kt`, and it reuses the `visualizerEnabled` preference key that the music plan reserves (its section 6). Source request: `PROMPTS/visualizer-n-pet-prompt.md`. Repository rules that still apply: `AGENTS.md`, `README.md`.
>
> **Target:** Windows 10/11, Kotlin/JVM with Swing, JDK toolchain 25 (`buildSrc`), external `yt-dlp`, `ffmpeg`, and `ffprobe` on `PATH` for playback. Build output `app/build/libs/app-all.jar`.

Evidence labels: **Verified** (source plus active runtime check), **Installed** (artifact exists, not gameplay-checked), **Authored** (source exists, runtime not proven), **Planned** (future work or design-only), **TBD** (needs an owner decision).

## 0. Outcome

With music playing, the Visualizer page shows a live preview. Changing a mode or a setting updates the preview and the window background at once. The mascot bobs to the beat in the bottom-right corner, relaxes on pause, and falls asleep after an idle period. Each feature can be turned off independently. Settings and presets survive a restart.

Complete when:

```text
music playing -> Visualizer page -> pick "Audio Ring", change glow and colors
  -> preview and window background update immediately
  -> mascot bobs on the beat in the bottom-right corner
  -> Pause: mascot relaxes, visualizer decays to rest
  -> Stop, wait out the sleep timeout -> mascot sleeps
  -> close and reopen -> mode, colors, mascot settings, and presets unchanged
```

## 1. Locked decisions

These come from the owner's answers to the planning questions and from verified repository constraints. Changing one requires editing this document first.

- **Playground placement.** The Visualizer Playground is a new sidebar page named "Visualizer" in `MainWindow.kt`, next to Home, Discover, Playlists, Queue, and Downloads. It is not a dialog.
- **Backdrop stays the window visualizer.** The current full-window bars in `Backdrop` (`Theme.kt`) become the **Classic Bars** mode and the default. First launch looks the same as today.
- **Mascot art is vector-drawn in code.** Java2D shapes only. No image, sprite, or GIF assets in v1. Appearance variants are palette swaps of one drawing.
- **Mascot default position.** Bottom-right, in a margin reserved at the right end of the player bar. It never covers a transport, seek, volume, or text control. Free dragging is an optional later phase (section 10, item 10.10).
- **Build on the current code.** Music player Phases 4 and 5 are not gates for this plan. Where this plan touches the same files (`AudioPlayer.kt`, `PlaybackController.kt`, `MainWindow.kt`), section 4 lists the changes and section 9 sequences them.
- **No sound effects in v1.** Mascot sounds are deferred until there are audio assets and a mixing decision.
- **One analysis feed.** No visualizer or mascot runs its own FFT or Goertzel pass. Each consumer reads the same immutable snapshot.
- **`takeLevel()` is not shared.** `AudioPlayer.takeLevel()` is destructive (it resets `peak`). It stays owned by the existing row-pulse path in `PlaybackController.onPosition`. The new feed keeps its own peak.
- **No new dependencies.** Presets use `kotlinx.serialization` JSON, which `app` already declares (`app/build.gradle.kts`). No animation libraries.
- **Master enable.** The visualizer on/off setting uses the preference key `visualizerEnabled` (node `org/catosaur/app`), as reserved in the music plan. It is not a second key.

## 2. Goals and non-goals

### Goals

- Six visualizer modes behind one renderer interface: Classic Bars, Circular Spectrum, Waveform, Particle Visualizer, Audio Ring, and Minimal.
- A settings model where each mode shows only the controls it uses.
- A Visualizer page with a live preview, grouped controls, reset, restore previous, randomize, and presets (save, rename, delete, import, export).
- A shared feature feed: overall energy, bass, mid, and treble energy, 32 frequency bands, waveform samples, beat pulses, playback state, track changes, and errors.
- A mascot state machine covering idle, playing (calm and energetic), paused, sitting, asleep, waking, skipping, confused, and click reactions.
- Mascot settings: enable, size, intensity, beat reactivity, idle frequency, variant, reduced motion, and sleep timeout.
- Hidden, minimized, and unfocused windows stop or slow all animation. Analysis runs only while something consumes it.

### Explicitly out of scope

- Mascot sound effects, audio assets, or mixing.
- Image, sprite-sheet, or GIF assets, and any new animation library.
- JavaFX, a replacement for Swing, or a second UI toolkit.
- Global hotkeys or media keys for the visualizer or mascot.
- Persisting the playback queue.
- Automatic per-track presets, or any online preset sharing.
- Changes to the yt-dlp argument list or the download flow.
- Editing audio files or metadata.

## 3. User experience and operational flow

1. **Launch.** Visualizer and mascot settings load from preferences. The mascot appears in the corner if enabled. It starts asleep when nothing is playing.
2. **Visualizer page.** The top area is a live preview. Controls sit below or beside it, in groups: Mode, Colors, Motion, Response, Shape (mode-specific), Background, and Mascot. The bottom row has Reset, Randomize, Restore previous, the preset selector, Save, Rename, Delete, Import, and Export.
3. **Preview source.** The preview uses live audio when music is playing. When stopped, the preview can use a simulated signal (toggle, default off). The simulated signal never reaches the window background, so the background stays flat when playback stops.
4. **Changing a control.** The preview updates on the next frame. The window background updates too. The new value is saved after a short debounce. Before the first change in an edit session, the previous configuration is stored for Restore previous.
5. **Randomize.** Generates a configuration inside the bounded ranges in section 6. It is applied like any other change. It never changes mascot settings.
6. **Presets.** Save as a new name, load, rename, or delete. Delete asks for confirmation through `ThemedDialog`. Export writes one preset to a JSON file. Import reads a JSON file, validates it, and adds presets with a collision suffix.
7. **Mascot.** Playback drives its pose. Clicking it plays a short reaction (wave, meow, or happy eyes), never repeating the same one twice in a row. Right-click offers Hide and Sleep now. The mascot never opens dialogs and never shows popups.

## 4. Architecture and ownership

### Verified facts (repository at `7d8304a`)

- `app/src/main/kotlin/playback/SpectrumAnalyzer.kt`: `internal class SpectrumAnalyzer`. `analyze(pcm, count)` returns a new `FloatArray` per call. It produces `SPECTRUM_BANDS = 32` log-spaced bands from 40 Hz to 16 kHz, using one Goertzel filter per band over 1024 mono frames with a Hann window. Output is 0 to 1 on a dB scale (floor -62 dB, range 52 dB).
- `app/src/main/kotlin/playback/AudioPlayer.kt`: one playback thread. `runLoop()` reads `CHUNK_BYTES = 4096` from the FFmpeg stdout pipe (1024 stereo frames per chunk, about 43 chunks per second at 44.1 kHz), then runs `applyGain`, `measure` (RMS peak into `peak`), and `analyzer.analyze`, then writes to the `SourceDataLine`. `spectrum()` returns the most recent `bands` array (`@Volatile`). No raw sample buffer is kept.
- `AudioPlayer.takeLevel()` returns and resets `peak`. It is called only from `PlaybackController.onPosition` (line 200).
- `app/src/main/kotlin/playback/PlaybackController.kt`: `PlayerState` (track, playing, positionMs, volume, muted, shuffle, repeat, level) goes to state listeners. Errors go to `addErrorListener` as a `String`. Queue changes go to `addQueueListener`. `spectrum()` returns zeros when not playing. Listeners run on the thread that raised the change, so `MainWindow` marshals to the EDT with `SwingUtilities.invokeLater`.
- `PlaybackController.advance(automatic: Boolean)`: `automatic = true` for track end, `false` for errors and manual skips. A track change carries no reason today, so the mascot cannot tell a skip from a user selection. This is a gap (Phase 8).
- `app/src/main/kotlin/Theme.kt`, `Backdrop(spectrum: () -> FloatArray)`: pulls bands every 33 ms (`BARS_FRAME_MS`), eases with `BARS_ATTACK = 0.55` and `BARS_RELEASE = 0.14`, repaints the whole window, and draws two radial glows plus the bars (`paintBars`). It is the `contentPane` in `MainWindow.kt` (line 129), and it reads `playback::spectrum`.
- `Theme.kt` also has `Eased`, `Card`, `FlatButton`, `TransportButton`, `SegmentedControl`, `ThemedComboBox`, `ThemedField`, `ThemedProgressBar`, `ProgressSlider` (the seek bar only), `SmoothTable`, and `LogoMark` (the app logo, not a mascot). There is no general-purpose slider, so `ThemedSlider` is new work.
- `app/src/main/kotlin/ui/SideNav.kt` and `MainWindow.kt` (lines 66 to 75): pages are registered as parallel `names` and `contents` lists.
- `MainWindow.kt` line 139: `PlayerBar(downloads, playback).view` is added to the south edge of the content pane.
- Preferences node `org/catosaur/app` is used by `library/LibraryFolder.kt` (`libraryRoot`) and `ui/AccentPicker.kt` (`accent`). `Palette.applyAccent` sets `Palette.focus` from the chosen accent, and `Backdrop` paints bars with `Palette.focus`.
- `kotlinx.serialization` is declared in `app/build.gradle.kts` and used in `library/LibraryStorage.kt`.
- Not present: a settings dialog, a mascot, beat detection, a waveform buffer, a visualizer toggle, a slider component, and any test source for playback feature code beyond `SpectrumAnalyzerTest` and `PlaybackQueueTest`.

### Proposed tree (Planned)

```text
app/src/main/kotlin/
├── playback/
│   ├── AudioFeatures.kt         immutable snapshot type + publisher (two preallocated buffers, swapped)
│   ├── BeatDetector.kt          onset detection on bass energy, adaptive threshold, refractory period
│   ├── WaveformBuffer.kt        ring of recent mono samples, written on the playback thread
│   ├── PlaybackEvent.kt         sealed events: TrackStarted(reason), Paused, Resumed, Stopped, Failed
│   └── SimulatedFeatures.kt     deterministic synthetic features for the preview while stopped
├── visual/                      new package (see decision 10.1)
│   ├── VisualizerSettings.kt    model, schemaVersion, defaults, ranges, per-mode relevance
│   ├── VisualizerStore.kt       preferences load/save, debounce, previous-configuration slot
│   ├── PresetStore.kt           preset file, import/export, validation
│   ├── VisualizerRenderer.kt    renderer interface and shared color helpers
│   ├── ClassicBars.kt           bar drawing moved out of Backdrop.paintBars
│   ├── CircularSpectrum.kt
│   ├── WaveformRenderer.kt
│   ├── ParticleField.kt
│   ├── AudioRing.kt
│   └── MinimalRenderer.kt
├── mascot/
│   ├── MascotMachine.kt         state table; consumes PlaybackEvent and AudioFeatures; no Swing
│   ├── MascotPose.kt            pose values (head tilt, ears, tail, eyes, mouth, blink)
│   ├── MascotView.kt            JComponent, Java2D drawing, hit test on drawn shape only
│   └── MascotSettings.kt        settings model and store keys
└── ui/
    └── VisualizerPage.kt        Playground page: preview, controls, preset row
```

Ownership rules:

- `playback/` produces features and events. It has no Swing imports.
- `visual/` renderers draw into a given `Graphics2D` with given bounds, features, and settings. They have no timers, no preferences access, and no Swing component state.
- Each host owns exactly one `javax.swing.Timer`: `Backdrop`, `VisualizerPage` preview, and `MascotView`. Renderers and the mascot machine never create timers.
- `mascot/` has no Swing in its state logic. `MascotView` only draws and forwards clicks.
- `ui/VisualizerPage.kt` wires controls to `VisualizerStore` and `PresetStore`. Renderers do not read preferences.
- `Theme.kt` keeps shared primitives (for example a new `ThemedSlider`) and the `Backdrop` shell. Mode-specific drawing lives in `visual/`.
- `MainWindow.kt` wires new pages and wraps the content pane in a `JLayeredPane` so the mascot can overlay the player bar. Behavior stays in the owning classes.

Proposed contracts (Kotlin, design-only names):

```kotlin
// playback/AudioFeatures.kt
internal class AudioFeatures {
    // Read-only view. Consumers must not keep a reference across frames; the publisher reuses the buffer.
    val bands: FloatArray          // SPECTRUM_BANDS values, 0..1, after smoothing
    val rawBands: FloatArray       // current analyzer bands; Classic Bars uses these for its existing easing
    val waveform: FloatArray       // WAVEFORM_SAMPLES mono values, -1..1
    val energy: Float              // block RMS, 0..1
    val bass: Float                // mean of bands 0..9 (about 40 to 250 Hz)
    val mid: Float                 // mean of bands 10..21 (about 250 Hz to 2.5 kHz)
    val treble: Float              // mean of bands 22..31 (above about 2.5 kHz)
    val beat: Float                // 0..1 pulse, set on onset, decays over about 200 ms
    val frame: Long                // increments per analysis block
    val active: Boolean            // false when not playing; consumers ease to rest
}

// playback/PlaybackEvent.kt
internal sealed interface PlaybackEvent {
    data class TrackStarted(val track: Track, val reason: StartReason) : PlaybackEvent
    data class Paused(val track: Track) : PlaybackEvent
    data class Resumed(val track: Track) : PlaybackEvent
    data object Stopped : PlaybackEvent
    data class Failed(val message: String) : PlaybackEvent
}

internal enum class StartReason { USER_SELECT, SKIP_NEXT, SKIP_PREVIOUS, AUTO_ADVANCE, REPEAT_ONE }

// visual/VisualizerRenderer.kt
internal interface VisualizerRenderer {
    val mode: VisualizerMode
    fun update(features: AudioFeatures, settings: VisualizerSettings): Boolean // advances easing; true means repaint
    fun render(g: Graphics2D, width: Int, height: Int, features: AudioFeatures, settings: VisualizerSettings)
    fun reset()   // clears particles, smoothing state, and cached geometry on mode switch
}
```

The renderer contract may advance state in `update`, called by its host's timer. `render` only paints. The initial `VisualizerSettings` contains the selected `VisualizerMode`; Phase 3 adds the persisted controls and schema.

The existing `PlayerState.level` (from `takeLevel()`) and the new `AudioFeatures.energy` are separate values. Both are derived from the same block, but each has its own consumer. The row pulse keeps using `level`.

## 5. Data, lifecycle, and failure handling

### Feature publication and analysis lifecycle

- `AudioFeatures` has two preallocated snapshot objects. The playback thread writes the back buffer, then publishes it with one `@Volatile` reference swap. Readers on the EDT read the reference and never lock.
- Each snapshot carries both smoothed `bands` and current `rawBands`. Classic Bars eases the raw values on the Backdrop timer, preserving its original response while other renderers use the shared smoothed bands.
- Consumers call `attach()` when they start showing and `detach()` when they stop (Backdrop, preview, mascot). The playback thread runs waveform copy and beat detection only while the consumer count is above zero. Playback audio output is unaffected.
- When `active` is false (paused, stopped, or no track), the publisher stops writing new values and consumers ease toward rest. Stopping playback sets bands, waveform, energy, and beat to zero, the same as `AudioPlayer` does today when it clears `bands` on stop.
- The analyzer writes into a caller-owned buffer. The per-chunk `FloatArray` allocation in `SpectrumAnalyzer.analyze` goes away.

### Settings persistence

- Keys (node `org/catosaur/app`):

```text
visualizerEnabled        boolean, default true          (shared with music plan section 6)
visualizer.settings      JSON string, VisualizerSettings schemaVersion 1
visualizer.previous      JSON string, last configuration before the most recent change
mascot.settings          JSON string, MascotSettings schemaVersion 1
```

- `java.util.prefs` limits each value to 8192 characters (JDK documented limit). The settings JSON is far below that. Presets are not stored in preferences (section 6).
- Writes are debounced (default 300 ms, non-repeating `Timer` on the EDT). A pending write flushes on window close.
- Invalid or out-of-range values are clamped to the ranges in section 6. A missing key means defaults. A corrupt JSON value falls back to defaults and is kept as `<key>.corrupt` in memory only, with a status line. Corrupt values are not silently overwritten on startup.
- A newer `schemaVersion` than the app supports is read as defaults and is not written back, the same policy as `library.json`.

### Preset file

- Location (default, see decision 10.3): `%APPDATA%\CatoTube\visualizer-presets.json`. Fallback when `APPDATA` is unset: `<user.home>\.catotube\visualizer-presets.json`.
- Format:

```json
{
  "format": "catotube.visualizer-presets",
  "schemaVersion": 1,
  "presets": [
    { "name": "Late night", "settings": { "schemaVersion": 1, "mode": "AUDIO_RING", "...": "..." } }
  ]
}
```

- Writes use the same temp-file, flush, and atomic-move pattern as `library/LibraryStorage.kt`. The previous file is kept as `visualizer-presets.json.bak`.
- A corrupt preset file is renamed `visualizer-presets.json.corrupt-<timestamp>`, the app starts with no presets, and the message names the file. It never overwrites the corrupt file.

### Renderer and mascot lifecycle

- Mode switch calls `reset()` on the outgoing renderer (its state is released), then `reset()` on the incoming renderer, then the next frame draws with the new renderer. No renderer keeps a timer or thread after a switch.
- Geometry tables (sin and cos for circular modes, the particle pool) are rebuilt only when width, height, or the relevant setting changes.
- The mascot state machine counts frames on the mascot's own ticker. One-shot animations (skip, confused, click) end by frame count. No extra `Timer` objects.

### Failure handling

- Missing audio data (no track, stopped, or an unreadable file): features are zero, renderers ease to rest, and the mascot shows the idle or sleeping pose. No exception reaches the EDT.
- A playback error (`Failed`) triggers the confused mascot pose once. The existing error line in `PlaybackController` is unchanged.
- This plan adds no new thread. The feature publisher runs on the existing playback thread and must not throw from `measure` or `analyze`; an exception there is a bug to fix, not a condition to handle silently.

## 6. Configuration, permissions, and integrations

### Visualizer settings (`VisualizerSettings`)

Defaults and ranges live in one table in `VisualizerSettings.kt`. The UI and the store both read that table.

| Field | Modes | Default | Range |
|---|---|---|---|
| `mode` | all | `CLASSIC` | six modes |
| `primaryColor` | all | accent (`Palette.focus`) | RGB |
| `secondaryColor` | Circular, Particles, Audio Ring | `#8FA3B8` (proposed) | RGB |
| `followAccent` | all | `true` | boolean; when true, `primaryColor` tracks the accent |
| `sensitivity` | all | `1.0` | 0.25 to 3.0 |
| `amplitude` | all | `1.0` | 0.25 to 2.0 |
| `smoothing` | all | `0.55` | 0.0 to 0.95 (maps to attack and release) |
| `speed` | all | `1.0` | 0.25 to 3.0 |
| `glow` | all | `0.6` | 0.0 to 1.0 |
| `barCount` | Classic, Circular | `32` | 16 to 64 (32 is the analyzer's band count, so values above it reuse bands; see tuning 11.1) |
| `barWidth` | Classic | `0.5` | 0.2 to 0.9 of slot |
| `barSpacing` | Classic, Circular | `0.5` | 0.0 to 0.8 |
| `particleCount` | Particles | `160` | 20 to 400 |
| `particleSize` | Particles | `3.0` | 1.0 to 8.0 px |
| `backgroundAlpha` | all | `1.0` | 0.0 to 1.0 (transparency of the Backdrop fill) |
| `bassEmphasis` | all | `0.0` | 0.0 to 1.0 |
| `fpsLimit` | all | `30` | `30` or `60` (60 only in the Playground preview; see 10.6) |
| `enabled` | all | `true` | mirrors `visualizerEnabled` |
| `schemaVersion` | all | `1` | fixed |

Only the fields listed for the selected mode appear in the controls. Hidden fields keep their stored values.

Randomize ranges (default, decision 10.8): sensitivity 0.6 to 2.0, amplitude 0.6 to 1.5, smoothing 0.3 to 0.85, speed 0.6 to 2.0, glow 0.2 to 0.9, bar count 16 to 48, particle count 60 to 240, colors chosen from the accent-harmonious palette. Randomize never sets `enabled`, `fpsLimit`, or `backgroundAlpha` below 0.5.

### Mascot settings (`MascotSettings`)

| Field | Default | Range or values |
|---|---|---|
| `enabled` | `true` | boolean |
| `size` | `M` | `S` (72 px), `M` (96 px), `L` (128 px) |
| `intensity` | `50` | 0 to 100 (scales pose amplitude) |
| `beatReactivity` | `70` | 0 to 100 |
| `idleFrequency` | `50` | 0 to 100 (blink and look-around rate) |
| `variant` | `TABBY` | `TABBY`, `BLACK`, `WHITE` (proposed, see 10.4) |
| `reducedMotion` | `false` | boolean |
| `sleepAfterSeconds` | `180` | 30 to 1800 (see 10.7) |
| `soundEffects` | not present | out of v1 (deferred) |

### Audio test material

Test clips are generated with `ffmpeg` (`lavfi` sources such as `sine` and `anoisesrc`) into `%TEMP%`. They are not committed. Real playback checks use an authorized clip, as the music plan does.

### Frame-rate policy

| Window state | Analysis | Animation |
|---|---|---|
| Focused, visible | on | 30 fps (60 fps only when `fpsLimit = 60` and only in the preview) |
| Visible, unfocused | on | 15 fps (proposed, 10.6) |
| Minimized or not showing | off (consumers detached) | off |

## 7. Safety, security, and product constraints

- No network calls. Nothing in this plan reaches YouTube, a server, or an online preset service.
- Preset import validates the file before applying anything. Accepted: the expected `format` string, `schemaVersion` 1, a `presets` array of at most 100 entries, and a file size under 64 KB. Each setting is clamped to its range; unknown fields are ignored. A file that fails any structural check is rejected whole, with a message, and the saved presets are unchanged.
- Preset names are trimmed, required, and unique ignoring case (the same rule as playlist names in `MusicLibrary`). Import collisions get the suffix ` (2)`, `(3)`, and so on, and never overwrite an existing preset.
- Exports contain only the preset name and the settings. No paths, no library data, no track names.
- The mascot never plays sound, never opens dialogs, never takes focus, and never sits on a control. Its hit test covers the drawn shape only, so it cannot swallow clicks meant for the player bar.
- Reduced motion turns off bob, sway, blink, and the energetic pose. One-shot reactions become a static change of expression for their duration.
- The visualizer and mascot must not alter playback. Analysis reads PCM copies and never writes to the output stream. Turning either feature off stops its consumers without affecting playback.
- Flashing: the visualizer's glow and peak changes are eased, and the beat pulse decays over about 200 ms. Brightness does not toggle between frames. Reduced motion also limits glow changes (Phase 8 and 9 checks cover it).

## 8. Verification matrix

| Scenario | Expected result | Evidence required |
|---|---|---|
| Play, pause, resume, stop, next | Visualizer and mascot follow each state; playback unaffected | Manual run, screenshots, timed log |
| Bass test tone versus silence | Bass energy and low bands rise on the tone, fall to zero in silence | Automated test on `SpectrumAnalyzer` and feature publisher; manual look |
| Treble test tone | Treble group rises; bass stays low | Automated test |
| Beat detector on a click train at 120 BPM | One beat per click within 1 frame; no beats on a steady tone after warm-up | Automated test `BeatDetectorTest` |
| Beat false triggers on noise | Count reported over a 60 s synthetic noise clip; threshold tuned to a stated limit | Automated test with recorded count |
| Mode switch, all six modes | Each mode draws; switching leaves no leftover particles or waveform state | Manual check plus `jcmd <pid> Thread.print` before and after 10 switches (no new threads) |
| Settings persistence | All settings identical after close and reopen | Manual close and reopen; compare the stored JSON |
| Corrupt `visualizer.settings` | Defaults used, message shown, value not overwritten at startup | Automated test plus manual check |
| Preset save, rename, delete | Presets change as expected; names unique ignoring case | Automated test of `PresetStore` |
| Preset export then import | Imported preset equals the exported one | Automated round-trip test |
| Preset import with bad input (malformed, truncated, wrong format, over 64 KB) | Rejected whole, saved presets unchanged | Automated test per case |
| Corrupt preset file | Renamed `.corrupt-<timestamp>`, app starts with no presets | Automated test plus file listing |
| Randomize | Values stay in the ranges; mascot settings unchanged | Automated test |
| Restore previous | Previous configuration restored, current one becomes previous | Automated test |
| Resize 1100 × 750 to 900 × 600 and back | No overlap, no exception, mascot stays inside the window | Screenshots at both sizes |
| Minimize and restore | Analysis stops while minimized; resumes on restore; CPU returns to baseline within 5 s | Task Manager or `Get-Process javaw` CPU seconds over 30 s |
| Unfocus and refocus | Frame rate drops to 15 fps while unfocused | Frame counter debug log (temporary), noted in the report |
| Visualizer toggle off | Backdrop shows no bars; analysis detached; playback unaffected | Manual check plus CPU comparison |
| Mascot toggle off | Mascot hidden; no timer running for it | Manual check plus thread dump |
| Both on, both off | Each combination behaves independently | Manual matrix of four runs |
| No audio data (stopped, simulated off) | Flat visuals, sleeping or sitting mascot; no exception | Manual check plus log |
| Playback error (corrupt file) | Mascot confused pose once; playback error line unchanged; queue advances | Manual check with a truncated file |
| Skip next and previous | Skipping pose plays; auto-advance does not | Manual check; `StartReason` in a temporary log |
| Mascot click | Reaction plays; no repeat of the same reaction twice in a row; no popup | Manual check, several clicks |
| Controls under the mascot at 900 × 600 | Every transport, seek, and volume control clickable | Manual click test, screenshot |
| Reduced motion on | No bob, sway, or blink; one-shot changes static | Manual check |
| Allocation during animation | No allocation per frame in steady state | JFR allocation sampling (JDK tool) during 60 s of playback |
| Build and tests | `:app:jar` and `./gradlew.bat test` pass with no new warnings | Gradle output |

## 9. Phased checklist

Phase numbers are local to this plan. Phase 0 is the design lock. Each phase must pass its verify items before the next starts.

### Phase 0 — Design lock and baseline

- [x] Owner decisions in section 10 are accepted or changed, and the changes are written back into this document.
- [x] Baseline CPU: record `javaw` CPU seconds over 30 s with music playing, music stopped, and window minimized (Task Manager or `Get-Process javaw`). Record numbers in section 11; do not estimate.
- [x] Baseline paint cost: measure `Backdrop.paintComponent` time at 1100 × 750 and 900 × 600 with a temporary timer around the call (not committed). Record numbers in section 11.
- [x] Grep confirms `takeLevel()` has one caller (`PlaybackController.onPosition`). Evidence: grep output.
- [x] Owner decision 10.1 on the AGENTS.md visual-component rule is recorded before any new package is created.
- [x] **Verify:** `./gradlew.bat :app:jar` and `./gradlew.bat test` succeed on the untouched baseline, so later phases have a clean comparison.

### Phase 1 — Shared audio feature feed (no visible change)

- [x] `SpectrumAnalyzer.analyze` writes into a caller-owned `FloatArray` instead of allocating one per chunk. Its existing behavior and test are unchanged.
- [x] `playback/AudioFeatures.kt`: two preallocated snapshots, swapped through one `@Volatile` reference. Energy comes from the same block as `measure`. Bass, mid, and treble are means of the band index groups in section 4 (the group bounds are pinned by a test).
- [x] `playback/WaveformBuffer.kt`: a ring holding the most recent 512 mono samples per chunk, written on the playback thread, copied out by readers into a caller array. (512 is a proposed size; see 11.3.)
- [x] `playback/BeatDetector.kt`: onset when bass energy exceeds its moving average (proposed window about 1 s) by a ratio (proposed 1.5), with a 250 ms refractory period and a pulse that decays over about 200 ms. All constants are named in one place.
- [x] Consumer counting: `attach()` and `detach()`. When the count is zero, waveform copy and beat detection are skipped. Playback still runs.
- [x] `playback/SimulatedFeatures.kt`: deterministic synthetic bands, energy, and beats for the preview while stopped. No audio thread involved.
- [x] Row pulse in the library and playlists still responds to playback (uses `PlayerState.level`, unchanged).
- [x] **Verify:** `SpectrumAnalyzerTest` extended: a 100 Hz sine lands in the bass group, a 4 kHz sine in the treble group, and silence gives zeros. Group bounds are asserted.
- [x] **Verify:** `BeatDetectorTest`: a 120 BPM click train yields one beat per click within one frame; a steady tone yields no beats after warm-up; the noise false-trigger count over 60 s of synthetic noise is recorded and below the stated limit.
- [x] **Verify:** Backdrop bars visually match in baseline and Phase 1 screenshots from the same FFmpeg-generated test clip at 3360 ms. Only 108 of 825,000 pixels differed (mean channel delta 0.000076; maximum 3). `:app:jar` completed without new Kotlin warnings; `test` passed.

### Phase 2 — Renderer interface and Classic Bars (behavior-preserving)

- [x] `visual/VisualizerRenderer.kt`: the interface from section 4, including an `update` method for timer-driven easing. Renderers have no timers.
- [x] `visual/ClassicBars.kt`: bar drawing moved out of `Backdrop.paintBars` with the same colors, halo layers, geometry, and easing constants.
- [x] Minimal `VisualizerMode` and `VisualizerSettings` contracts added so the renderer interface is typed; Phase 3 will add persisted settings.
- [x] `Backdrop` keeps the glows and the window fill, delegates bars to the active renderer, and attaches to `AudioFeatures`. It keeps one `Timer`.
- [x] Backdrop ticker checks visibility, detaches analysis while hidden or minimized, avoids repainting while not showing, and applies the focused/unfocused frame policy in section 6.
- [x] `:app:jar` and `test` pass.
- [x] **Owner visual check:** the owner confirmed the bars match at 1100 × 750 and ease flat on pause and stop (2026-11-14).
- [x] **Owner CPU check:** the owner confirmed playback stayed within the proposed 10 percent increase over the Phase 0 baseline (2026-11-14); no numeric CPU sample was supplied.

### Phase 3 — Settings model and persistence

- [x] `visual/VisualizerSettings.kt`: data class with `schemaVersion = 1`, all fields from section 6, and one table of defaults and ranges.
- [x] Per-mode relevance function. Tests assert that Waveform has no bar-count or particle-count control.
- [x] `visual/VisualizerStore.kt`: load from `visualizer.settings`, clamp values, fall back to defaults, keep corrupt values in memory only with a status callback, and treat a newer schema as read-only.
- [x] Debounced write (300 ms, non-repeating EDT `Timer`) and a flush on close through `AutoCloseable.close()`.
- [x] Previous-configuration slot: `visualizer.previous` is updated before the first change of an edit session. Restore swaps current and previous.
- [x] `visualizerEnabled` read and write, shared with the music plan.
- [x] Follow-accent: when `followAccent` is true, the primary color is read from `Palette.focus` at paint time, so accent changes apply without a settings write.
- [x] **Verify:** automated tests cover round trip, clamping, unknown-field handling, newer schema read-only, corrupt JSON, enabled preference handling, previous-slot swapping, and accent resolution. Reopening a store reads the saved current and previous settings.

### Phase 4 — Visualizer page with Classic Bars only

- [ ] `ui/VisualizerPage.kt` added to `MainWindow.kt`'s `names` and `contents` lists as "Visualizer".
- [ ] Live preview panel with its own single `Timer`. It attaches to `AudioFeatures` only while the page is showing. It uses `SimulatedFeatures` when stopped and the simulated toggle is on.
- [ ] Mode selector (names only in this phase; thumbnails are Phase 6 work if the owner keeps them, see 11.5).
- [ ] `ThemedSlider` added to `Theme.kt` (the existing code has no slider) and used for numeric controls. Controls are grouped Colors, Motion, Response, and Background.
- [ ] Reset (one setting and all), Restore previous, and Randomize (bounded ranges from section 6).
- [ ] Every change updates the preview on the next frame and updates the Backdrop.
- [ ] **Verify:** each Classic Bars control changes the preview visibly; controls for other modes are hidden.
- [ ] **Verify:** at 900 × 600 the page has no overlap (screenshot), and text is readable.

### Phase 5 — Presets

- [ ] `visual/PresetStore.kt`: file location and format from section 5, atomic writes with a `.bak` copy.
- [ ] Save as new, rename (unique ignoring case), delete (themed confirmation), and load (applies as a change and updates the previous slot).
- [ ] Export one preset to a chosen JSON file.
- [ ] Import one or more presets from a chosen JSON file, with the validation rules in section 7 and the collision suffix.
- [ ] Corrupt preset file: renamed `.corrupt-<timestamp>`, app starts with no presets, message names the file.
- [ ] **Verify:** automated round-trip (export, delete, import, compare equal). Automated rejection tests for malformed, truncated, wrong-format, and over-64 KB files, each leaving saved presets unchanged.
- [ ] **Verify:** corrupt preset file recovery (automated test plus file listing).

### Phase 6 — Remaining visualizer modes

Each mode is a separate item. A mode is done only when its verify items pass.

- [ ] **6a Circular Spectrum:** bands arranged on a circle, radius from `min(width, height)`, precomputed sin and cos table per bar count.
- [ ] **6b Waveform:** `WaveformBuffer` samples drawn as a smoothed polyline (3-point moving average, proposed). Flat line when inactive.
- [ ] **6c Particle Visualizer:** fixed pool (maximum 400, from section 6), preallocated particle objects, spawn on beat and bass, count and size controls, cleared on `reset()`.
- [ ] **6d Audio Ring:** ring radius from energy, deformed by 32 band values, glow from layered strokes.
- [ ] **6e Minimal:** baseline line and one energy dot. Lowest-cost mode.
- [ ] **Verify (each mode):** visible response to a real track; holds or decays on pause; shows only its relevant controls.
- [ ] **Verify:** `jcmd <pid> Thread.print` before and after 10 mode switches shows no new threads; JFR allocation sampling during 60 s of steady playback shows no per-frame allocation in the renderers.
- [ ] **Verify:** low-end settings (30 fps, 40 particles, bar count 16) run with CPU recorded in section 11.

### Phase 7 — Visibility, resize, and focus behavior

- [ ] Backdrop, preview, and mascot stop their tickers when not showing. Analysis detaches when the consumer count reaches zero.
- [ ] Minimized window: no repaints and no analysis. Checked by a temporary frame counter.
- [ ] Unfocused window: 15 fps (proposed in 10.6).
- [ ] Resize: renderers take their bounds on every frame. Per-size tables are rebuilt only when size changes.
- [ ] **Verify:** minimize and restore, unfocus and refocus, and resize between 1100 × 750 and 900 × 600. No exception, no stuck animation, CPU back to baseline within 5 s.

### Phase 8 — Mascot core

- [ ] `playback/PlaybackEvent.kt`: sealed events. `PlaybackController` emits `TrackStarted(track, reason)`, `Paused`, `Resumed`, `Stopped`, and `Failed`. Existing listeners are unchanged. `advance(automatic)` gains a `StartReason` so skip and auto-advance can be told apart.
- [ ] `mascot/MascotMachine.kt`: state table with states Asleep, Waking, Sitting, Playing (Calm or Energetic), Paused, Skipping, Confused, and Greeting. One-shot states end by frame count on the mascot's own ticker.

```text
Asleep    --TrackStarted-->            Waking --(frames)--> Playing
Playing   --Paused-->                  Paused
Paused    --Resumed-->                 Playing
Playing   --Stopped-->                 Sitting --(sleepAfterSeconds)--> Asleep
Paused    --Stopped-->                 Sitting
Any       --TrackStarted(SKIP_*)-->    Skipping --(frames)--> prior-or-Playing
Any       --Failed-->                  Confused --(frames)--> prior
Any       --click-->                   Greeting --(frames)--> prior
Sitting   --TrackStarted-->            Waking
Energetic is entered from Calm when features.energy or beat stays above a threshold for 1 s (proposed)
```

- [ ] `mascot/MascotView.kt`: Java2D drawing. Head bob on `beat`, ears and tail sway tied to `bass`, blink on a seeded random interval, eyes and mouth per state.
- [ ] `MainWindow.kt`: content pane wrapped in a `JLayeredPane`; mascot placed in a reserved margin at the right end of `PlayerBar`. The margin width is decided in this phase after reading `PlayerBar` layout (not yet inspected for free space).
- [ ] Mascot settings from section 6, shown on the Visualizer page in a Mascot group (decision 10.2).
- [ ] Idle timer: after `Stopped`, the mascot sits, then falls asleep after `sleepAfterSeconds`. A new `TrackStarted` wakes it.
- [ ] **Verify:** automated state-table test covering each transition, including ignored events for states that do not accept them.
- [ ] **Verify:** reduced motion has no bob, sway, or blink, and one-shot reactions become static expression changes.
- [ ] **Verify:** at 900 × 600 the mascot sits in its margin with no overlap on any control (bounds test plus screenshot).

### Phase 9 — Mascot interaction and polish

- [ ] Click reactions: wave, meow (mouth shape only, no sound), and happy eyes. Round-robin with no immediate repeat. Cooldown of 1.5 s (proposed) so clicks cannot stack animations.
- [ ] Hit test on the drawn shape only.
- [ ] Right-click menu contains only Hide and Sleep now. No dialogs, no popups, no focus changes.
- [ ] Mascot variants (`TABBY`, `BLACK`, `WHITE`) selectable in settings.
- [ ] **Verify:** manual click test of every transport, seek, and volume control with the mascot visible at 900 × 600.
- [ ] **Verify:** CPU with the mascot on versus off during playback, numbers recorded in section 11.
- [ ] Optional (10.10, default deferred): draggable mascot within window bounds, saved position clamped on resize.

### Phase 10 — Documentation and release

- [ ] `README.md`: Visualizer page, presets and their file location, mascot settings, reduced motion, and the requirements for each feature.
- [x] `AGENTS.md`: visual-components rule and package layout updated in Phase 0 after the owner accepted decision 10.1.
- [ ] `DOCS/music-player-devplan.md`: link this plan from its visualizer and Settings items. This needs owner approval, since this plan does not edit that file.
- [ ] **Verify:** `./gradlew.bat :app:jar` produces `app\build\libs\app-all.jar`; `javaw -jar app\build\libs\app-all.jar` opens the window; the final report lists every verification with its evidence type.
- [ ] Do not commit or push unless the owner asks.

## 10. Decision record (recommended defaults)

**Phase 0 decisions (2026-11-14):** The owner accepted the recommended defaults for 10.2–10.10. For 10.1, the owner chose separate `visual/` and `mascot/` packages, with shared Swing primitives and the `Backdrop` shell in `Theme.kt`. `AGENTS.md` was updated to match before any new package is created.

- **10.1 AGENTS.md rule conflict.** Decision: use new `visual/` and `mascot/` packages; keep shared primitives such as `ThemedSlider` and the `Backdrop` shell in `Theme.kt`. The alternative was to put everything in `Theme.kt`, which is already 1328 lines. Owner decision recorded 2026-11-14.
- **10.2 Where mascot settings live.** There is no Settings dialog yet. Default: a Mascot group on the Visualizer page. Move when the Settings dialog exists (music plan Phase 5).
- **10.3 Preset file location.** Default: `%APPDATA%\CatoTube\visualizer-presets.json`. Preferences cannot hold a large preset list reliably (8192-character value limit). Alternative: next to `library.json` in the library root, which ties presets to one library.
- **10.4 Mascot variants.** Default: three palettes (tabby, black, white), fixed and independent of the accent color. Alternative: derive the palette from the accent.
- **10.5 Simulated signal while stopped.** Default: toggle on the Playground preview, off by default, never fed to the window background. Reduced motion also lowers glow changes.
- **10.6 Frame-rate policy.** Default: 30 fps focused, 15 fps unfocused, off when minimized or not showing. 60 fps only in the Playground preview. Owner may choose 60 fps for the Backdrop too, but Phase 7 measurements decide whether that is affordable.
- **10.7 Sleep timeout.** Default 180 seconds, range 30 seconds to 30 minutes.
- **10.8 Randomize bounds.** Default: the ranges in section 6. Randomize never changes the mascot.
- **10.9 Double rendering.** When the Visualizer page is open, the Backdrop and the preview draw the same mode. Default: accept, and let the Backdrop drop to 15 fps while the preview is showing if Phase 7 measurements show it is needed.
- **10.10 Draggable mascot.** Default: deferred. Not part of v1 unless the owner asks for it.

## 11. Open tuning points and measurements

Measured values are recorded here during Phase 0 and later phases. Do not enter estimates.

- **Baseline CPU (Phase 0, 2026-11-14):** Windows, `Get-Process javaw` CPU-time deltas over measured 30 s windows. Playing: 42.9219 CPU s / 30.119 s wall time (142.51%). Stopped: 0.2188 CPU s / 30.106 s (0.73%). Minimized while playback continued: 0.5000 CPU s / 30.013 s (1.67%).
- **Baseline `Backdrop` paint time (Phase 0, 2026-11-14):** off-screen Java2D `BufferedImage`, 500 warm-up calls and 3,000 measured `paintComponent` calls per size, with all 32 bands at 0.65. At 1100 × 750: mean 17.2599 ms, p50 16.7417 ms, p95 19.8467 ms. At 900 × 600: mean 11.8165 ms, p50 11.5955 ms, p95 12.6135 ms. The probe was temporary and not committed; measurements isolate the method and do not include full-window painting or display compositing.
- **11.1 Bar count above 32.** The analyzer has 32 bands. Counts above 32 must either interpolate between bands or add a second analyzer pass. Interpolation is the default, since it avoids a second pass (see section 1, one analysis feed). Tune after the Phase 4 visual check.
- **11.2 Beat detector constants.** Starting values: 1 s exponential moving-average window, 1.5 onset ratio with a 0.12 minimum energy increase, 250 ms refractory period, and linear 200 ms pulse decay. The 120 BPM test detected all 8 clicks within one 23 ms frame; steady bass produced no retriggers after warm-up. Deterministic 60 s white noise produced 0 false triggers (limit: fewer than 2). Tune on authorized real tracks after Phase 8.
- **11.3 Waveform sample count.** 512 proposed. Tune in Phase 6b.
- **11.4 Particle pool size and spawn rate.** 400 maximum, rate from `particleCount`. Tune in Phase 6c against the CPU numbers.
- **11.5 Mode thumbnails.** The owner's original prompt asks for visual previews where feasible. Default: text names in Phase 4, thumbnails added in Phase 6 if the cost is small (rendered once at startup, cached). Owner decision after Phase 6.
- **11.6 Tuning reference.** Values in section 6 are starting points. Each one changes only after a visual or measured reason is written here.
