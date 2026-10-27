import {requireActive,requestId} from '../../../lib/request-lifecycle.mjs';
import {NextResponse} from 'next/server';
import {getSession,requireUser} from '../../../lib/session';
import {readState,writeState,addAudit} from '../../../lib/store';

import {validateAmounts,EXTRACTION_VERSION} from '../../../lib/amounts.mjs';
export async function POST(req){try{
  const user=requireUser(await getSession());const body=await req.json();const id=requestId(body.id);const state=await readState();const item=state.requests.find(x=>x.id===id);
  if(!item)return NextResponse.json({error:'Solicitud no encontrada'},{status:404});requireActive(item);
  const verified=body.extractionVersion===EXTRACTION_VERSION && ['VERIFIED','OCR_CANDIDATE'].includes(body.status) && validateAmounts(body.amounts);
  const amounts=verified?body.amounts:{neto:null,iva:null,total:null};
  item.amounts=amounts;item.amount=amounts.neto;item.neto=amounts.neto;item.iva=amounts.iva;item.total=amounts.total;
  item.ocr={method:body.method||'OCR',confidence:verified&&Number.isFinite(body.confidence)?Math.max(0,Math.min(100,body.confidence)):null,confidenceKind:String(body.confidenceKind||'UNSPECIFIED'),status:verified?body.status:'REVIEW_REQUIRED',extractionVersion:EXTRACTION_VERSION,amounts,numericEvidence:body.numericEvidence||null,textPreview:String(body.textPreview||'').slice(0,2500),at:new Date().toISOString(),user:user.email};item.updatedAt=new Date().toISOString();
  addAudit(state,user,'OCR_VALUES_EXTRACTED',id,{method:item.ocr.method,confidence:item.ocr.confidence,amounts});await writeState(state);return NextResponse.json({request:item});
}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}
