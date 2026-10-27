import test from 'node:test';import assert from 'node:assert/strict';
import {assessOcr} from '../src/lib/pdf-extraction.mjs';
function sample(score=93){return {text:'NETO 1.375.000\nIVA 261.250\nTOTAL 1.636.250',confidence:99,blocks:[{paragraphs:[{lines:[{words:['1.375.000','261.250','1.636.250'].map(text=>({text,confidence:score}))}]}]}]};}
test('use numeric token score, never pretend arithmetic is OCR confidence',()=>{const d=assessOcr(sample(87));assert.equal(d.confidence,87);assert.equal(d.status,'VERIFIED');});
test('low numeric confidence blocks arithmetically consistent reading',()=>{const d=assessOcr(sample(42));assert.equal(d.status,'REVIEW_REQUIRED');assert.equal(d.amounts.neto,1375000);assert.equal(d.reason,'LOW_NUMERIC_OCR_CONFIDENCE');});
test('missing word evidence cannot claim reliable OCR',()=>{const d=sample();delete d.blocks;assert.equal(assessOcr(d).status,'REVIEW_REQUIRED');});
test('never silently change letters to digits',()=>{const d=sample();d.text='NETO 1.375.OOO\nIVA 261.25O\nTOTAL 1.636.25O';assert.equal(assessOcr(d).status,'REVIEW_REQUIRED');});

test('OCR candidate remains visible without claiming human verification',async()=>{const {displayAmounts}=await import('../src/lib/amounts.mjs');const amounts={neto:1375000,iva:261250,total:1636250};assert.deepEqual(displayAmounts({amounts,ocr:{extractionVersion:2,status:'OCR_CANDIDATE',numericEvidence:{total:{score:54}}}}),amounts);});
