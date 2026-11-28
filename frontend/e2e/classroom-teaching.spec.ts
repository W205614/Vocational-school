import { expect, test, type Page } from '@playwright/test';
import { accounts, api, fixture, login } from './helpers';

const admin = '/admin/learning-hub/classrooms';
const staff = '/teacher/learning-hub/classrooms';
const learner = '/learning-hub/classrooms';

function chinaTime(minutes: number) {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium',
  }).format(new Date(Date.now() + minutes * 60_000)).replace(' ', 'T');
}

async function requestStatus(page: Page, method: string, path: string, data?: unknown) {
  const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
  return (await page.request.fetch('/api/v2' + path, {
    method, data, headers: { Authorization: 'Bearer ' + token, 'Idempotency-Key': crypto.randomUUID() },
  })).status();
}

async function operation(page: Page, service: string, path: string, data?: unknown) {
  const started = await api(page, 'POST', path, data, crypto.randomUUID());
  let current = started;
  await expect.poll(async () => {
    current = await api(page, 'GET', '/operations/' + service + '/' + started.operationId);
    return current.status;
  }, { timeout: 25_000 }).not.toBe('PENDING');
  expect(current.status, JSON.stringify(current)).toBe('SUCCEEDED');
  return current.result;
}

test('classroom teaching isolates concurrent cohorts, orders and exam access', async ({ page, browser }, info) => {
  test.skip(info.project.name !== 'student');
  test.setTimeout(180_000);
  const adminOrigin = process.env.ZXT_ADMIN_URL || 'http://127.0.0.1:23501';
  const studentOrigin = process.env.ZXT_STUDENT_URL || 'http://127.0.0.1:23500';
  const adminContext = await browser.newContext({ baseURL: adminOrigin });
  const teacherContext = await browser.newContext({ baseURL: adminOrigin });
  const assistantContext = await browser.newContext({ baseURL: adminOrigin });
  const otherStudentContext = await browser.newContext({ baseURL: studentOrigin });
  const principal = await adminContext.newPage();
  const teacher = await teacherContext.newPage();
  const assistant = await assistantContext.newPage();
  const other = await otherStudentContext.newPage();
  const created: string[] = [];
  try {
    const label = '班级教务 ' + crypto.randomUUID().slice(0, 7);
    await login(principal, 'admin', '/classrooms');
    const settings = {
      courseId: fixture.classroomCourse, startsAt: chinaTime(-90), endsAt: chinaTime(90),
      registrationOpens: chinaTime(-100), registrationCloses: chinaTime(85),
      capacity: 1, passPercent: 60,
    };
    const a = await api(principal, 'POST', admin, { ...settings, name: label + ' A 班' });
    const b = await api(principal, 'POST', admin, { ...settings, name: label + ' B 班' });
    created.push(a.id, b.id);
    await api(principal, 'PUT', admin + '/' + a.id + '/staff/' + accounts.teacher.id, { duty: 'TEACHER' });
    await api(principal, 'PUT', admin + '/' + b.id + '/staff/' + accounts.classroom_teacher.id, { duty: 'TEACHER' });
    await api(principal, 'PUT', admin + '/' + a.id + '/staff/' + accounts.classroom_teacher.id, { duty: 'ASSISTANT' });
    for (const id of [a.id, b.id])
      await api(principal, 'PUT', admin + '/' + id + '/state', { state: 'OPEN' });
    const assignments = [];
    for (const [index, id] of [a.id, b.id].entries()) {
      assignments.push(await api(principal, 'POST', staff + '/' + id + '/activities', {
        kind: 'ASSIGNMENT', title: '提交作品', instructions: '说明你的实现和验收结果。',
        scheduledAt: chinaTime(-45), closesAt: chinaTime(index ? 40 : -10), weight: 50,
      }));
    }
    const [homeworkA, homeworkB] = assignments;
    const paper = await operation(principal, 'exam', '/admin/exam-papers', {
      courseId: fixture.classroomCourse, sectionId: fixture.classroomExam,
      questionIds: [fixture.classroomObjective, fixture.classroomSubjective],
      classroomOnly: true, passPercent: 60,
    });
    const [examA, examB] = await Promise.all([a, b].map((item) =>
      api(principal, 'POST', staff + '/' + item.id + '/activities', {
        kind: 'EXAM', title: '班级测验', instructions: '按时答题。',
        paperId: paper.id, scheduledAt: chinaTime(-20), closesAt: chinaTime(40), weight: 50, maxAttempts: 1,
      })));
    await principal.goto('/classrooms?classId=' + a.id);
    await expect(principal.getByRole('heading', { name: a.name })).toBeVisible();
    await login(page, 'student', '/courses/' + fixture.classroomCourse);
    await login(other, 'classroom_student', '/courses/' + fixture.classroomCourse);
    await login(teacher, 'teacher', '/classrooms?classId=' + a.id);
    await login(assistant, 'classroom_teacher', '/classrooms?classId=' + a.id);

    async function enroll(student: Page, room: { id: string; name: string }) {
      const listing = student.locator('.toolbar').filter({ has: student.getByText(room.name, { exact: true }) });
      await listing.getByRole('button', { name: /报名/ }).click();
      if (await student.waitForURL(/\/(orders|classrooms)(\?|$)/, { timeout: 25_000 }).then(() => student.url().includes('/orders'))) {
        await student.getByRole('link', { name: '打开本地模拟支付 →' }).click();
        await student.getByRole('button', { name: '确认模拟支付成功' }).click();
      }
      await expect.poll(async () => {
        const memberships = await api(student, 'GET', learner);
        return memberships.find((item: any) => item.classroom.id === room.id)?.member.state;
      }).toBe('ACTIVE');
      return (await api(student, 'GET', learner)).find((item: any) => item.classroom.id === room.id).member;
    }
    const [memberA, memberB] = await Promise.all([enroll(page, a), enroll(other, b)]);

    const sample = { requestKey: crypto.randomUUID(), assignmentVersion: 1, description: '本班的实操作业' };
    expect(await requestStatus(page, 'POST', learner + '/' + a.id + '/activities/' + homeworkA.id + '/submissions', sample)).toBe(409);
    expect(await requestStatus(page, 'GET', learner + '/' + b.id)).toBe(403);
    expect(await requestStatus(teacher, 'GET', staff + '/' + b.id + '/grades')).toBe(403);
    expect(await requestStatus(teacher, 'GET', '/teacher/classroom-exams/' + examB.id + '/attempts')).toBe(403);
    const submissionPath = learner + '/' + b.id + '/activities/' + homeworkB.id + '/submissions';
    const submitted = await api(other, 'POST', submissionPath, sample);
    expect((await api(other, 'POST', submissionPath, sample)).id).toBe(submitted.id);
    const review = staff + '/' + b.id + '/activities/' + homeworkB.id + '/submissions/' + submitted.id;
    expect(await requestStatus(teacher, 'PUT', review, { state: 'REVIEWED', score: 80, feedback: '越权' })).toBe(403);
    await api(assistant, 'PUT', review, { state: 'REVIEWED', score: 80, feedback: '本班教师反馈' });
    await api(assistant, 'POST', staff + '/' + b.id + '/activities/' + homeworkB.id + '/grades', { memberId: memberB.id });

    const question = await api(other, 'POST', learner + '/' + b.id + '/discussion', { title: '班级内问题', body: '问题说明' });
    expect(await requestStatus(teacher, 'GET', staff + '/' + b.id + '/discussion')).toBe(403);
    await api(assistant, 'POST', staff + '/' + b.id + '/discussion', { parentId: question.id, body: '本班回复' });
    await other.goto('/classrooms?classId=' + b.id + '&memberId=' + memberB.id);
    await expect(other.getByRole('heading', { name: '我的班级成绩' })).toBeVisible();
    await expect(other.getByText('本班教师反馈', { exact: true })).toBeVisible();
    await expect(other.getByText('教学人员 · 本班回复', { exact: true })).toBeVisible();

    const attempt = await operation(other, 'exam', '/classroom-exams/' + examB.id + '/attempts');
    expect(attempt.class_activity_id).toBe(examB.id);
    expect(await requestStatus(page, 'POST', '/classroom-exams/' + examB.id + '/attempts')).toBe(403);
    const marked = await operation(other, 'exam', '/exam-attempts/' + attempt.id + '/submit', {
      answers: { [fixture.classroomObjective]: '1', [fixture.classroomSubjective]: '班级考试主观答案' },
    });
    expect(marked.status).toBe('WAIT_GRADING');
    expect(await requestStatus(teacher, 'GET', '/teacher/classroom-exam-attempts/' + attempt.id)).toBe(403);
    const grader = await api(assistant, 'GET', '/teacher/classroom-exam-attempts/' + attempt.id);
    expect(grader.class_member_id).toBe(memberB.id);
    const answer = grader.answers.find((item: any) => item.question_id === fixture.classroomSubjective);
    await operation(assistant, 'exam', '/teacher/exam-attempts/' + attempt.id + '/grades', {
      questionId: fixture.classroomSubjective, score: 8, version: answer.version, feedback: '主观题已复核',
    });
    const sealed = await api(other, 'GET', '/exam-attempts/' + attempt.id);
    expect(sealed.status).toBe('FINISHED');
    expect(sealed.score).toBeUndefined();
    expect(sealed.questions.every((question: any) => !('answer' in question))).toBe(true);
    const assistantPublishing = await requestStatus(assistant, 'POST',
      staff + '/' + a.id + '/activities/' + homeworkA.id + '/grades', { memberId: memberA.id });
    expect(assistantPublishing).toBe(403);
    expect(await requestStatus(page, 'GET', '/exam-attempts/' + attempt.id)).toBe(400);
    expect((await api(page, 'GET', '/classroom-exams/' + examA.id + '/attempts'))).toEqual([]);
  } finally {
    try {
      for (const id of created) {
        const members = await api(principal, 'GET', staff + '/' + id + '/members');
        for (const member of members.filter((item: any) => item.state === 'ACTIVE'))
          await api(principal, 'PUT', admin + '/' + id + '/members/' + member.memberId, { reason: '隔离验收结束' });
      }
    } finally {
      await Promise.all([adminContext.close(), teacherContext.close(), assistantContext.close(), otherStudentContext.close()]);
    }
  }
});
