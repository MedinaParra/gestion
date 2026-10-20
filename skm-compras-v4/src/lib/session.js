import crypto from 'node:crypto';
import {cookies} from 'next/headers';

const COOKIE='skm_purchase_session';
const secret=()=>process.env.SESSION_SECRET||'dev-only-change-me';
const adminEmail=()=>String(process.env.PRIMARY_ADMIN_EMAIL||process.env.ADMIN_EMAIL||'').toLowerCase();
const sign=p=>crypto.createHmac('sha256',secret()).update(p).digest('base64url');

export function roleFor(email=''){return email.toLowerCase()===adminEmail()?'ADMIN':'USER'}
export function makeSession(user){const p=Buffer.from(JSON.stringify({...user,role:roleFor(user.email),exp:Date.now()+12*60*60*1000})).toString('base64url');return `${p}.${sign(p)}`}
export function readToken(token){try{if(!token)return null;const [p,s]=token.split('.');if(!p||!s)return null;const expected=sign(p);if(s.length!==expected.length||!crypto.timingSafeEqual(Buffer.from(s),Buffer.from(expected)))return null;const u=JSON.parse(Buffer.from(p,'base64url').toString());if(!u.exp||u.exp<Date.now())return null;return {...u,role:roleFor(u.email)}}catch{return null}}
export async function getSession(){const c=await cookies();return readToken(c.get(COOKIE)?.value)}
export async function setSession(user){const c=await cookies();c.set(COOKIE,makeSession(user),{httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:43200})}
export async function clearSession(){const c=await cookies();c.set(COOKIE,'',{httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:0})}
export function requireUser(u){if(!u){const e=new Error('No autorizado');e.status=401;throw e}return u}
