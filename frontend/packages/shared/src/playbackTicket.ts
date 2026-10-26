import {ref} from 'vue';

export type PlaybackTicket={playUrl:string;expiresAt?:number|string|null};
export function ticketExpiry(ticket:PlaybackTicket):number|undefined{
 const explicit=Number(ticket.expiresAt);
 if(Number.isSafeInteger(explicit)&&explicit>0)return explicit;
 try{const seconds=Number(new URL(ticket.playUrl,'http://local.invalid').searchParams.get('expires'));if(Number.isSafeInteger(seconds)&&seconds>0)return seconds*1000;}catch{}
 return undefined;
}

/** Keep media-ticket renewal separate from the persistent learning session. */
export function playbackTicket(scope:()=>string|undefined,
 fetchTicket:(signal:AbortSignal)=>Promise<PlaybackTicket>,
 install:(ticket:PlaybackTicket,signal:AbortSignal,current:()=>boolean)=>Promise<void>,
 failed?:(status:number)=>void){
 const busy=ref(false),error=ref(''),errorStatus=ref(0);
 let generation=0,context:string|undefined,expiry:number|undefined,timer:ReturnType<typeof setTimeout>|undefined;
 let controller=new AbortController(),inflight:Promise<boolean>|undefined,failures=0;
 function clearTimer(){if(timer)clearTimeout(timer);timer=undefined;}
 function reset(){generation++;clearTimer();controller.abort();controller=new AbortController();context=undefined;expiry=undefined;inflight=undefined;failures=0;busy.value=false;error.value='';errorStatus.value=0;}
 function schedule(){
  clearTimer();if(expiry===undefined||!context)return;
  const remaining=expiry-Date.now(),lead=Math.min(60000,Math.max(1000,remaining/10));
  timer=setTimeout(()=>{void renew();},Math.max(100,remaining-lead));
 }
 function activate(ticket:PlaybackTicket){reset();context=scope();expiry=ticketExpiry(ticket);schedule();}
 async function renew():Promise<boolean>{
  if(inflight)return inflight;
  if(!context||scope()!==context)return false;
  clearTimer();const own=generation,selected=context,signal=controller.signal;
  const current=()=>own===generation&&!signal.aborted&&scope()===selected;
  busy.value=true;
  const work=(async()=>{
   try{
    const next=await fetchTicket(signal);if(!current())return false;
    if(!next.playUrl)throw new Error('服务端没有返回有效播放地址');
    const nextExpiry=ticketExpiry(next);if(nextExpiry!==undefined&&nextExpiry<=Date.now())throw new Error('服务端返回的播放地址已过期');
    await install(next,signal,current);if(!current())return false;
    expiry=nextExpiry;failures=0;error.value='';errorStatus.value=0;schedule();return true;
   }catch(cause){
    if(current()){
     errorStatus.value=typeof cause==='object'&&cause!==null&&'status' in cause?Number(cause.status):0;
     error.value='视频播放地址未更新：'+(cause instanceof Error?cause.message:'请重试');
     failed?.(errorStatus.value);
     if(![401,403,404].includes(errorStatus.value)&&++failures<3)timer=setTimeout(()=>{void renew();},15000);
    }
    return false;
   }finally{if(current())busy.value=false;}
  })();
  inflight=work;try{return await work;}finally{if(inflight===work)inflight=undefined;}
 }
 function due(){return expiry!==undefined&&Date.now()>=expiry-1000;}
 return {busy,error,errorStatus,activate,renew,due,reset};
}
