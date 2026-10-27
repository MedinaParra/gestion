// Lifecycle metadata is separate from workflow status: trash never discards a version.
export function fail(message,status){const e=new Error(message);e.status=status;throw e}
export function requestId(value){if(typeof value!=='string'||! /^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/.test(value))fail('Identificador de solicitud no válido',400);return value}
export function requireAdmin(user,roleFor){if(!user)fail('No autorizado',401);if(roleFor(user.email)!=='ADMIN')fail('Sólo el administrador puede gestionar la papelera',403);return user}
export function requireSameOrigin(req){const origin=req.headers.get('origin');if(!origin||origin!==new URL(req.url).origin||req.headers.get('sec-fetch-site')==='cross-site')fail('Origen de solicitud no permitido',403)}
export function requireActive(item){if(item?.deletedAt)fail('La solicitud está en la papelera. Debe restaurarse antes de continuar.',410);return item}
export function moveToTrash(state,user,id,action,addAudit){
 const item=state.requests.find(r=>r.id===requestId(id));if(!item)fail('Solicitud no encontrada',404);
 if(action==='trash'){
  if(item.deletedAt)fail('La solicitud ya está en la papelera',409);
  item.deletedAt=new Date().toISOString();item.deletedBy=user.email;
  addAudit(state,user,'REQUEST_TRASHED',id,{deletedAt:item.deletedAt});
 }else if(action==='restore'){
  if(!item.deletedAt)fail('La solicitud ya está activa',409);
  const deletedAt=item.deletedAt,deletedBy=item.deletedBy;delete item.deletedAt;delete item.deletedBy;
  addAudit(state,user,'REQUEST_RESTORED',id,{deletedAt,deletedBy});
 }else fail('Acción no válida',400);
 return item;
}
