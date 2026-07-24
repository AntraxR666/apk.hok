# Cambios V4.3 RC1

- Se procesó el video completo de 237.5 segundos y se registraron veto, picks, ajustes, carga y partida.
- Se eliminó el supuesto de que R-95 siempre ocupa el mismo slot.
- El OCR del nombre solo puede asignar slot si cae dentro de la fila aliada correspondiente.
- Se elevaron a cuatro las detecciones coherentes necesarias para aceptar el slot automático.
- Se recalibró la región del diamante de bloqueo: el ROI anterior estaba desplazado hacia el fondo lateral.
- Se separó el marcador de bloqueo del marco dorado de preselección.
- Un retrato/avatar con textura ya no basta para declarar que un héroe está fijado.
- Se añadió `DraftSubphase` para veto, picks, últimos ajustes, carga y partida.
- Se añadieron fixtures numéricos del fotograma real de 120 s.
- Se añadió `VideoCalibrationTest.kt` a la batería JUnit que ejecuta Gradle.
- Se reforzó el instalador: preflight de fuente, catálogo, JDK 17, informes JUnit y contenido de la APK.
- Versión: `4.3-rc1-video-calibrated` (`versionCode 9`).
