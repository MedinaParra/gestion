import {get,put} from '@vercel/blob';

const PATH='skm-compras/meta/requests.json';
const EMPTY={requests:[],audit:[],users:{}};

async function streamText(stream){const r=stream.getReader(),parts=[];for(;;){const x=await r.read();if(x.done)break;parts.push(Buffer.from(x.value))}return Buffer.concat(parts).toString('utf8')}
const revisions=new WeakMap();
export async function readState(){
  // A missing store is empty; a failed read is never an empty, writable snapshot.
  // Compression can weaken the HTTP ETag (W/), which cannot satisfy If-Match.
  // Request the identity representation: the returned strong ETag binds this exact body.
  const r=await get(PATH,{access:'private',useCache:false,headers:{'Accept-Encoding':'identity'}});
  if(!r){const s=structuredClone(EMPTY);revisions.set(s,null);return s}
  if(r.statusCode!==200||!r.blob?.etag||/^W\//i.test(r.blob.etag)){const e=new Error('No se obtuvo una revisión fuerte del registro. No se guardaron cambios; vuelve a intentar.');e.status=503;throw e}
  const parsed=JSON.parse(await streamText(r.stream));
  if(!Array.isArray(parsed.requests)||!Array.isArray(parsed.audit))throw new Error('Registro inválido; escritura bloqueada');
  const s={...parsed,users:parsed.users||{}};revisions.set(s,r.blob.etag);return s;
}
export async function writeState(s){
  if(!revisions.has(s))throw new Error('No se puede guardar sin una revisión del registro');
  const etag=revisions.get(s);
  try{
    const result=await put(PATH,JSON.stringify(s),{access:'private',contentType:'application/json',addRandomSuffix:false,...(etag?{ifMatch:etag,allowOverwrite:true}:{allowOverwrite:false})});
    revisions.set(s,result.etag);return s;
  }catch(error){
    if(error?.status===412||error?.statusCode===412||/precondition|condition.*match|already exists/i.test(`${error?.name} ${error?.message}`)){const e=new Error('El registro cambió en otra sesión. Actualiza y vuelve a intentar.');e.status=409;throw e}
    throw error;
  }
}
export function safeName(s='documento.pdf'){return String(s).normalize('NFKD').replace(/[^a-zA-Z0-9._-]+/g,'_').replace(/_+/g,'_').slice(0,120)}
export function addAudit(state,user,action,requestId=null,meta={}){state.audit=state.audit||[];state.audit.unshift({id:crypto.randomUUID(),at:new Date().toISOString(),user:user.email,name:user.name||user.email,action,requestId,meta})}
export function amountOf(r){const a=r.amounts||r.ocr?.amounts||{};return {neto:Number(a.neto||r.neto||r.amount||0)||0,iva:Number(a.iva||r.iva||0)||0,total:Number(a.total||r.total||0)||0}}
function normalizeDoc(d,r,version=1){if(!d)return null;const p=d.pathname||d.path||d.blobPath||d.pdfPath||d.storagePath||d.key||d.blob?.pathname||d.file?.pathname;return p?{...d,pathname:p,version:Number(d.version||version),filename:d.filename||d.name||d.originalName||r.filename||r.fileName||'cotizacion.pdf'}:null}
export function latestDoc(r){
  const arrays=[r.versions,r.documents,r.docs,r.files,r.attachments].filter(Array.isArray);
  for(const a of arrays){for(let i=a.length-1;i>=0;i--){const d=normalizeDoc(a[i],r,i+1);if(d)return d}}
  for(const candidate of [r.document,r.file,r.attachment,r.pdf,r]){const d=normalizeDoc(candidate,r,1);if(d)return d}
  return null;
}
export function nextId(requests=[]){let m=0;for(const r of requests){const x=String(r.id||'').match(/(\d+)$/);if(x)m=Math.max(m,Number(x[1]))}return `SC-${new Date().getFullYear()}-${String(m+1).padStart(4,'0')}`}
