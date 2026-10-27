import {authorizeAccount} from '../../../lib/user-access.mjs';
import {ownerEmail} from '../../../lib/session';
import {NextResponse} from 'next/server';import {getSession,requireUser} from '../../../lib/session';import {readState} from '../../../lib/store';export async function GET(){try{const user=requireUser(await getSession());const s=await readState();authorizeAccount(user,s,ownerEmail());return NextResponse.json({audit:s.audit||[]})}catch(e){return NextResponse.json({error:e.message},{status:e.status||500})}}
