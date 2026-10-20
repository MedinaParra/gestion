import {NextResponse} from 'next/server';
import {get} from '@vercel/blob';
import {getSession,requireUser} from '../../../lib/session';
import {readState,latestDoc} from '../../../lib/store';
export const runtime='nodejs';

export async function GET(req){try{
  requireUser(await getSession());const {searchParams}=new URL(req.url),id=searchParams.get('id');const state=await readState();const item=state.requests.find(x=>x.id===id);
  if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});let doc=latestDoc(item);const v=Number(searchParams.get('v')||0);
  if(v){const all=[...(item.versions||[]),...(item.documents||[])];const d=all.find(x=>Number(x.version)===v);if(d){const p=d.pathname||d.path||d.blobPath||d.pdfPath;doc={...d,pathname:p,filename:d.filename||d.name||doc?.filename}}}
  if(!doc?.pathname)return NextResponse.json({error:'Esta solicitud no tiene un PDF recuperable'},{status:404});
  const r=await get(doc.pathname,{access:'private',useCache:false});if(!r)return NextResponse.json({error:'PDF no encontrado en almacenamiento'},{status:404});
  return new Response(r.stream,{headers:{'Content-Type':'application/pdf','Content-Disposition':`inline; filename="${doc.filename||id+'.pdf'}"`,'Cache-Control':'private, no-store'}});
}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}
