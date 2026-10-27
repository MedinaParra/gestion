// Real installed @vercel/blob SDK + real store; synthetic HTTP transport only.
// The weak/compressed vs strong/identity shape was observed read-only in Vercel.
// MockAgent disables every external network connection. No real Blob credentials/data.
import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs/promises';import crypto from 'node:crypto';
import {get,put} from '@vercel/blob';import undici from 'undici';
const {MockAgent,setGlobalDispatcher,getGlobalDispatcher}=undici;
async function harness(run,{forceWeak=false}={}){
 const previous=getGlobalDispatcher(),mock=new MockAgent();mock.disableNetConnect();setGlobalDispatcher(mock);
 const token='vercel_blob_rw_synthetic_DO_NOT_USE',path='skm-compras/meta/requests.json';let revision=1,saved={requests:[{id:'PRUEBA-001',status:'APPROVED',versions:[{pathname:'synthetic/original.pdf'}],signatures:[{action:'APPROVED'}]}],audit:[],users:{}},writes=0;const observed=[];
 const metadata=()=>({url:`https://synthetic.private.blob.vercel-storage.com/${path}`,downloadUrl:'https://synthetic.private.blob.vercel-storage.com/synthetic',pathname:path,contentType:'application/json',contentDisposition:'',etag:`"revision-${revision}"`,size:1,uploadedAt:new Date(0).toISOString()});
 const key='__sdk'+crypto.randomUUID().replaceAll('-','');
 try{
  mock.get('https://synthetic.private.blob.vercel-storage.com').intercept({path:`/${path}?cache=0`,method:'GET'}).reply(options=>{const identity=new Headers(options.headers).get('accept-encoding')==='identity';observed.push({identity});return {statusCode:200,data:JSON.stringify(saved),responseOptions:{headers:{etag:identity&&!forceWeak?metadata().etag:'W/'+metadata().etag,'content-type':'application/json'}}}}).persist();
  mock.get('https://vercel.com').intercept({path:/./,method:'PUT'}).reply(options=>{const headers=new Headers(options.headers),etag=headers.get('x-if-match');observed.push({ifMatch:etag,allowOverwrite:headers.get('x-allow-overwrite')});if(etag!==metadata().etag)return {statusCode:412,data:JSON.stringify({error:{code:'precondition_failed',message:'Synthetic revision conflict'}}),responseOptions:{headers:{'content-type':'application/json'}}};saved=JSON.parse(options.body);revision++;writes++;return {statusCode:200,data:JSON.stringify(metadata()),responseOptions:{headers:{'content-type':'application/json'}}}}).persist();
  globalThis[key]={get:(p,o)=>get(p,{...o,token}),put:(p,b,o)=>put(p,b,{...o,token}),crypto};
  const source=(await fs.readFile(new URL('../src/lib/store.js',import.meta.url),'utf8')).replace(/^import .*;\n/gm,'').replace("'./document-reference.mjs'",JSON.stringify(new URL('../src/lib/document-reference.mjs',import.meta.url).href));const store=await import('data:text/javascript;base64,'+Buffer.from(`const {get,put,crypto}=globalThis.${key};\n`+source).toString('base64'));
  await run({store,observed,state:()=>structuredClone(saved),writes:()=>writes});
 }finally{delete globalThis[key];setGlobalDispatcher(previous);await mock.close()}
}
test('real SDK identity read yields strong If-Match and successfully saves unchanged revision',async()=>harness(async h=>{const state=await h.store.readState();state.requests.push({id:'PRUEBA-002'});await h.store.writeState(state);assert.equal(h.writes(),1);assert.equal(h.state().requests.length,2);assert.deepEqual(h.observed,[{identity:true},{ifMatch:'"revision-1"',allowOverwrite:'1'}])}));
test('real SDK still rejects stale CAS after trash wins; no revival or loss of documents',async()=>harness(async h=>{const first=await h.store.readState(),stale=await h.store.readState();first.requests[0].deletedAt='SYNTHETIC';await h.store.writeState(first);stale.requests[0].status='IN_SIGNATURES';await assert.rejects(h.store.writeState(stale),e=>e.status===409);assert.equal(h.writes(),1);assert.equal(h.state().requests[0].deletedAt,'SYNTHETIC');assert.equal(h.state().requests[0].status,'APPROVED');assert.equal(h.state().requests[0].versions[0].pathname,'synthetic/original.pdf')}));
test('weak validator despite identity fails closed before any upload, never strips W/',async()=>harness(async h=>{await assert.rejects(h.store.readState(),e=>e.status===503);assert.equal(h.writes(),0);assert.deepEqual(h.observed,[{identity:true}])},{forceWeak:true}));
