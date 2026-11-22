import { test, expect } from '@playwright/test';
import { login } from './helpers';

test('teacher opens the next practical submission inside the original course scope', async ({ page }, info) => {
  test.skip(info.project.name !== 'admin');
  const courseId = '900000000000000021';
  const firstId = '900000000000000022', nextId = '900000000000000023';
  const firstStudent = '900000000000000024', nextStudent = '900000000000000025';
  const base = '**/api/v2/teacher/learning-hub/practice';
  const detail = `${base}/${courseId}`;
  const time = '2026-11-22T08:00:00';
  const assignment = { courseId, title: '练习题', instructions: '解释实现步骤', publishedAt: time, version: 1 };
  const submission = (id: string, userId: string, status = 'SUBMITTED') => ({
    id, userId, revision: 1, assignmentVersion: 1, description: '作品 ' + id, status,
    submittedAt: time, feedback: status === 'PASSED' ? '通过' : null,
  });
  const success = (data: unknown) => ({ code: 200, msg: 'OK', data });
  await page.route('**/api/v2/teacher/courses?**', (route) => route.fulfill({
    json: success({ list: [{ id: courseId, name: '教师的课程' }], total: 1 }),
  }));
  await page.route(detail, (route) => route.fulfill({ json: success({
    assignment, versions: [{ ...assignment, effectiveAt: time, changeReason: '首次发布' }],
  }) }));
  await page.route(detail + '/submissions?**', (route) => route.fulfill({ json: success({ list: [], total: 0 }) }));
  for (const [student, id] of [[firstStudent, firstId], [nextStudent, nextId]]) {
    await page.route(detail + '/submissions/' + student, (route) => route.fulfill({
      json: success([submission(id!, student!)]),
    }));
  }
  await page.route(detail + '/submissions/' + firstId + '/feedback', (route) => route.fulfill({
    json: success(submission(firstId, firstStudent, 'PASSED')),
  }));
  await page.route(base + '/pending?**', (route) => {
    const params = new URL(route.request().url()).searchParams;
    expect(params.get('courseId')).toBe(courseId);
    expect(params.get('pageSize')).toBe('1');
    return route.fulfill({ json: success({ list: [{ courseId, studentId: nextStudent, submissionId: nextId }] }) });
  });
  await login(page, 'teacher', `/practice?courseId=${courseId}&studentId=${firstStudent}&submissionId=${firstId}`
    + `&from=teaching&workCourse=${courseId}&practicePage=3&pendingOnly=false`);
  const dialog = page.getByRole('dialog', { name: '作品与反馈' });
  await dialog.getByRole('textbox', { name: '教师反馈' }).fill('通过');
  await dialog.getByRole('button', { name: '通过作业' }).click();
  await dialog.getByRole('button', { name: '处理下一份作品' }).click();
  await expect(dialog.getByText('作品 ' + nextId, { exact: true })).toBeVisible();
  await expect(dialog.getByRole('textbox', { name: '教师反馈' })).toHaveValue('');
  await expect(page).toHaveURL(new RegExp('submissionId=' + nextId));
  const back = new URL((await dialog.getByRole('link', { name: '返回教学待办 →' }).getAttribute('href'))!, page.url());
  expect(back.searchParams.get('workCourse')).toBe(courseId);
  expect(back.searchParams.get('practicePage')).toBe('3');
  expect(back.searchParams.get('pendingOnly')).toBe('false');
});

test('teacher grading loads automatically, requires an explicit score and refreshes after grading', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'admin');
  let graded = false,
    listRequests = 0,
    gradeRequests = 0;
  const attemptId = '900000000000000001',
    nextId = '900000000000000006',
    courseId = '900000000000000004',
    questionId = '900000000000000002';
  const envelope = (data: unknown) => ({ code: 200, msg: 'OK', data });
  await page.route('**/api/v2/teacher/exam-attempts/page?**', async (route) => {
    listRequests++;
    const params = new URL(route.request().url()).searchParams;
    expect(params.get('courseId')).toBe(courseId);
    await route.fulfill({
      json: envelope({
        list: [{ id: graded ? nextId : attemptId, user_id: '900000000000000003', course_id: courseId }],
        total: 1,
      }),
    });
  });
  await page.route('**/api/v2/teacher/exam-attempts/' + attemptId, (route) =>
    route.fulfill({
      json: envelope({
        id: attemptId,
        status: graded ? 'FINISHED' : 'WAIT_GRADING',
        questions: [
          { question_id: questionId, name: 'Isolated grading question', score: 10, type: 5 },
        ],
        answers: [
          {
            question_id: questionId,
            response: 'Isolated student response',
            score: graded ? 0 : null,
            feedback: graded ? 'Explicit zero' : '',
            version: 0,
          },
        ],
      }),
    }),
  );
  await page.route('**/api/v2/teacher/exam-attempts/' + nextId, (route) => route.fulfill({
    json: envelope({ id: nextId, status: 'WAIT_GRADING', questions: [], answers: [] }),
  }));
  const operation = {
    operationId: 'isolated-grading-operation',
    status: 'SUCCEEDED',
    result: {},
    errorCode: null,
    errorMessage: null,
  };
  await page.route('**/api/v2/teacher/exam-attempts/' + attemptId + '/grades', async (route) => {
    gradeRequests++;
    expect(route.request().postDataJSON().score).toBe(0);
    graded = true;
    await route.fulfill({ json: envelope(operation) });
  });
  await page.route('**/api/v2/operations/exam/isolated-grading-operation', (route) =>
    route.fulfill({ json: envelope(operation) }),
  );
  await login(page, 'teacher', '/exams?pendingCourseId=' + courseId);
  const row = page.locator('.el-table__row').filter({ hasText: attemptId });
  await expect(row).toBeVisible();
  expect(listRequests).toBe(1);
  await row.getByRole('button', { name: '查看答卷', exact: true }).click();
  const submit = page.getByRole('button', { name: '提交评分', exact: true }),
    score = page.getByRole('spinbutton');
  await expect(submit).toBeDisabled();
  await score.fill('');
  await expect(submit).toBeDisabled();
  await score.fill('0.5');
  await expect(submit).toBeDisabled();
  expect(gradeRequests).toBe(0);
  await score.fill('0');
  await page.getByPlaceholder('评分反馈').fill('Explicit zero');
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page.getByText('已评分 0 · Explicit zero', { exact: true })).toBeVisible();
  await expect(row).toHaveCount(0);
  expect(listRequests).toBe(2);
  expect(gradeRequests).toBe(1);
  await page.getByRole('button', { name: '处理下一份答卷' }).click();
  await expect(page.getByRole('heading', { name: '考试 ' + nextId + ' · 等待教师评分' })).toBeFocused();
  expect(listRequests).toBe(3);
});

test('teacher answers show the question details and never carry replies or drafts into another question', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'admin');
  const envelope = (data: unknown) => ({ code: 200, msg: 'OK', data });
  let failSecond = true;
  const questions = [
    {
      id: '900000000000000011',
      title: 'Isolated question A',
      description: 'Full details for question A',
      answerTimes: 1,
    },
    {
      id: '900000000000000012',
      title: 'Isolated question B',
      description: 'Full details for question B',
      answerTimes: 0,
    },
  ];
  await page.route('**/api/v2/teacher/courses?**', (route) =>
    route.fulfill({
      json: envelope({
        list: [{ id: '900000000000000010', name: 'Isolated teaching course' }],
        total: 1,
      }),
    }),
  );
  await page.route('**/api/v2/teacher/learning-hub?**', (route) =>
    route.fulfill({ json: envelope({ list: [], total: 0 }) }),
  );
  await page.route('**/api/v2/teacher/exam-statistics?**', (route) =>
    route.fulfill({ json: envelope({ list: [], total: 0 }) }),
  );
  await page.route('**/api/v2/learning-hub/teaching/questions?**', (route) =>
    route.fulfill({ json: envelope({ list: questions, total: questions.length }) }),
  );
  await page.route('**/api/v2/services/learning/replies/page?**', async (route) => {
    const question = new URL(route.request().url()).searchParams.get('questionId');
    if (question === questions[1].id && failSecond) {
      await route.fulfill({
        status: 503,
        json: { code: 503, msg: 'Isolated replies unavailable', data: null },
      });
      return;
    }
    await route.fulfill({
      json: envelope({
        list:
          question === questions[0].id
            ? [{ id: '900000000000000013', content: 'Only belongs to question A' }]
            : [],
        total: question === questions[0].id ? 1 : 0,
      }),
    });
  });
  await login(page, 'teacher', '/teaching');
  await page
    .locator('.review-row')
    .filter({ hasText: questions[0].title })
    .getByRole('button', { name: '查看与答疑', exact: true })
    .click();
  const dialog = page.getByRole('dialog', { name: '课程答疑', exact: true });
  await expect(dialog.getByText(questions[0].description, { exact: true })).toBeVisible();
  await expect(dialog.getByText('Only belongs to question A', { exact: true })).toBeVisible();
  await dialog.getByPlaceholder('写下教师解答').fill('Draft for A only');
  await dialog.getByRole('button', { name: '关闭此对话框', exact: true }).click();
  await page
    .locator('.review-row')
    .filter({ hasText: questions[1].title })
    .getByRole('button', { name: '查看与答疑', exact: true })
    .click();
  await expect(dialog.getByText(questions[1].description, { exact: true })).toBeVisible();
  await expect(dialog.getByText('Only belongs to question A', { exact: true })).toHaveCount(0);
  await expect(dialog.getByPlaceholder('写下教师解答')).toHaveValue('');
  await expect(dialog.getByText(/Isolated replies unavailable/)).toBeVisible();
  await dialog.getByPlaceholder('写下教师解答').fill('Draft for B');
  await expect(dialog.getByRole('button', { name: '发布解答', exact: true })).toBeDisabled();
  failSecond = false;
  await dialog.getByRole('button', { name: '重新加载答疑', exact: true }).click();
  await expect(dialog.getByRole('button', { name: '发布解答', exact: true })).toBeEnabled();
});

test('teacher student and course filters restart both learning and exam pagination', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'admin');
  const envelope = (data: unknown) => ({ code: 200, msg: 'OK', data });
  const firstCourse = '900000000000000021',
    secondCourse = '900000000000000022';
  await page.route('**/api/v2/teacher/courses?**', (route) =>
    route.fulfill({
      json: envelope({
        list: [
          { id: firstCourse, name: 'First isolated course' },
          { id: secondCourse, name: 'Second isolated course' },
        ],
        total: 2,
      }),
    }),
  );
  await page.route('**/api/v2/teacher/learning-hub?**', (route) =>
    route.fulfill({
      json: envelope({
        summary: { students: 40, completedStudents: 0, completionRate: 0, unansweredQuestions: 0 },
        list: [],
        total: 40,
      }),
    }),
  );
  await page.route('**/api/v2/teacher/exam-statistics?**', (route) =>
    route.fulfill({ json: envelope({ list: [], total: 40 }) }),
  );
  await page.route('**/api/v2/learning-hub/teaching/questions?**', (route) =>
    route.fulfill({ json: envelope({ list: [], total: 0 }) }),
  );
  await login(page, 'teacher', '/teaching');
  const query = page.getByRole('button', { name: '查看课程学情', exact: true });
  await expect(query).toBeEnabled();
  async function secondPages() {
    for (let index = 0; index < 2; index++) {
      await page
        .locator('.el-pagination')
        .nth(index)
        .locator('.el-pager li.number')
        .filter({ hasText: /^2$/ })
        .click();
      await expect(query).toBeEnabled();
    }
  }
  function resetResponses(courseId: string, studentId?: string) {
    return Promise.all(
      ['/teacher/learning-hub', '/teacher/exam-statistics'].map((endpoint) =>
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            url.pathname.endsWith(endpoint) &&
            url.searchParams.get('courseId') === courseId &&
            url.searchParams.get('pageNo') === '1' &&
            (!studentId ||
              url.searchParams.get(endpoint.endsWith('learning-hub') ? 'userId' : 'studentId') ===
                studentId)
          );
        }),
      ),
    );
  }
  await secondPages();
  await page.getByRole('textbox', { name: '筛选学生编号', exact: true }).fill('900000000000000023');
  const studentReset = resetResponses(firstCourse, '900000000000000023');
  await query.click();
  await studentReset;
  await expect(query).toBeEnabled();
  await secondPages();
  await page
    .locator('.el-select')
    .filter({ has: page.getByRole('combobox', { name: '教学课程', exact: true }) })
    .click();
  const courseReset = resetResponses(secondCourse);
  await page.getByRole('option', { name: 'Second isolated course', exact: true }).click();
  await courseReset;
});
