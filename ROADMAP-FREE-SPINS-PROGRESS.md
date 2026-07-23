# Royal Spin 2.0 — avance aproximado 25%

## Completado

### v1.6 Feature Math Foundation
- `GameMode.BASE_GAME` y `GameMode.FREE_SPINS`.
- Trigger consolidado: 3 o más WILD consecutivos desde el primer reel en una línea activa.
- 30 Free Spins iniciales.
- Retrigger de +10 dentro del bonus.
- Máximo de 90 tiradas otorgadas por sesión.
- Varias líneas WILD generan una sola sesión.
- Resultado matemático incluye `FeatureTrigger`.
- Pruebas de trigger, no-trigger, retrigger y múltiples líneas.

### Inicio de v1.7 Feature Scene
- Controlador separado `FeatureSessionController`.
- Estado persistible `FeatureState`.
- Recuperación mediante `FeatureSessionRepository`.
- Frontend `RoyalSpinFreeSpinsView`.
- Intro de 30 juegos gratis.
- HUD de tiradas restantes y premio acumulado.
- Ejecución automática de tiradas gratis.
- Apuesta bloqueada durante el bonus.
- Overlay de retrigger +10.
- Resumen de premio del bonus.
- Free Spins sin descuento de créditos.

## Pendiente para el siguiente 25%

- Implementar Free Spins anidados en Stake Engine Math SDK.
- Simular RTP base, RTP bonus y RTP total.
- Reequilibrar pesos y tabla de pagos.
- Timeline cinematográfica avanzada del WILD.
- Animaciones `landing`, `win3`, `win4`, `win5` por símbolo.
- Audio real por eventos y música del bonus.
- Recuperación transaccional con `roundId` y `payoutCommitted` persistentes.
- Pruebas instrumentadas de destrucción del proceso.

## Decisión temporal de RTP

La interfaz muestra `RTP BONUS EN VALIDACIÓN`. No se publicará un RTP total nuevo hasta simular la función completa con retriggers.

## Definición del hito

Este avance corresponde aproximadamente al 25% del roadmap nuevo Royal Spin 2.0. No representa el 25% de una certificación de apuestas reales ni de producción artística comercial.
