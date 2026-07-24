# Full Hero Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar el catálogo local de 116 héroes en una APK funcional con búsqueda, filtros, counters explicados y overlay moderno.

**Architecture:** `CounterCatalog` concentra modelos, validación, normalización y consultas en Kotlin puro. `CounterEngine` carga el asset Android. La actividad y el servicio solo presentan resultados y transmiten el héroe seleccionado.

**Tech Stack:** Kotlin 1.8, Android SDK 33, AppCompat, Material Components, Gradle 8.0, JUnit 4, `org.json`.

## Global Constraints

- `minSdk 23`, `compileSdk 33`, `targetSdk 33`.
- Android Gradle Plugin 8.0.2, Gradle 8.0, JDK 17.
- El catálogo debe contener exactamente 116 héroes y tres counters por héroe.
- No se usará `EnemyHeroName` ni datos de demostración.
- La detección del héroe será manual; OCR y automatización quedan fuera de alcance.

---

### Task 1: Catálogo puro y validado

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalog.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/CounterEngine.kt`
- Replace: `app/src/test/java/com/example/honorofkingsassistant/CounterEngineTest.kt`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: `Hero`, `HeroCounter`, `HeroRecommendation`, `CounterCatalog.fromJson(String)`, `findHero(String)`, `heroesForRole(String?)`, `countersFor(String)`, `recommendFor(List<String>)`.
- Produces: `CounterEngine.allHeroes()`, `roles()`, `findHero()`, `getCounters()`, `getHeroRecommendations()`.

- [ ] Escribir pruebas que exijan 116 héroes, tres counters, unicidad y referencias válidas.
- [ ] Ejecutar las pruebas y confirmar que fallan porque `CounterCatalog` no existe.
- [ ] Implementar modelos, parser, normalización, validación y consultas mínimas.
- [ ] Sustituir `CounterEngine` por el adaptador de assets.
- [ ] Ejecutar pruebas y confirmar éxito.
- [ ] Commit: `feat: add validated hero counter catalog`.

### Task 2: Pantalla de consulta real

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/MainActivity.kt`

**Interfaces:**
- Consumes: APIs de `CounterEngine` de Task 1.
- Produces: selección manual por rol/nombre y renderizado de tres counters.

- [ ] Definir pruebas estáticas para IDs XML y ausencia de `EnemyHeroName`.
- [ ] Ejecutar validación y confirmar fallo con el layout actual.
- [ ] Crear selector de rol, autocompletado, contenedor de resultados y acciones.
- [ ] Implementar filtrado, consulta y renderizado dinámico.
- [ ] Ejecutar validaciones XML/textuales.
- [ ] Commit: `feat: add hero search and counter results UI`.

### Task 3: Overlay en primer plano

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: extra `OverlayService.EXTRA_HERO_NAME`.
- Produces: servicio foreground sin duplicados, notificación y botón cerrar.

- [ ] Añadir validaciones estáticas de permisos, `startForeground` y ausencia de `TYPE_PHONE`.
- [ ] Ejecutar validación y confirmar fallo.
- [ ] Implementar solicitud moderna de permiso y arranque con `ContextCompat.startForegroundService`.
- [ ] Implementar notificación y overlay actualizado por intent.
- [ ] Ejecutar validaciones.
- [ ] Commit: `feat: modernize hero counter overlay`.

### Task 4: Recursos, documentación y empaquetado

**Files:**
- Modify: `README.md`
- Modify: `.gitignore`
- Verify: `app/src/main/assets/hok_counters.json`
- Create: `tools/validate_project.py`

**Interfaces:**
- Produces: validador reproducible y proyecto limpio listo para abrir/compilar.

- [ ] Escribir validador que compruebe JSON, referencias, XML y archivos requeridos.
- [ ] Ejecutarlo y corregir cualquier incumplimiento.
- [ ] Actualizar README con uso y compilación.
- [ ] Ejecutar compilación disponible o, si no hay SDK, verificación Kotlin pura y dejar comando exacto para Windows.
- [ ] Crear ZIP final sin cachés ni artefactos.
- [ ] Commit: `chore: validate and document full hero app`.
