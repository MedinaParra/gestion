import {PDFDocument, PDFName, PDFDict, PDFArray, StandardFonts, rgb} from 'pdf-lib';
import {createCanvas} from '@napi-rs/canvas';
import path from 'node:path';
const standardFontDataUrl=path.join(path.join(process.cwd(),'node_modules/pdfjs-dist'),'standard_fonts')+path.sep;

export const ACTION_LABELS={SIGNED:'FIRMADO',APPROVED:'APROBADO',OBSERVED:'OBSERVADO',REJECTED:'RECHAZADO'};
function review(message){const e=new Error(`Revisión de ubicación requerida: ${message}`);e.status=422;return e}
function assertNotCryptographicallySigned(pdf){
  const seen=new WeakSet();
  function inspect(obj){
    if(!obj || typeof obj!=='object' || seen.has(obj))return;seen.add(obj);
    if(obj instanceof PDFDict){
      if(obj.has(PDFName.of('ByteRange')) || obj.has(PDFName.of('DocMDP')) || String(obj.get(PDFName.of('Type')))==='/Sig' || String(obj.get(PDFName.of('FT')))==='/Sig')
        throw review('el PDF contiene una firma digital o campo de firma. No se modificó el documento.');
      for(const [,value] of obj.entries())inspect(value);
    }else if(obj instanceof PDFArray)for(const value of obj.asArray())inspect(value);
  }
  for(const [,obj] of pdf.context.enumerateIndirectObjects())inspect(obj);
}
function fitted(text,font,maxWidth,start=8){
  // WinAnsi cannot encode arbitrary Unicode. Fail safely rather than alter identity.
  let width;try{width=font.widthOfTextAtSize(text,start)}catch{throw review('la identidad contiene caracteres que requieren una fuente compatible.')}
  const size=Math.min(start,start*maxWidth/Math.max(width,1));
  if(size<6)throw review('la identidad no cabe de forma legible en el sello.');
  return {text,size};
}

/** Read-only raster verification, followed by one visual audit stamp on page 1.
 * Does not create pages, flatten, scale, remove content, or provide a cryptographic signature.
 */
export async function stampFirstPage(bytes,stamp,previous=[]){
  const pdf=await PDFDocument.load(bytes);assertNotCryptographicallySigned(pdf);
  const page=pdf.getPages()[0];if(!page)throw review('el PDF no tiene páginas.');
  if(((page.getRotation().angle%360)+360)%360!==0)throw review('la primera página está rotada; debe revisarse la ubicación antes de firmar.');
  const crop=page.getCropBox(),media=page.getMediaBox();
  if(crop.x<media.x || crop.y<media.y || crop.x+crop.width>media.x+media.width || crop.y+crop.height>media.y+media.height)throw review('el recorte de página no está contenido en su formato.');
  const hasSignature=stamp.signaturePng!=null;
  // The server decides whether this act explicitly authorizes using the saved graphic.
  // This helper never fetches a profile or re-applies another person's signature.
  let graphic=null;
  if(hasSignature){
    const input=stamp.signaturePng;
    if(!(input instanceof Uint8Array)||input.length>512*1024||input.length<24||![137,80,78,71,13,10,26,10].every((v,i)=>input[i]===v))throw review('la imagen de firma no es un PNG válido dentro del límite permitido.');
    // Inspect IHDR before decompression to reject oversized compressed payloads early.
    const header=new DataView(input.buffer,input.byteOffset,input.byteLength);
    if(input.length<33||header.getUint32(8)!==13||String.fromCharCode(...input.subarray(12,16))!=='IHDR'||header.getUint32(16)<2||header.getUint32(20)<2||header.getUint32(16)>2000||header.getUint32(20)>1000)throw review('las dimensiones de la imagen de firma no son seguras.');
    try{graphic=await pdf.embedPng(input)}catch{throw review('no se pudo abrir la imagen de firma; no se modificó el documento.');}
    if(graphic.width<2||graphic.height<2||graphic.width>2000||graphic.height>1000)throw review('las dimensiones de la imagen de firma no son seguras.');
  }
  // Compact adjacent slots, right to left. Legacy stamps retain their original size.
  // Mixed legacy/profile rows are positioned from actual stored rectangles, not an
  // index multiplied by today's dimensions (which would overlap older signatures).
  const gap=12,height=hasSignature?72:42,margin=20,width=(hasSignature||stamp.rut)?110:90;
  if(crop.width<margin*2+width)throw review('la primera página es demasiado estrecha.');
  const knownKinds=new Set(['verified-compact-v2','verified-profile-v3']);
  const existing=previous.map(s=>s.placement).filter(p=>p?.page===1&&knownKinds.has(p.kind));
  for(const p of existing){
    if(![p.x,p.y,p.width,p.height].every(Number.isFinite)||p.width<=0||p.height<=0||p.x<crop.x||p.y<crop.y||p.x+p.width>crop.x+crop.width||p.y+p.height>crop.y+crop.height)throw review('una ubicación histórica no es válida; se requiere revisión antes de agregar otra firma.');
  }
  const last=existing.at(-1);
  let x=last?last.x-gap-width:crop.x+crop.width-margin-width,y=last?last.y:crop.y+margin;
  if(x<crop.x+margin){x=crop.x+crop.width-margin-width;y=Math.max(...existing.map(p=>p.y+p.height))+gap;}
  const box={x,y,width,height};
  // Wrap only inside the verified lower band. Never create a page or search table cells.
  if(box.y+height>crop.y+crop.height*.28)throw review('no queda espacio en la zona inferior de la primera hoja; no se agregó ninguna página.');
  if(existing.some(p=>p.x<box.x+width+4&&p.x+p.width>box.x-4&&p.y<box.y+height+4&&p.y+p.height>box.y-4))throw review('la nueva firma se superpondría a una ubicación histórica.');
  const {getDocument}=await import('pdfjs-dist/legacy/build/pdf.mjs');
  const loading=getDocument({data:Uint8Array.from(bytes),standardFontDataUrl,useSystemFonts:false,disableFontFace:true,isEvalSupported:false,stopAtErrors:true});
  let document;
  try{
    document=await loading.promise;const source=await document.getPage(1),viewport=source.getViewport({scale:2,rotation:0});
    if(viewport.width*viewport.height>24000000)throw review('la página excede el tamaño seguro para comprobar el espacio.');
    // Independent text geometry guard: missing font glyphs must never look like free space.
    const text=await source.getTextContent();
    for(const item of text.items){
      if(!item.str?.trim() || !item.transform)continue;
      const [a,b,c,d,x,y]=item.transform,h=Math.max(Math.hypot(c,d),item.height||0,1);
      const x1=x-Math.abs(c)-2,x2=x+Math.abs(item.width||0)+Math.abs(c)+2;
      const y1=y-h*.5-2,y2=y+h*1.5+Math.abs(b)+2;
      if(x1<box.x+width+4 && x2>box.x-4 && y1<box.y+height+4 && y2>box.y-4)
        throw review('la zona inferior contiene texto. No se agregó ninguna página.');
    }
    const canvas=createCanvas(Math.ceil(viewport.width),Math.ceil(viewport.height)),ctx=canvas.getContext('2d');
    await source.render({canvasContext:ctx,viewport,background:'rgb(255,255,255)'}).promise;
    const [left,top]=viewport.convertToViewportPoint(box.x-4,box.y+height+4);
    const [right,bottom]=viewport.convertToViewportPoint(box.x+width+4,box.y-4);
    const x0=Math.floor(left),y0=Math.floor(top),rw=Math.ceil(right)-x0,rh=Math.ceil(bottom)-y0;
    if(x0<0||y0<0||x0+rw>canvas.width||y0+rh>canvas.height)throw review('el sello queda fuera del área visible.');
    const pixels=ctx.getImageData(x0,y0,rw,rh).data;
    // Any non-white pixel rejects the placement, including images, vectors and annotations.
    for(let i=0;i<pixels.length;i+=4)if(pixels[i]<248||pixels[i+1]<248||pixels[i+2]<248)throw review('la zona inferior contiene contenido. Selecciona y revisa un espacio libre; no se agregó ninguna página.');
  }catch(e){if(e.status===422)throw e;throw review('no se pudo comprobar visualmente el espacio libre del PDF. No se modificó el documento.')}finally{await loading.destroy()}
  const font=await pdf.embedFont(StandardFonts.Helvetica),bold=await pdf.embedFont(StandardFonts.HelveticaBold);
  const label=ACTION_LABELS[stamp.action];if(!label)throw review('acción no válida.');
  const rawName=String(stamp.name||stamp.email);
  const displayName=rawName===rawName.toLocaleUpperCase('es')?rawName.toLocaleLowerCase('es').replace(/\p{L}+/gu,w=>w[0].toLocaleUpperCase('es')+w.slice(1)):rawName;
  const rows=[
    {text:displayName,font:bold,size:8},
    {text:String(stamp.rut||label),font:bold,size:7},
    {text:String(stamp.title||(stamp.role==='ADMIN'?'Rol: administrador':'Rol: firmante')),font:bold,size:8},
    {text:(hasSignature||stamp.rut)?`${label} · SKM INDUSTRIAL`:'SKM INDUSTRIAL',font:bold,size:(hasSignature||stamp.rut)?7:8},
  ];
  const fittedRows=rows.map(r=>({...r,...fitted(r.text,r.font,width,r.size)}));
  if(graphic){
    const scale=Math.min(width/graphic.width,26/graphic.height),w=graphic.width*scale,h=graphic.height*scale;
    page.drawImage(graphic,{x:box.x+(width-w)/2,y:box.y+height-26+(26-h)/2,width:w,height:h});
  }
  fittedRows.forEach((r,i)=>page.drawText(r.text,{x:box.x+(width-r.font.widthOfTextAtSize(r.text,r.size))/2,y:box.y+height-(hasSignature?35:8)-i*(hasSignature?9:10),font:r.font,size:r.size,color:rgb(0,0,0)}));

  return {bytes:await pdf.save(),placement:{...box,page:1,kind:hasSignature?'verified-profile-v3':'verified-compact-v2'},pageCount:pdf.getPageCount()};
}
