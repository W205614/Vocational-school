import { test, expect, type Locator, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { accounts, api, fixture, login, signIn } from './helpers';

const fixtureVideo = fileURLToPath(
  new URL('../../zxt-media/src/test/resources/media-probe/recorder.webm', import.meta.url),
);
const replacementVideo = fileURLToPath(
  new URL('../../zxt-media/src/test/resources/media-probe/tiny.mp4', import.meta.url),
);
const outcome = '完成一份可演示的课程成果';
const privateHandout = '随堂讲义示例：提交之前请核对作品中的输入、输出和边界情况。';

function post(page: Page, path: string, body: unknown) {
  return api(page, 'POST', path, body, crypto.randomUUID());
}

async function uploadVideo(admin: Page, path: string) {
  await admin.goto('/media');
  const webm = path.endsWith('.webm');
  const unique = Buffer.from(crypto.randomUUID().replaceAll('-', ''), 'hex');
  const filename = unique.toString('hex') + '-' + basename(path);
  // Keep each run's content-addressed upload distinct using legal container padding.
  const padding = webm ? Buffer.from([0xec, 0x90]) : Buffer.from([0, 0, 0, 24, 0x66, 0x72, 0x65, 0x65]);
  await admin.getByLabel('选择本地视频').setInputFiles({
    name: filename,
    mimeType: webm ? 'video/webm' : 'video/mp4',
    buffer: Buffer.concat([readFileSync(path), padding, unique]),
  });
  await admin.getByRole('button', { name: '上传并登记', exact: true }).click();
  await expect(admin.getByText('视频已持久化', { exact: true })).toBeVisible();
  const media = await admin.evaluate(() =>
    JSON.parse(sessionStorage.getItem('school-operations') || '[]').find(
      (item: any) => item.label === '登记本地视频',
    ).operation.result,
  );
  return { ...media, filename };
}

async function watchClip(player: Locator) {
  await expect.poll(
    () => player.evaluate((element) => (element as HTMLVideoElement).readyState),
    { timeout: 15_000 },
  ).toBeGreaterThanOrEqual(2);
  await player.evaluate((element) => {
    const video = element as HTMLVideoElement;
    video.muted = true;
    return video.play();
  });
  await expect.poll(() => player.evaluate((element) => (element as HTMLVideoElement).currentTime))
    .toBeGreaterThan(0);
}

async function verifyClip(page: Page, url: string) {
  expect(url).toMatch(/^\/api\/v2\/services\/media\/trial-content\/[0-9a-f]{64}\?/);
  const response = await page.request.get(url, { headers: { Range: 'bytes=0-15' } });
  expect(response.status()).toBe(206);
  expect(response.headers()['content-type']).toContain('video/mp4');
  expect(response.headers()['cache-control']).toContain('no-store');
  expect((await response.body()).subarray(4, 8).toString()).toBe('ftyp');
}

function deleteMedia(page: Page, mediaId: string, token: string | null) {
  return page.request.delete('/api/v2/admin/media/medias/' + mediaId, {
    headers: { Authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() },
  });
}

function mediaRow(page: Page, mediaId: string) {
  return page.getByRole('row')
    .filter({ has: page.getByRole('cell', { name: mediaId, exact: true }) })
    .filter({ has: page.getByRole('button', { name: '删除', exact: true }) });
}

async function findMedia(page: Page, filename: string) {
  await page.goto('/media');
  await page.getByRole('textbox', { name: '按文件名搜索' }).fill(filename);
  await page.getByRole('button', { name: '查询', exact: true }).click();
}

test('course publication gates trial generation and preserves draft, clip, and handout permissions', async ({
  page,
  browser,
}, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(180000);
  const adminContext = await browser.newContext({
    baseURL: process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501',
  });
  const admin = await adminContext.newPage();
  try {
    await login(admin, 'admin', '/media');
    const media = await uploadVideo(admin, fixtureVideo);
    const replacement = await uploadVideo(admin, replacementVideo);
    expect(replacement.id).not.toBe(media.id);
    const categories = await api(admin, 'GET', '/services/course/categorys/list?status=1');
    const courseName = 'Product course ' + fixture.marker + ' ' + Date.now().toString(36);
    const course = await post(admin, '/admin/course/courses/baseInfo/save', {
      name: courseName,
      thirdCateId: categories[0].children[0].children[0].id,
      coverUrl: '/course-preview-test.png',
      free: true,
      price: 0,
      validDuration: 0,
      purchaseEndTime: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 19),
      introduce: '<p><strong>课程草稿预览</strong>&nbsp;与正式发布</p>',
      usePeople: '开始学习的学生',
      detail: '视频和考试共同覆盖教学目标',
      learningOutcomes: outcome,
      prerequisites: '无',
      difficulty: 'BEGINNER',
      practiceOverview: '按资料完成练习',
      handout: privateHandout,
    });
    const editor = '/course-editor?courseId=' + course.id;
    const service = '/admin/course/courses';
    await post(admin, service + '/catas/save/' + course.id + '/2', [{
      name: '试点课程目录', type: 1, index: 1,
      sections: [
        { name: '试点视频', type: 2, index: 1 },
        { name: '试点考试', type: 3, index: 2 },
      ],
    }]);

    await admin.goto(editor);
    await admin.getByRole('button', { name: '预览与上架检查' }).click();
    const preview = admin.getByRole('dialog', { name: '学生视角预览与上架检查' });
    const introduction = preview.getByRole('region', { name: '学生视角预览', exact: true });
    await expect(introduction.getByText('课程草稿预览 与正式发布', { exact: true })).toBeVisible();
    await expect(introduction.locator('p strong')).toHaveCount(0);
    await expect(preview.getByText(outcome, { exact: true })).toBeVisible();
    await expect(preview.getByText(/缺少视频关联/)).toBeVisible();
    await expect(preview.getByText(/尚未发布试卷/)).toBeVisible();
    await expect(preview.getByText(/请保存至少一名授课教师/)).toBeVisible();
    await expect(preview.getByRole('button', { name: '确认上架' })).toBeDisabled();
    const anonymousDraft = await page.request.get('/api/v2/public/courses/' + course.id);
    expect(anonymousDraft.ok()).toBeFalsy();
    const adminToken = await admin.evaluate(() => sessionStorage.getItem('school-token'));
    const refusedPublication = await admin.request.post('/api/v2' + service + '/upShelf', {
      data: { id: course.id },
      headers: { Authorization: 'Bearer ' + adminToken },
    });
    expect(refusedPublication.ok()).toBeFalsy();

    const draft = await api(admin, 'GET', service + '/catas/' + course.id + '?see=false&withPractice=true');
    const video = draft[0].sections[0].id;
    const exam = draft[0].sections[1].id;
    await post(admin, service + '/media/save/' + course.id, [{
      cataId: video, mediaId: media.id, videoName: 'recorder.webm',
      mediaDuration: Math.ceil(Number(media.duration)), trailer: true,
    }]);
    await post(admin, service + '/subjects/save/' + course.id, [
      { cataId: exam, subjectIds: [fixture.objective] },
    ]);
    await post(admin, service + '/teachers/save', {
      id: course.id, teachers: [{ id: accounts.teacher.id, isShow: true }],
    });

    await admin.goto(editor);
    await admin.getByRole('button', { name: '预览与上架检查' }).click();
    const paper = preview.locator('li').filter({ hasText: '尚未发布试卷' });
    await paper.getByRole('button', { name: '前往处理' }).click();
    await expect(admin).toHaveURL(new RegExp('/exams.*sectionId=' + exam));
    await admin.getByRole('button', { name: '选择试卷题目', exact: true }).click();
    const picker = admin.getByRole('dialog', { name: '选择试卷题目', exact: true });
    await picker.getByRole('textbox', { name: '选择试卷题目关键词', exact: true }).fill(fixture.marker);
    await picker.getByRole('button', { name: '查询', exact: true }).click();
    const question = picker.locator('.el-table__row').filter({ hasText: 'Browser objective ' + fixture.marker });
    await expect(question).toBeVisible();
    await question.getByRole('button', { name: '添加', exact: true }).click();
    await picker.getByRole('button', { name: '确认选择', exact: true }).click();
    await admin.getByRole('button', { name: '发布不可变试卷版本' }).click();
    await expect(admin.getByText('试卷版本已发布', { exact: true })).toBeVisible();

    await admin.goto(editor);
    await admin.getByRole('button', { name: '预览与上架检查' }).click();
    const trial = preview.getByRole('region', { name: '小节试看片段' });
    await expect(trial).toContainText('待生成');
    await expect(preview.getByRole('button', { name: '确认上架' })).toBeDisabled();
    const blocked = await admin.request.post('/api/v2' + service + '/upShelf', {
      data: { id: course.id },
      headers: { Authorization: 'Bearer ' + adminToken, 'Idempotency-Key': crypto.randomUUID() },
    });
    expect(blocked.ok()).toBeFalsy();
    expect(JSON.stringify(await blocked.json())).toContain('试看片段尚未就绪');

    const draftDeletion = await deleteMedia(admin, media.id, adminToken);
    expect(draftDeletion.status()).toBe(202);
    const { data: pendingDeletion } = await draftDeletion.json();
    const operationPath = '/operations/media/' + pendingDeletion.operationId;
    await expect.poll(async () => (await api(admin, 'GET', operationPath)).status, { timeout: 20_000 })
      .toBe('FAILED');
    expect((await api(admin, 'GET', operationPath)).errorMessage).toContain('媒资仍被课程引用');

    const prepareTrial = async (entireCourse: boolean) => {
      if (entireCourse) {
        await trial.getByRole('button', { name: '生成整门课程的试看', exact: true }).click();
      } else {
        const checkbox = admin.getByRole('checkbox', { name: '选择试点视频', exact: true });
        await trial.locator('label').filter({ has: checkbox }).click();
        await expect(checkbox).toBeChecked();
        await trial.getByRole('button', { name: '生成所选小节', exact: true }).click();
      }
      const showClip = trial.getByRole('button', { name: '预览片段' });
      await expect(showClip).toBeEnabled({ timeout: 45_000 });
      await showClip.click();
      const player = trial.locator('video');
      await expect(player).toBeVisible();
      const url = (await player.getAttribute('src'))!;
      await verifyClip(admin, url);
      await watchClip(player);
      return url;
    };

    const firstClip = await prepareTrial(true);
    const wrongScope = new URL(firstClip, admin.url());
    wrongScope.searchParams.delete('previewCourse');
    expect((await admin.request.get(wrongScope.toString())).status()).toBe(403);
    await post(admin, service + '/media/save/' + course.id, [{
      cataId: video, mediaId: replacement.id, videoName: 'tiny.mp4',
      mediaDuration: Math.ceil(Number(replacement.duration)), trailer: true,
    }]);
    expect((await admin.request.get(firstClip, { headers: { Range: 'bytes=0-15' } })).status()).toBe(403);
    await preview.getByRole('button', { name: '刷新试看状态' }).click();
    await expect(trial).toContainText('待生成');
    await expect(trial.locator('video')).toHaveCount(0);
    await expect(preview.getByRole('button', { name: '确认上架' })).toBeDisabled();
    const draftClip = await prepareTrial(false);
    expect(draftClip.split('?')[0]).not.toBe(firstClip.split('?')[0]);
    await preview.getByRole('button', { name: '确认上架' }).click();
    await expect(admin).toHaveURL(/\/courses$/);
    expect((await admin.request.get(draftClip, { headers: { Range: 'bytes=0-15' } })).status()).toBe(403);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/courses/' + course.id);
    await expect(page.getByText(outcome, { exact: true })).toBeVisible();
    await expect(page.getByText(privateHandout, { exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: '登录后试看 · 试点视频', exact: true }).click();
    await expect(page).toHaveURL(/\/login\?redirect=/);
    const redirect = new URL(page.url()).searchParams.get('redirect');
    expect(redirect).toBe('/courses/' + course.id + '?sectionId=' + video);
    const [playResponse] = await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes('/medias/signature/play?sectionId=' + video) && response.ok(),
      ),
      signIn(page, 'student'),
    ]);
    await expect(page.getByText(outcome, { exact: true })).toBeVisible();
    const studentToken = await page.evaluate(() => sessionStorage.getItem('school-token'));
    const studentHeaders = { Authorization: 'Bearer ' + studentToken };
    const deniedPreview = await page.request.get('/api/v2' + service + '/publication/' + course.id, {
      headers: studentHeaders,
    });
    expect(deniedPreview.status()).toBe(403);
    const deniedTrialPreview = await page.request.post('/api/v2/admin/media/trial-previews', {
      headers: studentHeaders, data: { courseId: course.id, sectionId: video },
    });
    expect(deniedTrialPreview.status()).toBe(403);
    const { data: trialTicket } = await playResponse.json();
    expect(trialTicket).toMatchObject({ trial: true, state: 'READY', provider: 'TRIAL' });
    expect(trialTicket.fileId).toBeFalsy();
    expect(trialTicket.playUrl.split('?')[0]).toBe(draftClip.split('?')[0]);
    expect(trialTicket.playUrl).not.toContain('previewCourse');
    await verifyClip(page, trialTicket.playUrl);
    await expect(page.getByRole('tab', { name: '课程目录', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('当前播放独立试看片段，报名后可学习完整版。')).toBeVisible();
    await watchClip(page.locator('video.course-player'));
    const deniedFullPreview = await page.request.get(
      '/api/v2/services/media/medias/signature/preview?mediaId=' + replacement.id,
      { headers: studentHeaders },
    );
    expect(deniedFullPreview.status()).toBe(403);
    const deniedHandout = await page.request.get('/api/v2/services/course/courses/' + course.id + '/handout', {
      headers: studentHeaders,
    });
    expect(deniedHandout.status()).toBe(403);
    await page.getByRole('button', { name: '免费报名', exact: true }).click();
    await expect(page.getByRole('button', { name: '继续学习', exact: true })).toBeVisible();
    await page.getByRole('tab', { name: '随堂讲义', exact: true }).click();
    await expect(page.getByText(privateHandout, { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);

    await findMedia(admin, replacement.filename);
    await mediaRow(admin, replacement.id).getByRole('button', { name: '删除', exact: true }).click();
    const deletionDialog = admin.getByRole('dialog', { name: '删除媒资前检查引用' });
    await expect(deletionDialog.getByRole('listitem').filter({ hasText: courseName })).toContainText('已上架');
    await expect(deletionDialog.getByRole('button', { name: '确认删除视频' })).toBeDisabled();
    expect((await deleteMedia(admin, replacement.id, adminToken)).ok()).toBeFalsy();
    await post(admin, service + '/downShelf', { id: course.id });
    await deletionDialog.getByRole('button', { name: '刷新引用' }).click();
    await expect(deletionDialog.getByRole('listitem').filter({ hasText: courseName + ' · 试点视频 · 已下架' })).toBeVisible();
    await expect(deletionDialog.getByRole('button', { name: '确认删除视频' })).toBeDisabled();
    const retained = await api(page, 'POST', '/services/media/medias/signature/play?sectionId=' + video);
    expect(retained.playUrl).toContain('/local-content/');
    expect((await page.request.get(retained.playUrl, { headers: { Range: 'bytes=0-15' } })).status()).toBe(206);

    await findMedia(admin, media.filename);
    const unused = mediaRow(admin, media.id);
    await unused.getByRole('button', { name: '删除', exact: true }).click();
    await expect(deletionDialog).toContainText('目前没有课程引用');
    const deletionSent = admin.waitForResponse((response) =>
      response.request().method() === 'DELETE' &&
      new URL(response.url()).pathname === '/api/v2/admin/media/medias/' + media.id,
    );
    await deletionDialog.getByRole('button', { name: '确认删除视频' }).click();
    const deletionResponse = await deletionSent;
    expect(deletionResponse.status()).toBe(202);
    const { data: deletion } = await deletionResponse.json();
    await expect.poll(async () => (await api(admin, 'GET', '/operations/media/' + deletion.operationId)).status,
      { timeout: 20_000 }).toBe('SUCCEEDED');
    await expect(deletionDialog).toBeHidden();
    const inventoryRefreshed = admin.waitForResponse((response) => {
      const requestUrl = new URL(response.url());
      return response.ok() && requestUrl.pathname === '/api/v2/admin/media/medias'
        && requestUrl.searchParams.get('name') === media.filename;
    });
    await admin.getByRole('button', { name: '查询', exact: true }).click();
    const { data: mediaInventory } = await (await inventoryRefreshed).json();
    expect(mediaInventory.list ?? []).toEqual([]);
    await expect(unused).toHaveCount(0);
    const stale = await admin.request.post('/api/v2' + service + '/media/save/' + course.id, {
      headers: { Authorization: 'Bearer ' + adminToken, 'Idempotency-Key': crypto.randomUUID() },
      data: [{ cataId: video, mediaId: media.id, videoName: 'removed.webm', mediaDuration: 2, trailer: true }],
    });
    expect(stale.status()).toBe(409);
    expect(JSON.stringify(await stale.json())).toContain('关联的视频已进入删除流程');
  } finally {
    await adminContext.close();
  }
});

test('historical published videos can be prepared by section or entire course without taking the course offline', async ({
  page, browser,
}, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(120_000);
  const adminContext = await browser.newContext({
    baseURL: process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501',
  });
  const admin = await adminContext.newPage();
  const path = '/admin/media/trial-batches';
  const courseId = fixture.legacyTrialCourse;
  try {
    await login(admin, 'admin', '/media');
    const first = await uploadVideo(admin, fixtureVideo);
    const second = await uploadVideo(admin, replacementVideo);
    for (const [source, slot] of [[first, 'first'], [second, 'second']] as const) {
      execFileSync(process.env.ZXT_PYTHON || 'python', [
        process.env.ZXT_MEDIA_FIXTURE_SCRIPT || '../deploy/acceptance/attach_browser_media.py',
        source.id, '--trial-slot', slot,
      ]);
    }
    await api(admin, 'POST', '/admin/search/courses/up?courseIds=' + courseId);
    await expect.poll(async () => {
      const directory = await page.request.get('/api/v2/public/courses', {
        params: { keyword: fixture.legacyTrialName, hasTrial: true },
      });
      expect(directory.status()).toBe(200);
      const { data } = await directory.json();
      return data.list;
    }, { timeout: 30_000 }).toEqual(expect.arrayContaining([expect.objectContaining({ id: courseId, hasTrial: true })]));

    await admin.goto('/courses');
    await admin.getByRole('textbox', { name: '按课程名称搜索' }).fill(fixture.legacyTrialName);
    await admin.getByRole('button', { name: '查询', exact: true }).click();
    await admin.getByRole('row').filter({ hasText: fixture.legacyTrialName })
      .getByRole('button', { name: '准备公开试看' }).click();
    const dialog = admin.getByRole('dialog', { name: '准备公开试看' });
    const firstRow = dialog.getByRole('listitem').filter({ hasText: 'Legacy first' });
    const secondRow = dialog.getByRole('listitem').filter({ hasText: 'Legacy second' });
    const unavailable = dialog.getByRole('listitem').filter({ hasText: 'Legacy missing source' });
    await expect(firstRow).toContainText('待生成');
    await expect(secondRow).toContainText('待生成');
    await expect(unavailable).toContainText('源视频不可用');
    const checkbox = admin.getByRole('checkbox', { name: '选择Legacy first', exact: true });
    await firstRow.locator('label').filter({ has: checkbox }).click();
    await expect(checkbox).toBeChecked();
    await dialog.getByRole('button', { name: '生成所选小节', exact: true }).click();
    await expect(firstRow).toContainText('已就绪', { timeout: 45_000 });
    await expect(secondRow).toContainText('待生成');
    const resultsPath = path + '?courseId=' + courseId + '&draft=false';
    const prepared = await api(admin, 'GET', resultsPath);
    expect(prepared[0].sectionId).toBe(fixture.legacyTrialFirst);
    expect(prepared[1].sectionId).toBe(fixture.legacyTrialSecond);
    await dialog.getByRole('button', { name: '生成整门课程的试看', exact: true }).click();
    await expect(secondRow).toContainText('已就绪', { timeout: 45_000 });
    const ready = await api(admin, 'GET', resultsPath);
    expect(ready.map((item: any) => item.status.state)).toEqual(['READY', 'READY', 'UNAVAILABLE']);
    expect(ready[0].status.taskId).toBe(prepared[0].status.taskId);
    const published = await page.request.get('/api/v2/public/courses/' + courseId);
    expect(published.status()).toBe(200);
    await login(page, 'student', '/courses/' + courseId);
    const studentTicket = await api(page, 'POST', '/services/media/medias/signature/play?sectionId=' + fixture.legacyTrialSecond);
    expect(studentTicket.state).toBe('READY');
    expect(studentTicket.taskId).toBe(ready[1].status.taskId);
    await verifyClip(page, studentTicket.playUrl);
    const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
    const denied = await page.request.get('/api/v2' + resultsPath, { headers: { Authorization: 'Bearer ' + token } });
    expect(denied.status()).toBe(403);
  } finally {
    await adminContext.close();
  }
});
