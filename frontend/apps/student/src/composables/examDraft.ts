import {ref,watch,onBeforeUnmount,type Ref} from 'vue';
import {get,write,ApiError} from '../../../../packages/shared/src/client';
import type {Row} from '../../../../packages/shared/src/ui';

type Draft={version:string;answers:Record<string,string>;status:string};
export function useExamDraft(attempt:Ref<Row|undefined>,responses:Ref<Record<string,string>>) {
 const state=ref(''),conflict=ref(false),version=ref('0'),ready=ref(false),backup=ref<Record<string,string>>({});
 let generation=0,current='',ack='{}',restoring=false,timer:ReturnType<typeof setTimeout>|undefined,inflight:Promise<boolean>|undefined;
 const key=(id:string)=>'school-exam-draft-'+id;
 const json=()=>JSON.stringify(responses.value,Object.keys(responses.value).sort());
 function persist(){if(current){sessionStorage.setItem(key(current),JSON.stringify(responses.value));sessionStorage.setItem(key(current)+'-meta',JSON.stringify({version:version.value,dirty:json()!==ack}));}}
 function schedule(){if(timer)clearTimeout(timer);timer=setTimeout(()=>{void sync();},650);}
 watch(responses,()=>{if(restoring || !current || attempt.value?.status!=='IN_PROGRESS')return;persist();if(!conflict.value){state.value='草稿待同步';schedule();}},{deep:true,flush:'sync'});
 async function restore(value:Row){
  generation++;if(timer)clearTimeout(timer);current='';conflict.value=false;inflight=undefined;ready.value=false;backup.value={};
  if(value.status!=='IN_PROGRESS'){state.value='';ready.value=true;return;}
  restoring=true;responses.value={};restoring=false;state.value='正在载入草稿';
  const id=String(value.id),own=generation,remote=await get<Draft>('/exam-attempts/'+id+'/draft');
  if(own!==generation)return;
  try{backup.value=JSON.parse(sessionStorage.getItem('school-exam-draft-backup-'+id)||'{}');}catch{}
  if(remote.status!=='IN_PROGRESS')throw new Error('答卷状态已变化，请刷新评分状态');
  let local:Record<string,string>|undefined,meta:{version:string;dirty:boolean}|undefined;
  try{const raw=sessionStorage.getItem(key(id));if(raw)local=JSON.parse(raw);meta=JSON.parse(sessionStorage.getItem(key(id)+'-meta')||'null');}catch{}
  current=id;version.value=remote.version;ack=JSON.stringify(remote.answers,Object.keys(remote.answers).sort());restoring=true;
  const locallyChanged=!!local && (meta?.dirty || !meta),same=local && JSON.stringify(local,Object.keys(local).sort())===ack;
  responses.value=locallyChanged && !same?local!:remote.answers;restoring=false;
  if(locallyChanged && !same && remote.version!==(meta?.version??'0')){conflict.value=true;version.value=meta?.version??'0';state.value='其他页面已更新草稿，本机未同步答案已保留';}
  else {state.value=json()===ack?'草稿已同步，可在其他设备继续':'本机草稿待同步';if(json()!==ack)schedule();}
  persist();ready.value=true;
 }
 async function sync():Promise<boolean>{
  if(timer)clearTimeout(timer);
  if(inflight)return inflight;
  if(conflict.value || !ready.value)return false;
  if(!current || attempt.value?.status!=='IN_PROGRESS')return true;
  const id=current,own=generation;
  inflight=(async()=>{
   while(own===generation && current===id && attempt.value?.status==='IN_PROGRESS' && json()!==ack){
    const snapshot=json(),base=version.value;state.value='正在同步草稿';
    try{const saved=await write<Draft>('/exam-attempts/'+id+'/draft','PUT',{version:base,answers:JSON.parse(snapshot)});if(own!==generation)return false;version.value=saved.version;ack=JSON.stringify(saved.answers,Object.keys(saved.answers).sort());persist();}
    catch(error){if(own!==generation)return false;conflict.value=error instanceof ApiError && error.status===409;state.value=conflict.value?'其他页面已更新或提交答卷，本机未同步答案已保留':'草稿仅保存在本机，未同步：'+(error instanceof Error?error.message:'请稍后重试');return false;}
   }
   if(own===generation)state.value='草稿已同步，可在其他设备继续';
   return own===generation;
  })();
  const active=inflight;try{return await active;}finally{if(inflight===active)inflight=undefined;}
 }
 async function loadLatest(){if(!current)return;const id=current;backup.value={...responses.value};sessionStorage.setItem('school-exam-draft-backup-'+id,JSON.stringify(backup.value));const value=await get<Draft>('/exam-attempts/'+id+'/draft');if(value.status!=='IN_PROGRESS')throw new Error('答卷已提交，请刷新评分状态');generation++;if(timer)clearTimeout(timer);inflight=undefined;version.value=value.version;ack=JSON.stringify(value.answers,Object.keys(value.answers).sort());restoring=true;responses.value=value.answers;restoring=false;conflict.value=false;state.value='已载入最新草稿，本机答案已备份';persist();}
 onBeforeUnmount(()=>{generation++;if(timer)clearTimeout(timer);});
 return {state,conflict,version,ready,backup,restore,sync,loadLatest};
}
