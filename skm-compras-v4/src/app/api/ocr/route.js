import crypto from 'node:crypto';
import {get} from '@vercel/blob';
import {publicRequest,readProfileJson} from '../../../lib/profile.mjs';
import {requireActive,requestId,requireSameOrigin,fail} from '../../../lib/request-lifecycle.mjs';
import {NextResponse} from 'next/server';
import {getSession,requireUser,ownerEmail} from '../../../lib/session';
import {authorizeAccount} from '../../../lib/user-access.mjs';
import {readState,writeState,addAudit} from '../../../lib/store';
import {validateAmounts,EXTRACTION_VERSION,originalVersion} from '../../../lib/amounts.mjs';
import {requestedDocument} from '../../../lib/document-version.mjs';
export const runtime='nodejs';
export async function POST(req){try{
  let user=requireUser(await getSession());requireSameOrigin(req);const body=await readProfileJson(req,128*1024);if(!body||typeof body!=='object'||Array.isArray(body))fail('Lectura no válida.',400);
  const id=requestId(body.id),state=await readState();user=authorizeAccount(user,state,ownerEmail());const item=state.requests.find(x=>x.id===id);
  if(!item)fail('Solicitud no encontrada',404);requireActive(item);
  const email=user.email.toLowerCase(),inRoute=(item.signers||[]).some(e=>String(e).toLowerCase()===email);
  if(user.role!=='ADMIN'&&String(item.createdBy||'').toLowerCase()!==email&&!inRoute)fail('No tienes permiso para releer esta solicitud.',403);
  if(['APPROVED','REJECTED'].includes(item.status))fail('La solicitud está cerrada; no admite cambios de lectura.',409);
  if(!['DRAFT','IN_REVIEW','IN_SIGNATURES','FINAL_APPROVAL'].includes(item.status))fail('El estado de la solicitud requiere revisión.',409);
  const version=originalVersion(item),src=requestedDocument(item,version);
  if(!version||body.originalVersion!==version||!src?.pathname||typeof src.sha256!=='string'||!/^[a-f0-9]{64}$/i.test(src.sha256)||body.expectedSha256!==src.sha256)fail('La versión original cambió o no tiene una huella verificable. Revisa el documento.',409);
  const blob=await get(src.pathname,{access:'private',useCache:false});if(!blob)fail('No se encontró el PDF original.',404);
  const reader=blob.stream.getReader(),parts=[];let length=0;
  for(;;){const chunk=await reader.read();if(chunk.done)break;length+=chunk.value.length;if(length>20*1024*1024){await reader.cancel();fail('El PDF supera el límite de lectura.',413)}parts.push(Buffer.from(chunk.value))}
  if(crypto.createHash('sha256').update(Buffer.concat(parts)).digest('hex')!==src.sha256.toLowerCase())fail('El PDF no coincide con la versión original confirmada.',409);
  // Client OCR is evidence to review, never an authoritative VERIFIED assertion.
  const candidate=body.extractionVersion===EXTRACTION_VERSION&&['VERIFIED','OCR_CANDIDATE'].includes(body.status)&&validateAmounts(body.amounts);
  const amounts=candidate?{neto:body.amounts.neto,iva:body.amounts.iva,total:body.amounts.total}:{neto:null,iva:null,total:null};
  item.amounts=amounts;item.amount=amounts.neto;item.neto=amounts.neto;item.iva=amounts.iva;item.total=amounts.total;
  item.ocr={method:String(body.method||'OCR').slice(0,64),confidence:candidate&&Number.isFinite(body.confidence)?Math.max(0,Math.min(100,body.confidence)):null,confidenceKind:'CLIENT_CANDIDATE',status:candidate?'OCR_CANDIDATE':'REVIEW_REQUIRED',extractionVersion:EXTRACTION_VERSION,amounts,numericEvidence:null,textPreview:String(body.textPreview||'').slice(0,2500),sourceVersion:version,sourceSha256:src.sha256,at:new Date().toISOString(),user:user.email};item.updatedAt=item.ocr.at;
  addAudit(state,user,'OCR_VALUES_EXTRACTED',id,{method:item.ocr.method,status:item.ocr.status,sourceVersion:version});await writeState(state);return NextResponse.json({request:publicRequest(item)});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible guardar la lectura.'},{status:e.status||500})}}
