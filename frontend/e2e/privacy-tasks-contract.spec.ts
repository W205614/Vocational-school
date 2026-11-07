import { test, expect } from '@playwright/test';

// UI/HTTP contracts with a synthetic administrator; server ownership/approval use DB tests.
test('contract: administrator can create cleanup after closing the manual request dialog', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'admin');
  const requestId = '12345678-1234-1234-1234-123456789abc',
    taskId = '23456789-2345-2345-2345-23456789abcd';
  const requests: { requestId: string; key: string | undefined }[] = [],
    unexpected: string[] = [];
  const task = {
    id: taskId,
    request_id: requestId,
    user_id: '701',
    kind: 'CLEANUP',
    status: 'PENDING',
    version: 1,
    domains: [],
    delivery: {
      status: 'IN_PROGRESS',
      resolution: '已核对申请',
      evidence_reference: 'synthetic-review-reference',
      reviewed_by: '1',
      reviewed_at: '2026-11-07T00:00:00',
    },
  };
  await page.addInitScript(() => {
    sessionStorage.setItem(
      'school-token',
      'contract.' + btoa(JSON.stringify({ user: { userId: '1', roleId: 1 } })) + '.fixture',
    );
    sessionStorage.setItem('school-admin', 'true');
    sessionStorage.setItem('school-operations', '[]');
  });
  await page.route('**/api/v2/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    let data: unknown;
    if (path === '/api/v2/environment') data = { mode: 'SIMULATED', capabilities: {} };
    else if (path === '/api/v2/admin/user/account-privacy/requests')
      data = [
        {
          id: requestId,
          user_id: '701',
          kind: 'DELETE',
          status: 'PENDING_REVIEW',
          version: 0,
          retained_domains: 'all reviewed domains',
        },
      ];
    else if (path === '/api/v2/services/user/account-privacy/tasks') {
      if (request.method() === 'POST') {
        requests.push({
          requestId: request.postDataJSON().requestId,
          key: request.headers()['idempotency-key'],
        });
        data = task;
      } else data = { items: requests.length ? [task] : [], total: requests.length ? '1' : '0' };
    } else if (path === '/api/v2/services/user/account-privacy/tasks/' + taskId) data = task;
    else {
      unexpected.push(request.method() + ' ' + path);
      await route.fulfill({ status: 500, json: { code: 500, msg: 'Unhandled contract route' } });
      return;
    }
    await route.fulfill({ json: { code: 200, msg: 'OK', data } });
  });
  await page.goto('/privacy-requests');
  await page.getByRole('button', { name: '核对并处理', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '处理隐私申请' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: '处理隐私申请' })).toHaveCount(0);
  await expect(
    page.getByRole('status').filter({ hasText: '当前完整处理关联申请：' + requestId }),
  ).toBeVisible();
  await page.getByRole('button', { name: '创建完整处理任务', exact: true }).click();
  await expect.poll(() => requests.length).toBe(1);
  expect(requests[0]!.requestId).toBe(requestId);
  expect(requests[0]!.key).toBeTruthy();
  await expect(page.getByText('当前任务：' + taskId + ' · PENDING', { exact: true })).toBeVisible();
  await expect(
    page.getByText('交付／清理证据引用：synthetic-review-reference；处理依据：已核对申请', {
      exact: true,
    }),
  ).toBeVisible();
  expect(unexpected).toEqual([]);
});
