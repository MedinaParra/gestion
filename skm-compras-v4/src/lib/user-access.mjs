import crypto from 'node:crypto';
import {fail} from './request-lifecycle.mjs';
export const emailKey=value=>String(value||'').trim().toLowerCase();
export const effectiveRole=(state,email,owner)=>emailKey(owner)&&emailKey(email)===emailKey(owner)?'ADMIN':[undefined,'SIGNER'].includes(state.users?.[emailKey(email)]?.operationalRole)?'SIGNER':'USER';
export const identityVerified=account=>account?.googleIdentity?.issuer==='https://accounts.google.com'&&typeof account.googleIdentity.subject==='string'&&account.googleIdentity.subject.length>0;
export function authorizeAccount(user,state,owner){
 if(!user?.email)fail('No autorizado',401);const account=state.users?.[emailKey(user.email)];if(!account)fail('Vuelve a iniciar sesión para verificar tu cuenta.',401);
 if(account.status!==undefined&&!['ACTIVE','SUSPENDED'].includes(account.status))fail('El estado de acceso requiere revisión.',403);
 if(account.status==='SUSPENDED')fail('La cuenta está suspendida.',403);
 if((user.sessionVersion||0)!==(account.sessionVersion||0))fail('Tu acceso cambió. Vuelve a iniciar sesión.',401);
 if(identityVerified(account)&&(user.googleIdentity?.issuer!==account.googleIdentity.issuer||user.googleIdentity?.subject!==account.googleIdentity.subject))fail('Vuelve a iniciar sesión para verificar tu identidad.',401);
 return {...user,email:emailKey(user.email),role:effectiveRole(state,user.email,owner)};
}
export function publicAccount(state,email,actor,owner){const key=emailKey(email),a=state.users[key],isOwner=key===emailKey(owner);return {email:key,name:String(a.name||key),role:effectiveRole(state,key,owner),status:a.status==='SUSPENDED'?'SUSPENDED':'ACTIVE',revision:a.revision||null,lastLogin:a.lastLogin||null,identityVerified:identityVerified(a),isOwner,canManage:emailKey(actor.email)===emailKey(owner)&&!isOwner&&key!==emailKey(actor.email)}}
export function applyUserChange(state,actor,body,owner){
 actor=authorizeAccount(actor,state,owner);if(actor.role!=='ADMIN')fail('Sólo el administrador puede gestionar usuarios.',403);
 const allowed=new Set(['email','confirmEmail','expectedRevision','role','status']);if(!body||typeof body!=='object'||Array.isArray(body)||Object.keys(body).some(k=>!allowed.has(k)))fail('Cambio de usuario no válido.',400);
 const email=emailKey(body.email);if(typeof body.email!=='string'||! /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||emailKey(body.confirmEmail)!==email)fail('Confirma el correo exacto de la cuenta.',400);
 if(email===emailKey(actor.email)||email===emailKey(owner))fail('No puedes modificar tu cuenta ni al administrador propietario.',403);
 const account=Object.hasOwn(state.users||{},email)?state.users[email]:null;if(!account)fail('Usuario no encontrado.',404);
 if(body.expectedRevision!==(account.revision||null))fail('La cuenta cambió. Recarga antes de confirmar.',409);
 if(!['USER','SIGNER'].includes(body.role)||!['ACTIVE','SUSPENDED'].includes(body.status))fail('Rol o estado no permitido.',400);
 const before={role:effectiveRole(state,email,owner),status:account.status==='SUSPENDED'?'SUSPENDED':'ACTIVE'};
 if(before.role===body.role&&before.status===body.status)return {email,before,after:before,changed:false};
 account.operationalRole=body.role;account.status=body.status;account.revision=crypto.randomUUID();account.sessionVersion=(account.sessionVersion||0)+1;account.updatedAt=new Date().toISOString();return {email,before,after:{role:body.role,status:body.status},changed:true};
}
export function directoryUsers(state,owner){return Object.keys(state.users||{}).filter(e=>identityVerified(state.users[e])&&[undefined,'ACTIVE'].includes(state.users[e].status)&&effectiveRole(state,e,owner)!=='USER').map(e=>({email:e,name:String(state.users[e].name||e),role:effectiveRole(state,e,owner)})).sort((a,b)=>a.name.localeCompare(b.name))}
export function validateSignerEmails(state,emails,owner){if(!Array.isArray(emails)||emails.length<1||emails.length>30)fail('Selecciona entre 1 y 30 firmantes habilitados.',400);const normalized=emails.map(emailKey),allowed=new Set(directoryUsers(state,owner).map(u=>u.email));if(new Set(normalized).size!==normalized.length||normalized.some(e=>!allowed.has(e)))fail('La ruta contiene usuarios no verificados, suspendidos o sin permiso para firmar. Recarga el directorio.',409);return normalized}
