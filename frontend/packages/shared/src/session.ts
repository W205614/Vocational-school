import {defineStore} from 'pinia';import {ref,computed} from 'vue';import {write,get} from './client';
import {useOperations} from './operations';
export const useSession=defineStore('session',()=>{
 function clearDrafts(){for(const key of Object.keys(sessionStorage))if(key==='school-active-exam' || key.startsWith('school-exam-draft-'))sessionStorage.removeItem(key);}
 const token=ref(sessionStorage.getItem('school-token')||'');
 const admin=ref(sessionStorage.getItem('school-admin')==='true');
 // This claim controls presentation only. The gateway verifies identity and permissions.
 const role=computed(()=>{try {const part=token.value.split('.')[1];if(!part)return 0;const claims=JSON.parse(atob(part.replace(/-/g,'+').replace(/_/g,'/')));return Number(claims.user?.roleId||0);}catch{return 0;}});
 async function login(username:string,password:string,management:boolean){
  useOperations().clear();
  clearDrafts();
  token.value=await write<string>('/auth/accounts/'+(management?'admin/login':'login'),'POST',{type:1,username,password,rememberMe:false});
  admin.value=management;sessionStorage.setItem('school-token',token.value);sessionStorage.setItem('school-admin',String(management));
 }
 async function logout(){try{await write('/auth/accounts/logout','POST');}finally{token.value='';admin.value=false;sessionStorage.removeItem('school-token');sessionStorage.removeItem('school-admin');useOperations().clear();clearDrafts();}}
 async function refresh(){token.value=await get<string>('/auth/accounts/refresh?audience='+(admin.value?'admin':'student'));sessionStorage.setItem('school-token',token.value);}
 return {token,admin,role,login,logout,refresh};
});
