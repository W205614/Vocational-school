import {defineStore} from 'pinia';import {ref} from 'vue';import {get,write,waitOperation,newKey} from './client';import type {Operation} from './types';
export interface Tracked{service:string;label:string;path:string;method:string;payload:unknown;key:string;operation?:Operation;error?:string}
export const useOperations=defineStore('operations',()=>{
 function restore():Tracked[]{try{return JSON.parse(sessionStorage.getItem('school-operations')||'[]');}catch{return [];}}
 const items=ref<Tracked[]>(restore());const running=new Map<string,Promise<Operation>>();let abort=new AbortController();
 function save(){sessionStorage.setItem('school-operations',JSON.stringify(items.value));}
 async function perform(item:Tracked){
  item.error=undefined;save();
  try{
   if(!item.operation) item.operation=await write<Operation>(item.path,item.method,item.payload,item.key);
   if(item.operation.status!=='PENDING')item.operation=await get<Operation>('/operations/'+item.service+'/'+item.operation.operationId);
   save();item.operation=await waitOperation(item.service,item.operation,abort.signal);save();
   if(item.operation.status==='FAILED') throw new Error(item.operation.errorMessage||'操作失败');
   return item.operation;
  }catch(error){item.error=error instanceof Error?error.message:'网络请求失败';save();throw error;}
 }
 function run(item:Tracked){let current=running.get(item.key);if(current)return current;current=perform(item).finally(()=>running.delete(item.key));running.set(item.key,current);return current;}
 async function begin(service:string,label:string,path:string,payload:unknown,method='POST'){
  items.value.unshift({service,label,path,payload,method,key:newKey()});save();return run(items.value[0]!);
 }
 async function track(service:string,label:string,operation:Operation,key:string){
  const item:Tracked={service,label,path:'',method:'POST',payload:null,key,operation};items.value.unshift(item);save();return run(item);
 }
 async function resume(){await Promise.allSettled(items.value.filter(x=>!x.operation || x.operation.status==='PENDING').map(run));}
 function remove(key:string){if(running.has(key))return;items.value=items.value.filter(x=>x.key!==key);save();}
 function clear(){abort.abort();abort=new AbortController();items.value=[];sessionStorage.removeItem('school-operations');}
 return {items,begin,track,run,resume,remove,clear};
});
