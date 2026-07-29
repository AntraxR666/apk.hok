# HoK Draft Assistant 1.0 — guía de uso en Huawei JKM-LX3

Esta guía corresponde a la APK `1.0.0-personal-jkm-lx3-rc3`. La burbuja puede moverse
arrastrando solamente la franja azul `HOK · ...`; los botones dentro del panel no arrastran
la burbuja y deben responder a toques normales.

## Ruta rápida: lo único que necesitas recordar

1. Abre el asistente y acepta **superposición** y **captura de pantalla**.
2. En la selección de HoK, abre la burbuja, pulsa **Selección** y después
   **Escanear selección ahora**.
3. Si un slot queda dudoso, la app abre directamente ese puesto. Elige una sugerencia o
   escribe el nombre/título; no recorras otros menús.
4. En la pantalla grande entre selección y mapa, pulsa
   **Confirmar equipos antes de partida**.
5. Ya en el mapa, pulsa **Partida**. Si necesitas corregir, abre el marcador de los diez
   jugadores y pulsa **Verificar equipos y actualizar compra**.

Las tres acciones principales están arriba del panel. **Mostrar ajustes avanzados** es opcional:
no hace falta abrirlo para el uso normal.

## Antes de abrir Honor of Kings

1. Instala la APK actual sobre una versión anterior. Si Android rechaza la actualización por
   la firma, desinstala solamente la versión anterior de **HoK Draft Assistant** y vuelve a
   instalar la APK nueva.
2. Abre la aplicación y pulsa **Iniciar asistente**.
3. Cuando Android lo pida, permite **Mostrar sobre otras aplicaciones**. Vuelve a la app.
4. Acepta el diálogo de Android para capturar la pantalla. No es una cámara ni transmite la
   pantalla: la lectura se hace en el teléfono.
5. Debe aparecer una burbuja pequeña: `HOK · PAUSA`. Si no aparece, el permiso de superposición
   no quedó concedido; vuelve a abrir la app y repite los pasos 2–4.
6. En EMUI 9.1, desactiva la gestión automática de batería de la app y habilita inicio automático,
   inicio secundario y ejecución en segundo plano.
7. Puedes iniciar el asistente con el teléfono vertical. Abre HoK, espera a que quede horizontal
   y pulsa **Selección**: la app recalcula automáticamente la captura para la orientación y el
   tamaño reales antes de escanear.

## Qué significa cada etapa

| Botón | Cuándo pulsarlo | Qué habilita |
|---|---|---|
| **Selección** | Desde los vetos/picks hasta la pantalla de carga | OCR del draft, corrección manual y confirmación de equipos antes del mapa. |
| **Partida** | Cuando ya se puede mover el héroe en el mapa | Conserva la composición y habilita la verificación del marcador. |
| **Pausa** | Cuando no usarás el asistente | Conserva lo ya confirmado, sin analizar fotogramas. |

El botón **Confirmar equipos antes de partida** queda tenue e inactivo fuera de **Selección**.
El botón **Verificar equipos y actualizar compra** queda tenue e inactivo fuera de **Partida**.

## Primera partida clasificatoria: forma fiable de empezar

En clasificatoria la selección puede mostrar retratos y nombres de jugadores, sin el nombre del
héroe. Esta versión ya incluye **113 plantillas iniciales** de los retratos base de selección,
por lo que **Auto** debe intentar reconocerlos desde la primera instalación. No usa tarjetas de
skins como si fueran retratos de selección. La corrección manual queda como respaldo seguro para
un resultado ausente, ambiguo o para uno de los tres héroes temporales que todavía no tiene plantilla base
verificada.

1. Abre la burbuja con **Abrir**.
2. En la primera fila visible, pulsa **Selección**. No necesitas entrar en ajustes avanzados.
3. Cuando ya haya retratos en los laterales, pulsa **Escanear selección ahora**. La app reúne
   cinco lecturas sin que tengas que tocar más opciones.
4. Bajo **Héroes por posición** verás `A1`–`A5` y `E1`–`E5`: verde significa detectado,
   azul significa que aún está reuniendo lecturas, naranja significa dudoso y rojo significa
   no detectado.
5. Si un puesto queda naranja o rojo, se abre directamente la corrección de ese único puesto.
   Toca una de las sugerencias o **Buscar otro héroe por nombre o título**. No necesitas recorrer
   el editor completo. Después de corregirlo, la app avanza automáticamente al siguiente puesto
   pendiente; al resolver el último vuelve al resumen.
6. Si prefieres revisar los diez puestos, pulsa **Editar héroes manualmente**. Cada ficha muestra
   `Nombre · título español`; por ejemplo, `Angela · La Maga de Fuego` es una sola heroína.
7. Repite solo para los héroes ya bloqueados y visibles. Si un slot aún está vacío o solo es una
   preselección, déjalo sin asignar: no conviene enseñar una imagen equivocada.
8. Al elegir un héroe mientras esa selección está visible, el estado debe indicar
   **“se aprendió este retrato de selección localmente”**. No es un requisito para empezar a usar
   Auto: añade una corrección local adicional sin sustituir la plantilla inicial. No se sube a
   Internet.
9. Solo si lo necesitas, abre **Mostrar ajustes avanzados** para fijar `Mi slot` en 1–5, cambiar
   lados o forzar Clasificatoria/Normal. Marca `Pendiente` antes de bloquear tu héroe y `Fijado`
   después de bloquearlo si la detección automática de R-95 falla.

En partidas posteriores puedes dejar **Auto** activo. Si una actualización cambia el retrato base
o si una lectura no es suficientemente segura, la app debe dejar el slot sin afirmar en lugar de
inventar un héroe; corrígelo desde el editor. La corrección manual siempre tiene prioridad.

## Pantalla de carga: confirmar los diez héroes

Usa este paso solo en la pantalla intermedia posterior a los picks y anterior al mapa: se ven
cinco tarjetas de un equipo y cinco del otro, normalmente en dos filas. No es el marcador ni la
pantalla de selección.

1. Deja abierta esa pantalla de carga, sin cambiar de menú.
2. Mantén **Selección** activa.
3. Abre la burbuja y pulsa **Confirmar equipos antes de partida**.
4. La burbuja se oculta aproximadamente uno o dos segundos para tomar un único fotograma y luego
   se abre el editor de equipos.
5. Revisa los slots. Corrige solamente los que falten o estén mal. Al confirmar una tarjeta en
   esta pantalla, la app aprende la tarjeta de carga de forma separada del retrato de selección.

Las skins no se tratan como si fueran el retrato base. Si la app no puede demostrar una identidad
con suficiente evidencia, deja el slot sin resolver para que lo confirmes manualmente.

## Partida normal

1. En la burbuja, elige **Normal** y **Selección**.
2. Durante la selección normal, introduce o confirma solo los aliados visibles. La app no debe
   inventar enemigos que el juego aún no muestra.
3. Cuando aparezca la pantalla de carga con ambos equipos, usa **Confirmar equipos antes de
   partida** para revisar las diez posiciones.
4. Al entrar al mapa, pulsa **Partida**.

## Verificación durante la partida: marcador de equipos

Esta es la acción que buscabas en la pantalla del marcador con héroes, oro e ítems.

1. Ya dentro del mapa, abre el marcador de HoK que muestra los dos equipos, retratos, títulos,
   oro e ítems. Déjalo inmóvil y completamente visible.
2. Abre la burbuja y asegúrate de haber pulsado **Partida**. Si no lo hiciste, el botón siguiente
   estará tenue.
3. En la sección superior **Corrección y verificación**, pulsa **Verificar equipos y actualizar
   compra**.
4. La burbuja se oculta brevemente para no tapar el marcador. Espera uno o dos segundos.
5. Se abrirá el editor con los diez slots. La lectura usa los títulos que se ven junto a los
   retratos, por ejemplo `El Boticario`, `El Príncipe Caído` o `La Maga de Fuego`. Corrige cualquier
   slot faltante con el selector manual.
6. Cuando tu héroe y los enemigos estén confirmados, el panel muestra **Compra siguiente** con
   recomendaciones adaptadas a la composición.

Actualmente el botón usa los títulos del marcador para verificar héroes y luego actualiza la
recomendación de compra. No pretende leer con certeza cada icono individual de ítem ni el oro
actual de cada jugador; esos iconos necesitan un catálogo visual validado aparte. No bases una
decisión crítica en que la app haya leído un ítem ya comprado.

## Si algo no responde

- **No aparece la burbuja:** detén el asistente, abre la app principal y concede de nuevo la
  superposición y la captura.
- **No aparece Manual o el marcador:** confirma que instalaste la APK 1.0 actual. En el panel
  expandido las tres acciones aparecen arriba, bajo `Corrección y verificación`.
- **El marcador está tenue:** pulsa **Partida** primero.
- **La confirmación previa está tenue:** pulsa **Selección** primero y úsala únicamente en la
  pantalla de carga de dos filas.
- **La lista de héroes no se abre:** toca **Editar héroes manualmente**, luego toca un slot y el
   rol o `Todos`. El buscador admite el nombre canónico y los títulos españoles verificados;
   por ejemplo `Angela`, `La Maga de Fuego`, `Kaizer` y `Kaiser`. No intentes arrastrar desde el contenido; solo la cabecera azul arrastra la
  burbuja.
- **El panel es más largo que la pantalla:** desliza dentro del panel. El panel no debe ocupar más
  del 72 % de la altura del teléfono.
- **EMUI la cierra:** revisa los tres permisos de inicio/batería indicados al principio.

## Límites honestos de esta candidata

La app fue compilada, probada y ajustada para JKM-LX3, pero aún necesita prueba física de la
interfaz sobre HoK en ese teléfono. Ningún OCR o reconocedor de retratos puede prometer 100 % de
acierto frente a todas las skins, parches del juego y pantallas no vistas. Usa la corrección
manual y los escaneos de carga/marcador para resolver cualquier duda antes de seguir una
recomendación.
