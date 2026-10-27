# Administración y directorio institucional — alcance final
Base17d1670. Implementación sin cambios reales de usuarios ni documentos.

- Sólo el propietarioADMIN configurado gestiona cuentasGoogle previamente observadas. No crea admins, no cambia propietario, no se modifica/suspende a sí mismo ni al propietario. No harddelete.
- USER permite operación ordinaria, no firma; SIGNER conserva capacidad histórica de USER para firmar según ruta. Campos operativos ausentes conservan compatibilidad, no escriben migraciones. Roles explícitos desconocidos no habilitan firma; estados desconocidos bloquean.
- Suspender/cambiar rol incrementa generación de sesión. Reactivar no revive cookies previas; login nuevo obligatorio. Servidor reautoriza cada petición y snapshot usado para guardar; CAS impide guardar sobre suspensión concurrente.
- Directorio contiene sólo observados activos Googleissuer/sub verificados y rolSIGNER/ADMIN. Cuentaslegacy sin issuer/sub deben iniciar sesión para figurar; no consulta contactos ni directorio externo. Nombre/email solamente, sin RUT/foto/firma. Selección múltiple ordenada; servidor vuelve a comprobar disponibilidad. Aprobaciónfinal sigue sóloADMIN+FINAL_APPROVAL.
- Googletoken validado por tokeninfo: audience,email_verified,dominiohd exacto,issuer,subject,expiración; configuración explícita requerida. No se vincula automáticamente subject existente a email nuevo ni se sustituye identidad. Origin requerido; sesión sólo trascommitCAS. Sin rotación de secretos.
- PDFentrada validado por estructura,parser,páginas/dimensiones y tamaño. OCR sólo creator/firmante/admin, nunca terminal, originalversion/hash real y CAS; montos enviados cliente son candidatos, no VERIFIEDautoritativo.
- Auditoría de administración conserva actor,cuenta objetivo,antes/después; no datos de perfil. Cambiar acceso no altera firmas/versiones históricas.

Pruebas offline usan fixtures sintéticos, sin datos reales, network, tokens o cambios de producción. Build final y conteos en evidencia. No validado guardarperfiles reales/multifirma real ni recuperaciónproveedor; runbook separado requiere autorización. Confirmacionespercuenta son obligatorias trasdespliegue; desplegar código no ejecutaaccionesadministrativas.
