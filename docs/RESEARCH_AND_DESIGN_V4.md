# HoK Draft Assistant V4 — investigación y decisiones

Fecha de corte: 2026-07-24. Servidor objetivo: Honor of Kings Global / International.

## Flujo del draft

Las reglas oficiales describen un draft serpiente. En la fase de picks, el primer lado realiza una selección, el contrario responde con dos, el primer lado realiza dos, y la secuencia continúa con bloques alternados. El perfil visual de esta V4 no depende de que el lado izquierdo o derecho comience: prioriza el marcador visual de bloqueo, el retrato en preselección y el seguimiento temporal.

Las dos capturas reales de 1600×738 entregadas por el usuario se validaron como:

- lado aliado izquierdo: cinco picks confirmados;
- R-95: slot aliado 2;
- lado enemigo derecho: cuatro picks confirmados;
- slot enemigo 5: retrato en preselección, todavía sin marcador rojo de bloqueo;
- tercera captura: estado IN_GAME.

El resultado reproducible está en `docs/user_screenshot_calibration.json`.

## Reconocimiento visual

La arquitectura sigue un enfoque en dos etapas coherente con investigación publicada sobre reconocimiento de héroes en video de Honor of Kings:

1. detectar regiones y campamento/slot;
2. reconocer identidad mediante señales visuales.

La V4 combina:

- OCR local para texto visible;
- regiones fijas normalizadas para los 10 slots;
- marcador azul/rojo de confirmación;
- seguimiento temporal para evitar falsos bloqueos;
- huellas perceptuales locales de retratos;
- correcciones manuales que enseñan un retrato sin guardar la captura.

## Recomendación

El motor no trata el draft como un único duelo 1 contra 1. Puntúa:

- ventaja documentada contra cada enemigo detectado;
- riesgos cuando un enemigo ya seleccionado contrarresta al candidato;
- cobertura de varias amenazas;
- rol faltante y duplicación de roles;
- necesidad básica de primera línea;
- fuerza de meta y confianza de cada relación.

Este enfoque es consistente con la literatura de recomendación de composiciones y JueWuDraft, que modela la selección como un problema combinatorio y multirronda.

## Privacidad y operación

- Sin permiso `INTERNET`.
- Sin servidor, telemetría ni cuenta.
- OCR y huellas se procesan localmente.
- No se guardan capturas.
- Las huellas visuales son compactas y no reversibles.
- La corrección manual se conserva en almacenamiento privado de la aplicación.

## Fuentes utilizadas

- Honor of Kings, rulebooks oficiales de Ban & Pick 2025.
- Honor of Kings / Camp, datos oficiales de héroes y cambios.
- HoKStats.gg, base independiente del servidor internacional y contexto de meta Season 15 HOK Plus 2.0.
- Yao, Sun y Chen, *Understanding Video Content: Efficient Hero Detection and Recognition for the Game Honor of Kings*.
- Chen et al., *Which Heroes to Pick? Learning to Draft in MOBA Games with Neural Networks and Tree Search*.
- Chen et al., *The Art of Drafting: A Team-Oriented Hero Recommendation System for MOBA Games*.
