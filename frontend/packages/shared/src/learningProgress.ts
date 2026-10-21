import {ref} from 'vue';

export type ProgressSnapshot={owner:string;courseId:string;lessonId:string;sectionId:string;moment:number;ended:boolean};
// Writes remain serialized even when the selected course or section changes.
// Pending snapshots are scoped to the authenticated owner and never replayed for another account.
export function learningProgress(owner:()=>string,save:(value:ProgressSnapshot,signal:AbortSignal)=>Promise<void>){
 const busy=ref(false),error=ref('');
 const pending=new Map<string,ProgressSnapshot>(),saved=new Map<string,number>();
 let controller=new AbortController(),generation=0,inflight:Promise<boolean>|undefined;
 const key=(value:ProgressSnapshot)=>value.owner+':'+value.lessonId+':'+value.sectionId;
 function reset(){generation++;controller.abort();controller=new AbortController();pending.clear();saved.clear();error.value='';}
 async function flush():Promise<boolean>{
  if(inflight){await inflight;if(pending.size&&!error.value)return flush();return !error.value;}
  const own=generation,signal=controller.signal;busy.value=true;error.value='';
  inflight=(async()=>{
   while(pending.size&&own===generation){
    const [id,value]=pending.entries().next().value!;
    if(!value.owner||owner()!==value.owner){reset();return false;}
    try{await save(value,signal);}catch(cause){if(own===generation)error.value='学习进度尚未保存：'+(cause instanceof Error?cause.message:'请稍后重试');return false;}
    if(own!==generation)return false;
    saved.set(id,Math.max(saved.get(id)||0,value.moment));
    if(pending.get(id)===value)pending.delete(id);
   }
   return own===generation;
  })();
  try{return await inflight;}finally{inflight=undefined;busy.value=false;}
 }
 async function record(value:ProgressSnapshot,force=false){
  if(!value.owner||value.owner!==owner())return false;
  const id=key(value),previous=pending.get(id),last=saved.get(id)||0;
  if(!force&&!value.ended&&value.moment-Math.max(last,previous?.moment||0)<15)return true;
  if(!value.ended&&value.moment<=last&&!previous)return true;
  pending.set(id,{...value,moment:Math.max(value.moment,previous?.moment||0),ended:value.ended||!!previous?.ended});
  if(error.value)return false;
  return flush();
 }
 async function retry(){error.value='';return flush();}
 return {busy,error,record,retry,flush,reset};
}
