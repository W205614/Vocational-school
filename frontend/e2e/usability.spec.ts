import {test,expect,type Page} from '@playwright/test';import fs from 'node:fs';
const home=process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local',accounts=JSON.parse(fs.readFileSync(home+'/accounts.json','utf8'));
async function login(page:Page,role:string){await page.goto('/');await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
test('login explains invalid credentials and allows recovery',async({page},info)=>{
 const role=info.project.name==='student'?'student':'admin';await page.goto('/');const submit=page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true});await expect(submit).toBeDisabled();await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill('deliberately-invalid-qa-password');await submit.click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.locator('input[autocomplete="username"]')).toHaveValue(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await submit.click();await expect(page.locator('.sidebar')).toBeVisible();
});
test('course discovery has working categories, filters and empty-state recovery',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');await expect(page.getByText('课程分类暂时无法加载，仍可搜索课程。')).toHaveCount(0);await expect(page.locator('.category-button')).not.toHaveCount(1);
 const response=page.waitForResponse(r=>r.url().includes('/services/search/courses/portal?')&&r.url().includes('categoryIdLv1='));await page.locator('.category-button').nth(1).click();expect((await response).ok()).toBeTruthy();
 await page.getByRole('button',{name:'清除筛选',exact:true}).click();await page.getByRole('button',{name:'免费课程',exact:true}).click();await expect(page.getByRole('status')).toHaveCount(0);await expect(page.locator('.tile-bottom .price').first()).toBeVisible();for(const text of await page.locator('.tile-bottom .price').allTextContents())expect(text).toBe('免费');
 await page.getByRole('textbox',{name:'搜索课程',exact:true}).fill('no-such-course-'+crypto.randomUUID());await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.getByText('没有找到课程',{exact:true})).toBeVisible();await page.getByRole('button',{name:'查看全部课程',exact:true}).click();await expect(page.locator('.course-tile').first()).toBeVisible();
});
test('student can find exams without entering internal IDs',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');await page.getByRole('link',{name:'课程考试',exact:true}).click();await expect(page.getByRole('combobox',{name:'选择考试课程',exact:true})).toBeVisible();await expect(page.getByPlaceholder('课程 ID')).toHaveCount(0);await expect(page.getByText('请选择已报名课程查看考试',{exact:true})).toBeVisible();
});
test('dashboard quick actions and real-data details remain usable',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin');await expect(page.locator('.metric-card')).toHaveCount(5);await page.getByRole('button',{name:'查看明细',exact:true}).click();await expect(page.locator('.el-table')).toBeVisible();await page.locator('.quick-action-grid').getByRole('link',{name:'退款审核',exact:true}).click();await expect(page.getByRole('heading',{name:'退款审核',exact:true})).toBeVisible();
});

test('free enrollment survives refresh and expired enrollment is explained upfront',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');
 await page.goto('/courses/1549025085494521857');await expect(page.getByRole('button',{name:'报名已结束',exact:true})).toBeDisabled();
 const fixture=JSON.parse(fs.readFileSync(home+'/browser-fixture.json','utf8'));await page.goto('/courses/'+fixture.freeCourse);await page.getByRole('button',{name:'免费报名',exact:true}).click();
 await expect(page.getByText(/报名成功/).first()).toBeVisible();await expect.poll(async()=>{const r=await page.request.get('/api/v2/services/learning/learning-records/course/'+fixture.freeCourse,{headers:{Authorization:'Bearer '+await page.evaluate(()=>sessionStorage.getItem('school-token'))}});return (await r.json()).data?.id;},{timeout:15000}).toBeTruthy();
 await page.reload();await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'免费报名',exact:true})).toHaveCount(0);
});
