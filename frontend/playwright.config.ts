import {defineConfig,devices} from '@playwright/test';

const engine=process.env.TJ_BROWSER_ENGINE || 'chromium';
if(!['chromium','firefox','webkit'].includes(engine))throw new Error('TJ_BROWSER_ENGINE must be chromium, firefox or webkit');
const browserName=engine as 'chromium'|'firefox'|'webkit';
const desktop={chromium:'Desktop Chrome',firefox:'Desktop Firefox',webkit:'Desktop Safari'}[browserName];

export default defineConfig({
 testDir:'e2e',
 // Other engines check both portals against the real isolated backend without
 // repeating the full Chromium suite three times.
 testMatch:browserName==='chromium'?undefined:'**/cross-browser-smoke.spec.ts',
 testIgnore:browserName==='chromium'?'**/cross-browser-smoke.spec.ts':undefined,
 timeout:60000,workers:1,retries:0,
 reporter:[['list'],['html',{open:'never',outputFolder:'playwright-report/'+browserName}]],
 outputDir:'test-results/'+browserName,
 use:{...devices[desktop],browserName,
  ...(browserName==='chromium'?{channel:process.env.PLAYWRIGHT_CHANNEL || (process.platform==='win32'?'msedge':undefined)}:{}),
  actionTimeout:15000,trace:'retain-on-failure',screenshot:'only-on-failure'},
 projects:[
  {name:'student',use:{baseURL:process.env.TJ_STUDENT_URL||'http://127.0.0.1:23500'}},
  {name:'admin',use:{baseURL:process.env.TJ_ADMIN_URL||'http://127.0.0.1:23501'}},
 ],
 webServer:process.env.ACCEPTANCE_CONTAINER_UI ? undefined : [
  {command:'npm run dev --workspace @school/student -- --port 23500',url:process.env.TJ_STUDENT_URL||'http://127.0.0.1:23500',reuseExistingServer:false,timeout:60000},
  {command:'npm run dev --workspace @school/admin -- --port 23501',url:process.env.TJ_ADMIN_URL||'http://127.0.0.1:23501',reuseExistingServer:false,timeout:60000},
 ],
});
