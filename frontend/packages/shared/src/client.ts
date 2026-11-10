import type { Envelope, Operation } from './types';
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly requestId?: string,
  ) {
    super(message);
  }
}
let tokenProvider = () => sessionStorage.getItem('school-token') || '';
let refreshing: Promise<void> | undefined;
let identityUpdated = (token: string) => {},
  identityExpired = () => {};
export function setIdentityEvents(updated: (token: string) => void, expired: () => void) {
  identityUpdated = updated;
  identityExpired = expired;
}
function expired(requestId?: string) {
  identityExpired();
  return new ApiError('登录状态已过期，请重新登录后继续操作', 401, requestId);
}
function refreshIdentity(): Promise<void> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    const audience = sessionStorage.getItem('school-admin') === 'true' ? 'admin' : 'student';
    const response = await fetch('/api/v2/auth/accounts/refresh?audience=' + audience, {
      method: 'POST',
      credentials: 'include',
      signal: AbortSignal.timeout(10000),
    });
    const body = (await response.json()) as Envelope<string>;
    if (!response.ok || body.code !== 200 || typeof body.data !== 'string') {
      if ([400, 401, 403].includes(response.status) || [400, 401, 403].includes(body.code))
        throw expired(body.requestId);
      throw new ApiError('暂时无法恢复登录，请稍后重试', response.status, body.requestId);
    }
    sessionStorage.setItem('school-token', body.data);
    identityUpdated(body.data);
  })().finally(() => {
    refreshing = undefined;
  });
  return refreshing;
}
export function setTokenProvider(provider: () => string) {
  tokenProvider = provider;
}
function apiTarget(path: string) {
  // The fixed origin is only for URL normalization; fetch receives a relative path.
  const target = new URL('/api/v2' + path, 'https://api.invalid');
  if (!target.pathname.startsWith('/api/v2/')) throw new ApiError('无效的 API 路径', 400);
  return target.pathname + target.search;
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
  key?: string,
  retried = false,
): Promise<T> {
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), 15000);
  try {
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (options.body && !(options.body instanceof FormData))
      headers.set('Content-Type', 'application/json');
    const token = tokenProvider();
    if (token) headers.set('Authorization', 'Bearer ' + token);
    if (key) headers.set('Idempotency-Key', key);
    const signal = options.signal ? AbortSignal.any([options.signal, abort.signal]) : abort.signal;
    const response = await fetch(apiTarget(path), {
      ...options,
      headers,
      credentials: 'include',
      signal,
    });
    const body = (await response.json().catch(() => {
      throw new ApiError('服务暂时无法连接，请稍后重试', response.status);
    })) as Envelope<T>;
    options.signal?.throwIfAborted();
    if (response.status === 401 && token && !retried && !path.startsWith('/auth/accounts/')) {
      await refreshIdentity();
      return request<T>(path, options, key, true);
    }
    if (response.status === 401 && token && retried) throw expired(body.requestId);
    if (!response.ok || body.code !== 200)
      throw new ApiError(body.msg || '请求失败', response.status, body.requestId);
    return body.data;
  } catch (error) {
    options.signal?.throwIfAborted();
    if (error instanceof DOMException && error.name === 'AbortError')
      throw new ApiError('请求超时，请使用原请求重试', 408);
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
export function get<T>(path: string, signal?: AbortSignal) {
  return request<T>(path, { signal });
}
export function write<T>(
  path: string,
  method: string,
  body?: unknown,
  key?: string,
  signal?: AbortSignal,
) {
  return request<T>(
    path,
    { method, body: body === undefined ? undefined : JSON.stringify(body), signal },
    key,
  );
}
export function newKey() {
  return crypto.randomUUID();
}
export async function downloadFile(
  path: string,
  body: unknown,
  signal?: AbortSignal,
  retried = false,
): Promise<Blob> {
  const token = tokenProvider();
  const response = await fetch(apiTarget(path), {
    method: 'POST',
    credentials: 'include',
    redirect: 'error',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify(body),
    signal,
  });
  if (response.status === 401 && token && !retried) {
    await refreshIdentity();
    return downloadFile(path, body, signal, true);
  }
  if (!response.ok) {
    const value = await response.json();
    throw new ApiError(value.msg || '文件下载失败', response.status, value.requestId);
  }
  if (!response.headers.get('Content-Type')?.startsWith('application/zip'))
    throw new ApiError('导出响应格式错误', 502);
  return response.blob();
}
export async function waitOperation<T>(
  service: string,
  operation: Operation<T>,
  signal?: AbortSignal,
): Promise<Operation<T>> {
  let delay = 500;
  const deadline = Date.now() + 120000;
  let current = operation;
  while (current.status === 'PENDING' && Date.now() < deadline) {
    await new Promise<void>((resolve, reject) => {
      const aborted = () => {
        clearTimeout(timer);
        reject(new DOMException('Aborted', 'AbortError'));
      };
      const timer = setTimeout(() => {
        signal?.removeEventListener('abort', aborted);
        resolve();
      }, delay);
      if (signal?.aborted) {
        aborted();
        return;
      }
      signal?.addEventListener('abort', aborted, { once: true });
    });
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    current = await get<Operation<T>>('/operations/' + service + '/' + current.operationId, signal);
    delay = Math.min(5000, Math.round(delay * 1.5));
  }
  return current;
}
