import {expect, type Page} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

export const home = process.env.TJ_UI_RUNTIME_HOME || '../deploy/acceptance/.local';
export const accounts = JSON.parse(fs.readFileSync(path.join(home, 'accounts.json'), 'utf8'));
export const fixture = JSON.parse(fs.readFileSync(path.join(home, 'browser-fixture.json'), 'utf8'));

export async function signIn(page: Page, role: string) {
    await page.locator('input[autocomplete="username"]').fill(accounts[role].username);
    await page.locator('input[autocomplete="current-password"]').fill(accounts[role].password);
    await page.getByRole('button', {name: role === 'student' ? '登录' : '登录管理端', exact: true}).click();
    await expect(page.locator('.sidebar')).toBeVisible();
}

export async function login(page: Page, role: string, destination = '/') {
    await page.goto(destination);
    await signIn(page, role);
}

export async function api(page: Page, method: string, endpoint: string, data?: unknown, key?: string) {
    const token = await page.evaluate(() => sessionStorage.getItem('school-token'));
    const response = await page.request.fetch('/api/v2' + endpoint, {
        method, data,
        headers: {Authorization: 'Bearer ' + token, ...(key ? {'Idempotency-Key': key} : {})},
    });
    const envelope = await response.json();
    expect(response.ok(), JSON.stringify(envelope)).toBeTruthy();
    expect(envelope.code).toBe(200);
    return envelope.data;
}
