# Photo Editor FOSS build

The `default` flavor is the offline FOSS distribution intended for F-Droid.
It does not include Google Play Billing, Google Play Services, Firebase,
Bugly, or a vendor update client. The build also does not contact a remote
service during startup.

```sh
./gradlew assembleDefaultRelease
```

The generated F-Droid input APK is
`app/build/outputs/apk/default/release/app-default-release-unsigned.apk`
(`org.foss.photoeditor`). The GitHub Release also includes
`app-default-preview.apk`, a directly installable preview signed with the
project debug key for testing; F-Droid should use the unsigned artifact.

The editor works offline with local photos, all bundled LUT presets, imported `.cube`
and `.plut` LUTs, and the PhotonCamera rendering pipeline. Network-backed AI
features from the upstream camera project remain opt-in and are not used by
the Photo Editor launch screen.

The launch screen provides the complete Snapseed-style tool set: Tune Image
(exposure, contrast, saturation, ambiance, highlights, shadows, warmth),
Details (structure and sharpening), Tonal Contrast (shadows, midtones and highlights),
interactive RGB curves, White Balance,
Crop (free and common aspect ratios), Rotate/Straighten, four-point Perspective,
Expand, Selective control points, Brush, Healing, Lens Blur (radial/linear),
Vignette, Frames, HDR Scape, Glamour Glow, Drama, Vintage, Grainy Film,
Retrolux, Grunge, Black & White, Noir, Portrait, Face Enhance, Head Pose, Double Exposure
(blend modes), Text styling, and local LUT/Look presets. All edits are stored
as a non-destructive recipe with undo/redo, before/after comparison, high-resolution JPEG
export and a local Android share action.

The FOSS release is unsigned by design so F-Droid can apply its own reproducible
signing key. LUT URIs, curves, selective points, crop gestures and other scalar
edits survive activity recreation when the document provider grants a persistable
read permission. Rendering and LUT parsing run locally; Portrait and Head Pose
use deterministic offline image operations and never upload a photo.

Verification performed locally:

* `./gradlew assembleDefaultRelease` and `./gradlew :app:assembleDefaultPreview` succeed. The preview APK is installable directly; the unsigned release APK is the F-Droid input.
* Focused editor and LUT parser tests pass; the full suite executes 477 tests with 5 skipped and 2 known upstream RAW reference failures. `DngPhotonPercentileTest` and `DngProfileToneCurveTest` remain red because the upstream Photon PGTM implementation currently differs from their older numeric fixtures; they are unrelated to the Photo Editor recipe/editor code.
* The release manifest contains no `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`, or billing permission, and the APK contains no Play Services, Firebase, Bugly, Crashlytics, or Play Billing classes.
