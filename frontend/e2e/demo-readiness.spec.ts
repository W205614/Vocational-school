import {test,expect,type Page} from '@playwright/test';
import {login,api,accounts} from './helpers';

async function initialPassword(page:Page,username:string,temporary:string,next:string,admin:boolean){
 await page.locator('input[autocomplete="username"]').fill(username);
 await page.locator('input[autocomplete="current-password"]').fill(temporary);
 const response=page.waitForResponse(r=>r.url().endsWith(admin?'/accounts/admin/login':'/accounts/login'));
 await page.getByRole('button',{name:admin?'登录管理端':'登录',exact:true}).click();
 expect((await response).status()).toBe(428);
 expect(await page.evaluate(()=>sessionStorage.getItem('school-token'))).toBeFalsy();
 await expect(page.getByRole('heading',{name:'设置自己的登录密码',exact:true})).toBeVisible();
 await page.getByLabel('新密码',{exact:true}).fill(next);
 await page.getByLabel('确认新密码',{exact:true}).fill(next);
 await page.getByRole('button',{name:'保存新密码',exact:true}).click();
 await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible();
 await page.locator('input[autocomplete="current-password"]').fill(next);
 await page.getByRole('button',{name:admin?'登录管理端':'登录',exact:true}).click();
 await expect(page.locator('.sidebar')).toBeVisible();
}

test('demo admin creates and resets a teacher with one-time credentials and mandatory password setup',async({page,browser},info)=>{
 test.skip(info.project.name!=='admin');test.setTimeout(120000);
 await login(page,'admin','/staff');
 await page.context().grantPermissions(['clipboard-write'],{origin:new URL(page.url()).origin});
 const suffix=Date.now().toString().slice(-8),phone='139'+suffix,name='Demo teacher '+suffix;
 await page.getByRole('button',{name:'新增',exact:true}).click();
 const form=page.getByRole('dialog',{name:'新增记录',exact:true});
 await form.getByRole('textbox',{name:'姓名',exact:true}).fill(name);
 await form.getByRole('textbox',{name:'账号',exact:true}).fill(phone);
 await form.getByRole('textbox',{name:'手机',exact:true}).fill(phone);
 await form.getByRole('button',{name:'保存',exact:true}).click();
 const receipt=page.getByRole('dialog',{name:'临时登录凭据（仅显示本次）',exact:true});
 await expect(receipt).toBeVisible();const temporary=(await receipt.locator('code').innerText()).trim();
 expect(temporary.length).toBeGreaterThanOrEqual(16);await expect(receipt).toContainText(phone);
 await receipt.getByRole('button',{name:'复制临时凭据',exact:true}).click();
 await expect(receipt.getByRole('alert').filter({hasText:'临时凭据已复制'})).toBeVisible();
 await receipt.getByRole('button',{name:'已保存并关闭',exact:true}).click();
 const context=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL||'http://127.0.0.1:23501'});
 try{
  const teacher=await context.newPage();await teacher.goto('/exams');
  await initialPassword(teacher,phone,temporary,'DemoTeacher!'+suffix,true);
  await teacher.goto('/teaching');await expect(teacher.getByRole('heading',{name:'教学学情与答疑',exact:true})).toBeVisible();
  await page.getByRole('textbox',{name:'按教师或员工姓名搜索',exact:true}).fill(name);
  await page.getByRole('button',{name:'查询',exact:true}).click();
  const row=page.locator('.el-table__row').filter({hasText:name});await expect(row).toHaveCount(1);
  await row.getByRole('button',{name:'重置密码',exact:true}).click();
  await page.locator('.el-popconfirm__action .el-button--primary:visible').click();
  await expect(receipt).toBeVisible();const replacement=(await receipt.locator('code').innerText()).trim();expect(replacement).not.toBe(temporary);
  await receipt.getByRole('button',{name:'已保存并关闭',exact:true}).click();
  await expect.poll(async()=>{
   const token=await teacher.evaluate(()=>sessionStorage.getItem('school-token'));
   return (await teacher.request.get('/api/v2/services/user/users/me',{headers:{Authorization:'Bearer '+token}})).status();
  },{timeout:12000,intervals:[1000]}).toBe(401);
  await teacher.evaluate(()=>sessionStorage.clear());await teacher.goto('/exams');
  await initialPassword(teacher,phone,replacement,'DemoTeacherReset!'+suffix,true);
  await page.reload();await expect(page.getByRole('dialog',{name:'临时登录凭据（仅显示本次）'})).toHaveCount(0);
 }finally{await context.close();}
});

test('demo student reset uses initial password flow and restores the isolated fixture credential',async({page,browser},info)=>{
 test.skip(info.project.name!=='student');test.setTimeout(90000);
 const adminContext=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL||'http://127.0.0.1:23501'});
 const admin=await adminContext.newPage();let fixtureReset=false,fixtureRestored=false;
 try{
  await login(admin,'admin','/staff');
  const receipt=await api(admin,'PUT','/admin/user/users/'+accounts.student.id+'/password/default');fixtureReset=true;
  await page.goto('/login');const next='DemoStudent!'+Date.now();
  await initialPassword(page,receipt.username,receipt.temporaryPassword,next,false);
  await expect(page.getByRole('heading',{name:'我的学习',exact:true})).toBeVisible();
  await api(page,'PUT','/services/user/users',{type:2,oldPassword:next,password:accounts.student.password});fixtureRestored=true;
 }finally{
  try{if(fixtureReset&&!fixtureRestored){
   const recovery=await api(admin,'PUT','/admin/user/users/'+accounts.student.id+'/password/default');
   await api(admin,'POST','/auth/accounts/password/initial',{username:recovery.username,oldPassword:recovery.temporaryPassword,newPassword:accounts.student.password,audience:'student'});
  }}finally{await adminContext.close();}
 }
});
