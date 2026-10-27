import test from 'node:test';
import assert from 'node:assert/strict';
import {parseCLP,detectAmounts,textFromPdfItems,displayAmounts,originalVersion} from '../src/lib/amounts.mjs';
test('Chilean and international grouping, never truncate thousands',()=>{
 for(const [raw,n] of [['240.000',240000],['285.600',285600],['1.250.000,00',1250000],['1,250,000.00',1250000],['$ 240 000',240000],['240.000.-',240000]]) assert.equal(parseCLP(raw),n,raw);
 for(const raw of ['12.34.56','1,23,456','NaN','-240000','240,25']) assert.equal(parseCLP(raw),null,raw);
});
test('regression AC SOLUCIONES screenshot values and quantity 4',()=>{
 const input='ITEM DESCRIPCION UNIDAD CANT. VALOR TOTAL\n1 SOLDADURA OT-1729 C/U 4 30.000 120.000\n2 SOLDADURA OT-1731 C/U 4 30.000 120.000\nSUB TOTAL 240.000\nIVA 19% 45.600\nTOTAL 285.600';
 assert.deepEqual(detectAmounts(input).amounts,{neto:240000,iva:45600,total:285600});
});
test('flattened table cannot produce confident total 4',()=>assert.equal(detectAmounts('TOTAL 4 30.000 120.000 SUB TOTAL 240.000 IVA 19% 45.600 TOTAL 285.600').status,'REVIEW_REQUIRED'));
test('reject inconsistent or incomplete extraction, do not guess net',()=>{
 for(const text of ['TOTAL 4','NETO 240.000\nIVA 19% 45.600\nTOTAL 285.800','TOTAL 285.600']) assert.equal(detectAmounts(text).status,'REVIEW_REQUIRED');
});
test('two consistent totals are ambiguous, do not select by position',()=>assert.equal(detectAmounts('NETO 100\nIVA 19\nTOTAL 119\nNETO 200\nIVA 38\nTOTAL 238').status,'REVIEW_REQUIRED'));
test('rebuild physical PDF rows despite arbitrary item order',()=>{
 const items=[{str:'285.600',transform:[1,0,0,1,400,100]},{str:'SUB TOTAL',transform:[1,0,0,1,300,140]},{str:'240.000',transform:[1,0,0,1,400,140]},{str:'TOTAL',transform:[1,0,0,1,300,100]},{str:'45.600',transform:[1,0,0,1,400,120]},{str:'IVA 19%',transform:[1,0,0,1,300,120]}];
 assert.equal(detectAmounts(textFromPdfItems(items)).status,'VERIFIED');
});
test('legacy 98 percent is not treated as validated or displayed',()=>assert.deepEqual(displayAmounts({amounts:{total:4},ocr:{method:'TEXTO_PDF',confidence:98}}),{neto:null,iva:null,total:null}));
test('payment terms in left column do not hide right-hand totals',()=>assert.deepEqual(detectAmounts('CONDICIONES DE PAGO : 15 DIAS CONTRA FACTURA SUB TOTAL 240.000\nPLAZO ENTREGA : 01 DIAS IVA 19% 45.600\nINCLUYE TRASLADO : NO TOTAL 285.600').amounts,{neto:240000,iva:45600,total:285600}));
test('optional real source PDF regression (private fixture is never committed)',{skip:!process.env.SKM_TEST_PDF},async()=>{
 const fs=await import('node:fs');const {getDocument}=await import('pdfjs-dist/legacy/build/pdf.mjs');
 const doc=await getDocument({data:new Uint8Array(fs.readFileSync(process.env.SKM_TEST_PDF))}).promise;
 try{let text='';for(let i=1;i<=doc.numPages;i++)text+='\n'+textFromPdfItems((await (await doc.getPage(i)).getTextContent()).items);
 assert.deepEqual(detectAmounts(text).amounts,{neto:240000,iva:45600,total:285600});}finally{await doc.destroy();}
});
test('reject OCR-fragmented digits and malformed space grouping',()=>{
 for(const raw of ['1 2 3','24 0000','1 0 0','1 250.000','12 34','1  000']) assert.equal(parseCLP(raw),null,raw);
 assert.equal(parseCLP('1 250 000,00'),1250000);
 assert.equal(detectAmounts('NETO 1 0 0\nIVA 1 9\nTOTAL 1 1 9').status,'REVIEW_REQUIRED');
});
test('explicit non-CLP currency anywhere prevents displaying values as pesos',()=>{
 for(const currency of ['USD','EUR','UF','UTM','US$','DÓLARES','€']) {
  const result=detectAmounts(`COTIZACION EN ${currency}\nNETO 100\nIVA 19\nTOTAL 119`);
  assert.equal(result.status,'REVIEW_REQUIRED',currency);assert.equal(result.reason,'NON_CLP_CURRENCY');
 }
 assert.equal(detectAmounts('MONEDA CLP\nNETO 100\nIVA 19\nTOTAL 119').status,'VERIFIED');
});

test('never substitute a signed or ambiguous legacy document for the original',()=>{
 assert.equal(originalVersion({versions:[{version:1,kind:'ORIGINAL'},{version:2,kind:'SIGNED'}]}),1);
 assert.equal(originalVersion({documents:[{version:3,kind:'ORIGINAL'}]}),3);
 assert.equal(originalVersion({versions:[{version:2,kind:'SIGNED'}]}),null);
 assert.equal(originalVersion({versions:[{version:1}]}),null);
 assert.equal(originalVersion({versions:[{version:1,kind:'ORIGINAL'},{version:2,kind:'ORIGINAL'}]}),null);
});

test('label punctuation from OCR is not part of the number',()=>assert.deepEqual(detectAmounts('SUB TOTAL: 1.375.000\nIVA 19%: 261.250\nTOTAL, 1.636.250').amounts,{neto:1375000,iva:261250,total:1636250}));
