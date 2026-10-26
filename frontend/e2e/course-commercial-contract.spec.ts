import {test,expect,type Page} from '@playwright/test';

// Component/HTTP contracts with synthetic identity, order, entitlement and cloud responses.
// This file is not real-cloud, supplier, capacity, or playback-accounting acceptance evidence.
const courseId='700',sectionId='701',lessonId='702',sessionId='12345678-1234-1234-1234-123456789abc';
const cloudId='23456789-2345-2345-2345-23456789abcd';
const lesson={id:lessonId,status:1,courseId,records:[],learnedSections:0};

async function contracts(page:Page,options:{readyAt?:number,cloud?:boolean,video?:Buffer}={}){
 const state={enrollments:0,rightsReads:0,signatures:0,starts:0,heartbeats:[] as Record<string,unknown>[],unexpected:[] as string[]};
 await page.addInitScript(()=>{const claims=btoa(JSON.stringify({user:{userId:'42',roleId:2}}));sessionStorage.setItem('school-token','contract.'+claims+'.fixture');sessionStorage.setItem('school-admin','false');sessionStorage.setItem('school-operations','[]');});
 await page.route('**/api/v2/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname,method=request.method();let data:unknown;
  if(path==='/api/v2/environment')data={mode:'REAL',payment:'CONFIGURED',storage:'CONFIGURED',video:'CONFIGURED',sms:'CONFIGURED',capabilities:{localPlayback:!options.cloud,cloudPlayback:!!options.cloud}};
  else if(path==='/api/v2/services/user/users/me')data={name:'Contract student'};
  else if(path==='/api/v2/services/course/course/'+courseId)data={id:courseId,name:'Contract free course',price:0,status:2,sectionNum:1,teachers:[],chapters:[{id:'703',name:'Contract chapter',sections:[{id:sectionId,index:1,name:'Contract video',type:2,mediaDuration:120}]}]};
  else if(path==='/api/v2/favorites/'+courseId)data={favorited:false};
  else if(path==='/api/v2/services/learning/learning-records/course/'+courseId){state.rightsReads++;data=state.rightsReads>=(options.readyAt??1)?lesson:null;}
  else if(path==='/api/v2/orders/free-courses/'+courseId&&method==='POST'){state.enrollments++;data={operationId:'fixture-enrollment',status:'SUCCEEDED',result:{orderId:'704'}};}
  else if(path==='/api/v2/operations/trade/fixture-enrollment')data={operationId:'fixture-enrollment',status:'SUCCEEDED',result:{orderId:'704'}};
  else if(path==='/api/v2/services/media/medias/signature/play'){state.signatures++;data={playUrl:'/api/v2/services/media/cloud-content/'+cloudId+'?ticket=fixture-'+state.signatures,expiresAt:String(Date.now()+(state.signatures===1?6000:600000))};}
  else if(path==='/api/v2/services/media/cloud-content/'+cloudId){const bytes=options.video??Buffer.alloc(0),range=request.headers().range?.match(/^bytes=(\d+)-(\d*)$/);if(range){const start=Number(range[1]),end=range[2]?Math.min(Number(range[2]),bytes.length-1):bytes.length-1;await route.fulfill({status:206,contentType:'video/webm',body:bytes.subarray(start,end+1),headers:{'Content-Range':`bytes ${start}-${end}/${bytes.length}`,'Accept-Ranges':'bytes','Cache-Control':'no-store'}});}else await route.fulfill({contentType:'video/webm',body:bytes,headers:{'Accept-Ranges':'bytes','Cache-Control':'no-store'}});return;}
  else if(path==='/api/v2/services/learning/learning-records/playback-sessions'&&method==='POST'){state.starts++;data={sessionId,watchedMillis:'0',creditedMillis:'0',requiredMillis:'108000',durationMillis:'120000',finished:false,completionSource:null,lastSequence:'0',active:true};}
  else if(path.endsWith('/playback-sessions/heartbeat')||path.endsWith('/playback-sessions/end')){const body=request.postDataJSON();state.heartbeats.push(body);data={sessionId,watchedMillis:'0',creditedMillis:'0',requiredMillis:'108000',durationMillis:'120000',finished:false,completionSource:null,lastSequence:String(body.sequence),active:!path.endsWith('/end')};}
  else if(path==='/api/v2/services/learning/learning-records'&&method==='POST')data=null;
  else {state.unexpected.push(method+' '+path);await route.fulfill({status:500,json:{code:500,msg:'Unhandled component contract route',data:null}});return;}
  await route.fulfill({json:{code:200,msg:'OK',data}});
 });
 return state;
}

test('contract: free enrollment reconciles late entitlement without refreshing or submitting another order',async({page},info)=>{
 test.skip(info.project.name!=='student');const state=await contracts(page,{readyAt:4});await page.goto('/courses/'+courseId);
 await page.getByRole('button',{name:'免费报名',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'正在准备学习权限'})).toBeVisible();
 await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeEnabled();expect(state.rightsReads).toBeGreaterThanOrEqual(4);expect(state.enrollments).toBe(1);expect(state.unexpected).toEqual([]);await expect(page).toHaveURL(new RegExp('/courses/'+courseId+'$'));
});

test('contract: bounded entitlement timeout offers query retry without resubmitting enrollment',async({page},info)=>{
 test.skip(info.project.name!=='student');const state=await contracts(page,{readyAt:Number.MAX_SAFE_INTEGER});await page.goto('/courses/'+courseId);await page.clock.install();
 await page.getByRole('button',{name:'免费报名',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'正在准备学习权限'})).toBeVisible();await expect.poll(()=>state.rightsReads).toBeGreaterThanOrEqual(3);
 await page.clock.fastForward(31000);await expect(page.getByRole('button',{name:'继续查询学习权限',exact:true})).toBeVisible();expect(state.enrollments).toBe(1);
 const before=state.rightsReads;await page.getByRole('button',{name:'继续查询学习权限',exact:true}).click();await expect.poll(()=>state.rightsReads).toBeGreaterThan(before);expect(state.enrollments).toBe(1);expect(state.unexpected).toEqual([]);
});

test('contract: cloud-only capability uses native video renewal and preserves the timing session',async({page},info)=>{
 test.skip(info.project.name!=='student');
 // Actual browser-generated bytes exercise native metadata/seek events; cloud authorization and timing responses stay mocked.
 const bytes=await page.evaluate(async()=>{const canvas=document.createElement('canvas');canvas.width=64;canvas.height=36;const brush=canvas.getContext('2d')!,stream=canvas.captureStream(10),recorder=new MediaRecorder(stream,{mimeType:'video/webm'}),parts:Blob[]=[];recorder.ondataavailable=event=>parts.push(event.data);const draw=setInterval(()=>{brush.fillStyle='rgb('+(Date.now()%255)+',100,50)';brush.fillRect(0,0,64,36);},100);const stopped=new Promise<void>(resolve=>recorder.onstop=()=>resolve());recorder.start();await new Promise(resolve=>setTimeout(resolve,2200));recorder.stop();await stopped;clearInterval(draw);stream.getTracks().forEach(track=>track.stop());return Array.from(new Uint8Array(await new Blob(parts,{type:'video/webm'}).arrayBuffer()));});
 const state=await contracts(page,{readyAt:1,cloud:true,video:Buffer.from(bytes)});await page.goto('/courses/'+courseId);await page.getByRole('button',{name:'继续学习',exact:true}).click();
 const video=page.locator('video.course-player');await expect(video).toBeVisible();await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.readyState)).toBeGreaterThanOrEqual(1);expect(state.starts).toBe(1);
 await video.evaluate(async(element:HTMLVideoElement)=>{element.muted=true;element.playbackRate=2;await element.play();});await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.currentTime)).toBeGreaterThan(.5);await video.evaluate((element:HTMLVideoElement)=>element.pause());const baseline=await video.evaluate((element:HTMLVideoElement)=>element.currentTime);expect(baseline).toBeGreaterThan(.5);await expect.poll(()=>state.heartbeats.some(body=>body.state==='PAUSED')).toBe(true);const original=await video.getAttribute('src');
 await expect.poll(()=>video.getAttribute('src'),{timeout:12000}).not.toBe(original);await expect(page.getByRole('status').filter({hasText:'正在更新播放地址'})).toHaveCount(0);
 expect(await video.evaluate((element:HTMLVideoElement)=>element.paused)).toBe(true);expect(await video.evaluate((element:HTMLVideoElement)=>element.playbackRate)).toBe(2);expect(Math.abs(await video.evaluate((element:HTMLVideoElement)=>element.currentTime)-baseline)).toBeLessThan(.15);
 expect(state.signatures).toBeGreaterThanOrEqual(2);expect(state.starts).toBe(1);expect(state.heartbeats.every(body=>body.sessionId===sessionId&&typeof body.sequence==='number')).toBe(true);expect(state.heartbeats.some(body=>body.state==='BUFFERING')).toBe(true);expect(state.unexpected).toEqual([]);
});
