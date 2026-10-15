import {test, expect, type Page} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import {accounts, fixture, home, login, signIn, api} from './helpers';

/** Advanced scenarios require an explicitly synthetic, isolated deployment.
 * Never seed historical seasons or reward receipts into the retained deployment.
 * Reward fixture receipts must be produced by the actual outbox/consumer flow.
 */
type MotivationFixture = {
    synthetic: true;
    course: string;
    name: string;
    lesson: string;
    reviewAttempt: string;
    reviewQuestion: string;
    reviewName: string;
    question: string;
    questionTitle: string;
    unassignedCourse: string;
    historyMonth: string;
    historyName: string;
    historyStatus: string;
    historyPoints: number;
    badgeName: string;
    rewardRequestId: string;
    rewardCouponId: string;
    rewardMonth: string;
};
function data(): MotivationFixture {
    const result = JSON.parse(fs.readFileSync(path.join(home, 'motivation-fixture.json'), 'utf8'));
    expect(result.synthetic, 'Only the isolated synthetic fixture may run these scenarios').toBe(true);
    for (const key of ['course', 'lesson', 'reviewAttempt', 'reviewQuestion', 'question', 'unassignedCourse', 'rewardCouponId'])
        expect(result[key], key + ' must retain the exact long identifier').toMatch(/^\d+$/);
    return result;
}
const seasonStatus: Record<string, string> = {LEGACY_READONLY: '历史赛季', OPEN: '进行中', SETTLING: '结算中', BLOCKED: '等待核对', SETTLED: '已结算'};
async function choose(page: Page, label: string, option: string) {
    await page.getByRole('combobox', {name: label, exact: true}).click();
    await page.getByRole('option', {name: option, exact: true}).click();
}
async function operation(page: Page, service: string, submitted: any, expected = 'SUCCEEDED') {
    let result = submitted;
    await expect.poll(async () => {
        result = await api(page, 'GET', '/operations/' + service + '/' + submitted.operationId);
        return result.status;
    }, {timeout: 30000}).not.toBe('PENDING');
    expect(result.status, JSON.stringify(result)).toBe(expected);
    return result;
}
async function forbidden(page: Page, endpoint: string) {
    const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
    const response = await page.request.get('/api/v2' + endpoint, {headers: {Authorization: 'Bearer ' + token}});
    expect(response.status(), endpoint).toBe(403);
}
function assertPublicCatalogue(nodes: any[]) {
    for (const node of nodes) {
        expect(Object.keys(node).sort()).toEqual(['id', 'index', 'mediaDuration', 'name', 'sections', 'type']);
        expect(node).not.toHaveProperty('mediaId');
        expect(node).not.toHaveProperty('playUrl');
        assertPublicCatalogue(node.sections || []);
    }
}

test.beforeEach(async () => {
    test.skip(process.env.TJ_MOTIVATION_FIXTURE !== '1', 'Advanced motivation acceptance requires an isolated synthetic fixture');
});

test('visitor reads published curriculum and authentication returns to the requested purchase course', async ({page}, info) => {
    test.skip(info.project.name !== 'student');
    const privateWrites: string[] = [];
    page.on('request', request => {
        if (request.method() !== 'GET' && request.url().includes('/api/v2/services/trade/')) privateWrites.push(request.url());
    });
    const publicRequest = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/api/v2/public/courses/' + fixture.course));
    await page.goto('/courses/' + fixture.course);
    const response = await publicRequest;
    expect(response.status()).toBe(200);
    const published = (await response.json()).data;
    expect(published.name).toBe(fixture.name);
    expect(Number(published.status)).toBe(2);
    assertPublicCatalogue(published.chapters);
    await expect(page.getByRole('heading', {name: fixture.name, exact: true})).toBeVisible();
    await expect(page.getByRole('heading', {name: '课程目录', exact: true})).toBeVisible();
    await expect(page.locator('.syllabus')).toContainText('Browser video');
    await expect(page.locator('video')).toHaveCount(0);
    expect((await page.request.get('/api/v2/notes?pageNo=1&pageSize=20')).status()).toBe(401);
    await page.getByRole('button', {name: '加入购物车', exact: true}).click();
    await expect(page).toHaveURL(/\/login\?redirect=/);
    expect(new URL(page.url()).searchParams.get('redirect')).toBe('/courses/' + fixture.course);
    expect(privateWrites).toEqual([]);
    await signIn(page, 'student');
    await expect(page).toHaveURL(new RegExp('/courses/' + fixture.course + '$'));
    await expect(page.getByRole('heading', {name: fixture.name, exact: true})).toBeVisible();
});

test('student saves a real weekly plan and checks signed days, immutable historical rank and earned badges', async ({page}, info) => {
    test.skip(info.project.name !== 'student');
    test.setTimeout(120000);
    const m = data();
    await login(page, 'student', '/learning');
    const course = page.locator('.learning-row').filter({has: page.getByRole('link', {name: m.name, exact: true})});
    await expect(course).toHaveCount(1);
    await course.locator('summary').filter({hasText: '设置每周学习计划'}).click();
    const frequency = course.getByRole('spinbutton', {name: '每周学习小节数', exact: true});
    await expect(frequency).toHaveAttribute('aria-valuemin', '1');
    await expect(frequency).toHaveAttribute('aria-valuemax', '50');
    await frequency.fill('5');
    const saved = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/lessons/plans'));
    await course.getByRole('button', {name: '保存计划', exact: true}).click();
    expect((await saved).ok()).toBeTruthy();
    await expect(page.getByText('学习计划已保存', {exact: true})).toBeVisible();
    const overview = await api(page, 'GET', '/learning-hub/overview');
    expect(overview.plans.find((p: any) => String(p.courseId) === m.course).weekFreq).toBe(5);
    await expect(page.locator('.study-summary').filter({hasText: '本周计划'})).toContainText(String(overview.weekTotalPlan));
    await page.reload();
    await course.locator('summary').filter({hasText: '设置每周学习计划'}).click();
    await expect(frequency).toHaveValue('5');

    const signsBefore = await api(page, 'GET', '/sign-ins');
    const boardRequest = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/learning-hub/board'));
    await page.goto('/points');
    const initialBoard = (await (await boardRequest).json()).data;
    expect(initialBoard.month).toBe(signsBefore.month);
    if (!signsBefore.todaySigned) {
        await page.getByRole('button', {name: '今日签到', exact: true}).click();
        await expect(page.getByText('签到成功', {exact: true})).toBeVisible();
    }
    await expect(page.getByRole('button', {name: '今日已签到', exact: true})).toBeDisabled();
    const signed = await api(page, 'GET', '/sign-ins');
    expect(signed.todaySigned).toBe(true);
    const today = Number(new Intl.DateTimeFormat('en-US', {timeZone: 'Asia/Shanghai', day: 'numeric'}).format(new Date()));
    await expect(page.getByLabel('当月签到日历').getByLabel(today + '日已签到', {exact: true})).toBeVisible();
    await expect.poll(async () => (await api(page, 'GET', '/learning-hub/points?pageNo=1&pageSize=100')).list.some((p: any) => Number(p.type) === 2 && p.month === signed.month), {timeout: 30000}).toBe(true);
    await page.getByRole('tab', {name: '积分明细', exact: true}).click();
    await expect(page.locator('.el-table__row').filter({hasText: '每日签到'}).first()).toContainText('已到账');
    await page.getByRole('tab', {name: '积分排行榜', exact: true}).click();
    const historicalRequest = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/learning-hub/board') && new URL(r.url()).searchParams.get('month') === m.historyMonth);
    await choose(page, '选择赛季', m.historyName + ' · ' + seasonStatus[m.historyStatus]);
    const history = (await (await historicalRequest).json()).data;
    expect(history.status).toBe(m.historyStatus);
    expect(Number(history.myPoints)).toBe(m.historyPoints);
    expect(Number(history.myRank)).toBeGreaterThan(0);
    for (const row of history.list) for (const key of ['phone', 'mobile', 'username', 'password']) expect(row).not.toHaveProperty(key);
    await expect(page.locator('.season-panel')).toContainText('赛季积分 ' + m.historyPoints);
    await expect(page.getByText(/最终结算时间/)).toBeVisible();
    const again = await api(page, 'GET', '/learning-hub/board?month=' + m.historyMonth + '&pageNo=1&pageSize=20');
    expect(again.list).toEqual(history.list);
    await page.getByRole('tab', {name: '我的成就', exact: true}).click();
    const badges = await api(page, 'GET', '/learning-hub/achievements');
    expect(badges.list.some((b: any) => b.name === m.badgeName)).toBe(true);
    await expect(page.locator('.achievement-list').getByRole('heading', {name: m.badgeName, exact: true})).toBeVisible();
});

test('finished mistakes keep snapshot feedback and cross-device revision never alters the original grade', async ({page, browser}, info) => {
    test.skip(info.project.name !== 'student');
    test.setTimeout(120000);
    const m = data();
    await login(page, 'student', '/review');
    const endpoint = '/exam-review/' + m.reviewAttempt + '/' + m.reviewQuestion;
    let initial = await api(page, 'GET', endpoint);
    if (initial.mastered) {
        await operation(page, 'exam', await api(page, 'PUT', endpoint + '/state', {mastered: false, version: initial.version}, crypto.randomUUID()));
        initial = await api(page, 'GET', endpoint);
    }
    const original = await api(page, 'GET', '/exam-attempts/' + m.reviewAttempt);
    expect(original.status).toBe('FINISHED');
    const snapshot = original.questions.find((q: any) => String(q.question_id) === m.reviewQuestion);
    expect(snapshot.answer).toBe('1,2');
    expect(snapshot.analysis).toBe('Motivation snapshot analysis');
    await page.reload();
    await choose(page, '筛选错题课程', m.name);
    await choose(page, '筛选题型', '多选题');
    const row = page.locator('.review-row').filter({has: page.getByRole('heading', {name: m.reviewName, exact: true})});
    await row.getByRole('button', {name: '复习这道题', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: '复习错题', exact: true});
    await dialog.getByRole('textbox', {name: '复习答案', exact: true}).fill('1,2');
    await dialog.getByRole('button', {name: '提交复习', exact: true}).click();
    await expect(page.getByText('复习已记录，原考试成绩保持不变', {exact: true})).toBeVisible();
    await expect(dialog.getByRole('status').filter({hasText: '本次回答正确'})).toBeVisible();
    await expect(dialog).toContainText('标准答案：1,2');
    await expect(dialog).toContainText('Motivation snapshot analysis');
    if (initial.feedback) await expect(dialog).toContainText(String(initial.feedback));
    const revised = await api(page, 'GET', endpoint);
    expect(revised.reviews.length).toBe(initial.reviews.length + 1);
    expect(Number(revised.reviews[0].correct)).toBe(1);
    await dialog.getByRole('button', {name: '标记已掌握', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    await expect(row).toHaveCount(0);

    const deviceContext = await browser.newContext({baseURL: process.env.TJ_STUDENT_URL || 'http://127.0.0.1:23500'});
    try {
        const device = await deviceContext.newPage();
        await login(device, 'student', '/review');
        await choose(device, '筛选错题课程', m.name);
        await choose(device, '掌握状态', '已掌握');
        const retainedRow = device.locator('.review-row').filter({has: device.getByRole('heading', {name: m.reviewName, exact: true})});
        await retainedRow.getByRole('button', {name: '复习这道题', exact: true}).click();
        const retained = await api(device, 'GET', endpoint);
        expect(Number(retained.mastered)).toBe(1);
        expect(retained.reviews[0].response).toBe('1,2');
        expect(retained.reviews.length).toBe(revised.reviews.length);
        const retainedDialog = device.getByRole('dialog', {name: '复习错题', exact: true});
        await retainedDialog.locator('summary').filter({hasText: '最近练习记录'}).click();
        await expect(retainedDialog.locator('details').filter({hasText: '最近练习记录'})).toContainText('1,2');
        await expect(retainedDialog.locator('details').filter({hasText: '最近练习记录'})).toContainText('回答正确');
        await retainedDialog.getByRole('button', {name: '重新加入复习', exact: true}).click();
        await expect(retainedRow).toHaveCount(0);
        await choose(device, '掌握状态', '待复习');
        await expect(retainedRow).toBeVisible();
        const preserved = await api(device, 'GET', '/exam-attempts/' + m.reviewAttempt);
        expect(preserved.score).toBe(original.score);
        expect(preserved.passed).toBe(original.passed);
        expect(preserved.answers).toEqual(original.answers);
        expect(Number((await api(device, 'GET', endpoint)).mastered)).toBe(0);
    } finally {await deviceContext.close();}
});

test('teacher chooses a taught course, sees persisted learning and grading, answers a real question and is denied other courses', async ({page}, info) => {
    test.skip(info.project.name !== 'admin');
    test.setTimeout(120000);
    const m = data();
    await login(page, 'teacher', '/teaching');
    const taughtIds: string[] = [];
    let coursePage = 1;
    while (true) {
        const assigned = await api(page, 'GET', '/teacher/courses?pageNo=' + coursePage + '&pageSize=100');
        taughtIds.push(...assigned.list.map((c: any) => String(c.id)));
        if (!assigned.list.length || taughtIds.length >= Number(assigned.total)) break;
        coursePage++;
    }
    expect(taughtIds).toContain(m.course);
    expect(taughtIds).not.toContain(m.unassignedCourse);
    await choose(page, '教学课程', m.name);
    await page.getByRole('textbox', {name: '筛选学生编号', exact: true}).fill(accounts.student.id);
    const learningResponse = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/teacher/learning-hub') && new URL(r.url()).searchParams.get('courseId') === m.course && new URL(r.url()).searchParams.get('userId') === accounts.student.id);
    await page.getByRole('button', {name: '查看课程学情', exact: true}).click();
    const learning = (await (await learningResponse).json()).data;
    expect(learning.courseName).toBe(m.name);
    expect(Number(learning.total)).toBe(1);
    expect(String(learning.list[0].userId)).toBe(accounts.student.id);
    await expect(page.locator('.el-table__row').filter({hasText: learning.list[0].name}).first()).toContainText(String(learning.list[0].learnedSections) + ' / ' + learning.list[0].sections + ' 小节');
    const grading = await api(page, 'GET', '/teacher/exam-statistics?courseId=' + m.course + '&studentId=' + accounts.student.id);
    expect(grading.scope).toBe('GRADING_ASSIGNMENT');
    expect(grading.list.some((r: any) => String(r.student_id) === accounts.student.id && Number(r.finished) >= 1)).toBe(true);
    await expect(page.getByRole('heading', {name: '考试表现', exact: true})).toBeVisible();
    const question = page.locator('.review-row').filter({has: page.getByRole('heading', {name: m.questionTitle, exact: true})});
    await question.getByRole('button', {name: '查看与答疑', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: '课程答疑', exact: true});
    const content = '教师浏览器解答 ' + crypto.randomUUID();
    await dialog.getByPlaceholder('写下教师解答').fill(content);
    await dialog.getByRole('button', {name: '发布解答', exact: true}).click();
    await expect(dialog.getByText(content, {exact: true})).toBeVisible();
    const replies = await api(page, 'GET', '/services/learning/replies/page?questionId=' + m.question + '&pageNo=1&pageSize=100');
    expect(replies.list.some((r: any) => r.content === content && String(r.userId) === accounts.teacher.id)).toBe(true);
    await page.reload();
    await choose(page, '教学课程', m.name);
    await question.getByRole('button', {name: '查看与答疑', exact: true}).click();
    await expect(page.getByRole('dialog', {name: '课程答疑', exact: true}).getByText(content, {exact: true})).toBeVisible();
    await forbidden(page, '/teacher/learning-hub?courseId=' + m.unassignedCourse);
    await forbidden(page, '/learning-hub/teaching/questions?courseId=' + m.unassignedCourse);
    await forbidden(page, '/teacher/exam-attempts/' + fixture.expiredAttempt);
});

test('admin configures a future rule, current rules stay locked and real reward receipts match a student-owned ticket', async ({page, browser}, info) => {
    test.skip(info.project.name !== 'admin');
    test.setTimeout(120000);
    const m = data();
    await login(page, 'admin', '/seasons');
    const couponName = 'Future ' + crypto.randomUUID().slice(0, 8);
    await api(page, 'POST', '/admin/promotion/coupons', {name: couponName, specific: false, discountType: 3, thresholdAmount: 0, discountValue: 10, maxDiscountAmount: 10, totalNum: 2, userLimit: 1, obtainWay: 1}, crypto.randomUUID());
    const created = await api(page, 'GET', '/admin/promotion/coupons/page?name=' + encodeURIComponent(couponName) + '&pageNo=1&pageSize=20');
    expect(created.list).toHaveLength(1);
    const coupon = String(created.list[0].id);
    const seasons = (await api(page, 'GET', '/learning-hub/admin/seasons')).list;
    const now = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit'}).format(new Date()).replace('-', '');
    const future = seasons.filter((s: any) => s.month > now).sort((a: any, b: any) => a.month.localeCompare(b.month))[0];
    const current = seasons.find((s: any) => s.month === now);
    expect(future).toBeTruthy();expect(current).toBeTruthy();
    await page.reload();
    await choose(page, '管理赛季', future.name + ' · ' + seasonStatus[future.status]);
    await page.getByRole('button', {name: '新增奖励档位', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: '赛季奖励规则', exact: true});
    await dialog.getByRole('spinbutton', {name: '结束名次', exact: true}).fill('502');
    await dialog.getByRole('spinbutton', {name: '起始名次', exact: true}).fill('501');
    await choose(page, '奖励优惠券', couponName);
    await expect(dialog.getByRole('switch', {name: '启用发放', exact: true})).not.toBeChecked();
    await dialog.getByRole('button', {name: '保存规则', exact: true}).click();
    await expect(page.getByText('奖励规则已保存', {exact: true})).toBeVisible();
    const rules = (await api(page, 'GET', '/learning-hub/admin/reward-rules?month=' + future.month)).list;
    const persisted = rules.find((r: any) => String(r.couponId) === coupon);
    expect(persisted).toMatchObject({rankFrom: 501, rankTo: 502});expect(Boolean(persisted.enabled)).toBe(false);
    await expect(page.locator('.el-table__row').filter({hasText: coupon})).toContainText('未启用');
    await choose(page, '管理赛季', current.name + ' · ' + seasonStatus[current.status]);
    await expect(page.getByRole('button', {name: '新增奖励档位', exact: true})).toBeDisabled();
    const locked = await operation(page, 'learning', await api(page, 'PUT', '/learning-hub/admin/reward-rules/0', {month: now, rankFrom: 501, rankTo: 502, couponId: coupon, enabled: false, version: 0}, crypto.randomUUID()), 'FAILED');
    expect(locked.errorMessage).toContain('未来赛季');
    expect((await api(page, 'GET', '/learning-hub/admin/reward-rules?month=' + now)).list.some((r: any) => String(r.couponId) === coupon)).toBe(false);
    const rewardSeason = seasons.find((s: any) => s.month === m.rewardMonth);
    await choose(page, '管理赛季', rewardSeason.name + ' · ' + seasonStatus[rewardSeason.status]);
    const receipts = await api(page, 'GET', '/learning-hub/admin/rewards?month=' + m.rewardMonth + '&pageNo=1&pageSize=100');
    const receipt = receipts.list.find((r: any) => r.requestId === m.rewardRequestId);
    expect(receipt.status).toBe('FULFILLED');expect(String(receipt.couponId)).toBe(m.rewardCouponId);expect(String(receipt.userCouponId)).toMatch(/^\d+$/);
    const rewardRow = page.locator('.el-table').last().locator('.el-table__row').filter({hasText: m.rewardMonth});
    await expect(rewardRow.first()).toContainText('已发放');
    await expect(rewardRow.first().getByRole('button', {name: '重试发奖', exact: true})).toHaveCount(0);
    const studentContext = await browser.newContext({baseURL: process.env.TJ_STUDENT_URL || 'http://127.0.0.1:23500'});
    try {
        const student = await studentContext.newPage();await login(student, 'student', '/points');
        const ownedRewards = await api(student, 'GET', '/learning-hub/rewards?pageNo=1&pageSize=100');
        expect(ownedRewards.list.some((r: any) => r.requestId === m.rewardRequestId && r.status === 'FULFILLED' && String(r.userCouponId) === String(receipt.userCouponId))).toBe(true);
        const confirmation = await api(student, 'GET', '/orders/confirmation?courseIds=' + fixture.course);
        expect(confirmation.availableCoupons.some((c: any) => String(c.id) === String(receipt.userCouponId) && String(c.couponId) === m.rewardCouponId)).toBe(true);
        await student.getByRole('tab', {name: '赛季奖励', exact: true}).click();
        await expect(student.locator('.el-table__row').filter({hasText: m.rewardMonth}).first()).toContainText('已发放');
        await forbidden(student, '/teacher/learning-hub?courseId=' + m.course);
    } finally {await studentContext.close();}
});
