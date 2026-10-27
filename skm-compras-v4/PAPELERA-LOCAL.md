# Papelera recuperable SKM — revisión local

Base: `0b7ae6564484a28e8c8462c31068b47886c0ef66`.
Sin publicación, sin cambios de usuarios/permisos y sin operaciones sobre expedientes reales.

## Comportamiento
- Admin proviene de sesión autenticada y `roleFor(email)` con la configuración existente, nunca del body.
- PATCH `/api/requests` exige Origin mismo origen, ID válido, acción `trash`/`restore` y `confirmId` idéntico al ID. La UI pide escribir el identificador.
- Papelera conserva workflow status, versiones originales/firmadas, importes, firmas y auditoría. No purga ni elimina blobs. Historial no se trunca al superar 1.000 entradas.
- GET activo excluye eliminados; `?view=trash` sólo admin. Restaurar vuelve a activar el expediente idéntico; únicamente se retira deletedAt/deletedBy y se agrega auditoría.
- Documento, firma/aprobación/rechazo y relectura responden 410 mientras esté eliminado, incluso admin. Restaurar antes de leer/continuar. Sesión ausente 401; no-admin 403; ID inválido 400; ausente 404; acción repetida 409.
- Selección masiva se reconcilia con activos y valida antes de confirmar; errores detienen el lote y se muestran. Cada ruta protege contra eliminación concurrente.

## Integridad y concurrencia
SDK instalado/lockfile @vercel/blob 2.8.2: `get().blob.etag` y `put({ifMatch})` reales. Referencia: https://vercel.com/docs/vercel-blob . El SDK transmite x-if-match; no mutex local serverless. `readState` sólo inicia vacío si get devuelve null (404); errores/corrupción fallan cerrados. Cada writeState (incluye creación, firma, OCR y login) usa la revisión leída y responde conflicto 409, sin reintento ciego. Login emite cookie después de guardar auditoría.

PDFs se suben antes de confirmar metadata; nombres UUID por intento evitan sobrescribir o bloquear reintentos. Un conflicto puede dejar un blob privado huérfano no referenciado: no se borra automáticamente. Los PDFs y metadata no forman una transacción distribuida. Los registros previos permanecen intactos y sólo se muestran nuevas versiones confirmadas.

## Verificación
- `node --test tests/*test.mjs`: 64 tests, 62 pass, 0 fail, 2 skip (fixtures privados opcionales preexistentes no utilizados).
- Pruebas sintéticas con handlers reales/adapters memoria: admin/no-admin/sin sesión, body role falsificado, CSRF, IDs, confirmación, repetición/restauración, documentos/firmas/historial intactos, >1.000 entradas audit, lectura falla cerrada, CAS concurrente OCR y firma; eliminación gana, escritura vieja 409 sin revivir; restauración y retry firma tienen pathname distinto sin overwrite; login409 sin cookie.
- `npm run build`: exitoso Next 16.4.3.
- `git diff --check`: sin errores.
- Browser harness `tests/papelera-ui.mjs` preparado con API totalmente interceptada. Ejecución NO validada: Chromium del entorno falla creando socket con Operation not permitted. No se solicitó escalación ni acceso a PC. Para ejecutarlo en entorno apto: servidor local :3188 y `CHROMIUM_PATH=/ruta/chromium node tests/papelera-ui.mjs` (requiere Playwright). Incluye cancelación/ID incorrecto, trash/restore, selección masiva obsoleta y ocultar controles no-admin.
- No se ejecutaron OAuth/Blob real, firma/aprobación real ni eliminación/restauración real. No se borró SC-2026-0001 ni otro registro.

## Entrega
Fuentes, patch y tests únicamente; sin .env, secretos, credenciales, node_modules, .next, PDFs privados, foto de timbre ni screenshots. No push/deploy hasta autorización explícita.
