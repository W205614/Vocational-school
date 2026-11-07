import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const source = new URL('../packages/shared/assets/brand-logo.svg', import.meta.url);
const svg = await fs.readFile(source, 'utf8');
const browser = await chromium.launch({
  channel: process.env.PLAYWRIGHT_CHANNEL || (process.platform === 'win32' ? 'msedge' : undefined),
});
try {
  const page = await browser.newPage({ viewport: { width: 512, height: 512 } });
  await page.setContent(
    '<!doctype html><html><head><style>body{margin:0}</style></head><body>' +
      `<img width="512" height="512" src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}">` +
      '</body></html>',
  );
  await page.locator('img').evaluate((image) => image.decode());
  const png = await page.screenshot({ omitBackground: true });
  for (const app of ['student', 'admin']) {
    const publicAssets = new URL(`../apps/${app}/public/`, import.meta.url);
    await fs.writeFile(new URL('brand-logo.svg', publicAssets), svg);
    await fs.writeFile(new URL('brand-logo.png', publicAssets), png);
  }
  console.log(`Generated portal SVG/PNG assets from ${fileURLToPath(source)}`);
} finally {
  await browser.close();
}
