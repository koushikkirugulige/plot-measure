# Plot Measure — Android prototype 0.2.1

An Apple Measure–inspired Android app with live-camera Distance, manual Height and Plot area modes. This is an original implementation using Google ARCore, not affiliated with Apple. It establishes scale through a live AR session; importing an arbitrary still photo is not implemented.

## Install

Use `PlotMeasure-0.2.1-release.apk`. This build is **not debuggable** and uses the same private release signing certificate as 0.2.0, so it can be installed as an update over that version. **Uninstall the old 0.1.x debug app once before installing a release.** Android will reject installing it directly over the differently signed debug build. Uninstalling clears the old app's settings, session and private cache; photos already exported to your chosen location are separate.

Use an [ARCore-supported Android phone](https://developers.google.com/ar/devices), Android 8.0 or newer. Allow Camera access and install/update Google Play Services for AR when prompted. The app itself requires no account, API key, backend or location permission. This is a release-configured prototype for testing, not a Play Store publication or a field-validated measuring instrument.

## Measure

1. Slowly move the phone sideways to scan well-lit, textured surroundings. Aim within 0.2–5 m of a surface.
2. **Distance:** aim at the first endpoint and hold the target steady for about half a second. Tap **+** when it becomes gold and says **READY TO MARK**. Repeat at the second endpoint. The result is the straight-line 3D distance. For alignment help, tap **Guide** to cycle **Off → Horizontal → Vertical → Off**. An aligned, stable target turns green.
3. **Height:** mark a flat floor point directly beneath the top of the subject. If the person obstructs that floor point, mark it before they stand in position. Then aim at the top of their head along the dashed vertical guide, hold steady and tap **+**. The person must stay still above the same floor mark. The second point does not need a detected head surface. This is manual alignment, not automatic person detection.
4. **Plot area:** mark at least three ground corners in boundary order, walking near each corner. Tap **Finish plot** for area and closed perimeter.
5. **m / ft** switches units. **Undo** removes the last point. **Reset** clears the measurement. Changing mode asks before clearing points.
6. **Pause** stops the camera and rendering while retaining points in this AR session and allowing the screen to sleep. **Resume** restarts tracking and requires a fresh stable target before marking. Do not move the subject during a pause if you intend to continue measuring it.
7. **Photo** saves a PNG with visible measurement annotations and totals using Android's file picker. Camera processing pauses during capture/export and resumes when finished if the app is foregrounded and not manually paused.

The app supports portrait use and up to 32 plot corners, including concave boundaries. Crossed/overlapping boundaries are rejected. Numbered arrows represent points outside the unobscured camera view, including points behind the phone. Off-screen points remain included in results; arrows themselves are never used as measurement coordinates or included in exported photos.

## Horizontal and vertical guides

Guides are optional in **Distance** mode and start Off each new AR session. Mark the first point, then aim at a detectable surface for the second point. A blue dashed reference line and hollow ring show the aligned position; a connector shows the correction from the actual target. The text below the distance shows the offset in centimetres (inches with ft units), tilt angle, and guidance such as **Aim lower** or **Aim toward the guide**. Align the aiming dot with the ring, hold steady and tap **+**. A world-horizontal line can look tilted on screen when the phone is rotated; the guide follows gravity, not the screen edges.

- **Horizontal:** the two endpoints should have equal height (Y). X and Z may both differ; this allows any direction across the ground, rather than locking to an arbitrary world heading. The offset is the target's signed height difference and the reference ring has the target's X/Z at the start point's Y.
- **Vertical:** the endpoints should share X and Z, with different Y. The offset is the combined sideways displacement in X/Z. The reference ring lies directly above or below the first point at the target's height.

The indicator becomes green when the estimated offset is at most **2 cm** and the tilt from the chosen alignment is at most **2°**. The angle limit avoids calling a short, visibly tilted line aligned; the offset limit prevents large deviations on long lines. These are guidance thresholds, **not a guarantee of measurement accuracy**. The targeting stability check still applies before **+** is enabled. You may mark an unaligned point: guides never snap, constrain, or replace the measured surface hit, so the result remains the actual estimated 3D distance.

Guides hide when a surface target is unavailable or tracking is lost. After marking both endpoints, the alignment readout continues using their current tracked positions; Undo returns to live guidance. Reset and switching modes clear points as before. The guide selection remains for the current session, is hidden in Height/Plot modes and is not saved to storage. Pause hides the overlay and disables the guide control. Exported photos contain the measured geometry only, without the advisory reference ring/line. Height keeps its existing floor-to-head vertical guide; Plot area keeps its horizontal projection.

## Point readiness and accuracy

The target must remain within 3 cm of its initial candidate for 0.5 seconds of continuous camera frames. A missing target, tracking loss, stale frames, a large jump or a pause resets this check. **This is a stability filter, not a claim of 3 cm measurement accuracy.** Status and instructions explain unstable targeting, unsuitable surfaces, range limits and lost tracking.

Height uses the gravity-aligned vertical line through one ground anchor. It finds the point on that line nearest the camera's aiming ray, requiring lateral alignment within 10 cm, a ray distance of 0.2–5 m and a height above 2 cm and at most 5 m. Nearly vertical sight lines are rejected; step back for a better view. These are input limits, not calibrated error bounds. The top remains a vertical offset from the same base anchor as ARCore refines the scene, and Height results use vertical separation rather than a diagonal length. A mistaken floor point or head alignment will produce an incorrect height.

No field accuracy or percentage error has been established. Check a taped distance and a known small rectangle on the actual phone, then repeat from different viewpoints. Plot sides and area are horizontal XZ projections, not terrain surface measurements. Uneven land, long walks, grass, shadows, reflective/blank surfaces and motion can reduce accuracy. The app warns about height differences and spans above 10 m; those warnings are not calibrated confidence estimates.

All anchors must be tracking before adding points, finishing or saving. Automatic rectangle/person recognition, measurement history, cloud persistence and surveying-grade positioning are not implemented. Depth is enabled when supported for Distance surface hits. Height's first point and Plot corners require upward-facing horizontal planes. Height's second point uses the explicitly marked vertical reference; Distance and Plot do not invent a surface when none is detected.

## Privacy and photo cleanup

Camera is the only declared permission. The APK has no Internet permission, application backend or app-written analytics/upload code. Google Play Services for AR has its own [data collection and telemetry](https://developers.google.com/ar/develop/play-safety-label); the overall experience is not guaranteed network-free. Cloud Anchors and Geospatial APIs are not used. Opening the privacy policy uses a browser, and selecting a cloud provider in the file picker can upload the exported photo through that provider.

Only the unit preference is stored persistently by the app. Points live in memory in the current AR session; restarting after process death starts a new measurement. Android may preserve the manual Pause setting during Activity recreation. Backup and device-transfer exclusions are configured for app data.

**Captures are now held in memory, with no temporary PNG written to app cache.** The pending PNG buffer is cleared on cancellation, replacement, destruction or after export, including failed exports. The old `measurement.png` cache file is removed on startup if present. If removal fails, the app tells the user to clear the cache. Exported photos remain wherever the user saved them. If Android kills the app while the file picker is open, the capture is lost and must be retaken; a document provider may already have created an empty destination file.

## Battery and background behavior

Active AR uses the camera, motion tracking, supported depth processing, rendering and the display. Leaving the live view open continues processing after a completed measurement. Use Pause during foreground breaks, or leave the app/lock the phone. The keep-screen-on flag is set only while the camera is running and cleared when paused.

`ArLifecycle` allows the camera only while the Activity is foregrounded, not manually paused, not exporting a photo and not failed. It pauses rendering before the ARCore session, clears queued point actions and target stability, and prevents automatic resume behind the file picker or while manually paused. A fatal AR error also stops the camera. The session closes when the Activity is destroyed. There are no background services, scheduled measurement jobs or CPU wake locks. An explicitly requested photo export may finish on a worker thread. Android can keep the paused process in memory without continued camera rendering.

Google documents that [ARCore may continue computation for up to 10 seconds after pausing](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#pause()). Battery consumption, heat and camera release have not been profiled on a phone; no measured battery reduction is claimed. Frame-rate and depth quality settings were not changed without device evidence.

## Build and signing

Use JDK 17, SDK Platform 35 and Build Tools 35.0.0. Gradle 8.11.1 has a pinned download checksum; Android Gradle Plugin is pinned to 8.9.2 and ARCore to 1.57.0. JUnit 4.13.2 is used only for tests. No new runtime dependency was added.

```sh
./gradlew :measurement:test :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
```

Gradle produces the unsigned release at `app/build/outputs/apk/release/app-release-unsigned.apk`. See `SIGNING.md` for signing-key management and repeatable signing instructions. Private keys and credentials are excluded from this source package.

## Code and verification

- `measurement/`: geometry, measurement state, screen projection, vertical height estimation, advisory alignment guides and placement stability, all independent of Android.
- `MeasureRenderer`: owns anchors and measurement mutations on the GL thread, refreshes poses together and checks target readiness again when **+** is processed.
- `MeasurementOverlay`: camera annotations, height/alignment guides, off-screen indicators and pause cover.
- `ArLifecycle`: unit-tested camera start/stop policy, driven by Activity events and user controls.
- `PendingPhoto`: unit-tested in-memory PNG ownership and export cleanup.
- `MainActivity`: permissions, session integration, UI and asynchronous photo export.

See `VALIDATION.md` for automated results and the remaining device checklist. Local tests and build checks do not replace physical-device interaction, camera rendering, accuracy or battery tests.
