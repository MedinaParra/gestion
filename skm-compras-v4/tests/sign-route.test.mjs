// Offline route harness: the real POST handler with in-memory adapters only.
// No credentials, network, Blob storage or production requests are used.
import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs/promises';import crypto from 'node:crypto';
import {PDFDocument,StandardFonts,rgb} from 'pdf-lib';import {stampFirstPage,ACTION_LABELS} from '../src/lib/first-page-stamp.mjs';
async function run({blocked=false,duplicate=false}={}){
 const pdf=await PDFDocument.create(),page=pdf.addPage();page.drawText('PRUEBA LOCAL',{x:50,y:700,font:await pdf.embedFont(StandardFonts.Helvetica),size:12});if(blocked)page.drawRectangle({x:450,y:16,width:140,height:68,color:rgb(.5,.5,.5)});const source=await pdf.save();
 const item={id:'PRUEBA-001',signers:['prueba@example.invalid'],signatures:duplicate?[{email:'prueba@example.invalid',action:'SIGNED'}]:[],versions:[{version:1,pathname:'memory/original.pdf'}]},state={requests:[item],audit:[]};let uploads=[],writes=0;
 const adapters={crypto,stampFirstPage,ACTION_LABELS,NextResponse:{json:(v,o)=>Response.json(v,o)},get:async()=>({stream:new Blob([source]).stream()}),put:async(path,bytes)=>uploads.push({path,bytes}),getSession:async()=>({email:'prueba@example.invalid',name:'PERSONA PRUEBA'}),requireUser:x=>x,roleFor:()=> 'ADMIN',readState:async()=>structuredClone(state),writeState:async()=>{writes++},addAudit:()=>{},latestDoc:x=>x.versions.at(-1),safeName:x=>x};
 const key='__skmTest'+crypto.randomUUID().replaceAll('-','');globalThis[key]=adapters;
 try{const sourceCode=(await fs.readFile(new URL('../src/app/api/sign/route.js',import.meta.url),'utf8')).replace(/^import .*;\n/gm,'');const module=await import('data:text/javascript;base64,'+Buffer.from(`const {${Object.keys(adapters).join(',')}}=globalThis.${key};\n`+sourceCode).toString('base64'));
 const response=await module.POST(new Request('http://local.test/api/sign',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({id:item.id,action:'SIGNED'})}));return {status:response.status,body:await response.json(),uploads,writes};}finally{delete globalThis[key]}
}
test('POST offline adapter creates one first-page version and one audit-state write',async()=>{const r=await run();assert.equal(r.status,200);assert.equal(r.uploads.length,1);assert.equal(r.writes,1);assert.equal((await PDFDocument.load(r.uploads[0].bytes)).getPageCount(),1);assert.equal(r.body.request.signatures[0].placement.page,1);assert.equal(r.body.request.versions.length,2)});
test('POST unsafe placement returns422 without uploading or persisting',async()=>{const r=await run({blocked:true});assert.equal(r.status,422);assert.equal(r.uploads.length,0);assert.equal(r.writes,0)});
test('POST duplicate action returns409 without uploading or persisting',async()=>{const r=await run({duplicate:true});assert.equal(r.status,409);assert.equal(r.uploads.length,0);assert.equal(r.writes,0)});
