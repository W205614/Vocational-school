import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useOperations } from './operations';
import { setTokenProvider } from './client';
import type { Operation } from './types';

const pending: Operation = {
  operationId: 'old-operation',
  status: 'PENDING',
  result: null,
  errorCode: null,
  errorMessage: null,
};
const succeeded: Operation = { ...pending, status: 'SUCCEEDED', result: 'saved' };
const response = (data: Operation) =>
  new Response(JSON.stringify({ code: 200, data }), {
    headers: { 'Content-Type': 'application/json' },
  });
function deferred() {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.useFakeTimers();
  setActivePinia(createPinia());
  const storage = new Map<string, string>();
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  });
  setTokenProvider(() => 'student-token');
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

test('clearing during submission discards its late receipt without polling or restoring storage', async () => {
  const gate = deferred();
  const fetch = vi
    .fn<typeof globalThis.fetch>(() => Promise.resolve(response(succeeded)))
    .mockReturnValueOnce(gate.promise);
  vi.stubGlobal('fetch', fetch);
  const operations = useOperations();
  const result = operations.begin('trade', 'old purchase', '/orders', { courseId: 'course' });
  const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
  operations.clear();
  expect(signal.aborted).toBe(true);
  gate.resolve(response(pending));
  await vi.advanceTimersByTimeAsync(500);
  await rejected;
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(operations.items).toEqual([]);
  expect(sessionStorage.getItem('school-operations')).toBeNull();
});

test('clearing aborts an active poll and its late response cannot overwrite a new account task', async () => {
  const gate = deferred();
  const fetch = vi
    .fn<typeof globalThis.fetch>(() => Promise.resolve(response(succeeded)))
    .mockReturnValueOnce(gate.promise);
  vi.stubGlobal('fetch', fetch);
  const operations = useOperations();
  const result = operations.track('trade', 'old purchase', pending, 'old-key');
  const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  await vi.advanceTimersByTimeAsync(500);
  const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
  operations.clear();
  expect(signal.aborted).toBe(true);
  const next = operations.begin('trade', 'new purchase', '/orders', { courseId: 'new-course' });
  await next;
  const stored = sessionStorage.getItem('school-operations');
  gate.resolve(response(succeeded));
  await rejected;
  expect(operations.items.map((item) => item.label)).toEqual(['new purchase']);
  expect(sessionStorage.getItem('school-operations')).toBe(stored);
});

test('clearing during a terminal receipt refresh discards the old result', async () => {
  const gate = deferred();
  vi.stubGlobal('fetch', vi.fn().mockReturnValue(gate.promise));
  const operations = useOperations();
  const result = operations.track('trade', 'old purchase', succeeded, 'old-key');
  const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  operations.clear();
  gate.resolve(response(succeeded));
  await rejected;
  expect(sessionStorage.getItem('school-operations')).toBeNull();
});

test('normal submission keeps the idempotency key and persists the polled result', async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(response(pending))
    .mockResolvedValue(response(succeeded));
  vi.stubGlobal('fetch', fetch);
  const operations = useOperations();
  const result = operations.begin('trade', 'purchase', '/orders', { courseId: 'course' });
  await vi.advanceTimersByTimeAsync(500);
  expect(await result).toEqual(succeeded);
  expect(fetch.mock.calls.map((call) => call[0])).toEqual([
    '/api/v2/orders',
    '/api/v2/operations/trade/old-operation',
  ]);
  expect(fetch.mock.calls[0]![1].headers.get('Idempotency-Key')).toBe(operations.items[0]!.key);
  expect(JSON.parse(sessionStorage.getItem('school-operations')!)[0].operation).toEqual(succeeded);
});

test('a genuine failed task retains its server error for explicit recovery', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      response({ ...pending, status: 'FAILED', errorMessage: 'course unavailable' }),
    ),
  );
  const operations = useOperations();
  const result = operations.track('trade', 'purchase', pending, 'key');
  const rejected = expect(result).rejects.toThrow('course unavailable');
  await vi.advanceTimersByTimeAsync(500);
  await rejected;
  expect(operations.items[0]!.error).toBe('course unavailable');
});
