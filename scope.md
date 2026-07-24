# Alcance actual: Honor of Kings Assistant

## Incluido

- Aplicación Android para API 23 o superior.
- Catálogo local de 116 héroes.
- Tres counters y razones por héroe.
- Búsqueda y filtro manual por rol.
- Overlay iniciado bajo demanda.
- Servicio foreground y notificación.
- Validación automática de integridad.

## No incluido

- Reconocimiento automático de héroes en pantalla.
- OCR, captura de pantalla o lectura de memoria.
- Actualización automática del meta.
- API remota, cuentas, nube o analíticas.

## Criterios de aceptación

- `tools/validate_project.py` termina sin errores.
- Las pruebas unitarias del catálogo pasan.
- `assembleDebug` genera `app-debug.apk`.
- La pantalla muestra tres counters reales para un héroe conocido.
- El overlay refleja el héroe seleccionado y no duplica ventanas.
