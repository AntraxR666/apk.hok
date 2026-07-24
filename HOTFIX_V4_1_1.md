# V4.1.1 build hotfix

- Corrected a stale unit-test expectation. The composition-aware engine correctly ranks Donghuang above Liang when the allied draft already contains a Mid Lane hero and lacks frontline.
- Added an assertion that the winning recommendation includes the frontline-composition reason.
- Added explicit Kotlin lambda labels in `HeroPortraitMatcher` to remove ambiguous-label compiler warnings.
- Bumped Android version to 4.1.1 (versionCode 6).
