# CatoTube — Visualizer Playground & Interactive Cato Mascot

I want to add two major features to CatoTube:

1. **Visualizer Playground** — a fully customizable, audio-reactive visualizer system.
2. **Interactive Cato Mascot** — a cute, animated cat that reacts to music and playback events.

GitHub repository: https://github.com/ucwxcato/cato-tube

**IMPORTANT: Planning phase only. Do not write or modify any code yet.**

First, thoroughly inspect the existing project, its architecture, UI framework, audio playback implementation, current visualizer, settings system, and performance considerations.

Base your plan on the actual codebase. Do not assume frameworks, libraries, APIs, or capabilities without verifying them.

## Feature 1: Visualizer Playground 🎶

I want to expand the existing audio visualizer into a customizable system where users can choose different visualizer styles and tweak their appearance and behavior.

### Visualizer modes

Plan support for the following modes:

- **Classic Bars:** Traditional frequency spectrum bars with adjustable width, spacing, and height.
- **Circular Spectrum:** Frequency bars arranged around a circle, reacting outward to the music.
- **Waveform:** A smooth, animated representation of the audio signal.
- **Particle Visualizer:** Particles that move, pulse, and react to bass and overall energy.
- **Audio Ring:** A glowing ring that expands, contracts, and deforms with the music.
- **Minimal Mode:** A lightweight, subtle visualizer for users who prefer a cleaner interface.

Reuse and extend the existing visualizer wherever practical rather than replacing working functionality unnecessarily.

### Customization settings

Users should be able to customize:

- Visualizer mode
- Primary and secondary colors
- Option to follow the app's accent color
- Sensitivity and amplitude
- Smoothing
- Animation speed
- Glow intensity
- Bar count, width, and spacing where applicable
- Particle count and size where applicable
- Background transparency
- Optional bass emphasis
- FPS limit (30/60, if practical)
- Enable/disable visualizer

Only show settings relevant to the selected mode.

### Visualizer Playground UI

Create a dedicated customization interface that includes:

- A live visualizer preview
- A mode selector with visual previews if feasible
- Organized customization controls
- Reset-to-default buttons
- Presets users can save, rename, delete, import, and export
- A randomize button for generating interesting visualizer configurations
- An option to restore the previous configuration

Changes should appear immediately in the preview.

The preview should use the real audio signal when music is playing, with an optional simulated signal for customization while playback is stopped.

### Audio responsiveness

The visualizers should react to actual audio data rather than relying exclusively on random animations.

Investigate how the current audio system can provide:

- Frequency spectrum data
- Bass, midrange, and treble energy
- Overall audio amplitude
- Waveform samples

If the existing audio pipeline cannot expose some of these efficiently, propose realistic alternatives and explain their tradeoffs.

Avoid expensive duplicate audio processing for each visualizer.

### Performance requirements

- Smooth animation without blocking the UI thread
- Efficient rendering and audio processing
- Avoid unnecessary allocations during animation
- Proper cleanup when switching modes
- No memory leaks or orphaned animation loops
- Reasonable CPU usage while idle
- Automatically pause unnecessary rendering when hidden
- Graceful handling of low-end systems

## Feature 2: Interactive Cato Mascot 🐱

Add an optional, animated cat mascot that gives CatoTube personality.

The mascot should be cute, expressive, and customizable without becoming distracting.

### Mascot behavior

The mascot should react to application and playback states.

**While music is playing:**
- Bob its head to the beat
- Move its ears or tail
- Occasionally blink
- React more energetically during louder sections
- Have subtle idle movements between stronger beats

**When paused:**
- Relax into a calm idle pose
- Occasionally blink or look around

**When stopped:**
- Sit down or fall asleep after a configurable idle period

**When skipping tracks:**
- Perform a short surprised or playful animation

**When a song starts:**
- Wake up or become excited

**When an error occurs:**
- Show a brief confused expression

**When the user clicks the mascot:**
- React with a small animation, such as a wave, meow, or happy expression

The mascot should not constantly interrupt the interface or display excessive popups.

### Mascot customization

Allow users to configure:

- Enable/disable mascot
- Mascot size
- Mascot position
- Animation intensity
- Beat-reactivity
- Idle animation frequency
- Optional appearance variants
- Reduced-motion mode
- Sound effects on/off, default off

Consider supporting draggable positioning if it fits the existing UI architecture.

### Mascot design

The mascot should have a recognizable visual identity.

Prefer a lightweight animation approach appropriate for the project's existing UI framework.

Investigate whether the best solution is:

- Programmatically drawn/vector-based mascot
- Sprite-sheet animation
- Layered image animation
- Another lightweight approach compatible with the current codebase

Recommend one primary approach based on maintainability, performance, visual quality, and customization potential.

Do not assume that external animation libraries are necessary.

### Shared audio-reactive system

Ideally, the mascot and visualizers should share audio analysis data.

Design a reusable system that provides information such as:

- Current playback state
- Overall audio energy
- Bass intensity
- Beat events or approximate beat detection
- Track changes
- Playback errors

The mascot should not independently analyze the audio if the visualizer already performs that work.

Beat detection should be lightweight and reasonably stable, avoiding excessive false triggers.

## Integration and UX

Both features should feel native to CatoTube.

Requirements:

- Respect the existing theme and accent color system
- Follow existing UI conventions
- Persist settings between launches
- Avoid interfering with playback controls
- Work independently: users can enable either feature or both
- Maintain compatibility with the existing visualizer
- Avoid breaking current functionality
- Handle missing audio data gracefully

Consider how the mascot and visualizer should behave when the window is minimized, resized, or unfocused.

## Architecture and maintainability

I want a clean implementation that can be expanded later.

Please investigate and propose:

1. A shared audio-analysis service, if appropriate.
2. A modular visualizer interface supporting multiple renderers.
3. Separate visualizer and mascot settings models.
4. A mascot animation state machine.
5. An event-driven approach for playback reactions.
6. A preset serialization format with versioning.
7. Clear separation between audio processing, rendering, and UI controls.

Avoid overengineering. Reuse existing infrastructure wherever possible.

## Deliverables — Planning Only

After inspecting the repository, provide:

### 1. Current architecture analysis
Explain how the existing audio playback, visualizer, settings, and UI systems work.

Reference relevant files, classes, and methods.

### 2. Feasibility assessment
Identify what is straightforward, what requires refactoring, and what might be technically difficult.

Explicitly flag limitations in accessing audio data.

### 3. Proposed architecture
Describe the components/classes/interfaces to introduce or modify, their responsibilities, and how data flows between them.

### 4. Visualizer Playground design
Describe the proposed UI layout, controls, rendering approaches, and preset system.

### 5. Mascot design
Recommend the mascot rendering technique, animation states, transitions, and interaction handling.

### 6. Implementation roadmap
Divide development into small, independently testable phases.

For each phase include:
- Goals
- Exact files expected to change
- Dependencies
- Implementation steps
- Potential risks
- Acceptance criteria

Prioritize establishing the shared audio-reactive infrastructure before implementing complex animations.

### 7. Performance strategy
Explain how to keep both systems responsive while minimizing CPU, GPU, and memory usage.

### 8. Testing strategy
Cover playback transitions, audio responsiveness, settings persistence, preset import/export, resizing, minimizing, and feature toggles.

### 9. Decisions requiring my input
List genuine design decisions that need my approval, with recommended defaults.

### 10. Final implementation checklist
Provide a checklist that can be followed phase by phase.

## Important constraints

- **DO NOT IMPLEMENT ANYTHING YET.**
- Do not modify existing files.
- Do not install dependencies.
- Do not introduce unnecessary frameworks.
- Do not remove existing functionality.
- Do not invent existing classes or APIs.
- Prefer incremental changes over large rewrites.
- Clearly distinguish verified repository facts from recommendations.
- If the existing architecture makes a requested feature impractical, explain why and propose an alternative.

**Final goal:** Make CatoTube feel like a playful, highly customizable personal music player, with beautiful audio-reactive visuals and an adorable mascot that feels alive while music plays.

Start by inspecting the codebase, then produce the detailed implementation plan. Wait for my approval before writing code.