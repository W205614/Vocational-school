import {ref} from 'vue';

/** Bounded reconciliation of an accepted order and its asynchronous learning entitlement. */
export function entitlementPreparation<T>(scope:()=>string,read:(signal:AbortSignal)=>Promise<T>,ready:(value:T)=>boolean,timeout=30000){
 const state=ref<'idle'|'pending'|'ready'|'timeout'|'failed'>('idle'),error=ref('');
 let generation=0,controller=new AbortController(),inflight:Promise<T|undefined>|undefined;
 function reset(){generation++;controller.abort();controller=new AbortController();inflight=undefined;state.value='idle';error.value='';}
 function delay(signal:AbortSignal){return new Promise<void>((resolve,reject)=>{
  const aborted=()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'));};
  const timer=setTimeout(()=>{signal.removeEventListener('abort',aborted);resolve();},1000);
  if(signal.aborted){aborted();return;}signal.addEventListener('abort',aborted,{once:true});
 });}
 function start():Promise<T|undefined>{
  if(inflight)return inflight;reset();const own=generation,selected=scope(),signal=controller.signal,deadline=Date.now()+timeout;
  const current=()=>own===generation&&selected===scope();state.value='pending';
  const active=controller,expiry=setTimeout(()=>active.abort(),timeout);
  const work=(async()=>{
   let failures=0;
   try{while(current()&&!signal.aborted&&Date.now()<deadline){
    try{const result=await read(signal);if(!current()||signal.aborted)return;if(ready(result)){state.value='ready';return result;}failures=0;}
    catch(cause){if(!current())return;if(signal.aborted)break;const status=Number((cause as {status?:number})?.status);if([401,403,404].includes(status)||++failures>=3){state.value='failed';error.value=cause instanceof Error?cause.message:'暂时无法查询学习权限';return;}}
    await delay(signal);
   }}catch(cause){if(!signal.aborted&&current()){state.value='failed';error.value=cause instanceof Error?cause.message:'查询失败';return;}}
   finally{clearTimeout(expiry);if(own===generation)inflight=undefined;}
   if(current())state.value='timeout';
   return undefined;
  })();
  inflight=work;return work;
 }
 return {state,error,start,reset};
}
