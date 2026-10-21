import {test,expect,type Page} from '@playwright/test';
import {accounts,login,api} from './helpers';

test('administrator custom role permissions persist through refresh and revoke while student and teacher are forbidden',async({page,request},info)=>{
 test.skip(info.project.name!=='admin');test.setTimeout(60000);
 await login(page,'admin','/role-permissions');
 const marker=Date.now().toString(36)+'-'+crypto.randomUUID().slice(0,8),name='Isolated permission role '+marker;
 const role=await api(page,'POST','/admin/auth/roles',{name,code:'QA_PERMISSION_'+marker,description:'Synthetic role for real permission persistence acceptance'},crypto.randomUUID());
 const endpoint='/api/v2/admin/auth/privileges/role/'+role.id,save=page.getByRole('button',{name:'保存角色权限',exact:true});
 async function chooseRole(){
  const loaded=page.waitForResponse(response=>new URL(response.url()).pathname===endpoint&&response.request().method()==='GET');
  await page.getByRole('combobox').click();await page.getByRole('option',{name,exact:true}).click();
  expect((await loaded).status()).toBe(200);await expect(save).toBeEnabled();
 }
 // Refresh the roles loaded before creating this independent custom role.
 await page.reload();await chooseRole();
 const checkbox=page.locator('.el-checkbox').filter({has:page.locator('input:not([disabled])')}).first();
 await expect(checkbox).toBeVisible();await checkbox.click();
 const bound=page.waitForResponse(response=>new URL(response.url()).pathname===endpoint&&response.request().method()==='POST');
 await save.click();const binding=await bound;
 expect(binding.status(),'Real permission bind must not fail due to missing auth_cache_guard seed').toBe(200);
 const privilegeIds=binding.request().postDataJSON() as unknown[];expect(privilegeIds).toHaveLength(1);const privilegeId=String(privilegeIds[0]);
 await expect(page.getByText('权限已保存，缓存发布任务正在同步',{exact:true})).toBeVisible();
 expect((await api(page,'GET',endpoint.slice('/api/v2'.length))).map(String)).toEqual([privilegeId]);
 await page.reload();await chooseRole();await expect(page.getByRole('checkbox',{checked:true})).toHaveCount(1);
 for(const roleName of ['student','teacher']){
  const account=accounts[roleName],authenticated=await request.post('/api/v2/auth/accounts/'+(roleName==='student'?'login':'admin/login'),{data:{type:1,username:account.username,password:account.password,rememberMe:false}});
  expect(authenticated.ok()).toBeTruthy();const token=(await authenticated.json()).data,headers={Authorization:'Bearer '+token};
  for(const method of ['GET','POST','DELETE']){
   const denied=await request.fetch(endpoint,{method,headers,...(method==='GET'?{}:{data:[privilegeId]})});
   expect(denied.status(),roleName+' must not '+method+' custom role permissions').toBe(403);
  }
 }
 expect((await api(page,'GET',endpoint.slice('/api/v2'.length))).map(String)).toEqual([privilegeId]);
 await page.locator('.el-checkbox.is-checked').click();
 const revoked=page.waitForResponse(response=>new URL(response.url()).pathname===endpoint&&response.request().method()==='DELETE');
 await save.click();expect((await revoked).status()).toBe(200);await expect(save).toBeEnabled();
 expect(await api(page,'GET',endpoint.slice('/api/v2'.length))).toEqual([]);
 await page.reload();await chooseRole();await expect(page.getByRole('checkbox',{checked:true})).toHaveCount(0);
});
