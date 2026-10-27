import {NextResponse} from 'next/server';
import {getSession,requireUser,ownerEmail} from '../../../../lib/session';
import {readState} from '../../../../lib/store';
import {authorizeAccount,directoryUsers} from '../../../../lib/user-access.mjs';
export async function GET(){try{const user=requireUser(await getSession()),state=await readState();authorizeAccount(user,state,ownerEmail());return NextResponse.json({users:directoryUsers(state,ownerEmail())},{headers:{'Cache-Control':'private, no-store'}})}catch(e){return NextResponse.json({error:e.status?e.message:'No fue posible consultar el directorio.'},{status:e.status||500})}}
