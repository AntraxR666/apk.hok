# HoK Draft Assistant V4.3 RC1 — Estado de GitHub y compilación

## Validaciones ejecutadas en este entorno

- V4_DOMAIN_SMOKE_OK
- VIDEO_CALIBRATION_SMOKE_OK
- WORKFLOW_YAML_OK
- POWERSHELL_SHAPE_OK
- V4_VALIDATION_OK
- V4_UI_CONTRACT_OK
- V4_OVERLAY_CONTRACT_OK
- OVERLAY_SCROLL_CONTRACT_OK
- PLAYER_SLOT_DETECTION_CONTRACT_OK
- PLAYER_PICK_OVERRIDE_CONTRACT_OK
- V4_STAGE_CONTROL_CONTRACT_OK
- USER_CALIBRATION_CONTRACT_OK
- V4_DELIVERY_CONTRACT_OK

## GitHub

La cuenta conectada es `AntraxR666`, pero el conector actual no expone ningún repositorio y no ofrece una operación para crear uno.

Se añadieron:

- `CREAR_REPO_PRIVADO_Y_SUBIR.bat`
- `CREAR_REPO_PRIVADO_Y_SUBIR.ps1`
- `.github/workflows/android-ci.yml`

Al ejecutar el BAT en Windows se crea el repositorio privado `HoK-Draft-Assistant`, se sube el proyecto y se activa Android CI.

## Compilación local

Se añadieron:

- `COMPILAR_Y_VERIFICAR_LOCAL.bat`
- `COMPILAR_Y_VERIFICAR_LOCAL.ps1`

El proceso ejecuta validación estática, pruebas unitarias, `lintDebug`, `assembleDebug`, inspección interna de APK y SHA-256.

## Limitación del entorno actual

No se pudo ejecutar Gradle Android completo aquí porque este contenedor no tiene JDK 17 ni Android SDK instalados y no dispone de resolución DNS para descargar esos componentes. El proyecto sí incluye el Gradle Wrapper completo y el CI está preparado para realizar esa compilación en GitHub o en el PC Windows configurado.
