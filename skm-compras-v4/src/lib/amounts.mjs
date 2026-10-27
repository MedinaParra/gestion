// Currency parsing is deliberately conservative: ambiguous values require review.
export const EXTRACTION_VERSION = 2;
export function parseCLP(raw) {
  let s = String(raw ?? '').trim().replace(/(?:CLP|\$)/gi, '').trim().replace(/\.-$/, '');
  if (/\s/.test(s)) {
    // Spaces are allowed only as complete thousand groups, never OCR digit fragments.
    if (!/^\d{1,3}(?:[ \u00a0]\d{3})+(?:[.,]\d{1,2})?$/.test(s)) return null;
    s = s.replace(/[ \u00a0]/g, '');
  }
  if (!s || !/^[\d.,]+$/.test(s)) return null;
  if (/^\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?$/.test(s)) s = s.replace(/\./g, '').replace(',', '.');
  else if (/^\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?$/.test(s)) s = s.replace(/,/g, '');
  else if (/^\d+(?:[.,]\d{1,2})?$/.test(s)) s = s.replace(',', '.');
  else return null;
  const n = Number(s);
  return Number.isSafeInteger(n) && n >= 0 ? n : null;
}
export function validateAmounts(a) {
  const values = ['neto','iva','total'].map(k => a?.[k]);
  if (!values.every(v => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0) || !a.neto || !a.total) return false;
  return Math.abs(a.neto + a.iva - a.total) <= 1 && Math.abs(Math.round(a.neto * .19) - a.iva) <= 1;
}
// Preserve physical rows instead of flattening an entire page into a single line.
export function textFromPdfItems(items) {
  const rows = [];
  for (const item of items) {
    if (!item.str?.trim()) continue;
    const x = item.transform?.[4] ?? 0, y = item.transform?.[5];
    if (!Number.isFinite(y)) { rows.push({y:-rows.length*10,items:[{x,text:item.str}]}); continue; }
    let row = rows.find(r => Math.abs(r.y-y) <= 2);
    if (!row) { row={y,items:[]}; rows.push(row); }
    row.items.push({x,text:item.str});
  }
  return rows.sort((a,b)=>b.y-a.y).map(r=>r.items.sort((a,b)=>a.x-b.x).map(i=>i.text).join(' ')).join('\n');
}
export function detectAmounts(text) {
  const source=String(text??'');
  if (/\b(?:USD|EUR|UF|UTM|GBP|BRL|ARS|PEN|US\s*DOLLARS?|D[ÓO]LARES?|EUROS?|UNIDADES?\s+DE\s+FOMENTO)\b|US\$|[€£]/i.test(source)) return {amounts:{neto:null,iva:null,total:null},status:'REVIEW_REQUIRED',confidence:0,extractionVersion:EXTRACTION_VERSION,reason:'NON_CLP_CURRENCY',candidates:{neto:[],iva:[],total:[]}};
  const lines=source.replace(/\u00a0/g,' ').split(/\n+/).map(s=>s.trim()).filter(Boolean);
  const candidates={neto:[],iva:[],total:[]};
  const label=/\b(TOTAL\s+NETO|NETO|SUB\s*TOTAL|IVA(?:\s*19\s*%)?|TOTAL(?:\s+A\s+PAGAR|\s+PAGAR)?)\s*[:,;\-]?\s*(.*)$/i;
  const amount=/^(?:CLP\s*)?\$?\s*([\d][\d., ]*)(?:\s*CLP|\.\-)?\s*$/i;
  for(let i=0;i<lines.length;i++) {
    const m=lines[i].match(label); if(!m) continue;
    const key=/IVA/i.test(m[1])?'iva':/NETO|SUB/i.test(m[1])?'neto':'total';
    // A naked table header TOTAL must not consume quantities in the following row.
    let value=m[2]; if(!value && lines[i+1] && amount.test(lines[i+1])) value=lines[i+1];
    const numeric=value.match(amount); if(!numeric) continue;
    const n=parseCLP(numeric[1]); if(n!==null) candidates[key].push(n);
  }
  const unique=Object.fromEntries(Object.entries(candidates).map(([k,v])=>[k,[...new Set(v)]]));
  const matches=[];
  for(const neto of unique.neto) for(const iva of unique.iva) for(const total of unique.total) {
    const a={neto,iva,total}; if(validateAmounts(a)) matches.push(a);
  }
  const verified=matches.length===1;
  return {amounts:verified?matches[0]:{neto:null,iva:null,total:null},status:verified?'VERIFIED':'REVIEW_REQUIRED',confidence:null,confidenceKind:'TEXT_ARITHMETIC_CHECK',extractionVersion:EXTRACTION_VERSION,candidates:unique};
}
export function displayAmounts(r) {
  const a=r?.amounts??r?.ocr?.amounts;
  return r?.ocr?.extractionVersion===EXTRACTION_VERSION && ['VERIFIED','OCR_CANDIDATE'].includes(r?.ocr?.status) && validateAmounts(a) ? a : {neto:null,iva:null,total:null};
}
export function originalVersion(item) {
  const originals=[...(Array.isArray(item?.versions)?item.versions:[]),...(Array.isArray(item?.documents)?item.documents:[])].filter(d=>d.kind==='ORIGINAL' && Number.isInteger(Number(d.version)) && Number(d.version)>0);
  const versions=[...new Set(originals.map(d=>Number(d.version)))];
  return versions.length===1?versions[0]:null;
}
