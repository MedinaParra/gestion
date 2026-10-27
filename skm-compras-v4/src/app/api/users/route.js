import {NextResponse} from 'next/server';
import {getSession,requireUser,ownerEmail} from '../../../lib/session';
import {readState,writeState,addAudit} from '../../../lib/store';
import {authorizeAccount,publicAccount,applyUserChange} from '../../../lib/user-access.mjs';
import {requireSameOrigin,fail} from '../../../lib/request-lifecycle.mjs';
import {readProfileJson} from '../../../lib/profile.mjs';
const headers={'Cache-Control':'private, no-store'};
const error=e=>NextResponse.json({error:e.status?e.message:'No fue posible gestionar usuarios.'},{status:e.status||500,headers});
export async function GET(){try{const session=requireUser(await getSession()),state=await readState(),user=authorizeAccount(session,state,ownerEmail());if(user.role!=='ADMIN')fail('No autorizado',403);return NextResponse.json({users:Object.keys(state.users||{}).map(e=>publicAccount(state,e,user,ownerEmail())),actorEmail:user.email},{headers})}catch(e){return error(e)}}
export async function PATCH(req){try{const user=requireUser(await getSession());requireSameOrigin(req);const body=await readProfileJson(req,4096),state=await readState(),change=applyUserChange(state,user,body,ownerEmail());if(change.changed){addAudit(state,user,'USER_ACCESS_CHANGED',null,{targetEmail:change.email,before:change.before,after:change.after});await writeState(state)}return NextResponse.json({user:publicAccount(state,change.email,user,ownerEmail())},{headers})}catch(e){return error(e)}}
