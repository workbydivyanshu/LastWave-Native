# LastWave-Native Upstream Sync & Architecture Rules

This repository is LastWave-Native (fork: `https://github.com/workbydivyanshu/LastWave-Native`, upstream: `https://github.com/Clash-Projects/LastWave-Native`).
Aubade, YesPlayMusic, and LastWave-Native are personal music player twins maintained by Vianca.

## Core Directives

1. **100% Upstream Identity**:
   - Never rebrand LastWave. Keep application name "LastWave", package ID `com.lastwave.app`, logos, and upstream styling intact.
   - Do not rename product strings, branding assets, or package hierarchy to Aubade or anything else.
2. **Zero Telemetry**:
   - Permanently strip or block any third-party analytics, tracking, or remote metric beacons.
   - No data leaves the device without explicit user request.
3. **Local-First & Offline Architecture**:
   - Provide full local playback parity: Android MediaStore audio indexer, custom Storage Access Framework (SAF) folder picker, embedded tag metadata parser, sidecar `.lrc` and embedded lyrics priority, offline scrobbling queue, and bit-perfect USB DAC support.
4. **Harness Separation**:
   - **Antigravity**: Chief Architect, Planner, and QA Verifier. Author of architecture docs, Obsidian vault specs, OpenCode handshake prompts, and verification gates.
   - **OpenCode**: Lead Implementer and Coder. Executes wave tasks, writes clean Kotlin/Compose/C++ code, runs gradle builds and unit tests.
5. **Git Branch & Release Flow**:
   - All feature work and upstream adaptations land on `mainline` first.
   - Once a wave or batch is built, tested (`./gradlew testDebugUnitTest`), and verified, `mainline` is fast-forward merged to `main` (`git merge --ff-only mainline`).
   - Tags are created on `main` following upstream semantic versions (`vX.Y.Z`).

## Upstream Tracking

- Baseline upstream commit: `55b6bb8` (v4.2.4).
- Upstream remote: `upstream https://github.com/Clash-Projects/LastWave-Native.git`.
- Clean-room porting: inspect upstream diffs, port only relevant bug fixes or desired features into `mainline` as discrete, reviewable commits. Never blind-merge upstream branches.
