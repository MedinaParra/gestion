import {get,put} from '@vercel/blob';

const PATH='skm-compras/meta/requests.json';
const EMPTY={requests:[],audit:[],users:{}};

async function streamText(stream){const r=stream.getReader(),parts=[];for(;;){const x=await r.read();if(x.done)break;parts.push(Buffer.from(x.value))}return Buffer.concat(parts).toString('utf8')}
export async function readState(){try{const r=await get(PATH,{access:'private',useCache:false});if(!r||r.statusCode&&r.statusCode!==200)return structuredClone(EMPTY);const s=JSON.parse(await streamText(r.stream));return {requests:Array.isArray(s.requests)?s.requests:[],audit:Array.isArray(s.audit)?s.audit:[],users:s.users||{}}}catch{return structuredClone(EMPTY)}}
export async function writeState(s){await put(PATH,JSON.stringify(s),{access:'private',contentType:'application/json',allowOverwrite:true,addRandomSuffix:false});return s}
export function safeName(s='documento.pdf'){return String(s).normalize('NFKD').replace(/[^a-zA-Z0-9._-]+/g,'_').replace(/_+/g,'_').slice(0,120)}
export function addAudit(state,user,action,requestId=null,meta={}){state.audit=state.audit||[];state.audit.unshift({id:crypto.randomUUID(),at:new Date().toISOString(),user:user.email,name:user.name||user.email,action,requestId,meta});state.audit=state.audit.slice(0,1000)}
export function amountOf(r){const a=r.amounts||r.ocr?.amounts||{};return {neto:Number(a.neto||r.neto||r.amount||0)||0,iva:Number(a.iva||r.iva||0)||0,total:Number(a.total||r.total||0)||0}}
export function latestDoc(r){
  const arrays=[r.versions,r.documents].filter(Array.isArray);
  for(const a of arrays){if(a.length){const d=a[a.length-1];const p=d.pathname||d.path||d.blobPath||d.pdfPath; if(p)return {...d,pathname:p,version:d.version||a.length,filename:d.filename||d.name||r.filename||'cotizacion.pdf'}}}
  const d=r.document||r.file||r;const p=d?.pathname||d?.path||d?.blobPath||d?.pdfPath||r.pathname||r.pdfPath||r.blobPath;
  return p?{...d,pathname:p,version:d.version||1,filename:d.filename||d.name||r.filename||'cotizacion.pdf'}:null;
}
export function nextId(requests=[]){let m=0;for(const r of requests){const x=String(r.id||'').match(/(\d+)$/);if(x)m=Math.max(m,Number(x[1]))}return `SC-${new Date().getFullYear()}-${String(m+1).padStart(4,'0')}`}
