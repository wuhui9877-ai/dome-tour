# 赛博穹顶漫游 — PICO Space Program 2026, Spatial track

A Kotlin + Jetpack Compose + PICO Spatial SDK app for PICO OS 6.
Team: 零号空间.

**环形穹顶城 · 昼夜漫游** — an original miniature dome city in a floating
window. A courier drone patrols a ring road through four invented districts
（雾港 / 灯市 / 弦塔 / 静庭） under a translucent dome that shifts with a
day-night cycle. The city is built entirely from procedural low-poly geometry
generated in code (no model files, no 3D engine).

## What the app does

- **Main window**: the 3D diorama in a `SpatialView`, with a title row,
  district chips, and tour / day-night / zoom controls.
  - Drag on the diorama to orbit (yaw + pitch); pinch to zoom. On-screen
    缩小 / 放大 / 复位视角 buttons do the same without gestures, and keep
    working even if spatial gestures fail.
  - The dome tint, window-light glow, and backdrop follow the day-night
    phase （晨 / 昼 / 暮 / 夜）, auto-cycling on a ~60s loop.
  - The courier drone, its spinning rotors, and the bobbing moored
    airships are animated from Compose frame callbacks (`withFrameNanos`),
    once per frame, in UI-layer code — deliberately not from an ECS
    system, whose per-frame ticking inside `SpatialView` scenes cannot be
    verified here.
- **城区介绍 subwindow**: the selected district's flavor text and facts.
- **导览 subwindow**: the 4-stop courier route timeline, tour play/pause,
  and day-night controls. While the tour plays, the selection advances one
  stop every 8s, the district highlight ring moves, and the drone flies;
  pausing parks the drone in place.
- **Selection highlight**: the selected district's glow brightens and a
  glowing ring appears under it.
- **Failure fallback**: if the 3D scene fails to initialize, the main
  window still shows its title, controls, and subwindow buttons, with a
  fallback message in the diorama area instead of a blank window.

## Code layout

- `ui/DomeTourPage.kt` — main window, controls, both subwindows, tour and
  day-night timers, gesture handling, the `withFrameNanos` animation loop,
  and the scene-failure fallback.
- `ui/DistrictData.kt` — district definitions (names, palettes, copy) and
  day-night phases. All content is original to this project.
- `scene/CityScene.kt` — scene construction: base, ring road, districts,
  dome, highlight ring, drone (with rotor handles), airships (grouped under
  bobbing parent entities); `applyState` maps UI state onto the scene and
  `applyAnimation` maps per-frame animation angles onto the drone, rotors,
  and airships.
- `scene/Meshes.kt` — procedural mesh builders (box, cylinder, sphere,
  hemisphere, disc, ring).
- `scene/Materials.kt` — unlit material factories (opaque / transparent /
  additive glow / wireframe lattice) and mesh-resource creation.

## Build (this VM)

```sh
source ~/workspace/pico/env.sh
cd ~/workspace/pico/dome-tour
~/workspace/pico/gradle-8.10.2/bin/gradle assembleDebug --offline --no-daemon --console=plain
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Environment notes:

- JDK 17 (Temurin), Android SDK (API 35, build-tools 35.0.0) and pico-cli are
  installed user-local under `~/workspace/pico/`; see `../env.sh`.
- The VM has no nested virtualization: build-only here. No runtime
  verification is planned: the developer declined emulator use, so every
  runtime behavior below is unverified. The only remaining verification
  path would be an on-device debugging session (Beijing/Shanghai/
  Guangzhou), if one is arranged later.
- The sandbox intercepts all TCP connections from Java processes (it injects
  a policy message into the stream), so Gradle cannot use its daemon
  (daemon IPC breaks) or download dependencies. Builds therefore run
  `--no-daemon` in-process, Kotlin compiles in-process
  (`kotlin.compiler.execution.strategy=in-process`), and dependencies resolve
  `--offline` from the local Maven mirror at `../maven-mirror/`, populated by
  `../mirror-deps.py` (downloads via curl, which is unaffected).
- On a normal machine (or if the sandbox's "Direct network protocols /
  other_tcp" permission is enabled), the remote repositories declared in
  `settings.gradle.kts` work and the mirror is unnecessary.

## Verified vs unverified

Verified (this VM, `aapt2 dump badging` + manifest inspection):

- The project compiles, links, dexes, and packages; the APK declares the
  `dev.zero0space.dometour` package, the 赛博穹顶漫游 label, the launchable
  activity, and the `pico.spatial.windowcontainer` metadata block.
- Source-level checks: no references to any other work, no leftover
  package names, all materials unlit (no lighting dependency).

Unverified — everything at runtime. Nothing in this project has ever run
in an emulator or on a device, and no run is planned. If an on-device run
happens, it should check, in order:

1. The app launches without crashing and the main window appears.
2. The diorama renders: ground, buildings, dome shell + lattice, drone.
   (If the scene is empty, the prime suspects are `MeshResource` /
   `replaceWithMeshModel` behavior and `SpatialView` scene setup. If the
   scene fails to build, the fallback message should appear and the
   controls should stay usable.)
3. Drag orbits, pinch/buttons zoom, 复位视角 restores the view. Drag
   sensitivities (`DRAG_YAW_SENSITIVITY` / `DRAG_PITCH_SENSITIVITY` in
   `DomeTourPage.kt`) are guesses. If gestures fail entirely, the
   on-screen buttons must still work.
4. The drone flies while the tour plays and parks when paused; rotors spin
   while flying; airships bob continuously. All three are driven from the
   `withFrameNanos` loop in `DomeTourPage.kt` — if nothing moves, the
   frame loop or the transform writes are the suspects, not an ECS system
   (none remains).
5. Day-night phase changes re-tint the dome and glow; the highlight ring
   follows the selected district.
6. Both subwindows open, show live content, and close.
7. Framing/scale of the diorama inside the window: adjust `ROOT_Y_OFFSET`
   and the layout constants in `CityScene.kt` if the city is off-center or
   the wrong size.

Known simplifications / deliberately not done:

- All materials are unlit (flat colors + additive glow), so no lighting
  setup is needed. Physically-based materials + lights were deliberately
  NOT adopted: without a runnable device to check lighting, a PBM scene
  could render black.
- District selection is via chips / timeline buttons; tap-to-select on the
  3D diorama itself (raycast + `InteractableComponent`) was deliberately
  NOT added: it cannot be verified without a run, and a broken raycast
  path could make selection worse, not better.
- The drone's yaw convention and the 弦塔 string yaw math assume a
  right-handed Y-up space; flip the sign if strings/drone face sideways.
- Name-tag attachment panels are not implemented.
- No ECS systems remain: the drone patrol system was removed in phase 3
  in favor of the Compose frame-callback loop (see above).

## Attribution

Project structure, manifest WindowContainer metadata, theme, `Subwindow`
usage, `SpatialView` usage, and spatial gesture handling follow the
official PICO samples, licensed Apache 2.0.
See ATTRIBUTION.md. All diorama geometry, district content, and UI copy are
original to this project.
