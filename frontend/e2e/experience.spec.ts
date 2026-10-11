import {home, login} from './helpers';
import {test,expect} from '@playwright/test';
import fs from 'node:fs';import path from 'node:path';
for(const width of [1440,845,390]){
 test('layout, navigation and refresh at '+width+' pixels',async({page},info)=>{
  const role=info.project.name==='student'?'student':'admin';await page.setViewportSize({width,height:900});
  await login(page,role);await expect(page.locator('.el-alert--error')).toHaveCount(0);
  if(role==='student'){
   await expect(page.locator('.course-grid article').first()).toBeVisible();
   await expect(page.locator('.course-grid article').first()).toContainText(/\d+ 小节/);
  }
  const overflowing=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
  expect(overflowing,'The page must fit the viewport; navigation may scroll inside its own area').toBe(false);
  const shots=path.join(home,'ux-screenshots');fs.mkdirSync(shots,{recursive:true});
  await page.screenshot({path:path.join(shots,role+'-'+width+'.png'),fullPage:true});
  await page.goto('/orders');await page.reload();
  await expect(page.locator('.sidebar')).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)).toBe(false);
 });
}
