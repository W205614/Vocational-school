import { test, expect } from '@playwright/test';
import { login } from './helpers';

test('URL identifiers cannot redirect authenticated API requests', async ({ page }, info) => {
  const student = info.project.name === 'student';
  await login(page, student ? 'student' : 'admin');
  const unexpected: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/v2/') && url.pathname.includes('__route_probe__'))
      unexpected.push(url.pathname);
  });
  const value = encodeURIComponent('../__route_probe__');
  if (student) {
    await page.goto('/exams?courseId=' + value);
    await expect(page.getByText('课程编号无效', { exact: true })).toBeVisible();
    await page.goto('/orders?pay=' + value);
    await expect(page.getByText('订单编号无效', { exact: true })).toBeVisible();
  } else {
    await page.goto('/course-editor?courseId=' + value);
    await expect(page.getByText('课程编号无效', { exact: true })).toBeVisible();
  }
  expect(unexpected).toEqual([]);
});
