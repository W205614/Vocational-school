import {ref} from 'vue';

export type PlaybackView={sessionId:string;watchedMillis:number;creditedMillis:number;requiredMillis:number;durationMillis:number;finished:boolean;completionSource:string|null;lastSequence:number;active:boolean};
export type PlaybackState='PLAYING'|'PAUSED'|'BUFFERING'|'HIDDEN'|'SEEKING'|'ENDED';
export type PlaybackSample={positionMillis:number;rate:number;state:PlaybackState};
export type PlaybackHeartbeat=PlaybackSample&{sessionId:string;sequence:number};
type Context={owner:string;lessonId:string;sectionId:string};
// The common backend serializer renders long counters as decimal strings on the wire.
// TypeScript declarations do not convert them at runtime (notably sequence + 1).
function normalize(value:PlaybackView):PlaybackView{
 const result={...value};
 for(const key of ['watchedMillis','creditedMillis','requiredMillis','durationMillis','lastSequence'] as const){
  const number=Number(value[key]);if(!Number.isSafeInteger(number)||number<0)throw new Error('服务端计时回执无效，请重新开始计时');result[key]=number;
 }
 return result;
}

/** A lost response is retried with the original sequence and body, never credited twice. */
export function playbackTiming(owner:()=>string,
 create:(context:Context,signal:AbortSignal)=>Promise<PlaybackView>,
 send:(body:PlaybackHeartbeat,end:boolean,signal:AbortSignal)=>Promise<PlaybackView>){
 const current=ref<PlaybackView>(),error=ref(''),errorStatus=ref(0),busy=ref(false),replaced=ref(false);
 let generation=0,controller=new AbortController(),context:Context|undefined;
 let pending:{body:PlaybackHeartbeat;end:boolean}|undefined,inflight:Promise<boolean>|undefined,starting:Promise<boolean>|undefined,tail=Promise.resolve(true);
 function reset(){generation++;controller.abort();controller=new AbortController();context=undefined;current.value=undefined;pending=undefined;inflight=undefined;starting=undefined;tail=Promise.resolve(true);error.value='';errorStatus.value=0;replaced.value=false;busy.value=false;}
 async function start(value:Context){
  if(starting&&context?.owner===value.owner&&context.lessonId===value.lessonId&&context.sectionId===value.sectionId)return starting;
  reset();context=value;const own=generation,signal=controller.signal;busy.value=true;
  const work=(async()=>{
   try{const result=normalize(await create(value,signal));if(own!==generation||owner()!==value.owner)return false;current.value=result;return true;}
   catch(cause){if(own===generation)error.value='学习计时未开始：'+(cause instanceof Error?cause.message:'请重试');return false;}
   finally{if(own===generation)busy.value=false;}
  })();
  starting=work;try{return await work;}finally{if(starting===work)starting=undefined;}
 }
 async function transmit():Promise<boolean>{
  if(!pending||!context||!current.value)return true;
  if(owner()!==context.owner){reset();return false;}
  const own=generation,task=pending,signal=controller.signal;busy.value=true;
  const work=(async()=>{
   try{
    const result=normalize(await send(task.body,task.end,signal));if(own!==generation)return false;
    current.value=result;pending=undefined;error.value='';errorStatus.value=0;return true;
   }catch(cause){
    if(own===generation){errorStatus.value=typeof cause==='object'&&cause!==null&&'status' in cause?Number(cause.status):0;replaced.value=errorStatus.value===409;error.value='学习计时未保存：'+(cause instanceof Error?cause.message:'请重试');}
    return false;
   }finally{if(own===generation)busy.value=false;}
  })();
  inflight=work;try{return await work;}finally{if(inflight===work)inflight=undefined;}
 }
 async function heartbeat(sample:PlaybackSample,end=false):Promise<boolean>{
  const own=generation;
  const work=tail.then(async()=>{
   if(own!==generation||!current.value?.active||!context||error.value)return false;
   pending={body:{...sample,sessionId:current.value.sessionId,sequence:current.value.lastSequence+1},end};
   return transmit();
  });
  tail=work.catch(()=>false);return work;
 }
 async function retry(){
  if(inflight)return inflight;
  if(replaced.value)return false;
  error.value='';
  if(pending)return transmit();
  if(context&&!current.value)return start(context);
  return true;
 }
 return {current,error,errorStatus,busy,replaced,start,heartbeat,retry,reset};
}
