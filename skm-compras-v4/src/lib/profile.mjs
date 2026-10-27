import crypto from 'node:crypto';
import {fail} from './request-lifecycle.mjs';
export const PHOTO_MAX_BYTES=2*1024*1024,SIGNATURE_MAX_BYTES=200*1024,PROFILE_BODY_MAX_BYTES=3300000;
export const profileKey=user=>crypto.createHash('sha256').update(String(user.email).trim().toLowerCase()).digest('hex');
export const profileFor=(state,user)=>state.profiles?.[profileKey(user)]||null;
export function rutDigit(body){let sum=0,factor=2;for(let i=body.length-1;i>=0;i--){sum+=Number(body[i])*factor;factor=factor===7?2:factor+1}const n=11-sum%11;return n===11?'0':n===10?'K':String(n)}
export function normalizeRut(value){
 if(typeof value!=='string'||value.length>16)fail('RUT no válido. Revisa el formato y el dígito verificador.',400);
 const compact=value.trim().toUpperCase().replace(/[.\s-]/g,'');if(!compact)return '';
 if(!/^\d{1,8}[0-9K]$/.test(compact))fail('RUT no válido. Revisa el formato y el dígito verificador.',400);
 const body=compact.slice(0,-1).replace(/^0+(?=\d)/,''),digit=compact.at(-1);
 if(/^0+$/.test(body)||rutDigit(body)!==digit)fail('RUT no válido. Revisa el formato y el dígito verificador.',400);
 return `${body}-${digit}`;
}
function textField(value,max,label,required=false){if(typeof value!=='string'||value.length>max||/[\u0000-\u001f\u007f<>]/.test(value))fail(`${label} no válido.`,400);const result=value.trim();if(required&&!result)fail(`${label} es obligatorio.`,400);return result}
export function validateProfileInput(body){
 const allowed=['name','title','rut','expectedRevision','photoDataUrl','signatureDataUrl','removePhoto','removeSignature','signatureConsent'];
 if(!body||typeof body!=='object'||Array.isArray(body)||Object.keys(body).some(k=>!allowed.includes(k)))fail('Campos de perfil no permitidos.',400);
 if(body.expectedRevision!==null&&(typeof body.expectedRevision!=='string'||!/^[a-f0-9-]{36}$/i.test(body.expectedRevision)))fail('Revisión de perfil no válida.',400);
 for(const k of ['removePhoto','removeSignature','signatureConsent'])if(body[k]!==undefined&&typeof body[k]!=='boolean')fail('Opción de perfil no válida.',400);
 if((body.photoDataUrl&&body.removePhoto)||(body.signatureDataUrl&&body.removeSignature))fail('No se puede cargar y quitar el mismo recurso a la vez.',400);
 if(body.signatureDataUrl&&body.signatureConsent!==true)fail('Confirma que deseas guardar tu firma dibujada para uso posterior.',400);
 return {...body,name:textField(body.name,120,'Nombre',true),title:textField(body.title,80,'Cargo'),rut:normalizeRut(body.rut)};
}
export function publicProfile(profile,user){return {name:profile?.name||user.name||'',title:profile?.title||'',rut:profile?.rut||'',revision:profile?.revision||null,photo:profile?.photo?{available:true}:null,signature:profile?.signature?{available:true}:null}}
export function signingProfileSnapshot(profile,user,useSignature){
 if(useSignature&&!profile?.signature)fail('No hay una firma dibujada guardada. Revisa tu perfil.',409);
 return {revision:profile?.revision||null,name:profile?.name||user.name||user.email,title:profile?.title||'',rut:profile?.rut||'',photo:profile?.photo?structuredClone(profile.photo):null,signature:useSignature?structuredClone(profile.signature):null};
}
export function publicRequest(item){return {...item,signatures:(item.signatures||[]).map(({profileSnapshot,rut,signaturePng,...s})=>s)}}
export async function readProfileJson(req,maxBytes=PROFILE_BODY_MAX_BYTES){
 if(!req.headers.get('content-type')?.toLowerCase().startsWith('application/json'))fail('Se requiere JSON.',415);
 const length=Number(req.headers.get('content-length'));if(length>maxBytes)fail('La solicitud supera el tamaño permitido.',413);
 const reader=req.body?.getReader();if(!reader)fail('Faltan los datos de la solicitud.',400);const chunks=[];let size=0;
 try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>maxBytes){await reader.cancel();fail('La solicitud supera el tamaño permitido.',413)}chunks.push(Buffer.from(value))}}finally{reader.releaseLock()}
 try{return JSON.parse(Buffer.concat(chunks).toString('utf8'))}catch{fail('JSON no válido.',400)}
}
