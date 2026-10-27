import test from 'node:test';
import assert from 'node:assert/strict';
import {requestedDocument} from '../src/lib/document-version.mjs';
test('explicit missing original does not return latest signed version',()=>{
 const item={versions:[{version:2,kind:'SIGNED',pathname:'signed.pdf'}]};
 assert.equal(requestedDocument(item,1),null);assert.equal(requestedDocument(item,99),null);
 assert.equal(requestedDocument(item,0),null);assert.equal(requestedDocument(item,NaN),null);
 assert.equal(requestedDocument(item,2).pathname,'signed.pdf');
});
test('explicit existing original keeps exact source including legacy document paths',()=>{
 const item={versions:[{version:1,kind:'ORIGINAL',pathname:'original.pdf'},{version:2,kind:'SIGNED',pathname:'signed.pdf'}]};
 assert.equal(requestedDocument(item,1).pathname,'original.pdf');
 assert.equal(requestedDocument({documents:[{version:3,storagePath:'legacy.pdf'}]},3).pathname,'legacy.pdf');
 assert.equal(requestedDocument({versions:[{version:1}]},1),null);
});
