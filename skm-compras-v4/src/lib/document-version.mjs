// An explicit version request must never silently select the latest PDF.
export function requestedDocument(item, version) {
  if(!Number.isInteger(version)||version<1)return null;
  const all=[...(Array.isArray(item?.versions)?item.versions:[]),...(Array.isArray(item?.documents)?item.documents:[])];
  const d=all.find(x=>Number(x.version)===version);
  if(!d)return null;
  const pathname=d.pathname||d.path||d.blobPath||d.pdfPath||d.storagePath||d.key||d.blob?.pathname||d.file?.pathname;
  return pathname?{...d,pathname,filename:d.filename||d.name||d.originalName||'cotizacion.pdf'}:null;
}
