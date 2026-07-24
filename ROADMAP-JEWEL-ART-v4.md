# Royal Spin 4.0 — Jewel Art & Animation System

## Dirección visual

Esta iteración reemplaza las letras planas y los símbolos geométricos simples por un sistema de arte 2.5D inspirado en el concepto premium aprobado:

- logotipo dorado biselado con reflejos y corona integrada;
- letras A/K/Q/J convertidas en glifos joya con esmalte, molduras y gemas;
- WILD como emblema real multicapa;
- gema azul facetada con refracción;
- campana de oro con banda de rubíes;
- BAR como placa de bóveda metálica;
- 7 rubí con corte de energía;
- efectos idle, landing, win, feature y retrigger.

## Principios de animación

1. El símbolo se compone de capas independientes: sombra, base, bisel, esmalte, joyas, brillo y VFX.
2. El estado idle utiliza movimientos lentos y asincrónicos.
3. Landing comunica peso y material.
4. Win revela la identidad propia del símbolo.
5. Free Spins y retriggers añaden una transformación específica del WILD.
6. Las capas son de presentación y no pueden alterar RNG, pagos, RTP ni sesión.

## Estados

- IDLE
- SPIN_BLUR
- LANDING
- WIN_3
- WIN_4
- WIN_5
- FEATURE_TRIGGER
- RETRIGGER
- EXIT

## Implementación prevista

- `JewelArtPalette`: materiales, gradientes y luces.
- `JewelGlyphRenderer`: letras A/K/Q/J 2.5D.
- `PremiumIconRenderer`: campana, BAR, 7, gema y WILD.
- `JewelAnimationDirector`: timelines por símbolo.
- `RoyalLogoRenderer`: logotipo multicapa y corona.
- `PremiumSymbolOverlayV4`: integración con `RoyalSpinV2View`.

## Rendimiento

- Ultra: todos los reflejos, gemas y partículas.
- Alto: reduce partículas y reflejos secundarios.
- Lite: mantiene bisel, color y una sola pasada de brillo.
- Movimiento reducido: elimina rotaciones y sacudidas, conservando luminancia y legibilidad.
