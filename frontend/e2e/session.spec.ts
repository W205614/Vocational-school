import {test,expect,type Page} from '@playwright/test';import fs from 'node:fs';
const home=process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local',accounts=JSON.parse(fs.readFileSync(home+'/accounts.json','utf8'));
async function login(page:Page,role:string){await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
test('expired credentials return to sign-in and preserve same-account drafts',async({page,context},info)=>{
 const role=info.project.name==='student'?'student':'admin';await page.goto('/');await login(page,role);
 await page.evaluate(()=>sessionStorage.setItem('school-exam-draft-qa-recovery',JSON.stringify({answer:'keep my draft'})));
 await context.clearCookies();await page.route('**/api/v2/services/user/users/me',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:401,msg:'登录已过期',data:null,requestId:'qa-session-expiry'})}));
 if(role==='student')await page.goto('/settings');else {await page.route('**/api/v2/admin/dashboard',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:401,msg:'登录已过期',data:null,requestId:'qa-session-expiry'})}));await page.reload();}
 await expect(page.locator('input[autocomplete="username"]')).toBeVisible();await expect(page.getByRole('alert').filter({hasText:'登录状态已过期'})).toBeVisible();await page.unroute('**/api/v2/services/user/users/me');await page.unroute('**/api/v2/admin/dashboard');
 await page.reload();await login(page,role);expect(await page.evaluate(()=>JSON.parse(sessionStorage.getItem('school-exam-draft-qa-recovery')||'{}').answer)).toBe('keep my draft');
});
test('a rejected access token refreshes once and restores the protected page',async({page},info)=>{
 const role=info.project.name==='student'?'student':'admin';await page.goto('/');await login(page,role);const path=role==='student'?'**/api/v2/notes?*':'**/api/v2/admin/dashboard';let calls=0;
 await page.route(path,async route=>{calls++;if(calls===1)await route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:401,msg:'Access token expired',data:null,requestId:'qa-refresh'})});else await route.continue();});
 if(role==='student')await page.goto('/notes');else await page.reload();await expect(page.locator('.sidebar')).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);await expect.poll(()=>calls).toBe(2);
});
test('changing management accounts after expiry clears the previous account draft',async({page,context},info)=>{
 test.skip(info.project.name!=='admin');await page.goto('/');await login(page,'admin');
 await page.evaluate(()=>sessionStorage.setItem('school-exam-draft-qa-private','private previous account draft'));
 await context.clearCookies();await page.route('**/api/v2/admin/dashboard',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:401,msg:'Expired',data:null,requestId:'qa-account-switch'})}));await page.reload();
 await expect(page.locator('input[autocomplete="username"]')).toBeVisible();await page.unroute('**/api/v2/admin/dashboard');await login(page,'teacher');
 expect(await page.evaluate(()=>sessionStorage.getItem('school-exam-draft-qa-private'))).toBeNull();
});
