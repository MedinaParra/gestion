import {NextResponse} from 'next/server';
import {get} from '@vercel/blob';
import {getSession,requireUser} from '../../../../lib/session';
import {readState} from '../../../../lib/store';
import {profileKey,profileFor} from '../../../../lib/profile.mjs';
import {fail} from '../../../../lib/request-lifecycle.mjs';
export const runtime='nodejs';
export async function GET(req){try{
 const user=requireUser(await getSession()),kind=new URL(req.url).searchParams.get('kind');if(!['photo','signature'].includes(kind))fail('Recurso de perfil no válido.',400);
 const state=await readState(),profile=profileFor(state,user),params=new URL(req.url).searchParams,revision=params.get('revision')||params.get('v');
 if(revision&&revision!==profile?.revision)fail('El perfil cambió. Recarga la vista previa.',409);
 const asset=profile?.[kind];if(!asset?.pathname?.startsWith(`skm-compras/profiles/${profileKey(user)}/`))fail('Recurso de perfil no encontrado.',404);
 const blob=await get(asset.pathname,{access:'private',useCache:false});if(!blob)fail('Recurso de perfil no encontrado.',404);
 return new Response(blob.stream,{headers:{'Content-Type':'image/png','Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff','Content-Disposition':`inline; filename="${kind}.png"`}});
}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible leer el recurso.'},{status:e.status||500,headers:{'Cache-Control':'private, no-store'}})}}
