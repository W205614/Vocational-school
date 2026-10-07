import {test,expect} from '@playwright/test';
import fs from 'node:fs';import path from 'node:path';
const home=process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local';
const accounts=JSON.parse(fs.readFileSync(path.join(home,'accounts.json'),'utf8'));
for(const width of [1440,845,390]){
 test('layout, navigation and refresh at '+width+' pixels',async({page},info)=>{
  const role=info.project.name==='student'?'student':'admin';await page.setViewportSize({width,height:900});
  await page.goto('/');await page.locator('input[autocomplete="username"]').fill(accounts[role].username);
  await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);
  await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();
  await expect(page.locator('.sidebar')).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);
  if(role==='student'){
   await expect(page.locator('.course-grid article').first()).toBeVisible();
   await expect(page.locator('.course-grid article').first()).toContainText(/\d+ 小节/);
  }
  const overflowing=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
  expect(overflowing,'The page must fit the viewport; navigation may scroll inside its own area').toBe(false);
  const shots=path.join(home,'ux-screenshots');fs.mkdirSync(shots,{recursive:true});
  await page.screenshot({path:path.join(shots,role+'-'+width+'.png'),fullPage:true});
  const destination=role==='student'?'/orders':'/orders';await page.goto(destination);await page.reload();
  await expect(page.locator('.sidebar')).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)).toBe(false);
 });
}
