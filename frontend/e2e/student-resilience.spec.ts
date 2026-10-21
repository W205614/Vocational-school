import {test,expect,type Page,type Route} from '@playwright/test';

const envelope=(data:unknown)=>({code:200,data});
const answer=(route:Route,data:unknown)=>route.fulfill({json:envelope(data)});
function deferred(){let resolve!:()=>void;const promise=new Promise<void>(done=>{resolve=done;});return {promise,resolve};}
async function setup(page:Page){
 await page.addInitScript(()=>{sessionStorage.setItem('school-token','resilience-student');sessionStorage.setItem('school-admin','false');});
 await page.route('**/api/v2/**',route=>answer(route,new URL(route.request().url()).pathname.endsWith('/environment')?{capabilities:{localPlayback:true}}:{}));
}
async function navigateCourse(page:Page,id:string){await page.evaluate(id=>{history.pushState({...history.state,current:'/courses/'+id},'', '/courses/'+id);window.dispatchEvent(new PopStateEvent('popstate',{state:history.state}));},id);}
async function learningCourse(page:Page){
 await page.route('**/api/v2/services/course/course/11?**',route=>answer(route,{id:'11',name:'Learning course',chapters:[{id:'chapter',name:'Chapter',sections:[{id:'101',name:'Video one',type:2},{id:'102',name:'Video two',type:2}]}]}));
 await page.route('**/api/v2/favorites/*',route=>answer(route,{favorited:false}));
 await page.route('**/api/v2/services/learning/learning-records/course/*',route=>answer(route,{id:'lesson',status:1,records:[]}));
 await page.route('**/api/v2/services/media/medias/signature/play?**',route=>answer(route,{playUrl:'/fixture-'+new URL(route.request().url()).searchParams.get('sectionId')+'.mp4'}));
 await page.route('**/fixture-*.mp4',route=>route.fulfill({status:200,contentType:'video/mp4',body:''}));
}
test.beforeEach(async({page},info)=>{test.skip(info.project.name!=='student');await setup(page);});

test('a delayed old course never replaces the current course or its entitlement',async({page})=>{
 const started=deferred(),release=deferred();
 await page.route('**/api/v2/services/course/course/*',async route=>{const id=new URL(route.request().url()).pathname.split('/').pop();if(id==='11'){started.resolve();await release.promise;}await answer(route,{id,name:'Course '+id,chapters:[]});});
 await page.route('**/api/v2/favorites/*',route=>answer(route,{favorited:false}));
 await page.route('**/api/v2/services/learning/learning-records/course/*',route=>answer(route,null));
 await page.goto('/courses/11');await started.promise;await navigateCourse(page,'22');release.resolve();
 await expect(page.getByRole('heading',{name:'Course 22',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Course 11',exact:true})).toHaveCount(0);
});

test('rapid wrong-answer filters display the latest query',async({page})=>{
 const started=deferred(),release=deferred();let first=true;
 await page.route('**/api/v2/exam-review/page?**',async route=>{const params=new URL(route.request().url()).searchParams;if(first){first=false;started.resolve();await release.promise;}await answer(route,{list:[{attempt_id:'1',question_id:'2',name:params.get('mastered')==='true'?'Mastered result':'Old result',score:0,max_score:10}],total:1});});
 await page.route('**/api/v2/services/learning/lessons/page?**',route=>answer(route,{list:[],total:0}));
 await page.goto('/review');await started.promise;
 await page.getByRole('combobox',{name:'掌握状态',exact:true}).click();await page.getByRole('option',{name:'已掌握',exact:true}).click();release.resolve();
 await expect(page.locator('.review-row')).toContainText('Mastered result');await expect(page.locator('.review-row')).not.toContainText('Old result');
});

test('a delayed old playback signature cannot replace a newly selected video',async({page})=>{
 await learningCourse(page);const started=deferred(),release=deferred();
 await page.route('**/api/v2/services/media/medias/signature/play?sectionId=101',async route=>{started.resolve();await release.promise;await answer(route,{playUrl:'/fixture-101.mp4'});});
 await page.goto('/courses/11');await page.getByRole('button',{name:/Video one/}).click();await started.promise;
 await page.getByRole('button',{name:/Video two/}).click();await expect(page.locator('video')).toHaveAttribute('src','/fixture-102.mp4');release.resolve();
 await expect(page.locator('.lesson-workspace')).toContainText('Video two');await expect(page.locator('video')).toHaveAttribute('src','/fixture-102.mp4');
});

test('a short section tail is retained after failure and retried before switching',async({page})=>{
 await learningCourse(page);let fail=true;const writes:Record<string,unknown>[]=[];
 await page.route('**/api/v2/services/learning/learning-records',async route=>{writes.push(route.request().postDataJSON());if(fail){await route.fulfill({status:503,json:{code:503,msg:'Progress unavailable'}});return;}await answer(route,{});});
 await page.goto('/courses/11');await page.getByRole('button',{name:/Video one/}).click();await expect(page.locator('video')).toBeVisible();
 await page.locator('video').evaluate((video:HTMLVideoElement)=>{Object.defineProperty(video,'currentTime',{configurable:true,value:7});video.dispatchEvent(new Event('timeupdate'));});
 await page.getByRole('button',{name:/Video two/}).click();await expect(page.getByRole('alert').filter({hasText:'学习进度尚未保存'})).toBeVisible();
 expect(writes).toHaveLength(1);expect(writes[0]).toMatchObject({sectionId:'101',moment:7});await expect(page.locator('.lesson-workspace h2')).toHaveText('Video one');
 fail=false;await page.getByRole('button',{name:'重试保存学习进度',exact:true}).click();await expect(page.getByRole('button',{name:'重试保存学习进度',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:/Video two/}).click();await expect(page.locator('video')).toHaveAttribute('src','/fixture-102.mp4');expect(writes).toHaveLength(2);expect(writes[1]).toMatchObject({sectionId:'101',moment:7});
});

test('marking the last wrong answer on a final page returns to a valid page',async({page})=>{
 let marked=false;const row={attempt_id:'1',question_id:'2',name:'Last wrong answer',score:0,max_score:10,version:0};
 await page.route('**/api/v2/exam-review/page?**',route=>{const pageNo=new URL(route.request().url()).searchParams.get('pageNo');return answer(route,{list:marked&&pageNo==='2'?[]:[row],total:marked?20:21});});
 await page.route('**/api/v2/exam-review/1/2',route=>answer(route,row));
 await page.route('**/api/v2/exam-review/1/2/state',route=>{marked=true;return answer(route,{operationId:'mark',status:'SUCCEEDED',result:{}});});
 await page.route('**/api/v2/operations/exam/mark',route=>answer(route,{operationId:'mark',status:'SUCCEEDED',result:{}}));
 await page.route('**/api/v2/services/learning/lessons/page?**',route=>answer(route,{list:[],total:0}));
 await page.goto('/review');await page.locator('.el-pager li.number').filter({hasText:/^2$/}).click();
 await page.getByRole('button',{name:'复习这道题',exact:true}).click();const dialog=page.getByRole('dialog',{name:'复习错题',exact:true});
 await dialog.getByRole('button',{name:'标记已掌握',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.locator('.review-row')).toContainText('Last wrong answer');await expect(page.locator('.el-pager .is-active')).toHaveCount(0);
});

test('video completion is saved while a note operation is still running',async({page})=>{
 await learningCourse(page);const started=deferred(),release=deferred();const writes:Record<string,unknown>[]=[];
 await page.route('**/api/v2/notes',async route=>{started.resolve();await release.promise;await answer(route,{operationId:'note',status:'SUCCEEDED',result:{}});});
 await page.route('**/api/v2/operations/learning/note',route=>answer(route,{operationId:'note',status:'SUCCEEDED',result:{}}));
 await page.route('**/api/v2/services/learning/learning-records',route=>{writes.push(route.request().postDataJSON());return answer(route,{});});
 await page.goto('/courses/11');await page.getByRole('button',{name:/Video one/}).click();await expect(page.locator('video')).toBeVisible();
 await page.getByPlaceholder('记录私人笔记').fill('A note in flight');await page.getByRole('button',{name:'保存笔记',exact:true}).click();await started.promise;
 await page.locator('video').evaluate((video:HTMLVideoElement)=>{Object.defineProperty(video,'currentTime',{configurable:true,value:2});Object.defineProperty(video,'ended',{configurable:true,value:true});video.dispatchEvent(new Event('ended'));});
 await expect.poll(()=>writes.length).toBe(1);expect(writes[0]).toMatchObject({sectionId:'101',moment:2});release.resolve();await expect(page.getByText('笔记已保存',{exact:true})).toBeVisible();
});

test('changing discussion questions clears replies and disables posting after a read failure',async({page})=>{
 await learningCourse(page);const first={id:'q1',title:'Question one'},second={id:'q2',title:'Question two'};
 await page.route('**/api/v2/services/learning/questions/page?**',route=>answer(route,{list:[first,second],total:2}));
 await page.route('**/api/v2/services/learning/replies/page?**',route=>new URL(route.request().url()).searchParams.get('questionId')==='q1'?answer(route,{list:[{id:'r1',content:'Reply for question one'}],total:1}):route.fulfill({status:503,json:{code:503,msg:'Replies unavailable'}}));
 await page.goto('/courses/11');await page.getByRole('tab',{name:'课程互动',exact:true}).click();await page.getByRole('button',{name:'查看讨论',exact:true}).click();
 await page.getByRole('button',{name:'Question one',exact:true}).click();await expect(page.getByText('Reply for question one',{exact:true})).toBeVisible();await page.getByPlaceholder('写下回答').fill('Draft for the first question');
 await page.getByRole('button',{name:'Question two',exact:true}).click();await expect(page.getByText('Reply for question one',{exact:true})).toHaveCount(0);await expect(page.getByPlaceholder('写下回答')).toHaveValue('');
 await expect(page.getByText('Replies unavailable',{exact:true})).toBeVisible();await page.getByPlaceholder('写下回答').fill('New draft');await expect(page.getByRole('button',{name:'提交回答',exact:true})).toBeDisabled();
});
