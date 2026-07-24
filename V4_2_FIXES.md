# V4.2 — Correcciones de prueba real

## Panel flotante

- `ScrollView` vertical con barra de desplazamiento.
- Altura y anchura máximas calculadas mediante las dimensiones disponibles de la pantalla.
- El arrastre se realiza únicamente desde la etiqueta azul de la burbuja; ya no desde todo el panel.
- Posición limitada a los bordes visibles para evitar que el menú quede fuera de pantalla.
- Botones distribuidos en filas compactas.

## Identificación del jugador

- Coincidencia exacta y normalizada del nombre del jugador.
- OCR aceptado únicamente dentro de la columna aliada.
- Se descartan coincidencias del enemigo y lecturas con baja confianza.
- El mismo slot debe detectarse en tres fotogramas antes de aceptarse.
- La detección automática caduca después de lecturas perdidas, en lugar de quedar pegada indefinidamente.
- Control manual de slot Auto/1/2/3/4/5.

## Estado de selección

- Control Auto/Pendiente/Fijado.
- `Pendiente` mantiene las recomendaciones aunque el marcador visual se interprete erróneamente como confirmado.
- `Fijado` cambia inmediatamente al análisis estratégico.
- El override se reinicia a Auto al comenzar una sesión nueva.
