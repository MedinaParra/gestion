import {NextResponse} from 'next/server';
import {ownerEmail,setSession} from '../../../../lib/session';
import {readState,writeState,addAudit} from '../../../../lib/store';
import {requireSameOrigin,fail} from '../../../../lib/request-lifecycle.mjs';
import {readProfileJson} from '../../../../lib/profile.mjs';
import {effectiveRole,emailKey,identityVerified} from '../../../../lib/user-access.mjs';
export async function POST(req){try{
 requireSameOrigin(req);const body=await readProfileJson(req,16384),credential=body?.credential;if(typeof credential!=='string'||!credential||credential.length>12000)fail('Credencial Google no válida.',400);
 const client=process.env.GOOGLE_CLIENT_ID||process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,domain=emailKey(process.env.GOOGLE_ALLOWED_DOMAIN);if(!client||!domain)fail('Configuración de acceso institucional no disponible.',503);
 const response=await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(credential)}`,{cache:'no-store',signal:AbortSignal.timeout(10000)});if(!response.ok)fail('Google no pudo validar la identidad.',401);const p=await response.json(),email=emailKey(p.email);
 if(p.aud!==client||!['true',true].includes(p.email_verified)||emailKey(p.hd)!==domain||!email.endsWith('@'+domain)||!['accounts.google.com','https://accounts.google.com'].includes(p.iss)||typeof p.sub!=='string'||!/^\d{1,255}$/.test(p.sub)||!Number.isFinite(Number(p.exp))||Number(p.exp)*1000<=Date.now())fail('Cuenta no autorizada para este portal.',403);
 const googleIdentity={issuer:'https://accounts.google.com',subject:p.sub},state=await readState();state.users=state.users||{};const previous=state.users[email];if(previous?.status==='SUSPENDED')fail('La cuenta está suspendida.',403);
 if(identityVerified(previous)&&previous.googleIdentity.subject!==p.sub)fail('La identidad institucional requiere revisión por el administrador.',403);
 if(Object.entries(state.users).some(([key,a])=>key!==email&&identityVerified(a)&&a.googleIdentity.subject===p.sub))fail('La identidad institucional requiere revisión por el administrador.',403);
 const user={email,name:p.name||email,picture:p.picture||'',googleIdentity,sessionVersion:previous?.sessionVersion||0,role:effectiveRole(state,email,ownerEmail())};
 state.users[email]={...previous,email,name:user.name,lastLogin:new Date().toISOString(),googleIdentity};addAudit(state,user,'SESSION_LOGIN');await writeState(state);await setSession(user);return NextResponse.json({user},{headers:{'Cache-Control':'private, no-store'}});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible iniciar sesión.'},{status:e.status||500})}}
