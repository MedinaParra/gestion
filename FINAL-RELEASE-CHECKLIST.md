# Royal Spin 1.5.0-rc1 — Cierre técnico

## Alcance que define 100%

Este 100% corresponde al roadmap técnico del prototipo Android con créditos ficticios. No significa certificación de casino con dinero real ni reemplaza arte, música o QA comercial externo.

## Matemática

- [x] Tablero 5×3 y 20 líneas.
- [x] Wild, tabla de pagos y apuestas de 1–5 créditos por línea.
- [x] Resultado calculado antes de la animación.
- [x] Stake Engine Math SDK separado del frontend.
- [x] RTP teórico versionado: 95,4796451%.
- [x] Generación CI de 5.000 rondas trazables.
- [x] Prueba Java de estrés de 100.000 rondas e invariantes.

## Animación

- [x] Reloj sincronizado con Choreographer.
- [x] Aceleración, crucero y desaceleración por carrete.
- [x] Deformación cilíndrica y atenuación por profundidad.
- [x] Desenfoque direccional simulado según velocidad.
- [x] Paradas escalonadas, rebote amortiguado e impacto.
- [x] Anticipación únicamente ante un resultado real de 4+ símbolos o premio ≥5×.
- [x] Cámara cinematográfica proporcional al multiplicador.
- [x] Celebraciones Bell, BAR, Seven, Diamond, Wild y cartas.
- [x] BIG WIN, MEGA WIN y ROYAL WIN.
- [x] Botón para omitir sin perder ni duplicar pagos.

## Audio y háptica

- [x] Buses separados para UI, mecánica y recompensa.
- [x] Secuencias por símbolo.
- [x] Patrones hápticos limitados por evento.
- [x] Cancelación al pausar, omitir o desactivar.

## Rendimiento y accesibilidad

- [x] Presupuesto visual automático ULTRA/ALTA/LITE.
- [x] Conteo de FPS y fotogramas lentos.
- [x] Modo de movimiento reducido persistente.
- [x] Menos partículas, cámara y duración en movimiento reducido.
- [x] Descripción de contenido principal.
- [x] Interfaz escalable manteniendo relación 360×800.
- [x] Aplicación sin red ni tráfico HTTP.

## Seguridad y distribución

- [x] Créditos exclusivamente ficticios.
- [x] Sin depósitos, retiros, cuentas ni enlaces de apuestas.
- [x] Backup Android desactivado.
- [x] Tráfico en texto claro desactivado.
- [x] Orientación vertical y aceleración de hardware.
- [x] APK debug reproducible desde GitHub Actions.
- [x] SHA-256 e integridad ZIP generados en CI.
- [ ] Firma comercial de Play App Signing: requiere la cuenta del propietario.
- [ ] Ficha, política de privacidad y clasificación de tienda: requieren datos comerciales del propietario.

## Validaciones CI

1. `testDebugUnitTest`
2. `lintDebug`
3. `assembleDebug`
4. Simulación Stake Engine de 5.000 rondas
5. Prueba de estrés Java de 100.000 rondas
6. Instalación en emulador Android con KVM
7. Capturas: reposo, anticipación, campana, BAR, 7, gema y Wild
8. Hash SHA-256 y prueba de integridad del APK

## Próximas mejoras opcionales, fuera del 100% técnico

- Reemplazar tonos sintetizados por audio de estudio licenciado.
- Migrar símbolos a Spine/Rive o sprites dibujados por artista.
- Agregar modelos 3D selectivos y shaders PBR.
- Firmar AAB comercial y publicar en tiendas.
- Ejecutar QA en una matriz de teléfonos físicos.
