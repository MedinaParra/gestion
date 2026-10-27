import crypto from 'node:crypto';
import {NextResponse} from 'next/server';
import {put} from '@vercel/blob';
import {getSession,requireUser} from '../../../lib/session';
import {readState,writeState,addAudit} from '../../../lib/store';
import {requireSameOrigin,fail} from '../../../lib/request-lifecycle.mjs';
import {profileKey,profileFor,publicProfile,readProfileJson,validateProfileInput} from '../../../lib/profile.mjs';
import {normalizeProfileImage} from '../../../lib/profile-images.mjs';
export const runtime='nodejs';
const headers={'Cache-Control':'private, no-store'};
const errorResponse=e=>NextResponse.json({error:e.status?e.message:'No fue posible procesar el perfil.'},{status:e.status||500,headers});
export async function GET(){try{const user=requireUser(await getSession()),state=await readState();return NextResponse.json({profile:publicProfile(profileFor(state,user),user),userEmail:user.email},{headers})}catch(e){return errorResponse(e)}}
export async function PATCH(req){try{
 const user=requireUser(await getSession());requireSameOrigin(req);const body=validateProfileInput(await readProfileJson(req)),state=await readState(),previous=profileFor(state,user);
 if(body.expectedRevision!==(previous?.revision||null))fail('El perfil cambió en otra sesión. Recarga antes de guardar.',409);
 // Validate and decode all inputs before any asset upload. Never accept a caller-supplied path or owner.
 const normalized={};for(const kind of ['photo','signature'])if(body[`${kind}DataUrl`]!==undefined)normalized[kind]=await normalizeProfileImage(body[`${kind}DataUrl`],kind);
 const profile={name:body.name,title:body.title,rut:body.rut,revision:crypto.randomUUID(),updatedAt:new Date().toISOString(),photo:body.removePhoto?null:previous?.photo||null,signature:body.removeSignature?null:previous?.signature||null};
 for(const [kind,data] of Object.entries(normalized)){
  const pathname=`skm-compras/profiles/${profileKey(user)}/${crypto.randomUUID()}-${kind}.png`;
  await put(pathname,data.bytes,{access:'private',contentType:'image/png',addRandomSuffix:false,allowOverwrite:false});
  profile[kind]={pathname,sha256:data.sha256,width:data.width,height:data.height,createdAt:profile.updatedAt};
 }
 state.profiles=state.profiles||{};state.profiles[profileKey(user)]=profile;
 addAudit(state,user,'PROFILE_UPDATED',null,{photoChanged:Boolean(normalized.photo||body.removePhoto),signatureChanged:Boolean(normalized.signature||body.removeSignature)});
 await writeState(state);return NextResponse.json({profile:publicProfile(profile,user),userEmail:user.email},{headers});
}catch(e){return errorResponse(e)}}
