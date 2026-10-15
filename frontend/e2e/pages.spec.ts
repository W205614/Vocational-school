import {login} from './helpers';
import {test,expect} from '@playwright/test';
import {resources} from '../apps/admin/src/resources';
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
 const pages:[string,string][]=[['learning','/services/learning/lessons/page'],['favorites','/favorites'],['notes','/notes'],['points','/learning-hub/board'],['messages','/services/message/inboxes'],['settings','/services/user/users/me'],['cart','/services/trade/carts'],['coupons','/services/promotion/coupons/list'],['orders','/orders']];
 for(const [path,api] of pages){
  const response=page.waitForResponse(r=>r.url().includes('/api/v2'+api)&&r.request().method()==='GET');await page.goto('/'+path);
  const result=await response;expect(result.status(),path).toBe(200);expect((await result.json()).code,path).toBe(200);await expect(page.getByRole('status')).toHaveCount(0);await expect(page.locator('.el-alert--error')).toHaveCount(0);
 }
});
