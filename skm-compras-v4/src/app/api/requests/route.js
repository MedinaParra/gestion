import crypto from 'node:crypto';
import {NextResponse} from 'next/server';
import {put} from '@vercel/blob';
import {PDFDocument} from 'pdf-lib';
import {authorizeAccount,validateSignerEmails} from '../../../lib/user-access.mjs';
import {getSession,requireUser,roleFor,ownerEmail} from '../../../lib/session';
import {readState,writeState,addAudit,safeName,nextId} from '../../../lib/store';
import {validateAmounts,EXTRACTION_VERSION} from '../../../lib/amounts.mjs';
import {requireAdmin,requireSameOrigin,moveToTrash,fail} from '../../../lib/request-lifecycle.mjs';
import {publicRequest} from '../../../lib/profile.mjs';
export const runtime='nodejs';

export async function GET(req){try{const user=requireUser(await getSession());const trash=new URL(req.url).searchParams.get('view')==='trash';if(trash)requireAdmin(user,roleFor);const state=await readState();authorizeAccount(user,state,ownerEmail());return NextResponse.json({requests:state.requests.filter(r=>trash?Boolean(r.deletedAt):!r.deletedAt).map(publicRequest)},{headers:{'Cache-Control':'private, no-store'}})}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible procesar la solicitud.'},{status:e.status||500})}}

export async function PATCH(req){try{
 const user=requireAdmin(requireUser(await getSession()),roleFor);requireSameOrigin(req);
 const body=await req.json();if(body.confirmId!==body.id)fail('Confirma el identificador de la solicitud',400);const state=await readState();authorizeAccount(user,state,ownerEmail());const item=moveToTrash(state,user,body.id,body.action,addAudit);
 await writeState(state);return NextResponse.json({request:publicRequest(item)});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible procesar la solicitud.'},{status:e.status||500})}}

export async function POST(req){try{
  const user=requireUser(await getSession());requireSameOrigin(req);const declared=Number(req.headers.get('content-length')||0);if(declared>21*1024*1024)fail('La solicitud supera el límite permitido.',413);const fd=await req.formData();const file=fd.get('file');
  const ot=String(fd.get('ot')||'').trim(),supplier=String(fd.get('supplier')||'').trim(),description=String(fd.get('description')||'').trim();
  if(ot.length>120||supplier.length>240||description.length>4000)fail('Los datos de la solicitud superan el límite permitido.',400);
  if(String(fd.get('ocr')||'').length>128*1024||String(fd.get('signers')||'').length>16000)fail('Los datos adjuntos superan el límite permitido.',413);
  if(!ot||!supplier||!file||file.type!=='application/pdf')return NextResponse.json({error:'OT, proveedor y cotización PDF son obligatorios'},{status:400});
  if(file.size>20*1024*1024)return NextResponse.json({error:'El PDF supera 20 MB'},{status:413});
  const state=await readState();authorizeAccount(user,state,ownerEmail());
  const signers=validateSignerEmails(state,String(fd.get('signers')||'').split(/[;,\n]/).map(x=>x.trim()).filter(Boolean),ownerEmail());
  const id=nextId(state.requests),bytes=Buffer.from(await file.arrayBuffer());
  if(bytes.length!==file.size||bytes.length<16||!bytes.subarray(0,8).toString('ascii').startsWith('%PDF-')||!bytes.subarray(-1024).toString('ascii').includes('%%EOF'))fail('El archivo no es un PDF válido.',422);
  try{const pdf=await PDFDocument.load(bytes,{updateMetadata:false,throwOnInvalidObject:true});if(pdf.getPageCount()<1||pdf.getPageCount()>100)fail('El PDF debe tener entre 1 y 100 páginas.',422);for(const page of pdf.getPages()){const {width,height}=page.getSize();if(!Number.isFinite(width)||!Number.isFinite(height)||width<=0||height<=0||width*height>6000000)fail('El PDF tiene dimensiones no admitidas.',422)}}catch(e){if(e.status)throw e;fail('El PDF está dañado, cifrado o no puede verificarse.',422)}
  const sha256=crypto.createHash('sha256').update(bytes).digest('hex');
  const pathname=`skm-compras/requests/${id}/v1-${crypto.randomUUID()}-${safeName(file.name||'cotizacion.pdf')}`;
  await put(pathname,bytes,{access:'private',contentType:'application/pdf',addRandomSuffix:false});
  let ocr=null;try{ocr=JSON.parse(String(fd.get('ocr')||'null'))}catch{}
  const verified=ocr?.extractionVersion===EXTRACTION_VERSION && ['VERIFIED','OCR_CANDIDATE'].includes(ocr?.status) && validateAmounts(ocr?.amounts);
  const amounts=verified?ocr.amounts:{neto:null,iva:null,total:null},now=new Date().toISOString();
  if(ocr)ocr={method:String(ocr.method||'OCR').slice(0,64),amounts,status:verified?'OCR_CANDIDATE':'REVIEW_REQUIRED',confidence:verified&&Number.isFinite(ocr.confidence)?Math.max(0,Math.min(100,ocr.confidence)):null,confidenceKind:'CLIENT_CANDIDATE',numericEvidence:null,textPreview:String(ocr.textPreview||'').slice(0,2500),extractionVersion:EXTRACTION_VERSION};
  const item={id,ot,supplier,description,status:'IN_SIGNATURES',createdAt:now,createdBy:user.email,updatedAt:now,signers,signatures:[],amounts,ocr:ocr?{...ocr,at:now,user:user.email}:null,amount:Number(amounts.neto||0),neto:Number(amounts.neto||0),iva:Number(amounts.iva||0),total:Number(amounts.total||0),versions:[{version:1,pathname,filename:file.name||'cotizacion.pdf',sha256,createdAt:now,createdBy:user.email,kind:'ORIGINAL'}]};
  state.requests.unshift(item);addAudit(state,user,'REQUEST_CREATED',id,{ot,supplier,sha256,ocrMethod:ocr?.method||null});await writeState(state);return NextResponse.json({request:publicRequest(item)});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible crear la solicitud'},{status:e.status||500})}}
