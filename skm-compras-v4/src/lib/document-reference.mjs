// Shared pure legacy document normalization; no storage or browser side effects.
function normalizeDoc(d,r,version=1){if(!d)return null;const p=d.pathname||d.path||d.blobPath||d.pdfPath||d.storagePath||d.key||d.blob?.pathname||d.file?.pathname;return p?{...d,pathname:p,version:Number(d.version||version),filename:d.filename||d.name||d.originalName||r.filename||r.fileName||'cotizacion.pdf'}:null}
export function latestDoc(r){
  const arrays=[r.versions,r.documents,r.docs,r.files,r.attachments].filter(Array.isArray);
  for(const a of arrays){for(let i=a.length-1;i>=0;i--){const d=normalizeDoc(a[i],r,i+1);if(d)return d}}
  for(const candidate of [r.document,r.file,r.attachment,r.pdf,r]){const d=normalizeDoc(candidate,r,1);if(d)return d}
  return null;
}
