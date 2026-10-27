import {NextResponse} from 'next/server';
import {roleFor,setSession} from '../../../../lib/session';
import {readState,writeState,addAudit} from '../../../../lib/store';

export async function POST(req){try{
  const {credential}=await req.json();
  if(!credential)return NextResponse.json({error:'Credencial Google faltante'},{status:400});
  const r=await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(credential)}`,{cache:'no-store'});
  if(!r.ok)return NextResponse.json({error:'Google no pudo validar la identidad'},{status:401});
  const p=await r.json();
  const client=process.env.GOOGLE_CLIENT_ID||process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  const domain=(process.env.GOOGLE_ALLOWED_DOMAIN||'skmindustrial.cl').toLowerCase();
  const email=String(p.email||'').toLowerCase();
  if(p.aud!==client||p.email_verified!=='true'||!email.endsWith(`@${domain}`))return NextResponse.json({error:'Cuenta no autorizada para este portal'},{status:403});
  const user={email,name:p.name||email,picture:p.picture||'',role:roleFor(email)};
  const state=await readState();state.users=state.users||{};state.users[email]={email,name:user.name,role:user.role,lastLogin:new Date().toISOString()};addAudit(state,user,'SESSION_LOGIN');await writeState(state);await setSession(user);
  return NextResponse.json({user});
}catch(e){return NextResponse.json({error:e.message||'No fue posible iniciar sesión'},{status:e.status||500})}}
