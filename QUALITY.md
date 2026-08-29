# Quality gates

The editor uses a bounded, deterministic preview budget: simple stacks render up to 1728 px,
moderate stacks up to 1440 px, and RAW or costly stacks up to 1280 px. This only affects the
interactive preview. Sharing and export re-develop the immutable original at the selected export
resolution. The checked-in Baseline Profile is installed for locally sideloaded builds too.

The GitHub workflow runs the FOSS editor/LUT contract tests, the camera-experience contract tests,
and builds both the unsigned F-Droid APK and the signed preview APK. It also verifies package
identity, signing, permissions and the absence of Google/Play/Firebase artefacts.

For local verification:

```sh
./gradlew :app:compileDefaultPreviewKotlin
./gradlew :app:testDefaultPreviewUnitTest --tests 'com.hinnka.mycamera.fossin.*'
./gradlew :app:testDefaultPreviewUnitTest --tests 'com.hinnka.mycamera.model.CameraExperienceTest' --tests 'com.hinnka.mycamera.ui.camera.*'
```
