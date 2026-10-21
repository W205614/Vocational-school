import {test,expect,type Page} from '@playwright/test';
const envelope=(data:unknown)=>({code:200,msg:'OK',data});
async function enter(page:Page,path:string,role=1){
 await page.addInitScript(role=>{const claims=btoa(JSON.stringify({userId:'900000000000000099',user:{roleId:role}}));sessionStorage.setItem('school-token','e30.'+claims+'.signature');sessionStorage.setItem('school-admin','true');},role);
 await page.goto(path);
}
test('teacher keeps learning and questions available when exam statistics fail and retries only that section',async({page},info)=>{
 test.skip(info.project.name!=='admin');let failed=true,learningReads=0,questionReads=0;
 await page.route('**/api/v2/**',async route=>{const url=new URL(route.request().url());
 if(url.pathname.endsWith('/teacher/courses'))return route.fulfill({json:envelope({list:[{id:'100',name:'Isolated course'}],total:1})});
 if(url.pathname.endsWith('/teacher/learning-hub')){learningReads++;return route.fulfill({json:envelope({courseName:'Available learning',summary:{students:1,completedStudents:0,completionRate:0,unansweredQuestions:1},list:[],total:0})});}
 if(url.pathname.endsWith('/teacher/exam-statistics'))return failed?route.fulfill({status:503,json:{code:503,msg:'Statistics unavailable'}}):route.fulfill({json:envelope({list:[{student_id:'Recovered student'}],total:1})});
 if(url.pathname.endsWith('/learning-hub/teaching/questions')){questionReads++;return route.fulfill({json:envelope({list:[{id:'101',title:'Available question'}],total:1})});}
 return route.fulfill({json:envelope({})});});
 await enter(page,'/teaching',3);await expect(page.getByText('Available learning',{exact:true})).toBeVisible();await expect(page.getByText('Available question',{exact:true})).toBeVisible();
 await expect(page.getByText('Statistics unavailable',{exact:false})).toBeVisible();failed=false;
 await page.getByRole('button',{name:'重试考试统计',exact:true}).click();await expect(page.getByText('Recovered student',{exact:true})).toBeVisible();expect(learningReads).toBe(1);expect(questionReads).toBe(1);
});
test('teacher course switches discard older responses and clear denied course data',async({page},info)=>{
 test.skip(info.project.name!=='admin');let release!:()=>void;const held=new Promise<void>(resolve=>release=resolve);let started!:()=>void;const first=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v2/**',async route=>{const url=new URL(route.request().url());
 if(url.pathname.endsWith('/teacher/courses'))return route.fulfill({json:envelope({list:[{id:'100',name:'Course A'},{id:'200',name:'Course B'}],total:2})});
 if(url.pathname.endsWith('/teacher/learning-hub')){if(url.searchParams.get('courseId')==='100'){started();await held;return route.fulfill({json:envelope({courseName:'Stale A learning',summary:{students:1},list:[],total:0})});}return route.fulfill({status:403,json:{code:403,msg:'Course B denied'}});}
 if(url.pathname.endsWith('/teacher/exam-statistics'))return route.fulfill({json:envelope({list:[],total:0})});
 if(url.pathname.endsWith('/learning-hub/teaching/questions'))return route.fulfill({json:envelope({list:[{id:'201',title:url.searchParams.get('courseId')==='200'?'Question B':'Question A'}],total:1})});
 return route.fulfill({json:envelope({})});});
 await enter(page,'/teaching',3);await first;
 try{await page.getByRole('combobox',{name:'教学课程',exact:true}).click();await page.getByRole('option',{name:'Course B',exact:true}).click();await expect(page.getByText('Question B',{exact:true})).toBeVisible();await expect(page.getByText('Course B denied',{exact:false})).toBeVisible();}finally{release();}
 await expect(page.getByText('Stale A learning',{exact:true})).toHaveCount(0);await expect(page.getByText('Question A',{exact:true})).toHaveCount(0);
});
test('reliability sections survive transaction failures, page old failures and switch to auth safely',async({page},info)=>{
 test.skip(info.project.name!=='admin');const replayPaths:string[]=[];let eventReads=0,mediaConsumerReads=0;
 await page.route('**/api/v2/**',async route=>{const url=new URL(route.request().url()),p=url.pathname;
 if(route.request().method()==='POST'){replayPaths.push(p);return route.fulfill({json:envelope(null)});}
 if(p.endsWith('/admin/payment-conflicts'))return route.fulfill({status:503,json:{code:503,msg:'Transaction unavailable'}});
 if(p.includes('/admin/compensations/'))return route.fulfill({json:envelope([])});
 if(p.includes('/admin/events/')){eventReads++;const module=p.includes('/auth/')?'auth':'trade',second=url.searchParams.get('pageNo')==='2';return route.fulfill({json:envelope({list:[{event_id:module==='auth'?'auth-event':second?'old-trade-event':'new-trade-event',business_key:module+' source'}],total:121,pageNo:second?2:1,pageSize:20})});}
 if(p.includes('/admin/consumer-failures/media/'))mediaConsumerReads++;
 if(p.includes('/admin/operation-failures/')||p.includes('/admin/consumer-failures/'))return route.fulfill({json:envelope({list:[],total:0,pageNo:1,pageSize:20})});
 return route.fulfill({json:envelope({})});});
 await enter(page,'/reliability');await page.getByRole('button',{name:'查询服务',exact:true}).click();await expect(page.getByText('new-trade-event',{exact:true})).toBeVisible();await expect(page.getByText('Transaction unavailable',{exact:false})).toBeVisible();
 const eventSection=page.locator('[aria-label="投递失败任务"]');await eventSection.locator('.el-pager li.number').filter({hasText:/^2$/}).click();await expect(page.getByText('old-trade-event',{exact:true})).toBeVisible();
 await page.getByRole('combobox',{name:'故障模块',exact:true}).click();await page.getByRole('option',{name:'auth',exact:true}).click();await expect(page.getByText('auth-event',{exact:true})).toBeVisible();await expect(page.getByText('old-trade-event',{exact:true})).toHaveCount(0);
 await eventSection.getByRole('button',{name:'重放',exact:true}).click();await expect.poll(()=>replayPaths.length).toBe(1);expect(replayPaths[0]).toBe('/api/v2/admin/events/auth/auth-event/replay');expect(eventReads).toBeGreaterThan(2);
 await page.getByRole('combobox',{name:'故障模块',exact:true}).click();await page.getByRole('option',{name:'media',exact:true}).click();await expect(page.getByText('该模块无消息消费任务。',{exact:true})).toBeVisible();expect(mediaConsumerReads).toBe(0);
});

test('reliability discards late module responses and reports replay conflicts without success',async({page},info)=>{
 test.skip(info.project.name!=='admin');let release!:()=>void;const held=new Promise<void>(resolve=>release=resolve);let started!:()=>void;const pending=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v2/**',async route=>{const url=new URL(route.request().url()),p=url.pathname;
 if(route.request().method()==='POST')return route.fulfill({status:409,json:{code:409,msg:'Version changed; query again'}});
 if(p.includes('/admin/events/trade/')){started();await held;return route.fulfill({json:envelope({list:[{event_id:'stale-trade-event'}],total:1})});}
 if(p.includes('/admin/consumer-failures/auth/'))return route.fulfill({json:envelope({list:[{failure_id:'auth-failure',event_id:'auth-current',version:1}],total:1})});
 if(p.includes('/admin/events/')||p.includes('/admin/consumer-failures/')||p.includes('/admin/operation-failures/'))return route.fulfill({json:envelope({list:[],total:0})});
 return route.fulfill({json:envelope([])});});
 await enter(page,'/reliability');await page.getByRole('button',{name:'查询服务',exact:true}).click();await pending;
 try{await page.getByRole('combobox',{name:'故障模块',exact:true}).click();await page.getByRole('option',{name:'auth',exact:true}).click();await expect(page.getByText('auth-current',{exact:true})).toBeVisible();}finally{release();}
 await page.locator('[aria-label="消费失败任务"]').getByRole('button',{name:'重放',exact:true}).click();await expect(page.getByText('Version changed; query again',{exact:false})).toBeVisible();await expect(page.getByText('stale-trade-event',{exact:true})).toHaveCount(0);await expect(page.getByText('已安排重试',{exact:false})).toHaveCount(0);
});

test('teacher pagination refreshes its own section and filters reset both page numbers',async({page},info)=>{
 test.skip(info.project.name!=='admin');const reads:{kind:string;page:string|null;student:string|null}[]=[];
 await page.route('**/api/v2/**',async route=>{const url=new URL(route.request().url()),p=url.pathname;
 if(p.endsWith('/teacher/courses'))return route.fulfill({json:envelope({list:[{id:'100',name:'Pagination course'}],total:1})});
 if(p.endsWith('/teacher/learning-hub')){reads.push({kind:'learning',page:url.searchParams.get('pageNo'),student:url.searchParams.get('userId')});return route.fulfill({json:envelope({summary:{students:40},list:[],total:40})});}
 if(p.endsWith('/teacher/exam-statistics')){reads.push({kind:'exams',page:url.searchParams.get('pageNo'),student:url.searchParams.get('studentId')});return route.fulfill({json:envelope({list:[],total:40})});}
 if(p.endsWith('/learning-hub/teaching/questions'))return route.fulfill({json:envelope({list:[],total:0})});
 return route.fulfill({json:envelope({})});});
 await enter(page,'/teaching',3);const query=page.getByRole('button',{name:'查看课程学情',exact:true});await expect(query).toBeEnabled();
 await page.locator('[aria-label="课程学情"] .el-pager li.number').filter({hasText:/^2$/}).click();await expect(query).toBeEnabled();expect(reads.filter(r=>r.kind==='exams')).toHaveLength(1);
 await page.locator('[aria-label="考试统计"] .el-pager li.number').filter({hasText:/^2$/}).click();await expect(query).toBeEnabled();
 await page.getByRole('textbox',{name:'筛选学生编号',exact:true}).fill('123');await query.click();await expect(query).toBeEnabled();expect(reads.slice(-2)).toEqual([{kind:'learning',page:'1',student:'123'},{kind:'exams',page:'1',student:'123'}]);
});
