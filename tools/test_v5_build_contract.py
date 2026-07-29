from pathlib import Path

root = Path('.')
root_gradle = (root / 'build.gradle').read_text(encoding='utf-8')
app_gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
workflow = (root / '.github/workflows/android-ci.yml').read_text(encoding='utf-8')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')

expected = {
    "AGP 8.10.1": "version '8.10.1'" in root_gradle,
    "Kotlin 1.9.24": "version '1.9.24'" in root_gradle,
    "compileSdk 36": "compileSdk 36" in app_gradle,
    "targetSdk 36": "targetSdk 36" in app_gradle,
    "versionCode 14": "versionCode 14" in app_gradle,
    "1.0 versionName": 'versionName "1.0.0-personal-jkm-lx3-rc3"' in app_gradle,
    "Gradle 8.11.1": "gradle-8.11.1-bin.zip" in wrapper,
    "CI API 36": 'platforms;android-36' in workflow,
    "CI Build Tools 35": 'build-tools;35.0.0' in workflow,
    "1.0 artifact": 'HoK-Draft-Assistant-1.0-Personal-JKM-LX3' in workflow,
    "INTERNET removed from merged manifest": 'android.permission.INTERNET" tools:node="remove"' in manifest,
}

missing = [name for name, ok in expected.items() if not ok]
if missing:
    raise SystemExit("V5_BUILD_CONTRACT_FAILED: " + "; ".join(missing))
print("V5_BUILD_CONTRACT_OK")
