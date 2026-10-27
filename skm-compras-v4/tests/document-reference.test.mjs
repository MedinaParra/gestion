import test from 'node:test';import assert from 'node:assert/strict';import {latestDoc} from '../src/lib/document-reference.mjs';
test('shared document reference supports legacy arrays, single objects and root without changing precedence',()=>{
 for(const key of ['versions','documents','docs','files','attachments'])assert.deepEqual(latestDoc({[key]:[{path:'synthetic/first.pdf'},{blobPath:'synthetic/second.pdf',sha256:'synthetic-hash'}]}),{blobPath:'synthetic/second.pdf',sha256:'synthetic-hash',pathname:'synthetic/second.pdf',version:2,filename:'cotizacion.pdf'});
 for(const key of ['document','file','attachment','pdf'])assert.equal(latestDoc({[key]:{storagePath:'synthetic/test.pdf',version:7}}).version,7);
 assert.equal(latestDoc({pathname:'synthetic/root.pdf'}).version,1);
 assert.equal(latestDoc({versions:[{pathname:'synthetic/preferred.pdf'}],docs:[{pathname:'synthetic/other.pdf'}]}).pathname,'synthetic/preferred.pdf');
 assert.equal(latestDoc({versions:[{},null],file:{key:'synthetic/fallback.pdf'}}).pathname,'synthetic/fallback.pdf');assert.equal(latestDoc({}),null);
});
