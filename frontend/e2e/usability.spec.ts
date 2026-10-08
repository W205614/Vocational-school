import {test,expect,type Page} from '@playwright/test';import fs from 'node:fs';
const home=process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local',accounts=JSON.parse(fs.readFileSync(home+'/accounts.json','utf8'));
async function login(page:Page,role:string){await page.goto('/');await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
test('login explains invalid credentials and allows recovery',async({page},info)=>{
 const role=info.project.name==='student'?'student':'admin';await page.goto('/');const submit=page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true});await expect(submit).toBeDisabled();await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill('deliberately-invalid-qa-password');await submit.click();await expect(page.getByRole('alert')).toBeVisible();await expect(page.locator('input[autocomplete="username"]')).toHaveValue(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await submit.click();await expect(page.locator('.sidebar')).toBeVisible();
});
test('course discovery has working categories, filters and empty-state recovery',async({page},info)=>{
 test.skip(info.project.name!=='student');
 // Hold the initial course query so a category click occurs deterministically during loading.
 let releaseInitial!:()=>void,firstPortal=true;
 const initialBlocked=new Promise<void>(resolve=>{releaseInitial=resolve;});
 await page.route('**/api/v2/services/search/courses/portal?*',async route=>{
  if(firstPortal){firstPortal=false;const upstream=await route.fetch();await initialBlocked;await route.fulfill({response:upstream});}
  else await route.continue();
 });
 await login(page,'student');await expect(page.getByText('课程分类暂时无法加载，仍可搜索课程。')).toHaveCount(0);await expect(page.locator('.category-button')).not.toHaveCount(1);
 const response=page.waitForResponse(r=>r.url().includes('/services/search/courses/portal?')&&r.url().includes('categoryIdLv1='));try{await page.locator('.category-button').nth(1).click();}finally{releaseInitial();}expect((await response).ok()).toBeTruthy();
 await page.getByRole('button',{name:'清除筛选',exact:true}).click();const freeResponse=page.waitForResponse(r=>r.url().includes('/services/search/courses/portal?')&&r.url().includes('free=true'));await page.getByRole('button',{name:'免费课程',exact:true}).click();expect((await freeResponse).ok()).toBeTruthy();await expect(page.getByRole('status')).toHaveCount(0);await expect(page.locator('.tile-bottom .price').first()).toBeVisible();for(const text of await page.locator('.tile-bottom .price').allTextContents())expect(text).toBe('免费');
 await page.getByRole('textbox',{name:'搜索课程',exact:true}).fill('no-such-course-'+crypto.randomUUID());await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.getByText('没有找到课程',{exact:true})).toBeVisible();await page.getByRole('button',{name:'查看全部课程',exact:true}).click();await expect(page.locator('.course-tile').first()).toBeVisible();
});
test('student can find exams without entering internal IDs',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');await page.getByRole('link',{name:'课程考试',exact:true}).click();await expect(page.getByRole('combobox',{name:'选择考试课程',exact:true})).toBeVisible();await expect(page.getByPlaceholder('课程 ID')).toHaveCount(0);await expect(page.getByText('请选择已报名课程查看考试',{exact:true})).toBeVisible();
});

test('expired enrollment explains exam access and keeps history visible',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');
 const fixture=JSON.parse(fs.readFileSync(home+'/browser-fixture.json','utf8'));
 await page.goto('/exams?courseId='+fixture.expiredCourse);
 await expect(page.locator('.enrollment-hint')).toContainText('学习权益已失效');
 await expect(page.locator('.enrollment-hint')).toContainText('学习记录和已有答卷仍然保留');
 await expect(page.getByRole('button',{name:'查询试卷',exact:true})).toBeDisabled();
 await expect(page.locator('.el-alert--error')).toHaveCount(0);
 await page.getByRole('combobox',{name:'选择考试课程',exact:true}).click();
 await expect(page.getByRole('option',{name:'Browser expired '+fixture.marker+'（权益已失效）',exact:true})).toHaveAttribute('aria-disabled','true');
 await page.getByRole('option',{name:'Browser notes '+fixture.marker,exact:true}).click();
 await expect(page.getByText('这门课程暂未发布试卷',{exact:true})).toBeVisible();
 await expect(page.locator('.enrollment-hint')).toHaveCount(0);
 await page.getByRole('link',{name:'返回我的学习 →',exact:true}).click();
 await expect(page.getByText('Browser expired '+fixture.marker,{exact:true})).toBeVisible();
});
test('search highlights readable titles and applies the selected price order',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');
 const fixture=JSON.parse(fs.readFileSync(home+'/browser-fixture.json','utf8'));
 await page.getByRole('textbox',{name:'搜索课程',exact:true}).fill(fixture.marker);
 const searched=page.waitForResponse(r=>r.url().includes('/services/search/courses/portal?')&&r.url().includes('keyword='+fixture.marker));
 await page.getByRole('button',{name:'搜索',exact:true}).click();expect((await searched).ok()).toBeTruthy();
 const course=page.locator('.course-tile').filter({has:page.locator('a[href="/courses/'+fixture.course+'"]')});
 await expect(course).toHaveCount(1);await expect(course.locator('h3 mark')).toHaveText(fixture.marker);
 await expect(course.locator('h3')).toHaveText(fixture.name);await expect(course.locator('.cover-link')).toHaveAttribute('aria-label','查看课程：'+fixture.name);
 await page.locator('.el-select').filter({has:page.getByRole('combobox',{name:'课程排序',exact:true})}).click();
 const sorted=page.waitForResponse(r=>r.url().includes('/services/search/courses/portal?')&&r.url().includes('sortBy=price'));
 await page.getByRole('option',{name:'价格由低到高',exact:true}).click();const result=await sorted;
 expect(new URL(result.url()).searchParams.get('isAsc')).toBe('true');
 const data=(await result.json()).data.list;expect(data.length).toBeGreaterThan(1);
 expect(data.map((row:any)=>Number(row.price))).toEqual(data.map((row:any)=>Number(row.price)).sort((a:number,b:number)=>a-b));
});
test('private note filters use enrolled course names',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');await page.goto('/notes');
 const fixture=JSON.parse(fs.readFileSync(home+'/browser-fixture.json','utf8'));
 await expect(page.getByPlaceholder('课程 ID')).toHaveCount(0);
 await page.getByRole('combobox',{name:'筛选笔记课程',exact:true}).click();
 const filtered=page.waitForResponse(r=>r.url().includes('/notes?')&&r.url().includes('courseId='+fixture.notesCourse));
 await page.getByRole('option',{name:'Browser notes '+fixture.marker,exact:true}).click();
 expect((await filtered).ok()).toBeTruthy();
 await expect(page.locator('.el-select').filter({has:page.getByRole('combobox',{name:'筛选笔记课程',exact:true})})).toContainText('Browser notes '+fixture.marker);
 await expect(page.getByRole('alert')).toHaveCount(0);
});
test('dashboard quick actions and real-data details remain usable',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin');await expect(page.locator('.metric-card')).toHaveCount(5);await page.getByRole('button',{name:'查看明细',exact:true}).click();await expect(page.locator('.el-table')).toBeVisible();await page.locator('.quick-action-grid').getByRole('link',{name:'退款审核',exact:true}).click();await expect(page.getByRole('heading',{name:'退款审核',exact:true})).toBeVisible();
});

test('free enrollment survives refresh and expired enrollment is explained upfront',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student');
 const fixture=JSON.parse(fs.readFileSync(home+'/browser-fixture.json','utf8'));await page.goto('/courses/'+fixture.expiredCourse);await expect(page.getByRole('button',{name:'报名已结束',exact:true})).toBeDisabled();
 await page.goto('/courses/'+fixture.freeCourse);await page.getByRole('button',{name:'免费报名',exact:true}).click();
 await expect(page.getByText(/报名成功/).first()).toBeVisible();await expect.poll(async()=>{const r=await page.request.get('/api/v2/services/learning/learning-records/course/'+fixture.freeCourse,{headers:{Authorization:'Bearer '+await page.evaluate(()=>sessionStorage.getItem('school-token'))}});return (await r.json()).data?.id;},{timeout:15000}).toBeTruthy();
 await page.reload();await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'免费报名',exact:true})).toHaveCount(0);
});

test('teacher login opens the grading workspace without requesting the admin dashboard',async({page},info)=>{
 test.skip(info.project.name!=='admin');const dashboards:string[]=[];
 page.on('request',request=>{if(new URL(request.url()).pathname.endsWith('/admin/dashboard'))dashboards.push(request.url());});
 await login(page,'teacher');await expect(page).toHaveURL(/\/exams$/);
 await expect(page.getByRole('heading',{name:'试卷版本与教师评分',exact:true})).toBeVisible();
 await expect(page.getByText(/需要管理员权限/)).toHaveCount(0);expect(dashboards).toEqual([]);
 await page.goto('/refunds');await expect(page).toHaveURL(/\/exams$/);
 await expect(page.getByRole('link',{name:'退款审核',exact:true})).toHaveCount(0);
});

test('admin resource filters use supported parameters and preserve long IDs',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin');await page.goto('/refunds');
 const firstCell=page.locator('.el-table__body-wrapper tbody tr').first().locator('td').first();
 await expect(firstCell).toContainText(/\d{16,19}/);const id=(await firstCell.innerText()).trim();
 const search=page.getByRole('textbox',{name:'按退款 ID 查询',exact:true});await search.fill(id);
 const matching=page.waitForResponse(response=>response.url().includes('/refund-apply/page?')&&new URL(response.url()).searchParams.get('id')===id);
 await page.getByRole('button',{name:'查询',exact:true}).click();const body=(await (await matching).json()).data;
 expect(body.list.map((row:any)=>String(row.id))).toEqual([id]);
 await search.fill('9223372036854775807');const empty=page.waitForResponse(response=>response.url().includes('/refund-apply/page?')&&new URL(response.url()).searchParams.get('id')==='9223372036854775807');
 await page.getByRole('button',{name:'查询',exact:true}).click();expect(Number((await (await empty).json()).data.total)).toBe(0);await expect(page.getByText('暂无数据',{exact:true})).toBeVisible();
 await search.fill('invalid-id');await page.getByRole('button',{name:'查询',exact:true}).click();await expect(page.getByRole('alert')).toContainText('请输入有效的退款 ID');
 for(const resource of ['templates','notices']){
  await page.goto('/'+resource);const keyword='no-such-resource-'+crypto.randomUUID();await page.getByRole('textbox').fill(keyword);
  const filtered=page.waitForResponse(response=>response.url().includes(resource==='templates'?'/notice-templates?':'/notice-tasks?')&&new URL(response.url()).searchParams.get('keyword')===keyword);
  await page.getByRole('button',{name:'查询',exact:true}).click();expect(Number((await (await filtered).json()).data.total)).toBe(0);
 }
 await page.goto('/roles');await expect(page.locator('.el-table__body-wrapper tbody tr').first()).toContainText(/管理员|教师/);
 await page.getByRole('textbox',{name:'按角色名称搜索',exact:true}).fill('no-such-role-'+crypto.randomUUID());await page.getByRole('button',{name:'查询',exact:true}).click();await expect(page.getByText('暂无数据',{exact:true})).toBeVisible();
 await page.goto('/privileges');await expect(page.getByRole('textbox')).toHaveCount(0);await expect(page.getByRole('button',{name:'刷新列表',exact:true})).toBeVisible();
});

test('switching resources during a pending query loads the selected resource',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin');let release!:()=>void,started!:()=>void;
 const pending=new Promise<void>(resolve=>{release=resolve;}),blocked=new Promise<void>(resolve=>{started=resolve;});
 await page.route('**/api/v2/admin/course/courses/page?*',async route=>{const response=await route.fetch();started();await pending;await route.fulfill({response});});
 await page.goto('/courses');await blocked;
 const selected=page.waitForResponse(response=>response.url().includes('/refund-apply/page?'),{timeout:10000});
 try{await page.locator('.sidebar').getByRole('link',{name:'退款审核',exact:true}).click();expect((await selected).ok()).toBeTruthy();}finally{release();}
 await expect(page.getByRole('heading',{name:'退款审核',exact:true})).toBeVisible();
 await expect(page.locator('.el-table__header')).toContainText('退款金额');await expect(page.getByRole('alert')).toHaveCount(0);
});
