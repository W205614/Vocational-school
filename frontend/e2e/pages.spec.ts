import {test,expect,type Page} from '@playwright/test';
import fs from 'node:fs';import {resources} from '../apps/admin/src/resources';
const accounts=JSON.parse(fs.readFileSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/accounts.json','utf8'));
async function login(page:Page,role:string){await page.goto('/');await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
test('administrator resource pages load persistent data without server errors',async({page},info)=>{
 test.skip(info.project.name!=='admin');test.setTimeout(120000);await login(page,'admin');
 for(const [name,resource] of Object.entries(resources)){
  const response=page.waitForResponse(r=>r.url().includes('/api/v2'+resource.list.split('?')[0])&&r.request().method()==='GET');
  await page.goto('/'+name);const result=await response;expect(result.status(),name).toBe(200);expect((await result.json()).code,name).toBe(200);
  await expect(page.getByRole('heading',{name:resource.title,exact:true})).toBeVisible();await expect(page.getByRole('status')).toHaveCount(0);await expect(page.locator('.el-alert--error')).toHaveCount(0);
 }
});
test('student account pages load persistent data without server errors',async({page},info)=>{
 test.skip(info.project.name!=='student');test.setTimeout(120000);await login(page,'student');
 const pages:[string,string][]=[['learning','/services/learning/lessons/page'],['favorites','/favorites'],['notes','/notes'],['points','/services/learning/boards'],['messages','/services/message/inboxes'],['settings','/services/user/users/me'],['cart','/services/trade/carts'],['coupons','/services/promotion/coupons/list'],['orders','/orders']];
 for(const [path,api] of pages){
  const response=page.waitForResponse(r=>r.url().includes('/api/v2'+api)&&r.request().method()==='GET');await page.goto('/'+path);
  const result=await response;expect(result.status(),path).toBe(200);expect((await result.json()).code,path).toBe(200);await expect(page.getByRole('status')).toHaveCount(0);await expect(page.locator('.el-alert--error')).toHaveCount(0);
 }
});
