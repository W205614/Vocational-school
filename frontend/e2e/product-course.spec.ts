import { test, expect, type Locator, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { accounts, api, fixture, login } from './helpers';

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
  // Keep each run's content-addressed upload distinct using legal container padding.
  const padding = webm ? Buffer.from([0xec, 0x90]) : Buffer.from([0, 0, 0, 24, 0x66, 0x72, 0x65, 0x65]);
  await admin.getByLabel('选择本地视频').setInputFiles({
    name: basename(path),
    mimeType: webm ? 'video/webm' : 'video/mp4',
    buffer: Buffer.concat([readFileSync(path), padding, unique]),
  });
  await admin.getByRole('button', { name: '上传并登记', exact: true }).click();
  await expect(admin.getByText('视频已持久化', { exact: true })).toBeVisible();
  return admin.evaluate(() =>
    JSON.parse(sessionStorage.getItem('school-operations') || '[]').find(
      (item: any) => item.label === '登记本地视频',
    ).operation.result,
  );
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
    const course = await post(admin, '/admin/course/courses/baseInfo/save', {
      name: 'Product course ' + fixture.marker + ' ' + Date.now().toString(36),
      thirdCateId: categories[0].children[0].children[0].id,
      coverUrl: '/course-preview-test.png',
      free: true,
      price: 0,
      validDuration: 0,
      purchaseEndTime: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 19),
      introduce: '课程草稿预览与正式发布的隔离夹具',
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

    const prepareTrial = async () => {
      await trial.getByRole('button', { name: '生成试看片段' }).click();
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

    const firstClip = await prepareTrial();
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
    const draftClip = await prepareTrial();
    expect(draftClip.split('?')[0]).not.toBe(firstClip.split('?')[0]);
    await preview.getByRole('button', { name: '确认上架' }).click();
    await expect(admin).toHaveURL(/\/courses$/);
    expect((await admin.request.get(draftClip, { headers: { Range: 'bytes=0-15' } })).status()).toBe(403);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/courses/' + course.id);
    await expect(page.getByText(outcome, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '试看 · 试点视频' })).toBeVisible();
    await expect(page.getByText(privateHandout, { exact: true })).toHaveCount(0);
    await login(page, 'student', '/courses/' + course.id);
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
    await page.getByRole('tab', { name: '课程介绍', exact: true }).click();
    const [playResponse] = await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes('/medias/signature/play?sectionId=' + video) && response.ok(),
      ),
      page.getByRole('button', { name: '试看 · 试点视频' }).click(),
    ]);
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
  } finally {
    await adminContext.close();
  }
});
