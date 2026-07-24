# Integración completa de héroes — Diseño

## Objetivo

Convertir el prototipo Android en una aplicación funcional que cargue el catálogo local de 116 héroes, permita buscar y filtrar héroes, muestre sus tres counters con razones y comparta la selección con un overlay persistente y seguro.

## Arquitectura

La lógica se divide en tres unidades. `CounterCatalog` es una capa Kotlin pura que valida y consulta el JSON. `CounterEngine` adapta el catálogo a Android cargándolo desde `assets`. `MainActivity` y `OverlayService` consumen únicamente las APIs públicas del motor, sin interpretar JSON.

## Datos

El archivo canónico será `app/src/main/assets/hok_counters.json`. Su raíz contiene `heroes`; cada héroe tiene `id`, `name`, `role` y exactamente tres elementos en `counters`, cada uno con `hero_name` y `reason`. Al iniciar, el catálogo valida IDs y nombres únicos, counters no vacíos y referencias a héroes existentes.

## Búsqueda y recomendaciones

La búsqueda será insensible a mayúsculas, espacios repetidos y signos diacríticos. Se podrá resolver por nombre o ID. La lista principal podrá filtrarse por rol. Para varios enemigos, los counters se agregan por nombre y se ordenan por frecuencia, preservando el orden original como desempate.

## Interfaz principal

La pantalla tendrá selector de rol, buscador con autocompletado, botón para consultar, contenedor de resultados y botón para mostrar el overlay. Cada resultado incluirá nombre del counter y razón mecánica. La aplicación no usará valores ficticios como `EnemyHeroName`.

## Overlay

El overlay se iniciará solo por acción del usuario y después de conceder `SYSTEM_ALERT_WINDOW`. Será un servicio en primer plano con notificación, mostrará el héroe seleccionado y sus tres counters, y tendrá un botón para cerrarse. No duplicará vistas si recibe varios intents.

## Compatibilidad

Se mantiene `compileSdk 33`, `targetSdk 33`, `minSdk 23`, Android Gradle Plugin 8.0.2, Gradle 8.0 y JDK 17. Se elimina `package` del manifiesto y se usa el `namespace` del módulo.

## Pruebas

Las pruebas unitarias cubrirán: carga de 116 héroes, unicidad, integridad de referencias, búsqueda normalizada, filtro por rol, counters de héroe conocido, consulta inexistente y agregación de múltiples enemigos. También se validará el JSON con un script independiente y se verificará la estructura XML.

## Fuera de alcance

No se implementará reconocimiento automático de pantalla, OCR, lectura de memoria del juego, sincronización remota ni actualización automática del meta. La selección del enemigo será manual.
