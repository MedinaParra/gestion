import crypto from 'node:crypto';
import {NextResponse} from 'next/server';
import {put} from '@vercel/blob';
import {getSession,requireUser} from '../../../lib/session';
import {readState,writeState,addAudit,safeName,nextId} from '../../../lib/store';
import {validateAmounts,EXTRACTION_VERSION} from '../../../lib/amounts.mjs';
export const runtime='nodejs';

export async function GET(){try{requireUser(await getSession());const state=await readState();return NextResponse.json({requests:state.requests||[]})}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}

export async function POST(req){try{
  const user=requireUser(await getSession());const fd=await req.formData();const file=fd.get('file');
  const ot=String(fd.get('ot')||'').trim(),supplier=String(fd.get('supplier')||'').trim(),description=String(fd.get('description')||'').trim();
  if(!ot||!supplier||!file||file.type!=='application/pdf')return NextResponse.json({error:'OT, proveedor y cotización PDF son obligatorios'},{status:400});
  if(file.size>20*1024*1024)return NextResponse.json({error:'El PDF supera 20 MB'},{status:413});
  const state=await readState(),id=nextId(state.requests),bytes=Buffer.from(await file.arrayBuffer()),sha256=crypto.createHash('sha256').update(bytes).digest('hex');
  const pathname=`skm-compras/requests/${id}/v1-${safeName(file.name||'cotizacion.pdf')}`;
  await put(pathname,bytes,{access:'private',contentType:'application/pdf',addRandomSuffix:false});
  let signers=String(fd.get('signers')||'').split(/[;,\n]/).map(x=>x.trim().toLowerCase()).filter(x=>x.endsWith('@skmindustrial.cl'));
  if(!signers.includes(user.email.toLowerCase()))signers.unshift(user.email.toLowerCase());signers=[...new Set(signers)];
  let ocr=null;try{ocr=JSON.parse(String(fd.get('ocr')||'null'))}catch{}
  const verified=ocr?.extractionVersion===EXTRACTION_VERSION && ['VERIFIED','OCR_CANDIDATE'].includes(ocr?.status) && validateAmounts(ocr?.amounts);
  const amounts=verified?ocr.amounts:{neto:null,iva:null,total:null},now=new Date().toISOString();
  if(ocr)ocr={...ocr,amounts,status:verified?ocr.status:'REVIEW_REQUIRED',confidence:verified&&Number.isFinite(ocr.confidence)?Math.max(0,Math.min(100,ocr.confidence)):null,extractionVersion:EXTRACTION_VERSION};
  const item={id,ot,supplier,description,status:'IN_SIGNATURES',createdAt:now,createdBy:user.email,updatedAt:now,signers,signatures:[],amounts,ocr:ocr?{...ocr,at:now,user:user.email}:null,amount:Number(amounts.neto||0),neto:Number(amounts.neto||0),iva:Number(amounts.iva||0),total:Number(amounts.total||0),versions:[{version:1,pathname,filename:file.name||'cotizacion.pdf',sha256,createdAt:now,createdBy:user.email,kind:'ORIGINAL'}]};
  state.requests.unshift(item);addAudit(state,user,'REQUEST_CREATED',id,{ot,supplier,sha256,ocrMethod:ocr?.method||null});await writeState(state);return NextResponse.json({request:item});
}catch(e){return NextResponse.json({error:e.message||'No fue posible crear la solicitud'},{status:e.status||500})}}
