# HoK Draft Assistant — reconocimiento V2 y corrección rápida para JKM-LX3

## Objetivo

Hacer que la selección clasificatoria sea comprensible y operable mientras la
partida continúa: la aplicación debe reconocer los retratos base desde la
primera instalación, indicar el resultado de cada posición y llevar al usuario
directamente a corregir solo las posiciones dudosas o no identificadas.

La geometría primaria corresponde al Huawei JKM-LX3: pantalla física
`2340x1080` y captura interna proporcional `1170x540`. La grabación `848x392`
se usa como regresión del mismo aspecto, nunca como sustituto del perfil del
teléfono.

## Enfoques considerados

### 1. Aumentar el umbral del comparador actual

Recuperaría más retratos, pero convertiría casos como Kaizer/Daji en falsos
positivos. Se descarta porque mejora la cobertura sacrificando precisión.

### 2. Reconocimiento híbrido determinista y corrección contextual

Combina recorte interior, varias transformaciones de cada plantilla, firma de
forma/bordes, color y consenso temporal. Publica evidencia por slot y ofrece
corrección rápida solo donde sea necesaria. Es el enfoque elegido porque es
offline, explicable y apropiado para el Kirin 710.

### 3. Clasificador neuronal personalizado

Podría superar al enfoque determinista con miles de ejemplos etiquetados del
cliente real, pero actualmente no existe ese conjunto de entrenamiento ni una
evaluación por parche. Integrarlo ahora produciría una precisión no demostrada
y mayor consumo. Queda fuera de esta versión personal.

## Arquitectura de reconocimiento

### Geometría

`JkmLx3SelectionProfile` expresa todas las regiones como coordenadas
normalizadas derivadas de `2340x1080`. La captura reducida conserva la misma
relación de aspecto.

Cada posición tiene dos regiones:

- `slotBounds`: marco completo, usado para determinar vacío, preselección o
  bloqueo.
- `portraitInterior`: zona interior sin diamante, marco, nombre de jugador ni
  fondo lateral, usada exclusivamente para identificar al héroe.

Una tolerancia pequeña admite barras del sistema y redondeo de escalado, pero
no desplaza dinámicamente la región hacia contenido no validado.

### Plantillas derivadas

Las imágenes públicas base no se incorporan al APK. Durante la preparación del
catálogo se calculan firmas no reversibles de variantes controladas:

- recorte central nominal;
- recortes interiores leves para reproducir el encuadre del juego;
- brillo `-10 %`, nominal y `+10 %`;
- contraste reducido y aumentado moderadamente;
- escalado bilinear equivalente al de la captura.

Solo se guardan las firmas. Las variantes pertenecen al dominio
`DRAFT_PORTRAIT`; nunca se comparan con tarjetas de carga o skins.

### Firma visual V2

Cada firma contiene:

1. hash perceptual de luminancia;
2. hash de gradientes horizontales y verticales;
3. histograma de orientación de bordes por cuadrante;
4. color medio por cuadrante.

La distancia combinada prioriza forma y bordes sobre color. Esto evita que dos
héroes con paletas parecidas se vuelvan indistinguibles. El decodificador
acepta temporalmente las firmas V1 para migración, pero las plantillas
integradas se generan como V2.

### Consenso temporal por posición

`SlotRecognitionTracker` conserva como máximo cinco observaciones elegibles por
slot. Un héroe queda confirmado únicamente con:

- al menos tres coincidencias coherentes;
- umbral absoluto válido;
- margen suficiente frente al segundo candidato;
- estado visual `PREVIEWING` o `CONFIRMED`.

La historia se reinicia cuando el slot vuelve a vacío, cambia de fase o cambia
claramente de retrato. Las muestras dudosas no se promueven por repetición.

## Estado visible por slot

El servicio publica un `SlotRecognitionUiState` para cada posición:

```text
WAITING       El juego todavía no muestra un héroe en esa posición
SCANNING      Hay retrato y aún faltan muestras coherentes
DETECTED      Héroe confirmado y confianza
UNCERTAIN     Hay dos o más candidatos demasiado cercanos
NOT_DETECTED  Hay retrato estable pero ninguna plantilla supera el umbral
MANUAL        Identidad confirmada por el usuario
```

`WAITING` no se contabiliza como error ni solicita corrección.

`UNCERTAIN` conserva hasta tres candidatos ordenados para que la corrección sea
de uno o dos toques. `NOT_DETECTED` abre búsqueda directa.

## Interfaz de selección

### Burbuja cerrada

La cabecera muestra progreso comprensible:

```text
HOK · 6/8 · 2 pendientes
```

El denominador representa slots ocupados, no diez posiciones obligatorias.
Cuando todo lo ocupado está resuelto muestra `HOK · 8/8 ✓`.

### Panel principal simplificado

La parte superior contiene:

1. estado de captura: `Escaneando`, `Listo` o `Revisa 2`;
2. acción primaria `Escanear selección`;
3. resumen compacto de aliados y enemigos;
4. bloque `Corrección rápida` solo si existen slots pendientes;
5. recomendaciones.

Los controles de etapa, entrada, modo, slot propio y pick propio se agrupan
bajo `Opciones avanzadas`. Permanecen accesibles, pero no dominan el flujo
normal.

### Corrección rápida

Después de un escaneo solicitado manualmente, si hay posiciones ocupadas
pendientes, se abre el panel compacto sin tomar el foco del teclado:

```text
Aliado 2 · Dudoso
[Kaizer] [Daji] [Buscar otro]

Enemigo 4 · No detectado
[Elegir héroe]
```

Elegir un candidato confirma el slot y avanza automáticamente al siguiente
pendiente. `Buscar otro` muestra el campo de búsqueda y filtros existentes.
Al resolver el último slot se vuelve al resumen y se muestra
`Composición confirmada`.

El análisis automático continuo nunca expande por sí solo un panel que pueda
tapar una acción del juego. En ese caso solo actualiza la cabecera. El escaneo
solicitado por el usuario sí puede abrir la corrección rápida porque es una
acción deliberada.

### Comodidad y accesibilidad

- Todos los controles interactivos tienen área táctil mínima de `48dp`.
- El estado no depende solo del color: usa símbolo y texto.
- El panel conserva el límite de 72 % de altura y scroll interno.
- Solo la cabecera arrastra la ventana.
- La corrección rápida aparece arriba; no requiere subir o bajar por opciones.
- La búsqueda no abre el teclado hasta pulsar `Buscar otro`.
- La superposición se oculta durante la captura para no contaminar los ROIs.

## Integración con los otros flujos

- `Confirmar equipos antes de partida` usa tarjetas de carga y plantillas de
  skins separadas. Muestra los mismos estados por slot, pero nunca enseña una
  skin como plantilla de selección.
- `Verificar equipos y actualizar compra` usa la geometría del marcador y
  reutiliza el editor contextual para conflictos.
- Una corrección manual tiene confianza `1.0` y no puede ser sobrescrita por
  Auto durante la sesión.
- En modo normal solo aparecen los aliados durante selección. Los enemigos se
  incorporan desde carga o marcador.

## Rendimiento en JKM-LX3

- Captura máxima `1170x540`.
- Solo se procesan regiones pequeñas de retrato.
- Una firma V2 no conserva imágenes y ocupa pocos bytes.
- Se mantiene un único fotograma en procesamiento; los nuevos se descartan
  mientras el analizador está ocupado.
- La búsqueda de plantillas usa firmas precalculadas y evita crear bitmaps
  grandes.
- El consenso temporal reutiliza estructuras acotadas: diez slots por cinco
  observaciones.

## Pruebas

### Unitarias

- cálculo y serialización V2;
- migración V1;
- distancia de bordes, forma y color;
- rechazo por umbral y por margen;
- consenso 3/5 por slot;
- reinicio al vaciarse o cambiar el retrato;
- prioridad manual;
- estados `WAITING`, `SCANNING`, `DETECTED`, `UNCERTAIN`,
  `NOT_DETECTED` y `MANUAL`;
- secuencia de corrección rápida.

### Grabación y fixtures

- geometría escalada desde `2340x1080` hacia `1170x540` y `848x392`;
- el fotograma clasificatorio conocido acepta identidades inequívocas;
- Kaizer/Daji permanece dudoso hasta disponer de evidencia adicional;
- marcos y slots vacíos no se reconocen como héroes;
- varias muestras consecutivas resuelven candidatos estables;
- ninguna skin de carga entra al dominio de selección.

### Android

- panel de corrección visible sin scroll inicial;
- áreas táctiles mínimas;
- Auto no abre el panel inesperadamente;
- `Escanear selección` abre corrección rápida cuando corresponde;
- cada elección avanza al siguiente pendiente;
- overlay oculto durante captura y restaurado ante éxito o error;
- funcionamiento a `2340x1080` horizontal y Android 9.

## Criterios de aceptación

1. El usuario distingue inmediatamente qué fue detectado, qué está pendiente y
   qué todavía está vacío.
2. Un escaneo manual lleva directamente a corregir solo los slots pendientes.
3. Ningún candidato ambiguo se confirma automáticamente.
4. Las seis coincidencias claras del fixture siguen siendo válidas y los casos
   ambiguos se resuelven con varios fotogramas o corrección rápida.
5. No hay regresiones en carga, marcador, modo normal, recomendaciones,
   privacidad offline ni prioridad manual.
6. Pruebas unitarias, fixture, lint, compilación y QA Android pasan antes de
   generar una nueva APK.
