# HoK Draft Assistant V5 Personal — Huawei JKM-LX3

Esta candidata está calibrada para el **Huawei JKM-LX3**, Android 9, EMUI 9.1, Kirin 710, 4 GB de RAM y pantalla 2340×1080. Todo el procesamiento permanece en el teléfono y la aplicación no solicita acceso a Internet.

## Perfil de rendimiento

- La captura se reduce exactamente al 50 % de la resolución horizontal del dispositivo: **2340×1080 → 1170×540**.
- El OCR utiliza la misma superficie de 1170 px para evitar crear otro bitmap cuando no hace falta.
- `ImageReader` conserva dos buffers porque `acquireLatestImage()` necesita al menos dos para descartar fotogramas antiguos correctamente.
- La cadencia comienza en 700 ms y aumenta automáticamente hasta 1600 ms cuando la latencia del Kirin 710 sube.
- Los fotogramas que llegan mientras ML Kit está ocupado se descartan; nunca se acumula una cola retrasada.
- La identidad exige 3 coincidencias en 5 fotogramas con confianza mínima de 0,55 y rechazo de retratos ambiguos.

## Funcionamiento

- **Pausa:** mantiene la autorización sin analizar imágenes.
- **Selección:** analiza veto, picks y ajustes, y actualiza recomendaciones.
- **Partida:** conserva composición y estrategia sin continuar el OCR.
- La etapa manual siempre manda; la detección automática solo sugiere.
- La burbuja permite corregir slot, estado del pick y composición.

## Antes de instalar

La captura suministrada muestra aproximadamente **610 MB libres**. Libera al menos **2 GB** antes de instalar y probar; EMUI y Android necesitan margen temporal para actualizar aplicaciones y mantener estable el sistema.

En EMUI 9.1 configura la aplicación en **Inicio de aplicaciones / App launch** como administración manual y permite inicio automático, inicio secundario y ejecución en segundo plano. También exclúyela de la optimización de batería durante las pruebas.

## Compilación y entrega

Ejecuta una sola vez `PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.bat`. El lanzador valida el paquete, crea respaldo, aplica los cambios, sube el commit, espera GitHub Actions y solo descarga la APK después de `testDebugUnitTest`, `lintDebug` y `assembleDebug` exitosos.

Una compilación correcta no demuestra por sí sola precisión perfecta. La puerta final está en `docs/V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md`.

## Delivery V3

The Windows launcher creates a fresh temporary clone of `AntraxR666/apk.hok`. It does not reuse or depend on any previous local Git folder or `origin` configuration.
