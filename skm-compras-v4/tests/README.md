# Local regression checks

Run `node --test tests/amounts.test.mjs` for synthetic amount fixtures.
To exercise the recovered private source PDF without committing it:

```
SKM_TEST_PDF=/absolute/private/path/CT-0218-10.pdf node --test tests/amounts.test.mjs
```

The optional private test expects CLP 240000 net, 45600 IVA and 285600 total.
No production API is called and no business record is mutated. Never commit the
private PDF, screenshots, runtime storage, credentials or generated test signatures.

The current amount extractor accepts only a unique explicit Neto/IVA/Total trio
that satisfies 19% IVA and the sum (rounding tolerance CLP 1). Exempt quotations,
foreign currencies, ambiguous multiple totals, missing fields and fractional CLP
are intentionally sent to review, not guessed. Native PDF rows preserve geometry;
scanned pages use OCR. This is not a certification of OCR character accuracy.
Existing unversioned extraction results remain review-required until the user
explicitly re-reads the original quotation; opening a request performs no write.

Explicit document version selection is covered by `node --test tests/document-version.test.mjs`.
Unknown versions return an error instead of silently serving a newer signed PDF.
Re-reading requires exactly one explicitly marked `kind: ORIGINAL` version.
Historical records missing that marker need source identification before re-reading;
this patch does not migrate or rewrite their metadata.
Space-separated currency groups must be complete groups of three digits. Explicit
foreign currency markers force review rather than displaying the numbers as CLP.

## Firma en primera hoja

`first-page-stamp.test.mjs` verifica conteo de páginas, MediaBox/CropBox, orientación horizontal, apilado sin repetir sellos, contenido ocupado y texto de pie, falta de espacio, rechazo seguro de rotación y firmas criptográficas/DocMDP. El fixture privado opcional comprueba dos sellos de PRUEBA sobre una copia en memoria; jamás modifica el original.

`sign-route.test.mjs` ejecuta el handler POST real con adaptadores de sesión/almacenamiento exclusivamente en memoria: éxito crea una versión de una página; ubicación insegura devuelve422 sin persistir; repetición devuelve409 sin persistir. No es una prueba HTTP autenticada ni de almacenamiento real.

Implementación conservadora: sello compacto sin marco de90x42pt (aprox32x15mm), cuatro líneas negras centradas como la referencia SKM, en zona inferior libre de primera hoja; raster2x y geometría de texto comprueban contenido, sin escalar ni agregar/quitar páginas. Sellos sucesivos ocupan posiciones separadas de derecha a izquierda dentro del28% inferior y no reimprimen los anteriores. Falta de espacio, páginas rotadas, identidad no representable y firma digital exigen revisión (422), sin escribir versión. No existe todavía editor de ubicación manual: bloqueo significa revisión, no reparación automática. Históricos con hojas extra quedan intactos.

Dependencias servidor: canvas0.1.80 compatible con PDF.js4, módulos externos en Next y trazado de fuentes/worker. El build local verifica empaquetado; despliegue y flujo autenticado real siguen pendientes de autorización/validación.

### Diseño compacto propuesto (pendiente de aceptación visual)

El nombre usa identidad de sesión, normalizando únicamente mayúsculas a formato de nombre para legibilidad; nunca se acorta ni sustituye por otra identidad. Sin identificador/cargo confiable, el sello operativo muestra acción (FIRMADO/APROBADO/OBSERVADO/RECHAZADO) y rol explícitamente etiquetado, no RUT ni cargo profesional inventados. El endpoint no acepta rut/title del request; los campos opcionales del helper se usan solo con placeholders en QA. No se almacenó el RUT, foto ni firma manuscrita de referencia en el código. No hay imitación de firma manuscrita. Auditoría técnica (UUID/email/fecha) permanece fuera del timbre visual.

El primer diseño ancho fue rechazado y sustituido por esta propuesta. Sigue siendo WIP no autorizado para publicación mientras el usuario revisa el aspecto y los campos de identidad.

## OCR real (27 October 2026)
`node tests/ocr-corpus.mjs --pipeline` executes the SAME extraction module used by the browser against reproducibly generated image-only/native/mixed PDFs, using actual Tesseract.js 6 Spanish data (downloaded model, local recognition; no document upload). Optional `SKM_TEST_PDF` rasterizes a private original locally; `SKM_SCAN_PDF` reads a private scan directly. Both must remain outside the repository. Reports and generated artifacts are under ignored `test-results/`; never include private documents or reports in a distributed ZIP.

OCR output is a **candidate**, not human verification. Engine word scores are not calibrated probabilities. Low-scoring recognized numeric fields are marked `?`; numbers are not replaced with guessed arithmetic. Native text is labeled arithmetic consistency, not certainty. Currency outside CLP, conflicting complete page totals and unresolved readings remain review cases. The application extracts summary Neto/IVA/Total, NOT validated item quantities/prices.

Resource limits: maximum 10 pages, render bounded to 3500px per side (~300dpi), up to nine OCR attempts per unresolved page, 15s per recognition and 90s cooperative document budget (cleanup and synchronous rendering may add time). Worker terminates on completion/error/cancel. Closing the reading component cancels recognition; this is not a guarantee that an already-started server write can be rolled back. Tests run in Node, not a low-end mobile browser; mobile memory/performance and interactive cancellation still require browser QA.

Adversarial typography (O versus 0, visually separated digits) can be collapsed by the OCR engine even at high word scores. This limitation must remain explicit; an OCR candidate badge does not make an incorrect numeric interpretation correct. See corpus outcomes for actual exact/incorrect/ambiguous/abstained counts, not simply test exit status.
