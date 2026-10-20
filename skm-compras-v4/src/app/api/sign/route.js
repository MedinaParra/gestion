import crypto from 'node:crypto';
import {NextResponse} from 'next/server';
import {get,put} from '@vercel/blob';
import {PDFDocument,StandardFonts,rgb} from 'pdf-lib';
import {getSession,requireUser,roleFor} from '../../../lib/session';
import {readState,writeState,addAudit,latestDoc,safeName} from '../../../lib/store';
export const runtime='nodejs';
const label={SIGNED:'FIRMADO',APPROVED:'APROBADO',OBSERVED:'OBSERVADO',REJECTED:'RECHAZADO'};
async function buffer(stream){return Buffer.from(await new Response(stream).arrayBuffer())}

export async function POST(req){try{
  const user=requireUser(await getSession()),body=await req.json(),id=String(body.id||''),action=String(body.action||'SIGNED').toUpperCase();
  if(!label[action])return NextResponse.json({error:'Acción no válida'},{status:400});const state=await readState(),item=state.requests.find(x=>x.id===id);if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});
  const signers=(item.signers||[]).map(x=>String(x).toLowerCase()),admin=roleFor(user.email)==='ADMIN';if(signers.length&&!signers.includes(user.email.toLowerCase())&&!admin)return NextResponse.json({error:'No estás incluido en la ruta de firma'},{status:403});
  item.signatures=item.signatures||[];if(item.signatures.some(s=>String(s.email).toLowerCase()===user.email.toLowerCase()&&s.action===action))return NextResponse.json({error:'Esta acción ya fue registrada por tu usuario'},{status:409});
  const src=latestDoc(item);if(!src?.pathname)return NextResponse.json({error:'No se encontró el PDF a firmar'},{status:404});const blob=await get(src.pathname,{access:'private',useCache:false});if(!blob)return NextResponse.json({error:'No se pudo recuperar el PDF'},{status:404});
  const pdf=await PDFDocument.load(await buffer(blob.stream));const font=await pdf.embedFont(StandardFonts.Helvetica),bold=await pdf.embedFont(StandardFonts.HelveticaBold);const stamp={id:crypto.randomUUID(),email:user.email.toLowerCase(),name:user.name||user.email,role:roleFor(user.email),action,at:new Date().toISOString()};const signatures=[...item.signatures,stamp];
  const page=pdf.addPage([595.28,841.89]),w=page.getWidth();page.drawText('SKM INDUSTRIAL',{x:42,y:790,size:11,font:bold,color:rgb(.04,.16,.22)});page.drawText('REGISTRO DE APROBACION',{x:42,y:750,size:20,font:bold,color:rgb(.05,.1,.14)});page.drawText(`${item.id}  ·  OT ${item.ot||''}`,{x:42,y:722,size:10,font});page.drawLine({start:{x:42,y:704},end:{x:w-42,y:704},thickness:1,color:rgb(.82,.85,.87)});let y=665;
  for(const s of signatures.slice(-8)){const c=s.action==='REJECTED'?rgb(.55,.12,.12):s.action==='OBSERVED'?rgb(.55,.36,.05):rgb(.04,.28,.34);page.drawRectangle({x:42,y:y-58,width:w-84,height:66,borderWidth:1.4,borderColor:rgb(.08,.27,.34),color:rgb(.98,.99,.99)});page.drawText(label[s.action]||s.action,{x:57,y:y-10,size:13,font:bold,color:c});page.drawText(String(s.name||s.email).toUpperCase(),{x:57,y:y-30,size:10,font:bold});page.drawText(`${s.email}  ·  ${s.role==='ADMIN'?'ADMINISTRADOR':'FIRMANTE'}`,{x:57,y:y-46,size:8.2,font,color:rgb(.3,.34,.37)});const d=new Intl.DateTimeFormat('es-CL',{dateStyle:'medium',timeStyle:'short',timeZone:'America/Santiago'}).format(new Date(s.at));page.drawText(`${d}  ·  ${s.id.slice(0,8).toUpperCase()}`,{x:w-255,y:y-31,size:8.2,font,color:rgb(.3,.34,.37)});y-=82;if(y<90)break}
  page.drawText('Documento controlado · identidad validada mediante sesión institucional.',{x:42,y:47,size:7.5,font,color:rgb(.4,.43,.45)});
  const out=Buffer.from(await pdf.save()),sha256=crypto.createHash('sha256').update(out).digest('hex');const baseVersions=Array.isArray(item.versions)?item.versions:[];const version=Math.max(Number(src.version||1),...baseVersions.map(v=>Number(v.version||0)))+1;const filename=`${item.id}_v${version}_${label[action].toLowerCase()}.pdf`,pathname=`skm-compras/requests/${item.id}/v${version}-${safeName(filename)}`;await put(pathname,out,{access:'private',contentType:'application/pdf',addRandomSuffix:false});
  item.versions=baseVersions.length?baseVersions:[{...src,version:Number(src.version||1)}];item.versions.push({version,pathname,filename,sha256,createdAt:stamp.at,createdBy:user.email,kind:action});item.signatures=signatures;item.updatedAt=stamp.at;
  if(action==='REJECTED')item.status='REJECTED';else if(action==='APPROVED')item.status='APPROVED';else{const done=new Set(signatures.filter(s=>['SIGNED','APPROVED'].includes(s.action)).map(s=>String(s.email).toLowerCase()));item.status=signers.length&&signers.every(e=>done.has(e))?'FINAL_APPROVAL':'IN_SIGNATURES'}
  addAudit(state,user,`DOCUMENT_${action}`,id,{version,sha256});await writeState(state);return NextResponse.json({request:item});
}catch(e){return NextResponse.json({error:e.message||'No fue posible firmar'},{status:e.status||500})}}
