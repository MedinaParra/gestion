# Perfil privado y firma visual — revisión local CotizacionesSKM

Base de producción: `cf3a05634410ad859ac847407322b104ca093244`.
Estado: implementación local; NO publicada ni desplegada.

## Funcionalidad
- Mi perfil por cuenta Google autenticada: foto, nombre de sello, cargo y RUT ingresados directamente por su titular; firma dibujada con mouse/lápiz/touch, limpiar, vista previa, guardar y revocar.
- RUT opcional, sin valores predeterminados; validación de formato y módulo 11, no consulta ni certifica identidad/asignación. El agente no recuperó, completó ni transmitió un RUT real.
- Foto PNG/JPEG máximo2MiB/4096px por lado; firma PNG máximo200KiB/2000×1000. Servidor comprueba encabezados/dimensiones antes de decodificar, re-encodea a PNG sin metadata, limita foto a512px y firma a1200×400, rechaza firma vacía/saturada y recorta márgenes. No SVG/URLs externas.
- Datos propios GET/PATCH y assets GET, incluso admin sólo su perfil. Owner viene de sesión, nunca body/query. Role permanece calculado por configuración existente, separado del cargo declarado.
- Assets privados con nombres UUID inmutables; no URLs públicas ni paths/hashes en respuesta de perfil. GET no-store, nosniff; PATCH exige mismo Origin, JSON acotado, lista de campos y expectedRevision. Escritura global conserva CAS/ETag fuerte identity del hotfix.

## Consentimiento y conservación
Guardar un trazado NO aplica una firma a documentos. Cada acto exige confirmación explícita de IDs/versiones y datos de perfil; incluir firma dibujada es un opt-in separado. La vista previa debe cargar correctamente y corresponde a esa revisión de perfil. Servidor comprueba consentimiento, versión/hash del documento, revisión de perfil, ruta de firmantes y sesión; no toma identidad/rol desde body.

Al firmar conserva snapshot inmutable del perfil y referencias/hash privados de los assets consentidos. El PNG es transitorio al renderizar, no se inserta en JSON/audit. Versiones PDF conservadas y snapshots no cambian al editar/revocar el perfil. API de listados/operaciones oculta RUT y snapshot privados de las firmas; el documento firmado puede contener los datos que su titular confirmó. Auditoría registra evento/actor existente y flags de cambio, nunca RUT, contenido de imagen ni payload de perfil. No logs de PII/tokens.

Firma gráfica es una marca visual operativa: NO firma electrónica avanzada ni firma criptográfica. Cada firma usa el perfil del titular autenticado; no hay acto de firma realizado por el agente.

## Layout
PNG + nombre/RUT/cargo/acción: bloque compacto110×72pt; sin imagen conserva bloque90×42 (110×42 cuando incluye RUT y acción)pt. Nuevos bloques adyacentes horizontalmente de derecha a izquierda en primera hoja, compatibles con ubicaciones v2/v3. Wrap arriba sólo si no cabe horizontal y zona inferior comprobada libre. Verificación raster y geometría de texto, sin páginas extras, sin escalar ni tapar contenido; colisión, agotamiento, PDF criptográficamente firmado/rotado o identidad ilegible devuelve422 sin persistir una versión.

## Verificación local
- Suite `node --test tests/*test.mjs`: ver logs adjuntos para conteo final; fixtures exclusivamente sintéticos. Incluye self-only/admin/CSRF, límites/formato/imágenesmalformadas, RUT matemático, revisión CAS, revocación histórica, snapshot, consentimiento, stale preview y selección, firma con perfil cambiado durante render, ausencia de duplicación al conflicto.
- Tests UI ejecutan componente real transpilado y eventos/hooks aislados: touch/mouse lógica, guardar/limpiar/revocar, consentimiento, conflicto/doble envío y cancelación. NO equivalen a E2E de navegador.
- PDF raster sintético inspeccionado:4 bloques horizontales y quinto en fila superior, sin páginas extra. Ninguna foto/firma/RUT real usada.
- Build Next y diffcheck finales incluidos en evidencia.
- Navegador interactivo, OAuth/Blob reales, touch en equipo físico y escritura/firma reales NO probados. Chromium local permanece bloqueado por sandbox (socket no permitido); no se reintentó ni pidió acceso/permisos nuevos.

## Concurrencia y límites conocidos
Ante conflicto CAS no se reintenta firma automáticamente ni se usa una revisión nueva con datos viejos. Assets privados subidos antes de commit fallido pueden quedar huérfanos no referenciados; no se purgan automáticamente. Revocar deshabilita usos futuros; no borra bytes necesarios para snapshots/versiones históricos. No se han modificado ni migrado registros reales.

La interfaz nueva y el servidor deben publicarse juntos: clientes antiguos sin consentimiento explícito/revisiones reciben400/409 y deben recargar. Antes de publicar se requiere revisión/autorización específica; no hubo push/deploy ni cambios de seguridad/usuarios.
