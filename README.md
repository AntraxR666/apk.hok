# HoK Draft Assistant 1.0 Personal — Huawei JKM-LX3

Esta candidata está calibrada para el **Huawei JKM-LX3**, Android 9, EMUI 9.1, Kirin 710, 4 GB de RAM y pantalla 2340×1080. Todo el procesamiento permanece en el teléfono y la aplicación no solicita acceso a Internet.

## Perfil de rendimiento

- La captura se reduce exactamente al 50 % de la resolución horizontal del dispositivo: **2340×1080 → 1170×540**.
- El OCR utiliza la misma superficie de 1170 px para evitar crear otro bitmap cuando no hace falta.
- `ImageReader` conserva dos buffers porque `acquireLatestImage()` necesita al menos dos para descartar fotogramas antiguos correctamente.
- La cadencia comienza en 700 ms y aumenta automáticamente hasta 1600 ms cuando la latencia del Kirin 710 sube.
- Los fotogramas que llegan mientras ML Kit está ocupado se descartan; nunca se acumula una cola retrasada.
- En clasificatoria, cada puesto exige 3 coincidencias en 5 fotogramas y supera
  umbrales calibrados de distancia y margen visual antes de confirmar un retrato.
  El piso de confianza `0,55` se conserva para el camino temporal basado en
  OCR/nombres. Ambos caminos rechazan resultados ambiguos.

## Funcionamiento

- **Pausa:** mantiene la autorización sin analizar imágenes.
- **Selección:** analiza veto, picks y ajustes, y actualiza recomendaciones.
- **Partida:** conserva composición y estrategia sin continuar el OCR.
- La etapa manual siempre manda; la detección automática solo sugiere.
- Al pasar de vertical a horizontal, el servicio vuelve a calcular la superficie
  de captura para el marco real del JKM-LX3 antes del siguiente escaneo.
- La burbuja semitransparente permite corregir slot, estado del pick y composición sin arrastrarse al hacer scroll.
- **Confirmar equipos antes de partida** toma un único fotograma de la pantalla de carga de dos equipos y abre una revisión manual segura.
- **Verificar equipos e ítems** toma un único fotograma del marcador dentro de partida, reconcilia títulos de héroe y abre los diez slots para revisión.
- Las compras sugeridas se calculan localmente según tu rol y las amenazas confirmadas; si faltan nombres españoles verificados de objetos, se conserva el nombre de la fuente en vez de inventar una traducción.

## Antes de instalar

La captura suministrada muestra aproximadamente **610 MB libres**. Libera al menos **2 GB** antes de instalar y probar; EMUI y Android necesitan margen temporal para actualizar aplicaciones y mantener estable el sistema.

En EMUI 9.1 configura la aplicación en **Inicio de aplicaciones / App launch** como administración manual y permite inicio automático, inicio secundario y ejecución en segundo plano. También exclúyela de la optimización de batería durante las pruebas.

## Compilación y entrega

Para desarrollo local usa Android Studio o ejecuta `tools/v55_emulator_smoke.ps1` con un emulador o teléfono conectado. El script instala el APK debug, inicia la actividad, guarda el árbol de interfaz y falla ante una excepción fatal. La confirmación de captura de pantalla y el permiso de superposición siempre requieren interacción manual de Android.

Una compilación correcta no demuestra por sí sola precisión perfecta. La puerta final está en `docs/V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md`, completada en el Huawei JKM-LX3 real durante selección clasificatoria, selección normal, pantalla de carga y marcador dentro de partida.

## Estado 1.0 RC3

La versión es `1.0.0-personal-jkm-lx3-rc3` (`versionCode 14`). Es una candidata personal offline: no solicita `INTERNET` ni `ACCESS_NETWORK_STATE`. La rama de trabajo queda separada de la futura versión comercial, que deberá validar nuevas resoluciones, idiomas, rendimiento y derechos de activos antes de distribuirse.
