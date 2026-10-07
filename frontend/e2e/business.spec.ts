import {test,expect,type Page} from '@playwright/test';
import fs from 'node:fs';import {execFileSync} from 'node:child_process';
const accounts=JSON.parse(fs.readFileSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/accounts.json','utf8'));
const fixture=JSON.parse(fs.readFileSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/browser-fixture.json','utf8'));
async function login(page:Page,role:string,path:string){await page.goto(path);await page.locator('input[autocomplete="username"]').fill(accounts[role].username);await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);await page.getByRole('button',{name:role==='student'?'登录':'登录管理端',exact:true}).click();await expect(page.locator('.sidebar')).toBeVisible();}
async function api(page:Page,path:string,method='GET',data?:unknown){const token=await page.evaluate(()=>sessionStorage.getItem('school-token'));const r=await page.request.fetch('/api/v2'+path,{method,data,headers:{Authorization:'Bearer '+token,'Idempotency-Key':crypto.randomUUID()}});const body=await r.json();expect(r.ok(),JSON.stringify(body)).toBeTruthy();expect(body.code).toBe(200);return body.data;}
async function fill(page:Page,label:string,value:string){await page.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:label})}).first().locator('input').fill(value);}
test('browser purchase, real local video, discussions, exam grading and targeted refund',async({page,browser},info)=>{
 test.skip(info.project.name!=='student');test.setTimeout(180000);
 const adminContext=await browser.newContext({baseURL:'http://127.0.0.1:23501'}),admin=await adminContext.newPage();
 const teacherContext=await browser.newContext({baseURL:'http://127.0.0.1:23501'}),teacher=await teacherContext.newPage();
 try{
  await login(admin,'admin','/media');
  const bytes=await admin.evaluate(async()=>{
   const canvas=document.createElement('canvas');canvas.width=160;canvas.height=90;const ctx=canvas.getContext('2d')!;ctx.fillStyle='#2563eb';ctx.fillRect(0,0,160,90);
   const stream=canvas.captureStream(10),recorder=new MediaRecorder(stream,{mimeType:'video/webm'}),chunks:Blob[]=[];
   recorder.ondataavailable=e=>chunks.push(e.data);const frames=setInterval(()=>{ctx.fillStyle=`hsl(${Date.now()%360},70%,50%)`;ctx.fillRect(0,0,160,90);},100);
   const done=new Promise<void>(resolve=>recorder.onstop=()=>resolve());recorder.start();await new Promise(resolve=>setTimeout(resolve,2100));recorder.stop();await done;clearInterval(frames);stream.getTracks().forEach(t=>t.stop());
   return Array.from(new Uint8Array(await new Blob(chunks,{type:'video/webm'}).arrayBuffer()));
  });
  await admin.getByLabel('选择本地视频').setInputFiles({name:'browser-'+fixture.marker+'.webm',mimeType:'video/webm',buffer:Buffer.from(bytes)});
  await admin.locator('article').filter({has:admin.getByRole('heading',{name:'上传视频',exact:true})}).getByRole('spinbutton').fill('2');
  await admin.getByRole('button',{name:'上传并登记',exact:true}).click();await expect(admin.getByText('视频已持久化',{exact:true})).toBeVisible();
  const media=await admin.evaluate(()=>JSON.parse(sessionStorage.getItem('school-operations')||'[]').find((x:any)=>x.label==='登记本地视频').operation.result.id);
  execFileSync('E:/download/Anaconda/python.exe',['../deploy/acceptance/attach_browser_media.py',String(media)],{stdio:'pipe'});
  await admin.goto('/exams');await fill(admin,'课程 ID',fixture.course);await fill(admin,'考试小节 ID',fixture.exam);await fill(admin,'题目 ID，逗号分隔',fixture.objective+','+fixture.subjective);await fill(admin,'评分教师 ID，逗号分隔',accounts.teacher.id);
  await admin.getByRole('button',{name:'发布不可变试卷版本'}).click();await expect(admin.getByText('试卷版本已发布',{exact:true})).toBeVisible();
  await login(page,'student','/courses/'+fixture.course);await page.getByRole('button',{name:'加入购物车',exact:true}).click();await expect(page.getByText('已加入购物车',{exact:true})).toBeVisible();
  await page.goto('/cart');const cart=page.locator('.el-table__row').filter({hasText:fixture.name});await expect(cart).toBeVisible();await cart.locator('.el-checkbox').click();
  await page.getByRole('button',{name:/确认选购 1 门课程/}).click();await page.getByRole('button',{name:'提交订单',exact:true}).click();await expect(page.getByRole('dialog',{name:'订单创建成功'})).toBeVisible();await page.getByRole('button',{name:'立即付款',exact:true}).click();await expect(page.getByRole('dialog',{name:'完成付款'})).toBeVisible();await expect(page.getByRole('link',{name:'打开本地模拟支付 →',exact:true})).toBeVisible();await page.getByRole('dialog',{name:'完成付款',exact:true}).getByRole('button',{name:'稍后付款',exact:true}).click();
  const order=page.locator('.el-table__row').first();const orderId=(await order.locator('.cell').nth(1).innerText()).trim();expect(orderId).toMatch(/^\d+$/);
  await order.getByRole('button',{name:'取消',exact:true}).click();await expect(page.getByRole('dialog',{name:'取消这笔订单？'})).toBeVisible();await page.getByRole('button',{name:'继续保留',exact:true}).click();expect((await api(page,'/orders/'+orderId)).status).toBe(1);
  await order.getByRole('button',{name:'支付',exact:true}).click();await expect(page.getByRole('link',{name:'打开本地模拟支付 →',exact:true})).toBeVisible();
  const channels=await api(page,'/services/trade/pay/channels'),links=await Promise.all(Array.from({length:10},()=>api(page,'/services/trade/pay/order','POST',{orderId,payChannelCode:channels[0].channelCode})));expect(new Set(links).size).toBe(1);
  await page.getByRole('link',{name:'打开本地模拟支付 →'}).click();await page.getByRole('button',{name:'确认模拟支付成功'}).click();await expect(page.getByText('模拟器已保存支付事实，业务订单正在通过通知和对账更新',{exact:true})).toBeVisible();
  await expect.poll(async()=>(await api(page,'/orders/'+orderId)).status,{timeout:30000}).toBe(2);
  await expect.poll(async()=>(await api(page,'/services/learning/learning-records/course/'+fixture.course))?.id,{timeout:30000}).toBeTruthy();
  await page.goto('/courses/'+fixture.course);await expect(page.getByRole('heading',{name:fixture.name,exact:true})).toBeVisible();await page.getByRole('button',{name:/Browser video/}).click();
  const video=page.locator('video');await expect(video).toBeVisible();await video.evaluate(async(element:HTMLVideoElement)=>{element.muted=true;await element.play();});
  await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.ended),{timeout:15000}).toBe(true);
  await expect.poll(async()=>(await api(page,'/services/learning/learning-records/course/'+fixture.course)).records?.find((r:any)=>r.sectionId===fixture.video)?.finished,{timeout:15000}).toBe(true);
  const note='Video note '+fixture.marker;await page.getByPlaceholder('记录私人笔记').fill(note);await page.getByRole('button',{name:'保存笔记',exact:true}).click();await expect(page.getByText('笔记已保存',{exact:true})).toBeVisible();
  await page.getByPlaceholder('问题标题').fill('Question '+fixture.marker);await page.getByPlaceholder('描述问题').fill('A persisted browser discussion');await page.getByRole('button',{name:'在当前小节提问'}).click();await page.getByRole('button',{name:'Question '+fixture.marker,exact:true}).click();await page.getByPlaceholder('写下回答').fill('A persisted answer');await page.getByRole('button',{name:'提交回答',exact:true}).click();await expect(page.getByText('A persisted answer',{exact:true})).toBeVisible();await page.getByRole('button',{name:/^赞 /}).click();await expect(page.getByRole('button',{name:/^已赞 /})).toHaveAttribute('aria-pressed','true');
  await page.reload();await page.getByRole('button',{name:/Browser video/}).click();await page.getByRole('button',{name:'查看讨论',exact:true}).click();await page.getByRole('button',{name:'Question '+fixture.marker,exact:true}).click();await expect(page.getByRole('button',{name:/^已赞 /})).toHaveAttribute('aria-pressed','true');await page.getByRole('button',{name:/^已赞 /}).click();await expect(page.getByRole('button',{name:/^赞 /})).toHaveAttribute('aria-pressed','false');
  fs.mkdirSync((process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/ux-screenshots',{recursive:true});await page.screenshot({path:(process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/ux-screenshots/course-learning.png',fullPage:true});
  await page.goto('/notes');await expect(page.getByText(note,{exact:true})).toBeVisible();await page.reload();await expect(page.getByText(note,{exact:true})).toBeVisible();
  await page.goto('/exams?courseId='+fixture.course);await page.getByRole('button',{name:'开始 / 继续考试'}).first().click();await expect(page.locator('textarea')).toHaveCount(1);await page.locator('.answer-options .el-checkbox').filter({hasText:'A. A'}).click();await page.locator('.answer-options .el-checkbox').filter({hasText:'B. B'}).click();await page.locator('textarea').fill('Explain reliable transactions');
  await page.reload();await expect(page.locator('textarea')).toHaveValue('Explain reliable transactions');await expect(page.getByRole('checkbox',{name:'A. A',exact:true})).toBeChecked();await expect(page.getByRole('checkbox',{name:'B. B',exact:true})).toBeChecked();
  await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)).toBe(false);await page.screenshot({path:(process.env.TJ_UI_RUNTIME_HOME||'../deploy/acceptance/.local')+'/ux-screenshots/exam-390.png',fullPage:true});await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'提交全部答案'}).click();await page.getByRole('button',{name:'继续检查',exact:true}).click();await expect(page.getByText('答题中',{exact:true})).toBeVisible();await page.getByRole('button',{name:'提交全部答案'}).click();await page.getByRole('button',{name:'确认提交答卷',exact:true}).click();await expect(page.getByText('等待教师评分',{exact:true})).toBeVisible();
  const attempt=await page.evaluate(()=>sessionStorage.getItem('school-active-exam'));
  await login(teacher,'teacher','/exams');await teacher.getByRole('button',{name:'刷新待评分列表'}).click();await teacher.locator('.el-table__row').filter({hasText:attempt!}).getByRole('button',{name:'查看答卷'}).click();await teacher.getByRole('spinbutton').fill('10');await teacher.getByPlaceholder('评分反馈').fill('Reviewed browser answer');await teacher.getByRole('button',{name:'提交评分'}).click();await expect(teacher.getByText('已评分 10 · Reviewed browser answer',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'刷新评分状态'}).click();await expect(page.locator('.exam-result')).toContainText('20');await expect(page.locator('.exam-result')).toContainText('已通过');
  await expect.poll(async()=>(await api(page,'/services/learning/learning-records/course/'+fixture.course)).records.filter((r:any)=>r.finished).length,{timeout:30000}).toBe(2);
  await page.goto('/orders');await page.locator('.el-table__row').filter({hasText:orderId}).getByRole('button',{name:'详情'}).click();await page.getByRole('button',{name:'申请退款'}).click();await expect(page.getByRole('dialog',{name:'申请课程退款'})).toBeVisible();await expect(page.getByRole('button',{name:'提交退款申请',exact:true})).toBeDisabled();await page.getByPlaceholder('请说明退款原因').fill('浏览器验收退款，验证学习历史保留');await page.getByRole('button',{name:'提交退款申请',exact:true}).click();await expect(page.getByText('退款申请已提交',{exact:true})).toBeVisible();
  await admin.goto('/refunds');const refund=admin.locator('.el-table__row').filter({hasText:orderId});await refund.getByRole('button',{name:'同意退款'}).click();await admin.getByRole('button',{name:'确定',exact:true}).click();await expect(admin.getByText('同意退款已完成',{exact:true})).toBeVisible();
  await expect.poll(async()=>(await api(page,'/orders/'+orderId)).status,{timeout:60000}).toBe(7);
  await page.goto('/courses/'+fixture.course);await page.getByRole('button',{name:/Browser video/}).click();await expect(page.getByRole('alert').filter({hasText:/收费视频|课程|权限|免费/})).toBeVisible();await expect(page.locator('video')).toHaveCount(0);
  await page.goto('/notes');await expect(page.getByText(note,{exact:true})).toBeVisible();
 }finally{await adminContext.close();await teacherContext.close();}
});
