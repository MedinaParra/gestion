import {get,put} from '@vercel/blob';

const PATH='skm-compras/meta/requests.json';
const EMPTY={requests:[],audit:[],users:{}};

async function streamText(stream){const r=stream.getReader(),parts=[];for(;;){const x=await r.read();if(x.done)break;parts.push(Buffer.from(x.value))}return Buffer.concat(parts).toString('utf8')}
export async function readState(){try{const r=await get(PATH,{access:'private',useCache:false});if(!r||r.statusCode&&r.statusCode!==200)return structuredClone(EMPTY);const s=JSON.parse(await streamText(r.stream));return {requests:Array.isArray(s.requests)?s.requests:[],audit:Array.isArray(s.audit)?s.audit:[],users:s.users||{}}}catch{return structuredClone(EMPTY)}}
export async function writeState(s){await put(PATH,JSON.stringify(s),{access:'private',contentType:'application/json',allowOverwrite:true,addRandomSuffix:false});return s}
export function safeName(s='documento.pdf'){return String(s).normalize('NFKD').replace(/[^a-zA-Z0-9._-]+/g,'_').replace(/_+/g,'_').slice(0,120)}
export function addAudit(state,user,action,requestId=null,meta={}){state.audit=state.audit||[];state.audit.unshift({id:crypto.randomUUID(),at:new Date().toISOString(),user:user.email,name:user.name||user.email,action,requestId,meta});state.audit=state.audit.slice(0,1000)}
export function amountOf(r){const a=r.amounts||r.ocr?.amounts||{};return {neto:Number(a.neto||r.neto||r.amount||0)||0,iva:Number(a.iva||r.iva||0)||0,total:Number(a.total||r.total||0)||0}}
function normalizeDoc(d,r,version=1){if(!d)return null;const p=d.pathname||d.path||d.blobPath||d.pdfPath||d.storagePath||d.key||d.blob?.pathname||d.file?.pathname;return p?{...d,pathname:p,version:Number(d.version||version),filename:d.filename||d.name||d.originalName||r.filename||r.fileName||'cotizacion.pdf'}:null}
export function latestDoc(r){
  const arrays=[r.versions,r.documents,r.docs,r.files,r.attachments].filter(Array.isArray);
  for(const a of arrays){for(let i=a.length-1;i>=0;i--){const d=normalizeDoc(a[i],r,i+1);if(d)return d}}
  for(const candidate of [r.document,r.file,r.attachment,r.pdf,r]){const d=normalizeDoc(candidate,r,1);if(d)return d}
  return null;
}
export function nextId(requests=[]){let m=0;for(const r of requests){const x=String(r.id||'').match(/(\d+)$/);if(x)m=Math.max(m,Number(x[1]))}return `SC-${new Date().getFullYear()}-${String(m+1).padStart(4,'0')}`}
