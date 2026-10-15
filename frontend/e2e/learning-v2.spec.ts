import {test,expect} from '@playwright/test';
import {login,fixture} from './helpers';
test('visitor can browse course curriculum and returns to purchase after login',async({page},info)=>{
 test.skip(info.project.name!=='student');
 const privateRequests:string[]=[];page.on('request',request=>{if(request.url().includes('/api/v2/')&&!request.url().includes('/public/')&&!request.url().includes('/environment'))privateRequests.push(request.url());});
 await page.goto('/');await expect(page.getByRole('heading',{name:'课程中心',exact:true})).toBeVisible();await expect(page.locator('.course-grid article').first()).toBeVisible();
 await page.goto('/courses/'+fixture.course);await expect(page.getByRole('heading',{name:fixture.name,exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'课程目录',exact:true})).toBeVisible();expect(privateRequests).toEqual([]);
 await page.getByRole('button',{name:'加入购物车',exact:true}).click();await expect(page).toHaveURL(/\/login\?redirect=/);await expect(page.locator('input[autocomplete="username"]')).toBeVisible();
});
test('learning, monthly points, history and review workspaces load real data',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student','/learning');await expect(page.getByRole('heading',{name:'我的学习',exact:true})).toBeVisible();await expect(page.getByText('本周完成',{exact:true})).toBeVisible();
 await page.goto('/points');await expect(page.getByRole('heading',{name:'积分与签到',exact:true})).toBeVisible();await expect(page.getByLabel('当月签到日历')).toBeVisible();await expect(page.getByRole('combobox',{name:'选择赛季',exact:true})).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);
 await page.getByRole('tab',{name:'积分明细',exact:true}).click();await expect(page.getByRole('heading',{name:'积分明细',exact:true})).toBeVisible();await page.getByRole('tab',{name:'我的成就',exact:true}).click();await expect(page.getByRole('heading',{name:'我的成就',exact:true})).toBeVisible();
 await page.goto('/review');await expect(page.getByRole('heading',{name:'错题复习',exact:true})).toBeVisible();await expect(page.getByRole('combobox',{name:'掌握状态',exact:true})).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);
});
test('teacher sees named teaching courses and admin can inspect seasons',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'teacher','/teaching');await expect(page.getByRole('heading',{name:'教学学情与答疑',exact:true})).toBeVisible();await expect(page.getByRole('combobox',{name:'教学课程',exact:true})).toBeVisible();await expect(page.getByPlaceholder('输入所授课程编号')).toHaveCount(0);await expect(page.locator('.el-alert--error')).toHaveCount(0);
});

test('management forms use names and typed choices instead of numeric codes',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin','/staff');await page.getByRole('button',{name:'新增',exact:true}).click();let dialog=page.getByRole('dialog',{name:'新增记录',exact:true});await expect(dialog.getByRole('combobox',{name:'用户类型',exact:true})).toBeVisible();await expect(dialog.getByRole('button',{name:'选择角色',exact:true})).toBeVisible();await dialog.getByRole('button',{name:'取消',exact:true}).click();
 await page.goto('/questions');await page.getByRole('button',{name:'新增',exact:true}).click();dialog=page.getByRole('dialog',{name:'新增记录',exact:true});await expect(dialog.getByRole('combobox',{name:'题目类型',exact:true})).toBeVisible();await expect(dialog.getByRole('combobox',{name:'题目难度',exact:true})).toBeVisible();await dialog.getByRole('button',{name:'取消',exact:true}).click();
 await page.goto('/notices');await page.getByRole('button',{name:'新增',exact:true}).click();dialog=page.getByRole('dialog',{name:'新增记录',exact:true});await expect(dialog.getByRole('button',{name:'选择通知模板',exact:true})).toBeVisible();await expect(dialog.getByRole('button',{name:'选择接收人',exact:true})).toBeVisible();await dialog.getByRole('button',{name:'取消',exact:true}).click();
});
