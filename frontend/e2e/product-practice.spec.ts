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

test('product practice: publish, submit, request revision, resubmit and open feedback from inbox', async ({ page, browser }, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(120_000);
  const staff = { baseURL: process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501' };
  const adminContext = await browser.newContext(staff);
  const teacherContext = await browser.newContext(staff);
  const admin = await adminContext.newPage();
  const teacher = await teacherContext.newPage();
  const course = fixture.freeCourse;
  const courseName = 'Browser free ' + fixture.marker;
  const assignmentTitle = '课程实操 ' + fixture.marker;
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
    await admin.getByRole('textbox', { name: '任务说明' }).fill('提交一份作品说明，可以补充演示链接。');
    await admin.getByRole('button', { name: '保存草稿' }).click();
    await expect(admin.getByText('草稿已保存。')).toBeVisible();
    await admin.getByRole('button', { name: '发布作业' }).click();
    await expect(admin.getByText('作业已发布。')).toBeVisible();

    await page.setViewportSize({ width: 390, height: 844 });
    await login(page, 'student', '/courses/' + course);
    expect((await protectedRequest(page, '/learning-hub/practice/' + fixture.expiredCourse)).status()).toBe(403);
    expect((await protectedRequest(page, work)).status()).toBe(403);
    const enroll = page.getByRole('button', { name: '免费报名', exact: true });
    await expect(page.getByRole('button', { name: /^(免费报名|继续学习)$/ })).toBeVisible();
    if (await enroll.isVisible()) await enroll.click();
    await expect(page.getByRole('button', { name: '继续学习', exact: true })).toBeVisible();
    await page.goto('/learning');
    await page.getByRole('link', { name: '实操作业 →', exact: true }).click();
    await page.getByRole('link', { name: assignmentTitle, exact: true }).click();
    await expect(page.getByRole('heading', { name: assignmentTitle, exact: true })).toBeVisible();
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
    expect((await protectedRequest(page, submissionPath, 'POST', {
      ...body, submissionKey: crypto.randomUUID(),
    })).status()).toBe(409);

    await login(teacher, 'teacher', '/practice');
    expect((await protectedRequest(teacher, '/teacher/learning-hub/practice/' + fixture.notesCourse + '/submissions')).status()).toBe(403);
    await teacher.getByRole('combobox', { name: '实操课程' }).click();
    await teacher.getByRole('option', { name: courseName, exact: true }).click();
    await teacher.getByRole('button', { name: '查看作品' }).click();
    const dialog = teacher.getByRole('dialog', { name: '作品与反馈' });
    await expect(dialog.getByText('第一版作品说明')).toBeVisible();
    await dialog.getByRole('textbox', { name: '教师反馈' }).fill('补充成果验证。');
    await dialog.getByRole('button', { name: '需要修改', exact: true }).click();
    await expect(dialog.getByRole('heading', { name: '第 1 次提交 · 需要修改' })).toBeVisible();
    await api(teacher, 'PUT', work + '/' + first.id + '/feedback', {
      status: 'NEEDS_REVISION', feedback: '补充成果验证。',
    });

    async function feedbackCount() {
      const result = await api(page, 'POST', '/services/message/inboxes/query');
      return result.list.filter((row: { targetId: string; targetType: string }) =>
        row.targetType === 'PRACTICE' && row.targetId === course,
      ).length;
    }
    await expect.poll(feedbackCount).toBe(1);
    await page.goto('/messages');
    await page.getByRole('link', { name: '查看作业反馈', exact: true }).first().click();
    await expect(page.getByText('补充成果验证。')).toBeVisible();
    await page.getByRole('textbox', { name: '作品说明' }).fill('第二版，已补充成果验证。');
    await page.getByRole('button', { name: '修改后重交' }).click();
    await expect(page.getByRole('heading', { name: '第 2 次提交 · 待反馈' })).toBeVisible();

    await teacher.reload();
    await teacher.getByRole('combobox', { name: '实操课程' }).click();
    await teacher.getByRole('option', { name: courseName, exact: true }).click();
    await teacher.getByRole('button', { name: '查看作品' }).click();
    await dialog.getByRole('textbox', { name: '教师反馈' }).fill('通过，验证充分。');
    await dialog.getByRole('button', { name: '通过作业' }).click();
    await expect(dialog.getByRole('heading', { name: '第 2 次提交 · 已通过' })).toBeVisible();
    await expect.poll(feedbackCount).toBe(2);
    await page.reload();
    await expect(page.getByRole('heading', { name: '第 2 次提交 · 已通过' })).toBeVisible();
    await expect(page.getByText('通过，验证充分。')).toBeVisible();
    await expect(page.getByRole('heading', { name: '第 1 次提交 · 需要修改' })).toBeVisible();
    await expect(page.getByRole('button', { name: '修改后重交' })).toHaveCount(0);
  } finally {
    await teacherContext.close();
    await adminContext.close();
  }
});
