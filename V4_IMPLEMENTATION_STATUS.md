# Estado V4.3 RC1 — video completo y regresiones reales

## Implementado

- MediaProjection con servicio foreground `mediaProjection`.
- OCR latino ML Kit empaquetado y procesamiento local.
- Overlay móvil con altura acotada y contenido desplazable.
- Modos manuales Pausa / Selección / Partida.
- Subfases internas Veto / Picks / Ajustes / Carga / Partida.
- Slot del usuario resuelto por nombre dentro de una fila aliada exacta.
- Cuatro detecciones consecutivas antes de aceptar el slot automático.
- Slot manual 1–5 con prioridad absoluta.
- Estado manual Auto / Pendiente / Fijado para impedir falsos “ya elegiste”.
- Marcadores de bloqueo recalibrados con el video 848x392.
- Avatares de perfil tratados como vacíos salvo marcador de bloqueo o marco dorado real.
- Seguimiento temporal del tablero durante tres fotogramas.
- Top 3 antes del pick; análisis provisional/final después del pick.
- 116 héroes y 348 relaciones locales.
- Sin permiso INTERNET, servidor, telemetría ni almacenamiento de capturas.

## Evidencia del video

- Duración: 237.521 s.
- Resolución: 848x392 horizontal.
- Veto aproximado: 5–30 s.
- Picks: 35–145 s.
- Últimos ajustes: 146–160 s.
- Carga/VS: 165–189 s.
- Partida: 190 s en adelante.
- A 120 s: R-95 está en fila aliada 4, todavía preseleccionando.

## Verificaciones realizadas en este entorno

- 13 pruebas de dominio/JUnit compatibles ejecutadas: 13 aprobadas.
- Smoke tests Kotlin: dominio, etapas, slot, estado de pick y video.
- Validadores Python: recursos, XML, overlay, scroll, calibración, entrega y PowerShell.
- Catálogo: 116 héroes / 348 relaciones.
- ZIP validado sin entradas corruptas.

## Verificación que debe ocurrir en Windows

La compilación Android completa requiere el Android SDK, Gradle Wrapper y JDK 17 existentes en el PC del usuario. El instalador ejecuta `clean testDebugUnitTest assembleDebug`, verifica los XML JUnit y comprueba el contenido interno de la APK antes de anunciar éxito.
