# RecruiserTwo — Android 3D Model Player & Navigator

> **Status (2026-10-18):** Early-stage, Android-only 3D viewer. This README describes the checked-in implementation, not aspirational 4D features. Runtime, build, and device performance have not been independently verified.

RecruiserTwo is a Kotlin/Jetpack Compose Android application for importing, viewing, and navigating 3D meshes and point clouds. It offers a telemetry-inspired viewer, procedural sample scenes, orbit and zoom controls, configurable rendering, and saved camera viewpoints. A **4D Living Gaussian Splat World Builder** is proposed separately; it is **not yet implemented**.

## Presently implemented in source

- Import local **GLB** and **PLY** files through the Android content picker; copy selected files into application storage.
- Parse meshes/point clouds through `Scene3DEngine.kt`, with built-in procedural station and LiDAR-like corridor examples.
- Render a projected 3D scene using **Jetpack Compose Canvas**; select solid, wireframe, or point-display modes.
- Orbit, zoom, reset view, optionally auto-rotate, toggle spatial grids, and adjust lighting, background, and point size.
- Save and revisit camera viewpoints with Room persistence.
- Display model statistics and viewer controls in a Compose sidebar/HUD.

**Clarification:** Point-cloud circles in the current renderer are **not** anisotropic 3D Gaussian splats. The source also includes `app/src/main/assets/viewer.html`, a separate Three.js-based viewer prototype using CDN scripts, but the current Compose screen calls the native `ThreeDCanvas` rather than using that HTML as its primary renderer.

## Architecture at a glance

```text
MainActivity
  └─ MainViewerScreen (Jetpack Compose)
       ├─ ThreeDCanvas (software-style canvas projection/rendering)
       │    └─ Scene3DEngine (procedural scenes, GLB/PLY parsing)
       └─ ModelViewModel (StateFlow, camera/navigation/import state)
            └─ ModelRepository
                 └─ Room: ModelItem + SavedViewpoint
```

Key files:

| Area | Source |
| --- | --- |
| App entry | `app/src/main/java/com/example/MainActivity.kt` |
| Main UI | `app/src/main/java/com/example/ui/viewer/MainViewerScreen.kt` |
| Compose viewport | `app/src/main/java/com/example/ui/viewer/ThreeDCanvas.kt` |
| Geometry / parsers | `app/src/main/java/com/example/ui/viewer/Scene3DEngine.kt` |
| State and import | `app/src/main/java/com/example/ui/viewer/ModelViewModel.kt` |
| Database | `app/src/main/java/com/example/data/db/AppDatabase.kt` |
| Legacy/alternate Web viewer | `app/src/main/assets/viewer.html` |
| Gradle application config | `app/build.gradle.kts` |

## Build and run

1. Install Android Studio with a compatible Android SDK, Gradle toolchain, and JDK (project targets Java 11 source compatibility). Open the repository root as an Android Gradle project.
2. Sync Gradle and ensure Android SDK platform **36** plus the minor API requirements declared in `app/build.gradle.kts` are installed. The application declares **minSdk 24** and **targetSdk 36**.
3. Run the `app` configuration on an emulator or Android device. Application ID: `com.aistudio.spatialmodelplayer.zpkfwv`.
4. For a command-line development build, run `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows), assuming your local Android SDK, Gradle wrapper, and signing configuration are present.
5. Release signing references environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` and expects an alias `upload`. Configure these **outside version control** before a release build. The debug signing configuration refers to a project-local `debug.keystore`; verify its presence or adjust debug signing before relying on a debug build.

The Gradle file applies Google Services, Firebase-related libraries, and a secrets plugin, but this does **not** establish that cloud services are essential to the core viewer, nor that Firebase is fully configured. Inspect integration requirements before publishing. Do not commit secrets or signing keys.

## Basic usage

Launch the app, select one of the procedural sample environments, and drag to orbit or pinch to zoom. Open the sidebar to switch model/rendering settings, import a GLB/PLY file, or save a viewpoint. The navigation controls provide zoom and camera reset. Imported media remains in the application's local storage.

## Proposed next product: 4D Living Splat World Builder

The separate implementation plan proposes ingestion of regular/360-degree stills and video, pose estimation using available metadata/features, manually editable vignette placement, time-varying splat rendering, multiple simultaneous ambient animations, and procedural/generative synthesis of uncaptured connections.

**Not implemented in this repository as reviewed:**

- Real 3D Gaussian splatting with covariance-aware GPU rendering or an independently validated splat file pipeline.
- 4D/deformable splat training, video-to-4D reconstruction, synchronized multi-vignette animation, or verified 60 FPS on Quest/Android.
- Still/video/360 ingestion and camera-pose recovery by COLMAP or equivalent.
- Depth-estimation models, optical flow, world-coordinate registration, or reconstruction-confidence metadata.
- Generative 3D inpainting of missing corridors, decks, or hull structures.
- A world/vignette Room schema, editable spatial anchor graph, or a cross-platform/Quest build.

**Technical distinction:** Extruding an image through a heuristic depth map is useful for visual prototyping but is not equivalent to photogrammetrically recovered geometry. Similarly, interpolating colors/points between disconnected scenes is not evidence that missing architecture has been reconstructed. Label inferred content clearly.

## Suggested implementation sequence (not yet performed)

1. Create and freeze `DOCS/ARCHITECTURE.md` as the current/proposed-state digital twin, then `CHECKLIST.md` as the executable, testable recipe, before code changes. Give both files comprehensive metadata (project, owner, status, revision, date, upstream commit, phase, executor/model, inputs, outputs, acceptance evidence, dependencies).
2. Isolate scene storage, rendering, camera transforms, import/conversion, and animation behind interfaces while retaining the functioning GLB/PLY viewer.
3. Prove a real GPU-based **static 3DGS** loader/renderer on one supported Android device; measure memory, frame time, splat count, thermal behavior, and correctness.
4. Add a versioned spatial world/anchor data model with provenance and manual transforms; test multi-vignette placement and save/reload.
5. Introduce validated image/360/video ingestion and explicit quality tiers: panorama shell, depth-lifted proxy, photogrammetric reconstruction, and fitted/animated splats.
6. Implement time-domain animation/loop transitions and concurrency, including GPU/memory budgets and degradation policies.
7. Add user-approved inferred bridges with uncertainty/provenance overlays; treat photorealistic synthesis as a separate research milestone, not a guaranteed local feature.
8. Add repeatable instrumented device tests, supported-hardware matrix, crash reporting (opt-in as appropriate), and a performance gate before making Quest/60-FPS claims.

## Current limitations and cautions

- The Compose Canvas point renderer sorts projected points on the CPU, and is not a GPU splat renderer. Large reconstructions will require a different rendering backend.
- The alternate HTML viewer loads external libraries from CDNs, so it is **not offline-independent** as written; the native Compose path is distinct.
- File parsers should be tested with malformed/large assets, unsupported GLB extensions, diverse PLY encodings, and resource pressure before production use.
- Room schema changes require deliberate migration planning; do not silently destructively reset user libraries.
- The project currently uses generic package/Gradle naming in places. Establish stable application identity and signing strategy before distribution.
- Existing tests do not constitute verified end-to-end world-builder coverage.

## Contributions and development policy

All changes to application code or data schemas should be specified **first** in `DOCS/ARCHITECTURE.md`, then decomposed into independently assignable, verifiable operations in `CHECKLIST.md`. Freeze the relevant revision before delegating implementation. Record tests, observed performance, and limitations instead of reporting proposed functionality as complete.

Repository: https://github.com/rebots-online/RecruiserTwo
