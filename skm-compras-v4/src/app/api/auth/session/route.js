import {NextResponse} from 'next/server';
import {getSession,roleFor} from '../../../../lib/session';
export async function GET(){const user=await getSession();return NextResponse.json({user:user?{...user,role:roleFor(user.email)}:null,googleClientId:process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID||process.env.GOOGLE_CLIENT_ID||''})}
