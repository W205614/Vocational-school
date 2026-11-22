import { test, expect, type Locator, type Page } from '@playwright/test';
import { accounts, api, fixture, login } from './helpers';

const inventory = '/admin/course/study-information';

async function saveSupplement(page: Page, editor: Locator, courseId: string) {
  const response = page.waitForResponse((r) =>
    r.request().method() === 'PATCH' &&
    new URL(r.url()).pathname === '/api/v2' + inventory + '/' + courseId,
  );
  await editor.getByRole('button', { name: '保存补录' }).click();
  const saved = await response;
  expect(saved.status()).toBe(200);
  await expect(editor.getByText('已更新学生可见的选课资料')).toBeVisible();
}

test('historical course metadata can be supplemented while enrollment and independent draft edits persist', async ({
  page,
  browser,
}, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(90_000);
  const adminContext = await browser.newContext({
    baseURL: process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501',
  });
  const admin = await adminContext.newPage();
  const visitorContext = await browser.newContext({ baseURL: process.env.ZXT_STUDENT_URL || 'http://127.0.0.1:23500' });
  const visitor = await visitorContext.newPage();
  const publicUrl = '/public/courses/' + fixture.studyCourse;
  const coursePath = '/courses/' + fixture.studyCourse;

  try {
    await login(page, 'student', coursePath);
    const enroll = page.getByRole('button', { name: '免费报名', exact: true });
    const continueLearning = page.getByRole('button', { name: '继续学习', exact: true });
    await expect(enroll.or(continueLearning)).toBeVisible();
    if (await enroll.isVisible()) await enroll.click();
    await expect(continueLearning).toBeVisible({ timeout: 20000 });
    const before = await api(page, 'GET', publicUrl);
    const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
    const headers = { Authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() };
    const denied = await page.request.patch('/api/v2' + inventory + '/' + fixture.studyCourse, {
      headers, data: { status: 2, estimatedStudyMinutes: 90 },
    });
    expect(denied.status()).toBe(403);
    expect((await page.request.get('/api/v2' + inventory + '/' + fixture.studyCourse + '/changes', { headers })).status()).toBe(403);

    await login(admin, 'admin', '/courses');
    await admin.locator('main').getByRole('link', { name: '选课资料盘点' }).click();
    await expect(admin.getByRole('heading', { name: '选课资料盘点' })).toBeVisible();
    const byTime = '?pageNo=1&pageSize=1&missing=ESTIMATED_TIME&keyword=';
    const derived = await api(admin, 'GET', inventory + '?keyword=' + encodeURIComponent(fixture.name));
    expect(Number(derived.list[0].videoEstimateMinutes)).toBeGreaterThan(0);
    expect(derived.list[0].missingFields).not.toContain('ESTIMATED_TIME');
    const results = await api(admin, 'GET', inventory + byTime + encodeURIComponent(fixture.studyName));
    expect(Number(results.total)).toBe(1);
    expect(results.list[0].missingFields).toEqual(['OUTCOMES', 'PREREQUISITES', 'DIFFICULTY', 'ESTIMATED_TIME']);
    expect(results.list[0].counts).toEqual({ enrolled: '1', started: '0' });
    const byStudents = '?pageNo=1&pageSize=1&missing=ALL&keyword=' + fixture.marker + '&sort=';
    const enrolled = await api(admin, 'GET', inventory + byStudents + 'ENROLLED');
    expect(enrolled.list[0].id).toBe(fixture.studyCourse);
    const started = await api(admin, 'GET', inventory + byStudents + 'STARTED');
    expect(started.list[0]).toMatchObject({ id: fixture.rankedStudyCourse, counts: { enrolled: '1', started: '1' } });
    await admin.getByRole('textbox', { name: '课程名称', exact: true }).fill(fixture.studyName);
    await admin.locator('.el-select').filter({
      has: admin.getByRole('combobox', { name: '缺失字段', exact: true }),
    }).click();
    await admin.getByRole('option', { name: '缺预计学习时长', exact: true }).click();
    const row = admin.getByRole('row', { name: new RegExp(fixture.studyName) });
    await expect(row.getByText('待补录')).toBeVisible();
    await row.getByRole('button', { name: '补录资料', exact: true }).click();
    const editor = admin.getByRole('dialog', { name: '补录选课资料' });
    await expect(editor.getByRole('textbox', { name: '实操内容' })).toHaveValue('Existing practice summary');
    const historyPath = inventory + '/' + fixture.studyCourse + '/changes?pageNo=1&pageSize=10';
    expect((await api(admin, 'GET', historyPath)).list).toEqual([]);
    await editor.getByRole('textbox', { name: '学习成果' }).fill('掌握独立完成完整示例的方法');
    await editor.getByRole('textbox', { name: '先修要求' }).fill('无');
    await editor.locator('.el-select').filter({
      has: admin.getByRole('combobox', { name: '课程难度', exact: true }),
    }).click();
    await admin.getByRole('option', { name: '进阶', exact: true }).click();
    await editor.getByRole('spinbutton', { name: '预计学习时长（分钟）' }).fill('90');
    await saveSupplement(admin, editor, fixture.studyCourse);
    await expect(row).toHaveCount(0);
    const history = await api(admin, 'GET', historyPath);
    expect(history.list).toHaveLength(4);
    expect(history.list.map((entry: { field: string }) => entry.field).sort()).toEqual(
      ['DIFFICULTY', 'ESTIMATED_TIME', 'OUTCOMES', 'PREREQUISITES'],
    );
    expect(history.list.every((entry: { operatorId: string; operatorName: string; changedAt: string }) =>
      entry.operatorId === accounts.admin.id && entry.operatorName && entry.changedAt)).toBeTruthy();
    await editor.getByRole('button', { name: '查看字段变更' }).click();
    const audit = editor.getByRole('region', { name: '字段变更记录' });
    await expect(audit.getByText('新值 1 小时 30 分钟', { exact: true })).toBeVisible();
    await expect(audit.getByText('操作者 ' + history.list[0].operatorName, { exact: true })).toHaveCount(4);
    await editor.getByRole('button', { name: '查看学生页资料' }).click();
    const preview = editor.getByRole('region', { name: '学生课程详情' });
    await expect(preview.getByText('掌握独立完成完整示例的方法')).toBeVisible();
    await expect(preview.getByText('约 1 小时 30 分钟')).toBeVisible();

    const draft = await api(admin, 'GET', '/admin/course/courses/baseInfo/' + fixture.studyCourse + '?see=false');
    expect(draft.learningOutcomes).toBe('Independent draft outcome');
    expect(draft.detail).toBe('Historic draft details');
    expect(draft.prerequisites).toBe('无');
    expect(draft.estimatedStudyMinutes).toBe(90);

    await page.reload();
    const facts = page.locator('.course-facts');
    await expect(facts.getByText('掌握独立完成完整示例的方法')).toBeVisible();
    await expect(facts.getByText('Existing practice summary')).toBeVisible();
    await expect(facts.getByText('约 1 小时 30 分钟')).toBeVisible();
    await expect(continueLearning).toBeVisible();
    const after = await api(page, 'GET', publicUrl);
    expect(after).toMatchObject({
      status: before.status, price: before.price, purchaseEndTime: before.purchaseEndTime,
      introduce: before.introduce, detail: before.detail, difficulty: 'INTERMEDIATE',
    });

    const filter = '?keyword=' + encodeURIComponent(fixture.studyName) +
      '&difficulty=INTERMEDIATE&minStudyMinutes=61&maxStudyMinutes=180&hasTrial=false';
    const projected = { id: fixture.studyCourse, difficulty: 'INTERMEDIATE', estimatedStudyMinutes: 90, hasTrial: false };
    await expect.poll(async () => {
      const response = await page.request.get('/api/v2/public/courses' + filter);
      return (await response.json()).data?.list;
    }, { timeout: 30_000 }).toEqual([expect.objectContaining(projected)]);
    expect((await api(page, 'GET', '/services/search/courses/portal' + filter)).list).toEqual([expect.objectContaining(projected)]);
    expect((await api(page, 'GET', '/services/search/courses/portal' + filter.replace('hasTrial=false', 'hasTrial=true'))).list).toEqual([]);
    await visitor.goto('/?keyword=' + encodeURIComponent(fixture.studyName));
    for (const [label, option] of [
      ['筛选课程难度', '进阶'], ['筛选预计学习时长', '1 至 3 小时'], ['筛选试看', '未开放试看'],
    ]) {
      await visitor.locator('.el-select').filter({
        has: visitor.getByRole('combobox', { name: label, exact: true }),
      }).click();
      await visitor.getByRole('option', { name: option, exact: true }).click();
    }
    const tile = visitor.locator('.course-tile').filter({ hasText: fixture.studyName });
    await expect(tile.getByText('约 1 小时 30 分钟', { exact: true })).toBeVisible();
    await expect(tile.getByText('未开放试看', { exact: true })).toBeVisible();
  } finally {
    await visitorContext.close();
    await adminContext.close();
  }
});

test('concurrent course editors keep local changes and explicitly reconcile an outdated field', async ({
  browser,
}, info) => {
  test.skip(info.project.name !== 'student');
  const baseURL = process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501';
  const firstContext = await browser.newContext({ baseURL });
  const secondContext = await browser.newContext({ baseURL });
  const first = await firstContext.newPage();
  const second = await secondContext.newPage();
  const name = 'Browser study reference ' + fixture.marker;
  const course = inventory + '/' + fixture.rankedStudyCourse;
  const firstOutcome = '完成同事先发布的学习成果';
  const secondOutcome = '完成当前草稿中的学习成果';
  const prerequisites = '提前安装课程工具';
  const practice = '保留其他同事补录的独立实操内容';
  try {
    // Open both editors before either publishes a change.
    for (const admin of [first, second]) {
      await login(admin, 'admin', '/course-study-information');
      await admin.getByRole('textbox', { name: '课程名称', exact: true }).fill(name);
      await admin.getByRole('button', { name: '查询', exact: true }).click();
      await admin.getByRole('row', { name: new RegExp(name) }).getByRole('button', { name: '补录资料' }).click();
      await expect(admin.getByRole('dialog', { name: '补录选课资料' })).toBeVisible();
    }
    const firstEditor = first.getByRole('dialog', { name: '补录选课资料' });
    const secondEditor = second.getByRole('dialog', { name: '补录选课资料' });
    const save = secondEditor.getByRole('button', { name: '保存补录' });
    await firstEditor.getByRole('textbox', { name: '学习成果' }).fill(firstOutcome);
    await secondEditor.getByRole('textbox', { name: '学习成果' }).fill(secondOutcome);
    await secondEditor.getByRole('textbox', { name: '先修要求' }).fill(prerequisites);
    await saveSupplement(first, firstEditor, fixture.rankedStudyCourse);

    const staleWrite = second.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/v2' + course && response.request().method() === 'PATCH');
    await save.click();
    expect((await staleWrite).status()).toBe(409);
    await expect(save).toBeDisabled();
    const afterConflict = await api(first, 'GET', course);
    expect(afterConflict.learningOutcomes).toBe(firstOutcome);
    expect(afterConflict.prerequisites).toBeFalsy();

    const token = await first.evaluate(() => sessionStorage.getItem('school-token'));
    const withoutBaseline = await first.request.patch('/api/v2' + course, {
      headers: { Authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() },
      data: { status: 2, practiceOverview: practice },
    });
    expect(withoutBaseline.status()).toBe(400);
    await api(first, 'PATCH', course, {
      status: 2, practiceOverview: practice, expectedValues: { PRACTICE: { value: null } },
    }, crypto.randomUUID());

    const conflict = secondEditor.getByRole('region', { name: '公开资料冲突' });
    await conflict.getByRole('button', { name: '载入当前公开值' }).click();
    await expect(conflict.getByText('当前公开值 ' + firstOutcome, { exact: true })).toBeVisible();
    await expect(conflict.getByText('你的草稿 ' + secondOutcome, { exact: true })).toBeVisible();
    await expect(secondEditor.getByRole('textbox', { name: '学习成果' })).toHaveValue(secondOutcome);
    await expect(secondEditor.getByRole('textbox', { name: '先修要求' })).toHaveValue(prerequisites);
    expect((await api(first, 'GET', course)).learningOutcomes).toBe(firstOutcome);
    await conflict.getByRole('button', { name: '重应用我的修改' }).click();
    await expect(secondEditor.getByRole('textbox', { name: '实操内容' })).toHaveValue(practice);
    await api(first, 'PATCH', course, {
      status: 2, difficulty: 'BEGINNER', expectedValues: { DIFFICULTY: { value: null } },
    }, crypto.randomUUID());
    await saveSupplement(second, secondEditor, fixture.rankedStudyCourse);
    expect(await api(first, 'GET', '/public/courses/' + fixture.rankedStudyCourse)).toMatchObject({
      learningOutcomes: secondOutcome, prerequisites, practiceOverview: practice, difficulty: 'BEGINNER',
    });
    const history = await api(first, 'GET', course + '/changes?pageNo=1&pageSize=10');
    expect(history.list).toHaveLength(5);
    expect(history.list).toEqual(expect.arrayContaining([
      expect.objectContaining({ field: 'OUTCOMES', previousValue: firstOutcome, newValue: secondOutcome }),
      expect.objectContaining({ field: 'PRACTICE', newValue: practice }),
    ]));
  } finally {
    await secondContext.close();
    await firstContext.close();
  }
});
