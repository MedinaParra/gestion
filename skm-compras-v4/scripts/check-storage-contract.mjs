// Incident diagnostic, READ ONLY. Run once in an already-authorized build environment.
// No writes/deletes, new permissions or credential export. Logs booleans/status only.
import {head,get} from '@vercel/blob';
import {createHash} from 'node:crypto';
const pathname='skm-compras/meta/requests.json';
const shape=value=>({present:typeof value==='string'&&value.length>0,weak:typeof value==='string'&&value.startsWith('W/'),quoted:typeof value==='string'&&/^".*"$/.test(value)});
const normalize=value=>typeof value==='string'?value.replace(/^W\//,'').replace(/^"|"$/g,''):null;
const safeError=error=>({errorClass:['BlobError','BlobAccessError','BlobNotFoundError','BlobServiceNotAvailable','BlobPreconditionFailedError'].includes(error?.constructor?.name)?error.constructor.name:'Error',status:Number.isInteger(error?.status)?error.status:Number.isInteger(error?.statusCode)?error.statusCode:null});
async function inspectRead(identity,expectedTag){
 const result=await get(pathname,{access:'private',useCache:false,abortSignal:AbortSignal.timeout(10000),...(identity?{headers:{'Accept-Encoding':'identity'}}:{})});
 if(!result||result.statusCode!==200)return {readStatus:result?.statusCode||404};
 const contentEncodingAvailable=typeof result.headers?.get==='function';
 const hash=createHash('md5');for await(const chunk of result.stream)hash.update(chunk);const digest=hash.digest('hex');
 return {readStatus:result.statusCode,get:shape(result.blob?.etag),rawEqual:expectedTag===result.blob?.etag,normalizedEqual:normalize(expectedTag)===normalize(result.blob?.etag),headMatchesBodyMD5:normalize(expectedTag)===digest,getMatchesBodyMD5:normalize(result.blob?.etag)===digest,contentEncodingAvailable,compressed:contentEncodingAvailable?Boolean(result.headers.get('content-encoding')):null};
}
try{
 const before=await head(pathname,{abortSignal:AbortSignal.timeout(10000)});
 let ordinary,identity;
 try{ordinary=await inspectRead(false,before.etag)}catch(e){ordinary=safeError(e)}
 try{identity=await inspectRead(true,before.etag)}catch(e){identity=safeError(e)}
 const after=await head(pathname,{abortSignal:AbortSignal.timeout(10000)});
 console.info('SKM_STORAGE_READ_DIAGNOSTIC',JSON.stringify({head:shape(before.etag),headStable:before.etag===after.etag,ordinary,identity}));
}catch(error){console.info('SKM_STORAGE_READ_DIAGNOSTIC',JSON.stringify({diagnosticFailed:true,...safeError(error)}));}
