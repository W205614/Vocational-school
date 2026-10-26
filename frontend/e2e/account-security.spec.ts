import {test,expect} from '@playwright/test';

// UI/request contracts only; backend challenge, password and MFA proofs have separate DB/Redis tests.
test.beforeEach(async({page})=>{await page.route('**/api/v2/environment',route=>route.fulfill({json:{code:200,data:{environment:'contract-fixture',capabilities:{}},msg:'OK'}}));});

test('public account routes remain reachable while unconfigured registration stays closed',async({page},info)=>{
 test.skip(info.project.name!=='student');
 await page.route('**/api/v2/public/accounts/privacy',route=>route.fulfill({json:{code:200,data:{version:'fixture-v1',url:'/privacy',notice:'fixture privacy',registrationEnabled:false,verificationDelivery:'unconfigured'}}}));
 await page.goto('/register');await expect(page.getByRole('heading',{name:'注册学生账户'})).toBeVisible();await expect(page.getByRole('button',{name:'验证并注册'})).toBeDisabled();
 await page.goto('/recover-password');await expect(page.getByRole('heading',{name:'找回学生账户密码'})).toBeVisible();
 await page.goto('/privacy');await expect(page.getByRole('heading',{name:'隐私规则与账户权利'})).toBeVisible();
});

test('registration submits current policy consent and canonical form fields',async({page},info)=>{
 test.skip(info.project.name!=='student');
 await page.route('**/api/v2/public/accounts/privacy',route=>route.fulfill({json:{code:200,data:{version:'fixture-v1',url:'/privacy',notice:'fixture privacy',registrationEnabled:true,verificationDelivery:'simulated'}}}));
 let submitted:Record<string,unknown>|undefined;
 await page.route('**/api/v2/public/accounts/registration',async route=>{submitted=route.request().postDataJSON();await route.fulfill({json:{code:200,data:null,msg:'OK'}});});
 await page.goto('/register');await page.locator('input[autocomplete="tel"]').fill('13800010001');await page.locator('input[autocomplete="one-time-code"]').fill('123456');await page.locator('input[autocomplete="new-password"]').fill('fixture-password');
 await expect(page.getByRole('button',{name:'验证并注册'})).toBeDisabled();await page.locator('.el-checkbox__inner').click();await expect(page.getByRole('checkbox')).toBeChecked();await page.getByRole('button',{name:'验证并注册'}).click();
 await expect.poll(()=>submitted).toEqual({phone:'13800010001',code:'123456',password:'fixture-password',consent:true,policyVersion:'fixture-v1'});await expect(page.getByText('注册成功，请使用手机号与密码登录。',{exact:true})).toBeVisible();
});

test('management login carries MFA code without creating a session on rejection',async({page},info)=>{
 test.skip(info.project.name!=='admin');let submitted:Record<string,unknown>|undefined;
 await page.route('**/api/v2/auth/accounts/admin/login',async route=>{submitted=route.request().postDataJSON();await route.fulfill({status:401,json:{code:401,data:null,msg:'fixture MFA rejected'}});});
 await page.goto('/');await page.locator('input[autocomplete="username"]').fill('fixture-admin');await page.locator('input[autocomplete="current-password"]').fill('fixture-password');await page.getByRole('textbox',{name:'管理员验证器验证码'}).fill('123456');await page.getByRole('button',{name:'登录管理端',exact:true}).click();
 await expect.poll(()=>submitted).toMatchObject({type:1,username:'fixture-admin',password:'fixture-password',rememberMe:false,mfaCode:'123456'});await expect(page.getByText('fixture MFA rejected',{exact:true})).toBeVisible();expect(await page.evaluate(()=>sessionStorage.getItem('school-token'))).toBeNull();
});

test('password-authenticated MFA setup keeps business session empty',async({page},info)=>{
 test.skip(info.project.name!=='admin');
 await page.route('**/api/v2/auth/accounts/mfa/enroll',route=>route.fulfill({json:{code:200,data:{secret:'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ',provisioningUri:'otpauth://fixture'}}}));
 let confirmed=false;await page.route('**/api/v2/auth/accounts/mfa/confirm',async route=>{confirmed=route.request().postDataJSON().mfaCode==='123456';await route.fulfill({json:{code:200,data:null}});});
 await page.goto('/');await page.locator('input[autocomplete="username"]').fill('fixture-admin');await page.locator('input[autocomplete="current-password"]').fill('fixture-password');await page.getByRole('button',{name:'首次设置管理员验证器'}).click();await page.getByRole('button',{name:'生成验证器密钥'}).click();await page.getByRole('textbox',{name:'验证器设置验证码'}).fill('123456');await page.getByRole('button',{name:'验证并启用'}).click();await expect.poll(()=>confirmed).toBe(true);await expect(page.getByText('验证器已启用，请等待下一枚验证码后登录。',{exact:true})).toBeVisible();expect(await page.evaluate(()=>sessionStorage.getItem('school-token'))).toBeNull();
});
