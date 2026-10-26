// Explicit acceptance: TJ_MEDIA_RENEWAL_TEST=1, a fresh tianji-recovery-* backend
// with signature TTL 6-12 seconds, then --project=student --grep 'real short-ticket'.
// Registration is explicit: ordinary CI does not report this dedicated scenario as skipped.
import {test,expect,type Page} from '@playwright/test';
import {accounts,fixture,home,login,api} from './helpers';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';

async function completed(page:Page,service:string,operation:any){
 let result=operation;await expect.poll(async()=>{result=await api(page,'GET','/operations/'+service+'/'+operation.operationId);return result.status;},{timeout:30000}).not.toBe('PENDING');
 expect(result.status).toBe('SUCCEEDED');return result;
}
function gate(){let resolve!:()=>void;const promise=new Promise<void>(done=>{resolve=done;});return {promise,resolve};}

if(process.env.TJ_MEDIA_RENEWAL_TEST==='1')test('real short-ticket playback renews without changing timing sessions and retains pause, rate and rights',async({page,browser},info)=>{
 test.setTimeout(180000);expect(info.project.name).toBe('student');expect(process.env.TJ_COMPACT_PROJECT).toMatch(/^tianji-recovery-/);
 expect(process.env.TJ_STUDENT_URL).not.toContain(':24500');expect(process.env.TJ_ADMIN_URL).not.toContain(':24501');
 const context=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL}),admin=await context.newPage();
 let release:ReturnType<typeof gate>|undefined;
 try{
  await login(admin,'admin','/media');const environment=await api(admin,'GET','/environment');expect(environment.mode).toBe('SIMULATED');
  const bytes=await admin.evaluate(async()=>{
   const canvas=document.createElement('canvas');canvas.width=160;canvas.height=90;const brush=canvas.getContext('2d')!,stream=canvas.captureStream(10),recorder=new MediaRecorder(stream,{mimeType:'video/webm'}),parts:Blob[]=[];
   recorder.ondataavailable=event=>parts.push(event.data);const frames=setInterval(()=>{brush.fillStyle=`hsl(${Date.now()%360},70%,50%)`;brush.fillRect(0,0,160,90);},100);
   const stopped=new Promise<void>(done=>recorder.onstop=()=>done());recorder.start();await new Promise(done=>setTimeout(done,18100));recorder.stop();await stopped;clearInterval(frames);stream.getTracks().forEach(track=>track.stop());
   return Array.from(new Uint8Array(await new Blob(parts,{type:'video/webm'}).arrayBuffer()));
  });
  await admin.getByLabel('选择本地视频').setInputFiles({name:'renewal-'+fixture.marker+'.webm',mimeType:'video/webm',buffer:Buffer.from(bytes)});
  await admin.getByRole('button',{name:'上传并登记',exact:true}).click();await expect(admin.getByText('视频已持久化',{exact:true})).toBeVisible();await expect(admin.getByText(/已识别视频时长/)).toBeVisible();
  const media=await admin.evaluate(()=>JSON.parse(sessionStorage.getItem('school-operations')||'[]').find((item:any)=>item.label==='登记本地视频').operation.result.id);
  execFileSync(process.env.TJ_PYTHON||'python',[process.env.TJ_MEDIA_FIXTURE_SCRIPT||'../deploy/compact/attach_browser_media.py',String(media)],{stdio:'pipe'});
  await login(page,'student','/courses/'+fixture.course);
  const quote=await api(page,'GET','/orders/confirmation?courseIds='+fixture.course),orderId=String(quote.orderId);
  await completed(page,'trade',await api(page,'POST','/orders',{orderId,courseIds:[fixture.course],couponIds:[]},crypto.randomUUID()));
  const channels=await api(page,'GET','/services/trade/pay/channels'),link=await api(page,'POST','/services/trade/pay/order',{orderId,payChannelCode:channels[0].channelCode});
  expect(link).toMatch(/^\/simulate-payment\//);await page.goto(link);await page.getByRole('button',{name:'确认模拟支付成功',exact:true}).click();
  await expect.poll(async()=>(await api(page,'GET','/orders/'+orderId)).status,{timeout:30000}).toBe(2);
  await expect.poll(async()=>(await api(page,'GET','/services/learning/learning-records/course/'+fixture.course))?.id,{timeout:30000}).toBeTruthy();
  await page.goto('/courses/'+fixture.course);let starts=0;page.on('request',request=>{if(request.method()==='POST'&&new URL(request.url()).pathname==='/api/v2/services/learning/learning-records/playback-sessions')starts++;});
  const firstSignature=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v2/services/media/medias/signature/play');
  const firstSession=page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname==='/api/v2/services/learning/learning-records/playback-sessions');
  await page.getByRole('button',{name:/Browser video/}).click();const initial=(await (await firstSignature).json()).data,sessionId=(await (await firstSession).json()).data.sessionId;
  const remaining=Number(initial.expiresAt)-Date.now();expect(remaining).toBeGreaterThan(1500);expect(remaining).toBeLessThanOrEqual(12000);
  const video=page.locator('video');await expect(video).toBeVisible();
  await video.evaluate(async(element:HTMLVideoElement)=>{element.muted=true;element.playbackRate=2;await element.play();});
  await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.currentTime)).toBeGreaterThan(1.5);
  const paused=page.waitForResponse(response=>response.url().endsWith('/playback-sessions/heartbeat')&&response.request().postDataJSON()?.state==='PAUSED'&&response.ok());
  await video.evaluate((element:HTMLVideoElement)=>element.pause());await paused;
  const position=await video.evaluate((element:HTMLVideoElement)=>element.currentTime),before=await api(page,'GET','/services/learning/learning-records/course/'+fixture.course);
  const record=(value:any)=>value.records.find((row:any)=>String(row.sectionId)===fixture.video);
  const oldSrc=await video.getAttribute('src');await expect.poll(()=>video.getAttribute('src'),{timeout:15000}).not.toBe(oldSrc);
  await expect(page.getByRole('status').filter({hasText:'更新播放地址'})).toHaveCount(0);
  expect(await video.evaluate((element:HTMLVideoElement)=>element.paused)).toBe(true);expect(await video.evaluate((element:HTMLVideoElement)=>element.playbackRate)).toBe(2);
  expect(Math.abs(await video.evaluate((element:HTMLVideoElement)=>element.currentTime)-position)).toBeLessThan(.25);
  const after=await api(page,'GET','/services/learning/learning-records/course/'+fixture.course);expect(record(after).creditedMillis).toBe(record(before).creditedMillis);expect(starts).toBe(1);
  const pausedSrc=await video.getAttribute('src');await video.evaluate(async(element:HTMLVideoElement)=>{await element.play();});
  await expect.poll(()=>video.getAttribute('src'),{timeout:15000}).not.toBe(pausedSrc);await expect(page.getByRole('status').filter({hasText:'更新播放地址'})).toHaveCount(0);
  expect(await video.evaluate((element:HTMLVideoElement)=>element.paused)).toBe(false);expect(await video.evaluate((element:HTMLVideoElement)=>element.playbackRate)).toBe(2);
  const afterPlaying=await video.evaluate((element:HTMLVideoElement)=>element.currentTime);expect(afterPlaying).toBeGreaterThan(position);expect(starts).toBe(1);
  await video.evaluate((element:HTMLVideoElement)=>element.pause());
  // A real invalid signature causes the browser media error; no API response is mocked.
  const deniedBytes=page.waitForResponse(response=>response.url().includes('/local-content/')&&response.status()===403);
  await video.evaluate((element:HTMLVideoElement)=>{const expired=new URL(element.src);expired.searchParams.set('expires','0');element.src=expired.toString();element.load();});await deniedBytes;
  await expect(page.getByRole('button',{name:'重新加载视频',exact:true})).toBeVisible();
  await page.evaluate(()=>sessionStorage.setItem('school-token','expired-renewal-access'));
  await page.getByRole('button',{name:'重新加载视频',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'更新播放地址'})).toHaveCount(0);
  await expect(video).toBeVisible();expect(starts).toBe(1);expect(Math.abs(await video.evaluate((element:HTMLVideoElement)=>element.currentTime)-afterPlaying)).toBeLessThan(.5);
  // Delay an outgoing renewal, then switch sections. Continuing the genuine request
  // after cancellation must not install its old ticket on the new section.
  const started=gate();release=gate();
  await page.route('**/api/v2/services/media/medias/signature/play?sectionId='+fixture.video,async route=>{started.resolve();await release!.promise;await route.continue().catch(error=>{if(!route.request().failure()&&!page.isClosed())throw error;});});
  await started.promise;
  await page.getByRole('button',{name:/Browser exam/}).click();release.resolve();await expect(page.locator('.lesson-workspace h2')).toHaveText('Browser exam');await expect(page.locator('video')).toHaveCount(0);expect(starts).toBe(1);
  await page.unroute('**/api/v2/services/media/medias/signature/play?sectionId='+fixture.video);
  const purchase=await api(page,'GET','/orders/'+orderId);
  await completed(page,'trade',await api(page,'POST','/services/trade/refund-apply',{orderDetailId:purchase.details[0].id,refundReason:'Short-ticket acceptance withdrawal',questionDesc:'Synthetic playback acceptance'},crypto.randomUUID()));
  const refunds=await api(admin,'GET','/admin/trade/refund-apply/page?pageNo=1&pageSize=20'),refund=refunds.list.find((row:any)=>String(row.orderId)===orderId);expect(refund).toBeTruthy();
  await completed(admin,'trade',await api(admin,'PUT','/admin/trade/refund-apply/approval',{id:refund.id,approveType:1,approveOpinion:'Synthetic playback acceptance'},crypto.randomUUID()));
  const access=await page.evaluate(()=>sessionStorage.getItem('school-token'));
  await expect.poll(async()=>(await page.request.get('/api/v2/services/media/medias/signature/play?sectionId='+fixture.video,{headers:{Authorization:'Bearer '+access}})).status(),{timeout:5000}).toBe(403);
  fs.writeFileSync(home+'/media-renewal-result.json',JSON.stringify({status:'PASSED',realBackend:true,shortTicketMillis:remaining,sessionId,sessionStarts:starts,pausedPosition:position,playingPosition:afterPlaying,pausePreserved:true,ratePreserved:2,creditUnchangedWhilePaused:true,realMediaFailureRecovered:true,sameAccountRefreshPreservedSession:true,staleRequestCancelledOnSectionChange:true,refundedSignatureStatus:403},null,2));
 }finally{release?.resolve();await context.close();}
});
