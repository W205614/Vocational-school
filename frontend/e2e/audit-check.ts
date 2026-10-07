import {expect,type Page} from '@playwright/test';
import fs from 'node:fs';
const home=process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local';
const accounts=JSON.parse(fs.readFileSync(home+'/accounts.json','utf8'));

export async function assertPersistedAudit(page:Page){
 await page.goto('/audit');
 await expect(page.getByRole('heading',{name:'管理操作审计',exact:true})).toBeVisible();
 await expect(page.getByRole('combobox',{name:'选择审计模块',exact:true})).toBeEnabled();
 await page.locator('.el-select').filter({has:page.getByRole('combobox',{name:'选择审计模块',exact:true})}).click();
 await page.getByRole('option',{name:'trade',exact:true}).click();
 await page.getByRole('textbox',{name:'操作者编号',exact:true}).fill(accounts.admin.id);
 await page.locator('.el-select').filter({has:page.getByRole('combobox',{name:'处理结果',exact:true})}).click();
 await page.getByRole('option',{name:'SUCCEEDED',exact:true}).click();
 const response=page.waitForResponse(r=>r.url().includes('/admin/audit/trade?')&&r.url().includes('actor='+accounts.admin.id)&&r.url().includes('result=SUCCEEDED'));
 await page.getByRole('button',{name:'查询',exact:true}).click();
 expect((await response).ok()).toBeTruthy();
 const rows=page.locator('.el-table__row');await expect(rows.first()).toBeVisible();
 for(const row of await rows.all()){
  await expect(row.locator('.cell').nth(0)).toHaveText(accounts.admin.id);
  await expect(row.locator('.cell').nth(2)).toHaveText('SUCCEEDED');
 }
 const requestId=(await rows.first().locator('.cell').nth(3).innerText()).trim();expect(requestId).not.toBe('');
 await page.getByRole('textbox',{name:'请求编号',exact:true}).fill(requestId);
 const narrowed=page.waitForResponse(r=>r.url().includes('/admin/audit/trade?')&&new URL(r.url()).searchParams.get('requestId')===requestId);
 await page.getByRole('button',{name:'查询',exact:true}).click();expect((await narrowed).ok()).toBeTruthy();
 await expect(rows.first()).toBeVisible();
 for(const row of await rows.all())await expect(row.locator('.cell').nth(3)).toHaveText(requestId);
 fs.mkdirSync(home+'/ux-admin-screenshots',{recursive:true});
 await page.screenshot({path:home+'/ux-admin-screenshots/audit.png',fullPage:true,animations:'disabled'});
}
