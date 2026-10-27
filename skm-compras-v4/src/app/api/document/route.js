import {requireActive,requestId} from '../../../lib/request-lifecycle.mjs';
import {NextResponse} from 'next/server';
import {get} from '@vercel/blob';
import {getSession,requireUser} from '../../../lib/session';
import {readState,latestDoc} from '../../../lib/store';
import {requestedDocument} from '../../../lib/document-version.mjs';
export const runtime='nodejs';

export async function GET(req){try{
  requireUser(await getSession());const {searchParams}=new URL(req.url),id=requestId(searchParams.get('id'));const state=await readState();const item=state.requests.find(x=>x.id===id);
  if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});requireActive(item);let doc=latestDoc(item);const requestedVersion=searchParams.get('v'),v=Number(requestedVersion||0);
  if(requestedVersion!==null&&(!Number.isInteger(v)||v<1))return NextResponse.json({error:'Versión no válida'},{status:400});
  if(v){doc=requestedDocument(item,v);if(!doc)return NextResponse.json({error:'La versión solicitada no existe o no es recuperable; no se sustituye por otra versión'},{status:404});}
  if(!doc?.pathname)return NextResponse.json({error:'Esta solicitud no tiene un PDF recuperable'},{status:404});
  const r=await get(doc.pathname,{access:'private',useCache:false});if(!r)return NextResponse.json({error:'PDF no encontrado en almacenamiento'},{status:404});
  return new Response(r.stream,{headers:{'Content-Type':'application/pdf','Content-Disposition':`inline; filename="${doc.filename||id+'.pdf'}"`,'Cache-Control':'private, no-store'}});
}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}
