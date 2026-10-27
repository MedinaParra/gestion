import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {PDFDocument,StandardFonts,rgb,degrees,PDFName} from 'pdf-lib';
import {stampFirstPage} from '../src/lib/first-page-stamp.mjs';
const out=new URL('../test-results/signatures/',import.meta.url);await fs.mkdir(out,{recursive:true});
const stamp={id:'00000000-0000-4000-8000-000000000001',name:'NOMBRE DE PRUEBA',rut:'RUT: [SIN DATOS]',title:'CARGO DE PRUEBA',email:'prueba@example.invalid',role:'ADMIN',action:'SIGNED',at:'2026-10-27T12:00:00Z'};
async function fixture({size=[595.28,841.89],crop,rotation=0,blocked=false,pages=1,signature=false}={}){
 const pdf=await PDFDocument.create(),font=await pdf.embedFont(StandardFonts.Helvetica);
 const p=pdf.addPage(size);if(crop)p.setCropBox(...crop);p.setRotation(degrees(rotation));
 p.drawText('PRUEBA SINTETICA - NO ES COTIZACION REAL',{x:55,y:size[1]-60,font,size:12});
 p.drawText('OT 1729 / 1731 - Cantidad 4 x 30.000 = 120.000',{x:55,y:size[1]-110,font,size:10});
 p.drawText('SUB TOTAL 240.000 / IVA 45.600 / TOTAL 285.600',{x:55,y:size[1]-140,font,size:10});
 p.drawRectangle({x:50,y:size[1]-230,width:size[0]-100,height:70,borderWidth:1,borderColor:rgb(0,0,0)});
 if(blocked)p.drawRectangle({x:size[0]-140,y:20,width:120,height:50,color:rgb(.7,.7,.7)});
 for(let i=1;i<pages;i++)pdf.addPage(size).drawText('PAGINA EXISTENTE PRUEBA',{x:55,y:200,font,size:12});
 if(signature)pdf.context.register(pdf.context.obj({Type:'Sig',ByteRange:[0,10,20,30]}));
 return pdf.save();
}
async function geometry(bytes){const p=await PDFDocument.load(bytes);return p.getPages().map(x=>({media:x.getMediaBox(),crop:x.getCropBox(),rotation:x.getRotation()}))}
test('portrait: first-page stamp and second signer; unchanged pages/boxes',async()=>{
 const src=await fixture(),one=await stampFirstPage(src,stamp),two=await stampFirstPage(one.bytes,{...stamp,id:'00000000-0000-4000-8000-000000000002'},[{...stamp,placement:one.placement}]);
 assert.equal(one.pageCount,1);assert.equal(two.pageCount,1);assert.deepEqual(await geometry(src),await geometry(two.bytes));assert.ok(two.placement.x+two.placement.width<one.placement.x);
 await fs.writeFile(new URL('portrait-source-PRUEBA.pdf',out),src);await fs.writeFile(new URL('portrait-two-stamps-PRUEBA.pdf',out),two.bytes);
});
test('landscape and crop preserve page geometry and page count',async()=>{
 for(const options of [{size:[841.89,595.28]},{size:[650,900],crop:[20,20,600,850]}]){const src=await fixture(options),r=await stampFirstPage(src,stamp);assert.equal(r.pageCount,1);assert.deepEqual(await geometry(src),await geometry(r.bytes));await fs.writeFile(new URL(options.crop?'crop-stamp-PRUEBA.pdf':'landscape-stamp-PRUEBA.pdf',out),r.bytes)}
});
test('existing pages are not removed or appended',async()=>{const src=await fixture({pages:2}),r=await stampFirstPage(src,stamp);assert.equal(r.pageCount,2);assert.deepEqual(await geometry(src),await geometry(r.bytes))});
test('painted content blocks rather than covering',async()=>{await assert.rejects(()=>fixture({blocked:true}).then(src=>stampFirstPage(src,stamp)),e=>e.status===422&&/contiene contenido/.test(e.message))});
test('rotation requires review, no silent orientation change',async()=>{for(const rotation of [90,180,270])await assert.rejects(()=>fixture({rotation}).then(src=>stampFirstPage(src,stamp)),e=>e.status===422&&/rotada/.test(e.message))});
test('cryptographic signature rejects before mutation',async()=>{await assert.rejects(()=>fixture({signature:true}).then(src=>stampFirstPage(src,stamp)),e=>e.status===422&&/firma digital/.test(e.message))});
test('untracked prior stamp still detected as content',async()=>{const one=await stampFirstPage(await fixture(),stamp);await assert.rejects(()=>stampFirstPage(one.bytes,stamp),e=>e.status===422)});
test('exhausted area blocks instead of adding page',async()=>{const legacy={...stamp,rut:null};let bytes=await fixture(),previous=[];for(let i=0;i<20;i++){const r=await stampFirstPage(bytes,legacy,previous);bytes=r.bytes;previous.push({...legacy,placement:r.placement})}await assert.rejects(()=>stampFirstPage(bytes,legacy,previous),e=>e.status===422&&/no queda espacio/.test(e.message))});

test('direct nested DocMDP is rejected',async()=>{const p=await PDFDocument.load(await fixture());p.catalog.set(PDFName.of('Perms'),p.context.obj({DocMDP:{Type:'Sig'}}));await assert.rejects(()=>p.save().then(b=>stampFirstPage(b,stamp)),e=>e.status===422&&/firma digital/.test(e.message))});
test('footer text blocks',async()=>{const p=await PDFDocument.load(await fixture()),font=await p.embedFont(StandardFonts.Helvetica);p.getPage(0).drawText('NO TAPAR - PIE DE PAGINA',{x:450,y:45,font,size:10});await assert.rejects(()=>p.save().then(b=>stampFirstPage(b,stamp)),e=>e.status===422&&/contiene texto/.test(e.message))});
test('optional exact private source: two first-page test stamps preserve pages and boxes',{skip:!process.env.SKM_TEST_PDF},async()=>{
 const source=await fs.readFile(process.env.SKM_TEST_PDF),one=await stampFirstPage(source,stamp),two=await stampFirstPage(one.bytes,{...stamp,id:'00000000-0000-4000-8000-000000000002'},[{...stamp,placement:one.placement}]);
 assert.equal(one.pageCount,1);assert.equal(two.pageCount,1);assert.deepEqual(await geometry(source),await geometry(two.bytes));
});
