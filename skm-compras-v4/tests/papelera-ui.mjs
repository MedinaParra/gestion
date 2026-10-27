// Synthetic browser QA only: all /api endpoints intercepted; no auth/storage traffic.
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)('playwright');
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--no-sandbox']});
try {
 const page=await browser.newPage();let role='ADMIN',rows=[{id:'PRUEBA-001',ot:'TEST',supplier:'Proveedor sintético',status:'IN_SIGNATURES',signers:[],versions:[],signatures:[]}],trash=[],patches=0,signs=0;
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url());let body={};
  if(url.pathname==='/api/auth/session')body={user:{email:'synthetic@example.invalid',name:'Prueba',role}};
  else if(url.pathname==='/api/audit')body={audit:[]};
  else if(url.pathname==='/api/requests'&&req.method()==='GET')body={requests:url.searchParams.has('view')?trash:rows};
  else if(url.pathname==='/api/requests'&&req.method()==='PATCH'){
   const data=req.postDataJSON();assert.equal(data.confirmId,data.id);patches++;
   if(data.action==='trash'){trash=rows.map(r=>({...r,deletedAt:new Date().toISOString(),deletedBy:'synthetic@example.invalid'}));rows=[]}else{rows=trash.map(({deletedAt,deletedBy,...r})=>r);trash=[]}body={request:(rows[0]||trash[0])};
  }else if(url.pathname==='/api/sign'){signs++;body={}}
  else {await route.fulfill({status:404,json:{error:'Documento sintético sin PDF'}});return}
  await route.fulfill({json:body});
 });
 await page.goto('http://127.0.0.1:3188');await page.getByRole('button',{name:'Solicitudes',exact:true}).click();await page.getByText('PRUEBA-001',{exact:true}).click();
 let dialogMode='cancel';page.on('dialog',async dialog=>{assert.match(dialog.message(),/PRUEBA-001/);if(dialogMode==='confirm')await dialog.accept('PRUEBA-001');else if(dialogMode==='wrong')await dialog.accept('OTHER');else await dialog.dismiss()});
 await page.getByRole('button',{name:'Mover a la papelera',exact:true}).click();assert.equal(patches,0);
 dialogMode='wrong';await page.getByRole('button',{name:'Mover a la papelera',exact:true}).click();assert.equal(patches,0);
 dialogMode='confirm';await page.getByRole('button',{name:'Mover a la papelera',exact:true}).click();await page.getByRole('status').filter({hasText:'movida a la papelera'}).waitFor();assert.equal(patches,1);
 await page.getByRole('button',{name:'Papelera',exact:true}).click();await page.getByRole('button',{name:'Restaurar',exact:true}).click();await page.getByRole('status').filter({hasText:'restaurada'}).waitFor();assert.equal(patches,2);assert.equal(rows[0].status,'IN_SIGNATURES');
 await page.getByRole('button',{name:'Firma masiva',exact:true}).click();await page.getByRole('checkbox').check();rows=[];await page.getByRole('button',{name:'FIRMAR 1',exact:true}).click();await page.getByRole('status').filter({hasText:'selección cambió'}).waitFor();assert.equal(signs,0);
 role='USER';rows=[{id:'PRUEBA-001',ot:'TEST',supplier:'Prueba',status:'IN_SIGNATURES',versions:[]}];await page.reload();await page.getByRole('button',{name:'Solicitudes',exact:true}).click();assert.equal(await page.getByRole('button',{name:'Papelera',exact:true}).count(),0);await page.getByText('PRUEBA-001',{exact:true}).click();assert.equal(await page.getByRole('button',{name:'Mover a la papelera',exact:true}).count(),0);
 console.log('PASS browser: admin trash/restore, cancel/wrong ID, data retained, stale bulk blocked, non-admin controls hidden. API mocked only.');
}finally{await browser.close()}
