import {detectAmounts,parseCLP,EXTRACTION_VERSION,textFromPdfItems} from './amounts.mjs';
const empty=()=>({neto:null,iva:null,total:null});
// Scores are OCR engine scores, NOT calibrated probabilities of a correct amount.
export function assessOcr(data){
  const result=detectAmounts(data?.text||'');
  const words=(data?.blocks||[]).flatMap(b=>(b.paragraphs||[]).flatMap(p=>(p.lines||[]).flatMap(l=>l.words||[])));
  const evidence=Object.fromEntries(Object.entries(result.amounts).map(([key,n])=>{
    const matches=n===null?[]:words.filter(w=>parseCLP(w.text)===n);
    return [key,{value:n,score:matches.length?Math.min(...matches.map(w=>Number(w.confidence)||0)):null}];
  }));
  const scores=Object.values(evidence).map(e=>e.score), reliable=result.status==='VERIFIED'&&scores.every(s=>s!==null&&s>=80);
  return {...result,amounts:result.amounts,status:reliable?'VERIFIED':'REVIEW_REQUIRED',confidence:reliable?Math.min(...scores):0,confidenceKind:'OCR_NUMERIC_TOKEN_MIN',numericEvidence:evidence,reason:result.status==='VERIFIED'&&!reliable?'LOW_NUMERIC_OCR_CONFIDENCE':result.reason};
}
function rotatedCanvas(source,angle,createCanvas){
  if(!angle)return source;
  const swap=angle===90||angle===270,c= createCanvas(swap?source.height:source.width,swap?source.width:source.height),ctx=c.getContext('2d');
  ctx.fillStyle='white';ctx.fillRect(0,0,c.width,c.height);ctx.translate(c.width/2,c.height/2);ctx.rotate(angle*Math.PI/180);ctx.drawImage(source,-source.width/2,-source.height/2);return c;
}
// OCR-only preprocessing: remove long straight table rules, never alter the source PDF.
export function withoutTableRules(source,createCanvas){
  const c=createCanvas(source.width,source.height),ctx=c.getContext('2d');ctx.drawImage(source,0,0);
  const image=ctx.getImageData(0,0,c.width,c.height),d=image.data,w=c.width,h=c.height;
  const dark=(x,y)=>{const i=(y*w+x)*4;return d[i]<130&&d[i+1]<130&&d[i+2]<130;};
  const white=(x,y)=>{const i=(y*w+x)*4;d[i]=d[i+1]=d[i+2]=255;};
  for(let y=0;y<h;y++){let start=-1;for(let x=0;x<=w;x++){if(x<w&&dark(x,y)){if(start<0)start=x;}else if(start>=0){if(x-start>Math.max(80,w*.12))for(let k=start;k<x;k++)white(k,y);start=-1;}}}
  for(let x=0;x<w;x++){let start=-1;for(let y=0;y<=h;y++){if(y<h&&dark(x,y)){if(start<0)start=y;}else if(start>=0){if(y-start>Math.max(80,h*.08))for(let k=start;k<y;k++)white(x,k);start=-1;}}}
  ctx.putImageData(image,0,0);return c;
}
export async function extractPdfValues(source,{pdfjs,createCanvas,createWorker,onProgress=()=>{},imageInput=c=>c,documentOptions={},signal,timeoutMs=90000,maxPages=10}){
  const bytes=source instanceof ArrayBuffer?new Uint8Array(source):source instanceof Uint8Array?source:new Uint8Array(await source.arrayBuffer());
  const pages=[];let worker,doc,loading,renderTask;let finished=false;const deadline=Date.now()+timeoutMs;
  const check=()=>{if(signal?.aborted)throw new Error('Lectura cancelada');if(Date.now()>=deadline)throw new Error('La lectura excedió el tiempo disponible. Usa un PDF más pequeño o legible.');};
  const bounded=async promise=>{check();let timer,abort;const stop=new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('OCR excedió el tiempo disponible')),Math.min(15000,Math.max(1,deadline-Date.now())));abort=()=>reject(new Error('Lectura cancelada'));signal?.addEventListener('abort',abort,{once:true});});try{return await Promise.race([promise,stop]);}finally{clearTimeout(timer);signal?.removeEventListener('abort',abort);check();}};

  try{
    check();loading=pdfjs.getDocument({...documentOptions,data:bytes.slice()});doc=await bounded(loading.promise);
    if(doc.numPages>maxPages)throw new Error(`Lectura automática limitada a ${maxPages} páginas por archivo. Divide el PDF para revisarlo.`);
    for(let i=1;i<=doc.numPages;i++){
      check();onProgress(`Leyendo página ${i} de ${doc.numPages}…`);
      const page=await bounded(doc.getPage(i)),native=textFromPdfItems((await bounded(page.getTextContent())).items),nativeResult=detectAmounts(native);
      if(nativeResult.status==='VERIFIED'||nativeResult.reason==='NON_CLP_CURRENCY'){pages.push({...nativeResult,confidence:null,confidenceKind:'TEXT_ARITHMETIC_CHECK',page:i,method:'TEXTO_PDF',text:native});continue;}
      if(!worker){const pending=createWorker('spa');pending.then(w=>{if(finished)w.terminate().catch(()=>{});},()=>{});worker=await bounded(pending);await bounded(worker.setParameters({preserve_interword_spaces:'1'}));}
      const base=page.getViewport({scale:1}),scale=Math.min(300/72,3500/Math.max(base.width,base.height)),vp=page.getViewport({scale});
      const canvas=createCanvas(Math.ceil(vp.width),Math.ceil(vp.height));
      renderTask=page.render({canvasContext:canvas.getContext('2d'),viewport:vp,background:'white'});await bounded(renderTask.promise);renderTask=null;
      const attempts=[];let primaryData;let tiledData=[];
      attemptLoop: for(const [angle,variant,psm] of [[0,'original',3],[0,'original',6],[0,'original',11],[0,'without-table-rules',6],[90,'original',6],[270,'original',6],[180,'original',6],[0,'lower-right',6],[0,'lower-half',6]]){
        check();
        await bounded(worker.setParameters({tessedit_pageseg_mode:String(psm)}));
        onProgress(`OCR página ${i} de ${doc.numPages}${angle?` · giro ${angle}°`:''}…`);
        const rectangle=variant==='lower-right'?{left:Math.floor(canvas.width*.55),top:Math.floor(canvas.height*.5),width:Math.floor(canvas.width*.45),height:Math.floor(canvas.height*.5)}:variant==='lower-half'?{left:0,top:Math.floor(canvas.height*.5),width:canvas.width,height:Math.floor(canvas.height*.5)}:undefined;
        const {data}=await bounded(worker.recognize(imageInput(rotatedCanvas(variant==='without-table-rules'?withoutTableRules(canvas,createCanvas):canvas,angle,createCanvas)),{rotateAuto:true,...(rectangle?{rectangle}:{})},{text:true,blocks:true}));
        if(angle===0&&variant==='original'&&psm===3)primaryData=data;
        if(rectangle)tiledData.push(data);
        const merged=rectangle?{text:[primaryData,...tiledData].map(d=>d?.text||'').join('\n'),blocks:[primaryData,...tiledData].flatMap(d=>d?.blocks||[])}:data;
        const assessed=assessOcr(merged);
        if(assessed.reason==='NON_CLP_CURRENCY'&&angle!==0)continue;attempts.push({...assessed,page:i,method:'PDF_OCR',text:merged.text||'',rotation:angle,segmentation:psm,preprocessing:variant,engineConfidence:data.confidence});
        if(assessed.status==='VERIFIED'||assessed.reason==='NON_CLP_CURRENCY')break attemptLoop;
      }
      pages.push(attempts.find(a=>a.reason==='NON_CLP_CURRENCY')||attempts.find(a=>a.status==='VERIFIED')||attempts.find(a=>Object.values(a.amounts).every(n=>n!==null))||{...attempts[0],attempts:attempts.map(a=>({rotation:a.rotation,reason:a.reason,engineConfidence:a.engineConfidence}))});
    }
    const verified=pages.filter(p=>p.status==='VERIFIED'||Object.values(p.amounts).every(n=>n!==null)),unique=[...new Set(verified.map(p=>JSON.stringify(p.amounts)))];
    const foreign=pages.some(p=>p.reason==='NON_CLP_CURRENCY');
    const result=!foreign&&unique.length===1?verified[0]:null;
    const method=pages.some(p=>p.method==='PDF_OCR')?'PDF_OCR':'TEXTO_PDF';
    return {amounts:result?.amounts||empty(),status:result?(method==='PDF_OCR'?'OCR_CANDIDATE':'VERIFIED'):'REVIEW_REQUIRED',extractionVersion:EXTRACTION_VERSION,confidence:result?.confidence??null,confidenceKind:result?.confidenceKind||'UNRESOLVED',numericEvidence:result?.numericEvidence,incompletePages:pages.filter(p=>p.status!=='VERIFIED').map(p=>p.page),method,reason:foreign?'NON_CLP_CURRENCY':unique.length>1?'CONFLICTING_PAGE_TOTALS':result?.reason||(!result?'NO_RELIABLE_TOTALS':undefined),pages:pages.map(({text,...p})=>p),textPreview:pages.map(p=>p.text).join('\n').slice(0,2500)};
  }finally{finished=true;renderTask?.cancel?.();if(worker)await worker.terminate();if(doc)await doc.destroy();else await loading?.destroy?.();check();}
}
