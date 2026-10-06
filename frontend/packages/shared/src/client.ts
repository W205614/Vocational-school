import type {Envelope,Operation} from './types';
export class ApiError extends Error {
 constructor(message:string,readonly status:number,readonly requestId?:string){super(message);}
}
let tokenProvider=()=>sessionStorage.getItem('school-token')||'';
let refreshing:Promise<void>|undefined;
function refreshIdentity():Promise<void>{
 if(refreshing)return refreshing;
 refreshing=(async()=>{
  const audience=sessionStorage.getItem('school-admin')==='true'?'admin':'student';
  const response=await fetch('/api/v2/auth/accounts/refresh?audience='+audience,{credentials:'include',signal:AbortSignal.timeout(10000)});
  const body=await response.json() as Envelope<string>;
  if(!response.ok || body.code!==200 || typeof body.data!=='string')throw new ApiError(body.msg||'登录已过期，请重新登录',401,body.requestId);
  sessionStorage.setItem('school-token',body.data);
 })().finally(()=>{refreshing=undefined;});return refreshing;
}
export function setTokenProvider(provider:()=>string){tokenProvider=provider;}
export async function request<T>(path:string,options:RequestInit={},key?:string,retried=false):Promise<T>{
 const abort=new AbortController();const timer=setTimeout(()=>abort.abort(),15000);
 try{
  const headers=new Headers(options.headers);headers.set('Accept','application/json');
  if(options.body && !(options.body instanceof FormData)) headers.set('Content-Type','application/json');
  const token=tokenProvider();if(token) headers.set('Authorization','Bearer '+token);
  if(key) headers.set('Idempotency-Key',key);
  const signal=options.signal?AbortSignal.any([options.signal,abort.signal]):abort.signal;
  const response=await fetch('/api/v2'+path,{...options,headers,credentials:'include',signal});
  const body=await response.json() as Envelope<T>;
  if(response.status===401 && token && !retried && !path.startsWith('/auth/accounts/')){
   await refreshIdentity();return request<T>(path,options,key,true);
  }
  if(!response.ok || body.code!==200) throw new ApiError(body.msg||'请求失败',response.status,body.requestId);
  return body.data;
 }catch(error){
  if(error instanceof DOMException && error.name==='AbortError') throw new ApiError('请求超时，请使用原请求重试',408);
  throw error;
 }finally{clearTimeout(timer);}
}
export function get<T>(path:string){return request<T>(path);}
export function write<T>(path:string,method:string,body?:unknown,key?:string){return request<T>(path,{method,body:body===undefined?undefined:JSON.stringify(body)},key);}
export function newKey(){return crypto.randomUUID();}
export async function waitOperation<T>(service:string,operation:Operation<T>,signal?:AbortSignal):Promise<Operation<T>>{
 let delay=500;const deadline=Date.now()+120000;let current=operation;
 while(current.status==='PENDING' && Date.now()<deadline){
  await new Promise<void>((resolve,reject)=>{
   const aborted=()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'));};
   const timer=setTimeout(()=>{signal?.removeEventListener('abort',aborted);resolve();},delay);
   if(signal?.aborted){aborted();return;}signal?.addEventListener('abort',aborted,{once:true});
  });
  if(signal?.aborted) throw new DOMException('Aborted','AbortError');
  current=await get<Operation<T>>('/operations/'+service+'/'+current.operationId);delay=Math.min(5000,Math.round(delay*1.5));
 }
 return current;
}
