import crypto from 'node:crypto';
import {createCanvas,loadImage} from '@napi-rs/canvas';
import {PHOTO_MAX_BYTES,SIGNATURE_MAX_BYTES} from './profile.mjs';
import {fail} from './request-lifecycle.mjs';
function dimensions(bytes,type){
 if(type==='png'){
  if(bytes.length<24||!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))||bytes.toString('ascii',12,16)!=='IHDR')fail('Imagen PNG no válida.',400);
  return {width:bytes.readUInt32BE(16),height:bytes.readUInt32BE(20)};
 }
 if(bytes[0]!==255||bytes[1]!==216)fail('Imagen JPEG no válida.',400);
 let at=2;while(at+4<=bytes.length){if(bytes[at]!==255)fail('Imagen JPEG no válida.',400);while(at<bytes.length&&bytes[at]===255)at++;if(at>=bytes.length)break;const marker=bytes[at++];if(marker===0xd9||marker===0xda)break;if(at+2>bytes.length)break;const length=bytes.readUInt16BE(at);if(length<2||at+length>bytes.length)break;if([0xc0,0xc1,0xc2].includes(marker)&&length>=7)return {height:bytes.readUInt16BE(at+3),width:bytes.readUInt16BE(at+5)};at+=length}
 fail('Imagen JPEG no válida.',400);
}
export async function normalizeProfileImage(dataUrl,kind){
 const max=kind==='photo'?PHOTO_MAX_BYTES:SIGNATURE_MAX_BYTES;
 if(typeof dataUrl!=='string'||dataUrl.length>Math.ceil(max/3)*4+40)fail('La imagen supera el tamaño permitido.',413);
 const match=dataUrl.match(/^data:image\/(png|jpeg);base64,([A-Za-z0-9+/]+={0,2})$/);if(!match||(kind==='signature'&&match[1]!=='png'))fail('Formato de imagen no permitido. Usa PNG o JPEG para la foto y PNG para la firma.',400);
 const bytes=Buffer.from(match[2],'base64');if(!bytes.length||bytes.length>max||bytes.toString('base64')!==match[2])fail('Imagen no válida o demasiado grande.',400);
 const {width,height}=dimensions(bytes,match[1]);const limit=kind==='photo'?4096:2000;
 if(width<1||height<1||width>limit||height>(kind==='photo'?4096:1000)||width*height>16777216)fail('Las dimensiones de la imagen no están permitidas.',400);
 let image;try{image=await loadImage(bytes)}catch{fail('No se pudo decodificar la imagen.',400)}
 if(image.width!==width||image.height!==height)fail('Las dimensiones de la imagen no son coherentes.',400);
 const scale=Math.min(1,(kind==='photo'?512:1200)/width,(kind==='photo'?512:400)/height),w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
 const canvas=createCanvas(w,h),ctx=canvas.getContext('2d');ctx.drawImage(image,0,0,w,h);let outputCanvas=canvas;
 if(kind==='signature'){
  const pixels=ctx.getImageData(0,0,w,h);let ink=0,minX=w,minY=h,maxX=0,maxY=0;
  for(let i=0;i<pixels.data.length;i+=4){const alpha=pixels.data[i+3];const dark=alpha>80&&(pixels.data[i]+pixels.data[i+1]+pixels.data[i+2])/3<200;
   if(dark){const x=(i/4)%w,y=Math.floor(i/4/w);ink++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);pixels.data[i]=pixels.data[i+1]=pixels.data[i+2]=0}else pixels.data[i+3]=0;
  }
  if(ink<15||maxX-minX<10||maxY-minY<4||ink>w*h*.5)fail('Dibuja una firma legible; el lienzo está vacío o no es válido.',400);
  ctx.putImageData(pixels,0,0);
  const pad=4,x=Math.max(0,minX-pad),y=Math.max(0,minY-pad),right=Math.min(w,maxX+pad+1),bottom=Math.min(h,maxY+pad+1);
  outputCanvas=createCanvas(right-x,bottom-y);outputCanvas.getContext('2d').drawImage(canvas,x,y,right-x,bottom-y,0,0,right-x,bottom-y);
 }
 const output=outputCanvas.toBuffer('image/png');return {bytes:output,width:outputCanvas.width,height:outputCanvas.height,contentType:'image/png',sha256:crypto.createHash('sha256').update(output).digest('hex')};
}
