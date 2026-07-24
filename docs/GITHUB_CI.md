# Compilación automática con GitHub Actions

El proyecto incluye `.github/workflows/android-ci.yml`.

En cada `push`, pull request o ejecución manual, GitHub Actions:

1. configura Temurin JDK 17;
2. valida el Gradle Wrapper;
3. instala Android SDK Platform 33 y Build Tools 33.0.2;
4. ejecuta los validadores V4;
5. ejecuta `testDebugUnitTest`, `lintDebug` y `assembleDebug`;
6. verifica que la APK contenga manifiesto, DEX y el catálogo de héroes;
7. publica la APK, su SHA-256 y los reportes como artefacto descargable.

No se requiere `local.properties` en GitHub. Tampoco deben subirse `.gradle/`, `app/build/`, APKs, llaves o certificados privados.
