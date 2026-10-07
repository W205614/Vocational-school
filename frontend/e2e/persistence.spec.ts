import {test,expect,type Page} from '@playwright/test';import fs from 'node:fs';
const accounts=JSON.parse(fs.readFileSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/accounts.json','utf8'));
const fixture=JSON.parse(fs.readFileSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/browser-fixture.json','utf8'));
async function login(page:Page,role:string,path:string){await page.goto(path);const credential=accounts[role];await page.locator('input[autocomplete="username"]').fill(credential.username);await page.locator('input[autocomplete="current-password"]').fill(credential.password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
async function api(page:Page,method:string,path:string,body?:unknown,key?:string){const token=await page.evaluate(()=>sessionStorage.getItem('school-token'));const response=await page.request.fetch('/api/v2'+path,{method,data:body,headers:{Authorization:'Bearer '+token,...(key?{'Idempotency-Key':key}:{})}});const envelope=await response.json();expect(response.ok(),JSON.stringify(envelope)).toBeTruthy();expect(envelope.code).toBe(200);return envelope.data;}
async function operation(page:Page,service:string,op:any){await expect.poll(async()=>{op=await api(page,'GET','/operations/'+service+'/'+op.operationId);return op.status;},{timeout:30000}).not.toBe('PENDING');return op;}
test('student notes persist after refresh and reject stale edits',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student','/notes');const marker='Browser note '+crypto.randomUUID(),key=crypto.randomUUID();
 const first=await api(page,'POST','/notes',{courseId:fixture.notesCourse,content:marker},key),repeat=await api(page,'POST','/notes',{courseId:fixture.notesCourse,content:marker},key);expect(repeat.operationId).toBe(first.operationId);
 const result=await operation(page,'learning',first);expect(result.status).toBe('SUCCEEDED');const note=result.result;
 await page.reload();await expect(page.getByText(marker,{exact:true})).toBeVisible();const row=page.locator('article').filter({has:page.getByText(marker,{exact:true})});await row.getByRole('button',{name:'编辑',exact:true}).click();
 await operation(page,'learning',await api(page,'PUT','/notes/'+note.id,{content:marker+' concurrent',version:note.version},crypto.randomUUID()));
 await page.locator('textarea').fill(marker+' stale');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('dialog',{name:'编辑笔记'}).getByRole('alert').filter({hasText:'笔记已删除或被其他编辑更新'})).toBeVisible();
 expect((await api(page,'GET','/notes/'+note.id)).content).toBe(marker+' concurrent');
});
test('student favorites and access errors remain correct after refresh',async({page},info)=>{
 test.skip(info.project.name!=='student');await login(page,'student','/favorites');await api(page,'PUT','/favorites/1');await api(page,'PUT','/favorites/1');await page.reload();await expect(page.getByRole('link',{name:'查看课程',exact:true})).toBeVisible();
 const token=await page.evaluate(()=>sessionStorage.getItem('school-token'));const forbidden=await page.request.get('/api/v2/admin/payment-conflicts',{headers:{Authorization:'Bearer '+token,'user-info':accounts.admin.id,'user-role':'1'}});expect(forbidden.status()).toBe(403);
 await api(page,'DELETE','/favorites/1');await api(page,'DELETE','/favorites/1');await page.reload();await expect(page.getByText('暂无记录',{exact:true})).toBeVisible();
});
test('admin reliability pages render real persistent task states',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'admin','/reliability');const loaded=page.waitForResponse(r=>r.url().endsWith('/admin/operation-failures/trade'));await page.getByRole('button',{name:'查询服务',exact:true}).click();expect((await loaded).status()).toBe(200);await expect(page.getByRole('heading',{name:'事件与业务冲突处理'})).toBeVisible();await expect(page.locator('.el-alert--error')).toHaveCount(0);await page.reload();await expect(page.getByRole('heading',{name:'事件与业务冲突处理'})).toBeVisible();
});
test('teacher workspace exposes scoring without administrator actions',async({page},info)=>{
 test.skip(info.project.name!=='admin');await login(page,'teacher','/exams');await expect(page.getByRole('heading',{name:'待评分考试'})).toBeVisible();await expect(page.getByRole('button',{name:'发布不可变试卷版本'})).toHaveCount(0);await expect(page.getByRole('link',{name:'失败与补偿任务'})).toHaveCount(0);
});
