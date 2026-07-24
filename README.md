# HoK Draft Assistant V4.3 RC1 — calibrada con video completo

Esta candidata integra la grabación horizontal completa suministrada por el usuario (848x392, 237.5 s) y corrige los dos fallos más graves observados en V4.1/V4.2:

1. el slot del jugador ya no queda fijado entre partidas;
2. un avatar de perfil ya no se interpreta como héroe confirmado.

## Uso desde la burbuja

- **Pausa:** mantiene la sesión sin procesar fotogramas.
- **Selección:** activa veto/picks/ajustes, OCR, slots y recomendaciones.
- **Partida:** conserva la composición final y muestra la estrategia sin seguir escaneando el draft.

El panel es desplazable. Puedes fijar manualmente:

- tu slot: Auto / 1 / 2 / 3 / 4 / 5;
- tu pick: Auto / Pendiente / Fijado.

El modo automático solo sugiere cambios de etapa; nunca los fuerza.

## Mejoras derivadas del video

- subfases detectables: veto, selección, últimos ajustes, carga y partida;
- `R-95` aparece en slot 4 en el video, aunque estaba en slot 2 en capturas anteriores: el slot se detecta por fila y exige 4 lecturas consistentes;
- regiones del diamante de bloqueo recalibradas contra el fotograma real de 120 s;
- el estado observado a 120 s se usa como prueba de regresión: 4 aliados fijados, R-95 preseleccionando, 4 enemigos fijados y último enemigo vacío;
- tres fotogramas estables para confirmar el tablero y reducir parpadeos.

## Aplicar, probar y compilar

1. Extrae el ZIP en una carpeta nueva.
2. Ejecuta `APLICAR_V4_Y_COMPILAR.bat`.
3. El instalador crea respaldo, valida el catálogo y la configuración, ejecuta las pruebas JUnit, compila y abre internamente la APK para verificar sus archivos esenciales.
4. Solo devuelve éxito cuando encuentra una APK real con `AndroidManifest.xml`, `classes.dex` y `assets/hok_counters.json`.

Salida esperada en el Escritorio:

`HoK_Draft_Assistant_V4_3_RC1_VIDEO_CALIBRATED.apk`

Proyecto de destino predeterminado:

`C:\Users\Windows 11 Pro\Documents\HoK_Counter_App`

## GitHub privado y compilación automática

La carpeta incluye `CREAR_REPO_PRIVADO_Y_SUBIR.bat`. Al ejecutarlo en Windows:

1. Comprueba Git y GitHub CLI.
2. Instala las herramientas con `winget` si faltan.
3. Abre el inicio de sesión oficial de GitHub.
4. Crea `AntraxR666/HoK-Draft-Assistant` como repositorio privado.
5. Sube el proyecto completo.
6. Activa `.github/workflows/android-ci.yml` automáticamente.

El flujo CI ejecuta validadores, pruebas unitarias, lint, compilación Debug y verificación interna de la APK.

Para compilar directamente en el PC sin copiar sobre otro proyecto, ejecuta `COMPILAR_Y_VERIFICAR_LOCAL.bat`.
