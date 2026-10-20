import {NextResponse} from 'next/server';
import {getSession,requireUser} from '../../../lib/session';
import {readState,writeState,addAudit} from '../../../lib/store';

const num=v=>Number(v||0)||0;
export async function POST(req){try{
  const user=requireUser(await getSession());const body=await req.json();const id=String(body.id||'');const state=await readState();const item=state.requests.find(x=>x.id===id);
  if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});
  const amounts={neto:num(body.amounts?.neto),iva:num(body.amounts?.iva),total:num(body.amounts?.total)};
  item.amounts=amounts;item.amount=amounts.neto;item.neto=amounts.neto;item.iva=amounts.iva;item.total=amounts.total;
  item.ocr={method:body.method||'OCR',confidence:num(body.confidence),amounts,textPreview:String(body.textPreview||'').slice(0,2500),at:new Date().toISOString(),user:user.email};item.updatedAt=new Date().toISOString();
  addAudit(state,user,'OCR_VALUES_EXTRACTED',id,{method:item.ocr.method,confidence:item.ocr.confidence,amounts});await writeState(state);return NextResponse.json({request:item});
}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}
