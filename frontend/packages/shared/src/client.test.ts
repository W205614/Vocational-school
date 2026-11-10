import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { downloadFile, get, setTokenProvider } from './client';

beforeEach(() => {
  setTokenProvider(() => 'synthetic-token');
});
afterEach(() => vi.unstubAllGlobals());

test('normalizes API paths and preserves encoded query values', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 200, data: 'ok' })));
  vi.stubGlobal('fetch', fetcher);
  expect(await get('/services/course/./courses/1?name=a%20b')).toBe('ok');
  expect(fetcher.mock.calls[0][0]).toBe('/api/v2/services/course/courses/1?name=a%20b');
  expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toBe(
    'Bearer synthetic-token',
  );
});

test('rejects traversal outside the API before sending credentials', async () => {
  const fetcher = vi.fn();
  vi.stubGlobal('fetch', fetcher);
  for (const path of ['/../../private', '/%2e%2e/%2e%2e/private', '/..\\..\\private', 'https://evil.test']) {
    await expect(get(path)).rejects.toMatchObject({ status: 400 });
    await expect(downloadFile(path, {})).rejects.toMatchObject({ status: 400 });
  }
  expect(fetcher).not.toHaveBeenCalled();
});
