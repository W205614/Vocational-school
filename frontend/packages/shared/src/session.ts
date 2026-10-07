import {defineStore} from 'pinia';import {ref,computed} from 'vue';import {write,get,setIdentityEvents} from './client';
import {useOperations} from './operations';
export const useSession=defineStore('session',()=>{
 function clearDrafts(){for(const key of Object.keys(sessionStorage))if((key==='school-active-exam' || key==='school-active-exam-course') || key.startsWith('school-exam-draft-'))sessionStorage.removeItem(key);}
 const token=ref(sessionStorage.getItem('school-token')||'');
 const admin=ref(sessionStorage.getItem('school-admin')==='true'),expiredMessage=ref(sessionStorage.getItem('school-expired-owner')?'登录状态已过期，请重新登录后继续学习。':'');
 // Read decimal IDs without JavaScript number rounding; authentication remains server-side.
 function owner(value:string){try{const raw=atob(value.split('.')[1]!.replace(/-/g,'+').replace(/_/g,'/'));return raw.match(/"userId"\s*:\s*(?:"([0-9]+)"|([0-9]+))/)?.slice(1).find(Boolean)||'';}catch{return '';}}
 let expiredOwner=sessionStorage.getItem('school-expired-owner')||'';
 setIdentityEvents(value=>{token.value=value;},()=>{if(token.value){expiredOwner=owner(token.value);sessionStorage.setItem('school-expired-owner',expiredOwner);}token.value='';sessionStorage.removeItem('school-token');expiredMessage.value='登录状态已过期，请重新登录；同一账号的考试草稿会保留。';});
 // This claim controls presentation only. The gateway verifies identity and permissions.
 const role=computed(()=>{try {const part=token.value.split('.')[1];if(!part)return 0;const claims=JSON.parse(atob(part.replace(/-/g,'+').replace(/_/g,'/')));return Number(claims.user?.roleId||0);}catch{return 0;}});
 async function login(username:string,password:string,management:boolean){
  const next=await write<string>('/auth/accounts/'+(management?'admin/login':'login'),'POST',{type:1,username,password,rememberMe:false});
  const previous=owner(token.value)||expiredOwner;
  if(!previous || previous!==owner(next) || admin.value!==management){useOperations().clear();clearDrafts();}
  token.value=next;admin.value=management;expiredOwner='';expiredMessage.value='';sessionStorage.removeItem('school-expired-owner');
  sessionStorage.setItem('school-token',token.value);sessionStorage.setItem('school-admin',String(management));
 }
 async function logout(){try{await write('/auth/accounts/logout','POST');}finally{token.value='';admin.value=false;expiredOwner='';expiredMessage.value='';sessionStorage.removeItem('school-expired-owner');sessionStorage.removeItem('school-token');sessionStorage.removeItem('school-admin');useOperations().clear();clearDrafts();}}
 async function refresh(){token.value=await get<string>('/auth/accounts/refresh?audience='+(admin.value?'admin':'student'));sessionStorage.setItem('school-token',token.value);}
 return {token,admin,role,expiredMessage,login,logout,refresh};
});
