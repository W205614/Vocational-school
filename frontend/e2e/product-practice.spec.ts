import { expect, test, type Page } from '@playwright/test';
import { api, fixture, login } from './helpers';

async function protectedRequest(page: Page, path: string, method = 'GET', data?: unknown) {
  const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
  return page.request.fetch('/api/v2' + path, {
    method,
    data,
    headers: { Authorization: 'Bearer ' + token },
  });
}

test('product practice: correct a published task and review each version from the teacher queue', async ({ page, browser }, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(180_000);
  const staff = { baseURL: process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501' };
  const adminContext = await browser.newContext(staff);
  const teacherContext = await browser.newContext(staff);
  const admin = await adminContext.newPage();
  const teacher = await teacherContext.newPage();
  const course = fixture.freeCourse;
  const courseName = 'Browser free ' + fixture.marker;
  const assignmentTitle = '课程实操 ' + fixture.marker;
  const correctedTitle = '更正后的课程实操 ' + fixture.marker;
  const originalInstructions = '提交一份作品说明，可以补充演示链接。';
  const correctedInstructions = '请提交作品说明，并注明成果验证方法。';
  const reason = '补充成果验证要求';
  const correctionPath = '/admin/learning-hub/practice/' + course + '/corrections';
  const queuePath = '/teacher/learning-hub/practice/pending';
  const work = '/teacher/learning-hub/practice/' + course + '/submissions';
  const submissionPath = '/learning-hub/practice/' + course + '/submissions';
  try {
    await login(admin, 'admin', '/practice');
    await admin.getByRole('button', { name: '选择试点课程', exact: true }).click();
    const picker = admin.getByRole('dialog', { name: '选择试点课程' });
    await picker.getByRole('textbox', { name: '选择试点课程关键词' }).fill(courseName);
    await picker.getByRole('button', { name: '查询', exact: true }).click();
    await picker.getByRole('row').filter({ hasText: courseName }).getByRole('button', { name: '选用' }).click();
    await admin.getByRole('textbox', { name: '作业标题' }).fill(assignmentTitle);
    await admin.getByRole('textbox', { name: '任务说明' }).fill(originalInstructions);
    await admin.getByRole('button', { name: '保存草稿' }).click();
    await expect(admin.getByText('草稿已保存。')).toBeVisible();
    await admin.getByRole('button', { name: '发布作业' }).click();
    await expect(admin.getByText('作业已发布。')).toBeVisible();
    await expect(admin.getByText('已发布 · 当前第 1 版')).toBeVisible();

    await page.setViewportSize({ width: 390, height: 844 });
    await login(page, 'student', '/courses/' + course);
    expect((await protectedRequest(page, '/learning-hub/practice/' + fixture.expiredCourse)).status()).toBe(403);
    expect((await protectedRequest(page, work)).status()).toBe(403);
    expect((await protectedRequest(page, queuePath)).status()).toBe(403);
    const enroll = page.getByRole('button', { name: '免费报名', exact: true });
    await expect(page.getByRole('button', { name: /^(免费报名|继续学习)$/ })).toBeVisible();
    if (await enroll.isVisible()) await enroll.click();
    await expect(page.getByRole('button', { name: '继续学习', exact: true })).toBeVisible();
    await page.goto('/learning');
    await page.getByRole('link', { name: '实操作业 →', exact: true }).click();
    await page.getByRole('link', { name: assignmentTitle, exact: true }).click();
    const currentTask = page.getByRole('article').filter({
      has: page.getByRole('heading', { name: assignmentTitle, exact: true }),
    });
    await expect(currentTask.locator(':scope > p').filter({ hasText: originalInstructions })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(391);

    await page.getByRole('textbox', { name: '作品说明' }).fill('第一版作品说明');
    await page.getByRole('textbox', { name: '作品链接（可选）' }).fill('https://example.com/practice');
    const captured = page.waitForRequest((request) =>
      request.method() === 'POST' && new URL(request.url()).pathname.endsWith(submissionPath),
    );
    await page.getByRole('button', { name: '提交作品' }).click();
    const body = (await captured).postDataJSON();
    await expect(page.getByRole('heading', { name: '第 1 次提交 · 待反馈' })).toBeVisible();
    const first = await api(page, 'POST', submissionPath, body);
    expect(first.revision).toBe(1);
    expect(first.assignmentVersion).toBe(1);
    expect((await protectedRequest(page, submissionPath, 'POST', {
      ...body, submissionKey: crypto.randomUUID(),
    })).status()).toBe(409);

    const correction = { title: correctedTitle, instructions: correctedInstructions, reason, expectedVersion: 1 };
    expect((await protectedRequest(admin, correctionPath, 'PUT', { ...correction, reason: ' ' })).status()).toBe(400);
    await admin.getByRole('button', { name: '更正任务说明' }).click();
    await admin.getByRole('textbox', { name: '作业标题' }).fill(correctedTitle);
    await admin.getByRole('textbox', { name: '任务说明' }).fill(correctedInstructions);
    await expect(admin.getByRole('button', { name: '发布新版本' })).toBeDisabled();
    await admin.getByRole('textbox', { name: '更正说明' }).fill(reason);
    await admin.getByRole('button', { name: '发布新版本' }).click();
    await expect(admin.getByText('新版本已发布。')).toBeVisible();
    await expect(admin.getByText('已发布 · 当前第 2 版')).toBeVisible();
    await admin.getByText('任务版本记录（2）', { exact: true }).click();
    await admin.getByText(new RegExp('第 2 版 · ' + correctedTitle)).click();
    await expect(admin.getByText('更正说明 · ' + reason, { exact: true })).toBeVisible();
    expect((await api(admin, 'PUT', correctionPath, correction)).versions.map((item: { version: number }) => item.version)).toEqual([2, 1]);
    expect((await protectedRequest(admin, correctionPath, 'PUT', { ...correction, reason: '另一份修改' })).status()).toBe(409);
    expect((await api(page, 'POST', submissionPath, body)).assignmentVersion).toBe(1);

    async function correctionNotices() {
      const result = await api(page, 'POST', '/services/message/inboxes/query');
      return result.list.filter((row: { targetType: string; courseId: string; targetId: string }) =>
        row.targetType === 'PRACTICE_CORRECTION' && row.courseId === course && Number(row.targetId) === 2,
      );
    }
    await expect.poll(async () => (await correctionNotices()).length, { timeout: 15_000 }).toBe(1);
    await page.goto('/messages');
    const correctionLink = page.getByRole('link', { name: '查看作业更正', exact: true })
      .and(page.locator('a[href="/practice?courseId=' + course + '&version=2"]'));
    const correctionArticle = page.getByRole('article').filter({ has: correctionLink });
    await expect(correctionArticle.getByRole('button', { name: '标为已读' })).toBeVisible();
    await correctionLink.click();
    const correctionDetail = page.getByRole('region', { name: '第 2 版更正' });
    await expect(correctionDetail).toContainText(reason);
    await expect(correctionDetail).toContainText(originalInstructions);
    await expect(correctionDetail).toContainText(correctedInstructions);
    expect((await correctionNotices())[0].isRead).toBeTruthy();
    const studentFeedback = page.getByRole('listitem').filter({
      has: page.getByRole('heading', { name: '第 1 次提交 · 待反馈', exact: true }),
    });
    await studentFeedback.getByRole('button', { name: '对照当前任务要求' }).click();
    const studentComparison = studentFeedback.getByRole('region', { name: '任务要求差异' });
    await expect(studentComparison).toContainText('提交使用 · 第 1 版');
    await expect(studentComparison).toContainText('当前要求 · 第 2 版');
    await expect(studentComparison.locator('mark').first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(391);

    await login(teacher, 'teacher', '/teaching?workCourse=' + course + '&pendingOnly=false');
    expect((await protectedRequest(teacher, '/teacher/learning-hub/practice/' + fixture.notesCourse + '/submissions')).status()).toBe(403);
    expect((await protectedRequest(teacher, queuePath + '?courseId=' + fixture.notesCourse)).status()).toBe(403);
    const practiceSummary = teacher.locator('[aria-label="待办概览"] > div').filter({
      has: teacher.getByText('待反馈实操作业', { exact: true }),
    });
    await expect(practiceSummary).toContainText('1 项');
    await expect(practiceSummary).toContainText('最早等待自');
    const queue = teacher.getByRole('region', { name: '待反馈实操作业' });
    const firstQueueLink = queue.getByRole('article').filter({ hasText: assignmentTitle })
      .getByRole('link', { name: '查看待反馈作品 →' });
    await firstQueueLink.click();
    const dialog = teacher.getByRole('dialog', { name: '作品与反馈' });
    await expect(teacher).toHaveURL(new RegExp('submissionId=' + first.id));
    const firstWorkUrl = teacher.url();
    await expect(teacher.getByText('当前任务 · 第 2 版', { exact: true })).toBeVisible();
    await expect(dialog.getByText('第一版作品说明')).toBeVisible();
    await dialog.getByText('提交时的任务说明 · 第 1 版', { exact: true }).click();
    await expect(dialog.getByText(originalInstructions, { exact: true })).toBeVisible();
    await dialog.getByRole('button', { name: '对照当前任务要求' }).click();
    const teacherComparison = dialog.getByRole('region', { name: '任务要求差异' });
    await expect(teacherComparison).toContainText(originalInstructions);
    await expect(teacherComparison).toContainText(correctedInstructions);
    await dialog.getByRole('textbox', { name: '教师反馈' }).fill('补充成果验证。');
    await dialog.getByRole('button', { name: '需要修改', exact: true }).click();
    await expect(dialog.getByRole('heading', { name: '第 1 次提交 · 需要修改' })).toBeVisible();
    await api(teacher, 'PUT', work + '/' + first.id + '/feedback', {
      status: 'NEEDS_REVISION', feedback: '补充成果验证。',
    });
    await dialog.getByRole('link', { name: '返回教学待办 →' }).click();
    await expect(teacher).toHaveURL(/\/teaching\?.*#teacher-practice-heading$/);
    const returned = new URL(teacher.url());
    expect(returned.hash).toBe('#teacher-practice-heading');
    expect(returned.searchParams.get('workCourse')).toBe(String(course));
    expect(returned.searchParams.get('pendingOnly')).toBe('false');
    await expect(firstQueueLink).toHaveCount(0);
    await expect(queue.getByText('当前范围没有待反馈实操作业', { exact: true })).toBeVisible();
    expect((await protectedRequest(page, submissionPath, 'POST', {
      ...body, submissionKey: crypto.randomUUID(),
    })).status()).toBe(409);

    async function feedbackCount() {
      const result = await api(page, 'POST', '/services/message/inboxes/query');
      return result.list.filter((row: { targetId: string; targetType: string }) =>
        row.targetType === 'PRACTICE' && row.targetId === course,
      ).length;
    }
    await expect.poll(feedbackCount).toBe(1);
    await page.goto('/messages');
    await page.getByRole('link', { name: '查看作业反馈', exact: true })
      .and(page.locator('a[href="/practice?courseId=' + course + '"]')).first().click();
    await expect(page.getByText('补充成果验证。')).toBeVisible();
    const correctedTask = page.getByRole('article').filter({
      has: page.getByRole('heading', { name: correctedTitle, exact: true }),
    });
    await expect(correctedTask.locator(':scope > p').filter({ hasText: correctedInstructions })).toBeVisible();
    await page.getByRole('textbox', { name: '作品说明' }).fill('第二版，已补充成果验证。');
    await page.getByRole('button', { name: '修改后重交' }).click();
    await expect(page.getByRole('heading', { name: '第 2 次提交 · 待反馈' })).toBeVisible();

    await teacher.goto(firstWorkUrl);
    await expect(dialog.getByText('当前打开的是历史提交。', { exact: true })).toBeVisible();
    await expect(dialog.getByRole('textbox', { name: '教师反馈' })).toHaveCount(0);
    await dialog.getByRole('link', { name: '返回教学待办 →' }).click();
    const secondQueueLink = queue.getByRole('article').filter({ hasText: correctedTitle })
      .getByRole('link', { name: '查看待反馈作品 →' });
    await secondQueueLink.click();
    await dialog.getByText('提交时的任务说明 · 第 2 版', { exact: true }).click();
    await expect(dialog.getByText(correctedInstructions, { exact: true })).toBeVisible();
    await dialog.getByRole('textbox', { name: '教师反馈' }).fill('通过，验证充分。');
    await dialog.getByRole('button', { name: '通过作业' }).click();
    await expect(dialog.getByRole('heading', { name: '第 2 次提交 · 已通过' })).toBeVisible();
    await dialog.getByRole('button', { name: '处理下一份作品' }).click();
    await expect(dialog.getByText('当前范围没有其他待反馈实操作业')).toBeVisible();
    await dialog.getByRole('link', { name: '返回教学待办 →' }).click();
    await expect(secondQueueLink).toHaveCount(0);
    await expect(queue.getByText('当前范围没有待反馈实操作业', { exact: true })).toBeVisible();
    await expect.poll(feedbackCount).toBe(2);
    await page.reload();
    await expect(page.getByRole('heading', { name: '第 2 次提交 · 已通过' })).toBeVisible();
    await expect(page.getByText('通过，验证充分。')).toBeVisible();
    const firstSubmission = page.getByRole('listitem').filter({
      has: page.getByRole('heading', { name: '第 1 次提交 · 需要修改', exact: true }),
    });
    await firstSubmission.getByText('提交时的任务说明 · 第 1 版', { exact: true }).click();
    await expect(firstSubmission.getByText(originalInstructions, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '修改后重交' })).toHaveCount(0);

    const draftTitle = '并行编辑时保留的作业标题 ' + fixture.marker;
    const draftReason = '确认最新版后修订标题';
    const concurrentInstructions = '教师在另一会话补充了预习要求。';
    await admin.getByRole('button', { name: '更正任务说明' }).click();
    await admin.getByRole('textbox', { name: '作业标题' }).fill(draftTitle);
    await admin.getByRole('textbox', { name: '更正说明' }).fill(draftReason);
    await api(admin, 'PUT', correctionPath, {
      ...correction, expectedVersion: 2, instructions: concurrentInstructions, reason: '补充预习要求',
    });
    await admin.getByRole('button', { name: '发布新版本' }).click();
    await expect(admin.getByText(/作业已有更新/)).toBeVisible();
    await expect(admin.getByRole('button', { name: '发布新版本' })).toBeDisabled();
    await admin.getByRole('button', { name: '载入最新版本并保留草稿' }).click();
    await expect(admin.getByText('已发布 · 当前第 3 版')).toBeVisible();
    await expect(admin.getByRole('textbox', { name: '作业标题' })).toHaveValue(draftTitle);
    await expect(admin.getByRole('textbox', { name: '任务说明' })).toHaveValue(concurrentInstructions);
    await expect(admin.getByRole('textbox', { name: '更正说明' })).toHaveValue(draftReason);
    const draftComparison = admin.getByRole('region', { name: '任务要求差异' });
    await expect(draftComparison).toContainText('当前发布 · 第 3 版');
    await expect(draftComparison).toContainText('任务说明相同');
    await admin.getByRole('button', { name: '发布新版本' }).click();
    await expect(admin.getByText('已发布 · 当前第 4 版')).toBeVisible();
  } finally {
    await teacherContext.close();
    await adminContext.close();
  }
});
