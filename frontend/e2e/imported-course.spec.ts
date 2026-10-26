// Dedicated real-backend acceptance. Register only with a completed isolated
// three-file TJ_IMPORTED_COURSE_MANIFEST; ordinary CI has no skipped placeholder.
import {test,expect,type Page} from '@playwright/test';
import {accounts,home,login,api} from './helpers';
import fs from 'node:fs';
import path from 'node:path';

if(process.env.TJ_IMPORTED_COURSE_MANIFEST)test('real imported course is discoverable and preserves learning across roles',async({page,browser},info)=>{
 test.setTimeout(180000);
 const manifest=JSON.parse(fs.readFileSync(process.env.TJ_IMPORTED_COURSE_MANIFEST!,'utf8'));
 expect(info.project.name).toBe('student');expect(manifest.owner).toBe('tianji-offline-course-import-v1');expect(manifest.status).toBe('VERIFIED');expect(manifest.inventory.sample).toBe(true);
 expect(manifest.deployment.project).toMatch(/^tianji-recovery-/);expect(manifest.deployment.project).toBe(process.env.TJ_COMPACT_PROJECT);
 for(const origin of [process.env.TJ_STUDENT_URL,process.env.TJ_ADMIN_URL]){expect(origin).toBeTruthy();const url=new URL(origin!);expect(['127.0.0.1','localhost']).toContain(url.hostname);expect(['24500','24501']).not.toContain(url.port);}
 const files=manifest.inventory.files as any[];expect(files).toHaveLength(3);const days=[...new Set(files.map(row=>row.day))];expect(days).toHaveLength(2);
 const course=String(manifest.state.courseId),name=String(manifest.options.name),first=files[0],section=String(manifest.state.catalogueIds[first.relativePath]);
 expect(String(manifest.state.teacherId)).toBe(String(accounts.teacher.id));for(const entry of files){expect(manifest.state.media[entry.relativePath].fileId).toMatch(/\.mp4$/);expect(manifest.state.media[entry.relativePath].duration).toBeGreaterThan(10);}
 const teacherContext=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL}),adminContext=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL});
 const teacher=await teacherContext.newPage(),admin=await adminContext.newPage();
 const rawStatus=async(target:Page,path:string)=>{const token=await target.evaluate(()=>sessionStorage.getItem('school-token'));return (await target.request.get('/api/v2'+path,{headers:token?{Authorization:'Bearer '+token}:{}})).status();};
 const readLearning=()=>api(page,'GET','/services/learning/learning-records/course/'+course);
 const matchingRecord=(lesson:any)=>lesson.records.find((row:any)=>String(row.sectionId)===section);
 try{
  // Guest uses the actual indexed search and detail page, then its login return.
  await page.goto('/');await page.getByRole('textbox',{name:'搜索课程',exact:true}).fill(name);await page.getByRole('button',{name:'搜索',exact:true}).click();
  const card=page.locator('.course-tile').filter({has:page.locator('a[href="/courses/'+course+'"]')});await expect(card).toHaveCount(1);await card.getByRole('link',{name:'查看课程：'+name,exact:true}).click();
  await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();await expect(page.locator('.syllabus h3')).toHaveCount(days.length);await expect(page.locator('.lesson-item')).toHaveCount(files.length);
  for(const entry of files)await expect(page.locator('.lesson-item').filter({hasText:entry.sectionTitle})).toHaveCount(1);
  expect(await rawStatus(page,'/services/media/medias/signature/play?sectionId='+section)).toBe(401);
  await page.getByRole('button',{name:'免费报名',exact:true}).click();await expect(page).toHaveURL(new RegExp('/login\\?redirect='));expect(new URL(page.url()).searchParams.get('redirect')).toBe('/courses/'+course);
  await page.locator('input[autocomplete="username"]').fill(accounts.student.username);await page.locator('input[autocomplete="current-password"]').fill(accounts.student.password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).toHaveURL(new RegExp('/courses/'+course+'$'));
  await page.getByRole('button',{name:'免费报名',exact:true}).click();await expect.poll(async()=>(await readLearning())?.id,{timeout:30000}).toBeTruthy();
  await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeVisible();
  const signature=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v2/services/media/medias/signature/play');
  await page.locator('.lesson-item').filter({hasText:first.sectionTitle}).click();const ticket=(await (await signature).json()).data;expect(Number(ticket.expiresAt)-Date.now()).toBeLessThanOrEqual(12000);
  const video=page.locator('video');await expect(video).toBeVisible();await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.readyState)).toBeGreaterThanOrEqual(2);
  const mediaDuration=await video.evaluate((element:HTMLVideoElement)=>element.duration);expect(Number.isFinite(mediaDuration)).toBe(true);expect(Math.abs(mediaDuration-manifest.state.media[first.relativePath].duration)).toBeLessThan(1);
  // Actual H264 playback at normal speed, with no advancing of the media clock.
  await video.evaluate(async(element:HTMLVideoElement)=>{element.muted=true;element.playbackRate=1;await element.play();});
  await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.currentTime),{timeout:20000}).toBeGreaterThan(5);
  const paused=page.waitForResponse(response=>response.url().endsWith('/playback-sessions/heartbeat')&&response.request().postDataJSON()?.state==='PAUSED'&&response.ok());await video.evaluate((element:HTMLVideoElement)=>element.pause());await paused;
  const position=await video.evaluate((element:HTMLVideoElement)=>element.currentTime);const learned=await readLearning(),credit=Number(matchingRecord(learned).creditedMillis);expect(credit).toBeGreaterThan(1000);
  const pausedSrc=await video.getAttribute('src');await expect.poll(()=>video.getAttribute('src'),{timeout:15000}).not.toBe(pausedSrc);await expect(page.getByRole('status').filter({hasText:'更新播放地址'})).toHaveCount(0);
  expect(await video.evaluate((element:HTMLVideoElement)=>element.paused)).toBe(true);expect(Math.abs(await video.evaluate((element:HTMLVideoElement)=>element.currentTime)-position)).toBeLessThan(.3);expect(Number(matchingRecord(await readLearning()).creditedMillis)).toBe(credit);
  // A real native seek only updates the resume location and establishes a baseline.
  const seekTarget=Math.min(mediaDuration-1,position+20);expect(seekTarget-position).toBeGreaterThan(2);
  const seeked=page.waitForResponse(response=>response.url().endsWith('/playback-sessions/heartbeat')&&response.request().postDataJSON()?.state==='PAUSED'&&response.ok());
  await video.evaluate((element:HTMLVideoElement,target:number)=>{element.currentTime=target;},seekTarget);await seeked;
  expect(Number(matchingRecord(await readLearning()).creditedMillis)).toBe(credit);await expect.poll(async()=>Number(matchingRecord(await readLearning()).moment),{timeout:15000}).toBe(Math.floor(seekTarget));
  await page.reload();await page.locator('.lesson-item').filter({hasText:first.sectionTitle}).click();await expect(video).toBeVisible();await expect.poll(()=>video.evaluate((element:HTMLVideoElement,target:number)=>Math.abs(element.currentTime-Math.floor(target)),seekTarget),{timeout:10000}).toBeLessThan(.5);expect(Number(matchingRecord(await readLearning()).creditedMillis)).toBe(credit);
  expect(await rawStatus(page,'/teacher/learning-hub?courseId='+course+'&pageNo=1&pageSize=20')).toBe(403);
  // Teacher selects the newly imported assignment in the real UI and sees the
  // same accumulated server receipt; unrelated courses remain unauthorized.
  await login(teacher,'teacher','/teaching');await teacher.getByRole('combobox',{name:'教学课程',exact:true}).click();
  await teacher.getByRole('option',{name,exact:true}).click();await teacher.getByRole('textbox',{name:'筛选学生编号',exact:true}).fill(String(accounts.student.id));
  const filtered=teacher.waitForResponse(response=>{const url=new URL(response.url());return url.pathname==='/api/v2/teacher/learning-hub'&&url.searchParams.get('courseId')===course&&url.searchParams.get('userId')===String(accounts.student.id)&&response.ok();});await teacher.getByRole('button',{name:'查看课程学情',exact:true}).click();const hub=(await (await filtered).json()).data;
  expect(hub.list).toHaveLength(1);expect(Number(hub.list[0].creditedMillis)).toBe(credit);await expect(teacher.getByRole('region',{name:'课程学情'}).getByRole('heading',{name,exact:true})).toBeVisible();
  const seconds=Math.floor(credit/1000);await expect(teacher.locator('section[aria-label="课程学情"] .el-table__row').filter({hasText:Math.floor(seconds/60)+' 分 '+seconds%60+' 秒'})).toHaveCount(1);
  // Published course UI deliberately exposes the list and media records; its
  // draft editor is not a published read-only catalogue viewer.
  await login(admin,'admin','/courses');await admin.getByRole('textbox',{name:'按课程名称搜索',exact:true}).fill(name);await admin.getByRole('button',{name:'查询',exact:true}).click();const courseRow=admin.locator('.el-table__row').filter({hasText:course});await expect(courseRow).toHaveCount(1);await expect(courseRow).toContainText(name);await expect(courseRow).toContainText('已上架');
  const tree=await api(admin,'GET','/admin/course/courses/catas/'+course+'?see=true&withPractice=true');expect(tree).toHaveLength(days.length);const catalogue=tree.flatMap((chapter:any)=>chapter.sections||[]);expect(catalogue).toHaveLength(files.length);
  for(const entry of files){const actual=catalogue.find((row:any)=>String(row.id)===String(manifest.state.catalogueIds[entry.relativePath]));expect(actual.name).toBe(entry.sectionTitle);expect(String(actual.mediaId)).toBe(String(manifest.state.media[entry.relativePath].id));}
  const assigned=await api(teacher,'GET','/teacher/courses?pageNo=1&pageSize=100'),all=await api(admin,'GET','/admin/course/courses/page?pageNo=1&pageSize=100');const allowed=new Set(assigned.list.map((row:any)=>String(row.id)));
  expect(allowed.has(course)).toBe(true);const unrelated=all.list.find((row:any)=>!allowed.has(String(row.id)));expect(unrelated,'An existing non-assigned course is required for the authorization assertion').toBeTruthy();expect(await rawStatus(teacher,'/teacher/learning-hub?courseId='+unrelated.id+'&pageNo=1&pageSize=20')).toBe(403);
  await admin.goto('/media');for(const entry of files){await admin.getByRole('textbox',{name:'按文件名搜索',exact:true}).fill(entry.filename);await admin.getByRole('button',{name:'查询',exact:true}).click();await expect(admin.locator('.el-table__row').filter({hasText:String(manifest.state.media[entry.relativePath].id)})).toHaveCount(1);}
  // This is bounded evidence for a >200 MiB source: genuine metadata, a decoded
  // first frame and a matching 64-byte HTTP range, not full-video decoding or
  // a 35-hour continuous-playback acceptance. Keep playback paused throughout.
  const large=files.find(entry=>Number(entry.size)>200*1024*1024);expect(large,'A >200 MiB source is required for the large-media assertion').toBeTruthy();
  const largeSection=String(manifest.state.catalogueIds[large.relativePath]);await page.locator('.lesson-item').filter({hasText:large.sectionTitle}).click();
  await expect(video).toBeVisible();await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.readyState),{timeout:20000}).toBeGreaterThanOrEqual(2);await expect.poll(()=>video.evaluate((element:HTMLVideoElement)=>element.videoWidth),{timeout:20000}).toBeGreaterThan(0);
  expect(await video.evaluate((element:HTMLVideoElement)=>element.paused)).toBe(true);const largeDuration=await video.evaluate((element:HTMLVideoElement)=>element.duration);expect(Number.isFinite(largeDuration)).toBe(true);expect(Math.abs(largeDuration-manifest.state.media[large.relativePath].duration)).toBeLessThan(1);
  const largeTicket=await api(page,'GET','/services/media/medias/signature/play?sectionId='+largeSection),range=await page.request.get(new URL(largeTicket.playUrl,page.url()).toString(),{headers:{Range:'bytes=0-63'}});
  expect(range.status()).toBe(206);expect(range.headers()['content-range']).toBe('bytes 0-63/'+large.size);const bytes=await range.body();expect(bytes).toHaveLength(64);
  const source=await fs.promises.open(path.resolve(manifest.inventory.sourceRoot,large.relativePath),'r');const original=Buffer.alloc(64);try{const read=await source.read(original,0,64,0);expect(read.bytesRead).toBe(64);}finally{await source.close();}expect(bytes.equals(original)).toBe(true);
  const finalLearning=await readLearning();expect(Number(finalLearning.records.find((row:any)=>String(row.sectionId)===largeSection).creditedMillis)).toBe(0);expect(Number(matchingRecord(finalLearning).creditedMillis)).toBe(credit);
  fs.writeFileSync(home+'/imported-course-result.json',JSON.stringify({status:'PASSED',realBackend:true,courseId:course,chapters:days.length,sections:files.length,guestSearchAndLoginReturn:true,freeEnrollment:true,realH264Playback:true,pausedRenewalPreserved:true,seekDoesNotCredit:true,refreshResumePosition:Math.floor(seekTarget),creditedMillis:credit,teacherAuthorizedCourse:true,teacherUnassignedCourseStatus:403,adminPublishedCatalogueCheckedViaRealApi:true,adminMediaRecordsVisible:true,largeFileRangeAndFirstFrame:true,largeFileEvidenceScope:'64-byte range and first frame; not full decoding or long-run playback'},null,2));
 }finally{await teacherContext.close();await adminContext.close();}
});
