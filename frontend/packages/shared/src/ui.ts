import {ref} from 'vue';import {ApiError} from './client';
export function useTask(){
 const busy=ref(false),error=ref(''),message=ref('');
 async function run<T>(work:()=>Promise<T>):Promise<T|undefined>{
  if(busy.value)return;busy.value=true;error.value='';message.value='';
  try{return await work();}catch(cause){error.value=cause instanceof Error?cause.message:'请求失败';if(cause instanceof ApiError && cause.requestId)error.value+='（请求 '+cause.requestId+'）';}
  finally{busy.value=false;}
 }
 return {busy,error,message,run};
}
export function money(value:unknown){return '¥'+(Number(value||0)/100).toFixed(2);}
export function query(values:Record<string,unknown>){return new URLSearchParams(Object.entries(values).filter(([,v])=>v!==undefined && v!==null && v!=='').map(([k,v])=>[k,String(v)])).toString();}
export type Row=Record<string,any>;

export function examStatus(value:unknown){return ({IN_PROGRESS:'答题中',WAIT_GRADING:'等待教师评分',FINISHED:'评分完成'} as Record<string,string>)[String(value)]||'状态待确认';}
