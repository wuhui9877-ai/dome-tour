# Attribution

This project reuses structure and patterns from the official PICO samples:

- PICO Spatial SDK Sample — Spatial UI
  https://github.com/Pico-Developer/PICO-Spatial-SDK-Sample-SpatialUI
  Copyright 2025 - 2026 PICO. Licensed under the Apache License, Version 2.0
  (http://www.apache.org/licenses/LICENSE-2.0).
- PICO Spatial SDK Sample — Welcome Space
  https://github.com/Pico-Developer/PICO-Spatial-SDK-Sample-WelcomeSpace
  Copyright 2025 - 2026 PICO. Licensed under the Apache License, Version 2.0
  (http://www.apache.org/licenses/LICENSE-2.0).

What was adapted from the samples (not copied verbatim, except where noted):

From the Spatial UI sample:

- Gradle setup: repository list (Google / Maven Central / ByteDance
  Volcengine artifact repo), AGP 8.8.2 / Kotlin 2.0.0 / Spatial BOM 6.2.8
  versions, and the Compose-UI resolutionStrategy excludes.
- App entry pattern: `SpatialApplication` calling `launch(::mainApp)`,
  `LaunchActivity : SpatialLaunchActivity()`, `DefaultWindowContainer` +
  `PicoTheme` in `mainApp`.
- AndroidManifest WindowContainer `<meta-data>` block (copied with comments).
- `Theme.SpatialApp` style definition.
- `Subwindow` usage pattern (state-driven floating windows).

From the Welcome Space sample:

- `SpatialView` usage pattern (`initial` / `update` / `attachments`
  lambdas, `content.addEntity`, per-recomposition transform updates).
- Spatial gesture handling: `detectSpatialDragGesture` /
  `detectSpatialScaleGesture` via `pointerInput`, with drag/scale state
  applied to the scene root's `TransformComponent` in the `update` lambda.
- Custom ECS system pattern (a `Component` subclass with a `clone()`
  override plus a `System` subclass querying entities per frame, cf. the
  sample's `RotationComponent` / `RotationSystem`): used in an earlier
  revision to drive the drone patrol, then removed in phase 3. Animation is
  now driven from Compose frame callbacks (`withFrameNanos` in
  `DomeTourPage.kt`), which does not depend on ECS systems ticking inside
  `SpatialView` scenes. No ECS system code remains in the project.

All diorama geometry (procedural meshes, city layout, dome, drone), all
district names, palettes, flavor text, and facts, all UI copy, and the
launcher icon are original to this project.
