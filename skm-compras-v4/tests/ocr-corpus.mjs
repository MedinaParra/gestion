// Reproducible local OCR integration experiment. No network document uploads.
// node tests/ocr-corpus.mjs [--pipeline] [--case=name]
// Optional SKM_TEST_PDF remains outside the repository and is rasterized in memory.
import fs from 'node:fs/promises';
import {createRequire} from 'node:module';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {PDFDocument,StandardFonts} from 'pdf-lib';
import {createCanvas,DOMMatrix,ImageData,Path2D} from '@napi-rs/canvas';
import {createWorker} from 'tesseract.js';
import {detectAmounts} from '../src/lib/amounts.mjs';
Object.assign(globalThis,{DOMMatrix,ImageData,Path2D});
const pdfjs=await import('pdfjs-dist/legacy/build/pdf.mjs');
const require=createRequire(import.meta.url);
const out=new URL('../test-results/ocr-corpus/',import.meta.url);await fs.mkdir(out,{recursive:true});
const selected=process.argv.find(a=>a.startsWith('--case='))?.slice(7);
const pipelineMode=process.argv.includes('--pipeline');
const pipeline=pipelineMode?(await import('../src/lib/pdf-extraction.mjs')).extractPdfValues:null;
async function measured(fn){const start=performance.now();let peakRss=process.memoryUsage().rss;const poll=setInterval(()=>{peakRss=Math.max(peakRss,process.memoryUsage().rss)},20);try{const result=await fn();return {...result,metrics:{elapsedMs:Math.round(performance.now()-start),peakRssBytes:Math.max(peakRss,process.memoryUsage().rss)}};}finally{clearInterval(poll)}}
const expected={neto:1375000,iva:261250,total:1636250};
const cases=[
 {name:'clp-dots',values:['1.375.000','261.250','1.636.250']},
 {name:'clp-commas',values:['1,375,000','261,250','1,636,250']},
 {name:'clp-spaces',values:['1 375 000','261 250','1 636 250']},
 {name:'skew-2deg',values:['1.375.000','261.250','1.636.250'],skew:2},
 {name:'rotated-pixels-90',values:['1.375.000','261.250','1.636.250'],rotate:90},
 {name:'low-resolution',values:['1.375.000','261.250','1.636.250'],lowres:true},
 {name:'ambiguous-letter-O',values:['1.375.OOO','261.25O','1.636.25O'],review:true},
 {name:'invalid-digit-spaces',values:['1 3 7 5 0 0 0','2 6 1 2 5 0','1 6 3 6 2 5 0'],review:true},
 {name:'foreign-usd',values:['1.375.000','261.250','1.636.250'],currency:'USD',review:true},
 {name:'mixed-pdf',values:['1.375.000','261.250','1.636.250'],mixed:true},
 {name:'native-pdf',values:['1.375.000','261.250','1.636.250'],native:true},
];
function fixtureImage(c){
 const canvas=createCanvas(1240,1754),ctx=canvas.getContext('2d');ctx.fillStyle='white';ctx.fillRect(0,0,canvas.width,canvas.height);
 ctx.save();if(c.skew){ctx.translate(620,877);ctx.rotate(c.skew*Math.PI/180);ctx.translate(-620,-877)}
 ctx.fillStyle='black';ctx.font='bold 29px sans-serif';ctx.fillText('COTIZACION SINTETICA - PRUEBA OCR',100,130);ctx.font='25px sans-serif';ctx.fillText(`MONEDA ${c.currency||'CLP'}`,100,180);
 ctx.fillText('DETALLE                       CANTIDAD     UNITARIO        TOTAL',100,300);
 ctx.strokeStyle='black';ctx.lineWidth=2;ctx.strokeRect(90,320,1050,170);ctx.beginPath();ctx.moveTo(90,400);ctx.lineTo(1140,400);ctx.stroke();
 ctx.fillText('SERVICIO ALFA',110,370);ctx.fillText('5',610,370);ctx.fillText('125.000',755,370);ctx.fillText('625.000',960,370);
 ctx.fillText('SERVICIO BETA',110,450);ctx.fillText('3',610,450);ctx.fillText('250.000',755,450);ctx.fillText('750.000',960,450);
 ctx.font='28px sans-serif';[['SUB TOTAL',c.values[0]],['IVA 19%',c.values[1]],['TOTAL',c.values[2]]].forEach(([label,value],i)=>{ctx.fillText(label,540,650+i*70);ctx.fillText(value,805,650+i*70)});ctx.restore();
 if(c.lowres){const tiny=createCanvas(310,439);tiny.getContext('2d').drawImage(canvas,0,0,310,439);const blur=createCanvas(1240,1754);blur.getContext('2d').drawImage(tiny,0,0,1240,1754);return blur;}
 if(c.rotate){const rotated=createCanvas(1754,1240),r=rotated.getContext('2d');r.translate(1754,0);r.rotate(Math.PI/2);r.drawImage(canvas,0,0);return rotated;}
 return canvas;
}
async function fixturePdf(c){const doc=await PDFDocument.create(),font=await doc.embedFont(StandardFonts.Helvetica);
 if(c.mixed){const p=doc.addPage([595.28,841.89]);p.drawText('ANEXO INFORMATIVO SIN IMPORTES. La cotizacion esta en la pagina siguiente.',{x:40,y:700,size:12,font});}
 if(c.native){const p=doc.addPage([595.28,841.89]);['COTIZACION SINTETICA CLP',...['SUB TOTAL','IVA 19%','TOTAL'].map((k,i)=>`${k} ${c.values[i]}`)].forEach((line,i)=>p.drawText(line,{x:50,y:700-i*40,size:16,font}));}
 else{const image=fixtureImage(c),png=await doc.embedPng(image.toBuffer('image/png')),size=c.rotate?[841.89,595.28]:[595.28,841.89],p=doc.addPage(size);p.drawImage(png,{x:0,y:0,width:size[0],height:size[1]});}
 return doc.save();}
async function baseline(bytes,worker){const doc=await pdfjs.getDocument({data:new Uint8Array(bytes),standardFontDataUrl:path.join(path.dirname(require.resolve('pdfjs-dist/package.json')),'standard_fonts/')}).promise;let text='',pages=[];
 try{for(let i=1;i<=doc.numPages;i++){const page=await doc.getPage(i),vp=page.getViewport({scale:2}),canvas=createCanvas(Math.ceil(vp.width),Math.ceil(vp.height));await page.render({canvasContext:canvas.getContext('2d'),viewport:vp}).promise;const {data}=await worker.recognize(canvas.toBuffer('image/png'),{},{text:true,blocks:true});text+='\n'+data.text;const words=data.blocks?.flatMap(b=>b.paragraphs.flatMap(p=>p.lines.flatMap(l=>l.words)))||[];pages.push({page:i,text:data.text,confidence:data.confidence,numericWords:words.filter(w=>/[0-9]/.test(w.text)).map(w=>({text:w.text,confidence:w.confidence}))});}return {...detectAmounts(text),pages};}finally{await doc.destroy();}}
const sourceHashes={};for(const file of ['amounts.mjs','pdf-extraction.mjs']){try{sourceHashes[file]=createHash('sha256').update(await fs.readFile(new URL('../src/lib/'+file,import.meta.url))).digest('hex')}catch{}}
const report={sourceHashes,engine:'tesseract.js',version:require('tesseract.js/package.json').version,mode:pipelineMode?'shared-production-pipeline':'baseline-scale2-real-ocr',cases:[]};let worker;
try{if(!pipelineMode)worker=await createWorker('spa');for(const c of cases.filter(c=>!selected||c.name===selected)){const bytes=await fixturePdf(c);await fs.writeFile(new URL(c.name+'.pdf',out),bytes);const result=await measured(()=>pipeline?pipeline(bytes,{pdfjs,createCanvas,createWorker,imageInput:c=>c.toBuffer('image/png'),documentOptions:{standardFontDataUrl:path.join(path.dirname(require.resolve('pdfjs-dist/package.json')),'standard_fonts/')},onProgress:()=>{}}):baseline(bytes,worker));const exact=JSON.stringify(result.amounts)===JSON.stringify(expected);const safe=c.review?result.status!=='VERIFIED':exact||result.status==='REVIEW_REQUIRED';const outcome=result.status==='REVIEW_REQUIRED'?'ABSTAIN':result.status==='OCR_CANDIDATE'?(c.review?'AMBIGUOUS_CANDIDATE':exact?'EXACT_CANDIDATE':'WRONG_CANDIDATE'):c.review?'FALSE_ACCEPT':exact?'EXACT':'WRONG_ACCEPT';const entry={name:c.name,expected:c.review?'REVIEW_REQUIRED':expected,exact,safe,outcome,result};report.cases.push(entry);console.log(JSON.stringify(entry));}
 if(process.env.SKM_TEST_PDF&&(!selected||selected==='private-source-rasterized-in-memory')){const original=await fs.readFile(process.env.SKM_TEST_PDF);const doc=await pdfjs.getDocument({data:new Uint8Array(original),standardFontDataUrl:path.join(path.dirname(require.resolve('pdfjs-dist/package.json')),'standard_fonts/')}).promise;const raster=await PDFDocument.create(),rasterScale=Number(process.env.SKM_RASTER_SCALE||2);for(let i=1;i<=doc.numPages;i++){const page=await doc.getPage(i),vp=page.getViewport({scale:rasterScale}),canvas=createCanvas(Math.ceil(vp.width),Math.ceil(vp.height));await page.render({canvasContext:canvas.getContext('2d'),viewport:vp}).promise;const image=await raster.embedPng(canvas.toBuffer('image/png')),p=raster.addPage([vp.width/rasterScale,vp.height/rasterScale]);p.drawImage(image,{x:0,y:0,width:vp.width/rasterScale,height:vp.height/rasterScale});}await doc.destroy();const bytes=await raster.save();const result=await measured(()=>pipeline?pipeline(bytes,{pdfjs,createCanvas,createWorker,imageInput:c=>c.toBuffer('image/png'),documentOptions:{standardFontDataUrl:path.join(path.dirname(require.resolve('pdfjs-dist/package.json')),'standard_fonts/')},onProgress:()=>{}}):baseline(bytes,worker));report.cases.push({name:'private-source-rasterized-in-memory',rasterDpi:rasterScale*72,result});console.log(JSON.stringify(report.cases.at(-1)));}
 if(process.env.SKM_SCAN_PDF&&(!selected||selected==='private-scan-direct')){const bytes=new Uint8Array(await fs.readFile(process.env.SKM_SCAN_PDF));const result=await measured(()=>pipeline?pipeline(bytes,{pdfjs,createCanvas,createWorker,imageInput:c=>c.toBuffer('image/png'),documentOptions:{standardFontDataUrl:path.join(path.dirname(require.resolve('pdfjs-dist/package.json')),'standard_fonts/')},onProgress:()=>{}}):baseline(bytes,worker));report.cases.push({name:'private-scan-direct',result});console.log(JSON.stringify(report.cases.at(-1)));}
}finally{if(worker)await worker.terminate();await fs.writeFile(new URL(`${pipelineMode?'pipeline':'baseline'}${selected?'-'+selected:''}-report.json`,out),JSON.stringify(report,null,2));}
