import {requireActive,requestId,requireSameOrigin,fail} from '../../../lib/request-lifecycle.mjs';
import crypto from 'node:crypto';
import {NextResponse} from 'next/server';
import {get,put} from '@vercel/blob';
import {stampFirstPage,ACTION_LABELS} from '../../../lib/first-page-stamp.mjs';
import {getSession,requireUser,roleFor} from '../../../lib/session';
import {readState,writeState,addAudit,latestDoc,safeName} from '../../../lib/store';
import {profileFor,profileKey,signingProfileSnapshot,publicRequest,readProfileJson} from '../../../lib/profile.mjs';
export const runtime='nodejs';
const label=ACTION_LABELS;
async function buffer(stream){return Buffer.from(await new Response(stream).arrayBuffer())}

export async function POST(req){try{
  const user=requireUser(await getSession());requireSameOrigin(req);const body=await readProfileJson(req,8192);if(!body||typeof body!=='object'||Array.isArray(body))fail('Solicitud de firma no válida.',400);const id=requestId(body.id),action=String(body.action||'SIGNED').toUpperCase();
  if(!label[action])return NextResponse.json({error:'Acción no válida'},{status:400});const state=await readState(),item=state.requests.find(x=>x.id===id);if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});requireActive(item);
  const signers=(item.signers||[]).map(x=>String(x).toLowerCase()),admin=roleFor(user.email)==='ADMIN';if(signers.length&&!signers.includes(user.email.toLowerCase())&&!admin)return NextResponse.json({error:'No estás incluido en la ruta de firma'},{status:403});
  item.signatures=item.signatures||[];if(item.signatures.some(s=>String(s.email).toLowerCase()===user.email.toLowerCase()&&s.action===action))return NextResponse.json({error:'Esta acción ya fue registrada por tu usuario'},{status:409});
  const src=latestDoc(item);if(!src?.pathname)return NextResponse.json({error:'No se encontró el PDF a firmar'},{status:404});
  if(body.consent!==true||typeof body.useProfileSignature!=='boolean')fail('Confirma expresamente esta acción y si deseas incluir tu firma dibujada.',400);
  if(body.expectedVersion!==Number(src.version||1)||(body.expectedSha256??null)!==(src.sha256||null))fail('El documento cambió. Vuelve a abrirlo y confirma la versión actual.',409);
  const profile=profileFor(state,user);if(body.profileRevision!==(profile?.revision||null))fail('Tu perfil cambió. Revisa los datos antes de volver a confirmar.',409);
  const snapshot=signingProfileSnapshot(profile,user,body.useProfileSignature);
  let signaturePng;
  if(snapshot.signature){
    if(!snapshot.signature.pathname?.startsWith(`skm-compras/profiles/${profileKey(user)}/`))fail('La firma guardada no es recuperable.',409);
    const asset=await get(snapshot.signature.pathname,{access:'private',useCache:false});if(!asset)fail('La firma guardada no es recuperable.',409);
    signaturePng=await buffer(asset.stream);if(signaturePng.length>512*1024||crypto.createHash('sha256').update(signaturePng).digest('hex')!==snapshot.signature.sha256)fail('La firma guardada no pudo verificarse. Revisa tu perfil.',409);
  }
  const blob=await get(src.pathname,{access:'private',useCache:false});if(!blob)return NextResponse.json({error:'No se pudo recuperar el PDF'},{status:404});
  const stamp={id:crypto.randomUUID(),email:user.email.toLowerCase(),name:snapshot.name,title:snapshot.title,rut:snapshot.rut,profileSnapshot:snapshot,role:roleFor(user.email),action,at:new Date().toISOString(),consent:{confirmed:true,useProfileSignature:body.useProfileSignature,documentVersion:Number(src.version||1)}};
  const result=await stampFirstPage(await buffer(blob.stream),{...stamp,signaturePng},item.signatures);
  stamp.placement=result.placement;const signatures=[...item.signatures,stamp];
  const out=Buffer.from(result.bytes),sha256=crypto.createHash('sha256').update(out).digest('hex');const baseVersions=Array.isArray(item.versions)?item.versions:[];const version=Math.max(Number(src.version||1),...baseVersions.map(v=>Number(v.version||0)))+1;const filename=`${item.id}_v${version}_${label[action].toLowerCase()}.pdf`,pathname=`skm-compras/requests/${item.id}/v${version}-${stamp.id}-${safeName(filename)}`;await put(pathname,out,{access:'private',contentType:'application/pdf',addRandomSuffix:false});
  item.versions=baseVersions.length?baseVersions:[{...src,version:Number(src.version||1)}];item.versions.push({version,pathname,filename,sha256,createdAt:stamp.at,createdBy:user.email,kind:action});item.signatures=signatures;item.updatedAt=stamp.at;
  if(action==='REJECTED')item.status='REJECTED';else if(action==='APPROVED')item.status='APPROVED';else{const done=new Set(signatures.filter(s=>['SIGNED','APPROVED'].includes(s.action)).map(s=>String(s.email).toLowerCase()));item.status=signers.length&&signers.every(e=>done.has(e))?'FINAL_APPROVAL':'IN_SIGNATURES'}
  addAudit(state,user,`DOCUMENT_${action}`,id,{version,sha256});await writeState(state);return NextResponse.json({request:publicRequest(item)});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible firmar'},{status:e.status||500})}}
