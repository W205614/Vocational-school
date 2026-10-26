import {expect, test, type Page, type Route} from '@playwright/test';

function deferred(){let resolve!:()=>void;const promise=new Promise<void>(done=>{resolve=done;});return {promise,resolve};}
async function respond(route:Route,data:unknown){await route.fulfill({json:{code:200,data}});}
async function mockStudent(page:Page){
 await page.addInitScript(()=>{sessionStorage.setItem('school-token','navigation-fixture');sessionStorage.setItem('school-admin','false');});
 await page.route('**/api/v2/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  await respond(route,path.endsWith('/users/me')?{name:'导航测试同学'}:path.endsWith('/lessons/page')?{list:[],total:0}:{});
 });
}
test.beforeEach(async({page},info)=>{test.skip(info.project.name!=='student');await mockStudent(page);});

test('switching favorites to notes while loading fetches the current page',async({page})=>{
 const started=deferred(),release=deferred();
 await page.route('**/api/v2/favorites?**',async route=>{started.resolve();await release.promise;await respond(route,{list:[{id:'favorite',course_id:'11'}],total:1});});
 await page.route('**/api/v2/notes?**',route=>respond(route,{list:[{id:'note',course_id:'22',content:'切换后的私人笔记',version:0}],total:1}));
 await page.route('**/api/v2/services/course/course/*',route=>respond(route,{name:'导航测试课程'}));
 await page.goto('/favorites');await started.promise;
 await page.getByRole('navigation',{name:'个人中心导航'}).getByRole('link',{name:'私人笔记',exact:true}).click();
 await expect(page.getByRole('heading',{name:'私人笔记',exact:true})).toBeVisible();release.resolve();
 await expect(page.locator('.journal-entry')).toContainText('切换后的私人笔记');
 await expect(page.locator('.journal-entry')).toHaveCount(1);
});

test('switching cart to orders while loading fetches orders',async({page})=>{
 const started=deferred(),release=deferred();
 await page.route('**/api/v2/services/trade/carts',async route=>{started.resolve();await release.promise;await respond(route,[{id:'cart',courseId:'11',courseName:'旧购物车课程',price:100}]);});
 await page.route('**/api/v2/orders?**',route=>respond(route,{list:[{id:'order',status:2,realAmount:100,details:[{name:'切换后的订单课程'}]}],total:1}));
 await page.goto('/cart');await started.promise;
 await page.getByRole('navigation',{name:'个人中心导航'}).getByRole('link',{name:'我的订单',exact:true}).click();
 await expect(page.getByRole('heading',{name:'我的订单',exact:true})).toBeVisible();release.resolve();
 await expect(page.locator('.el-table__body-wrapper')).toContainText('切换后的订单课程');
 await expect(page.locator('.el-table__body-wrapper')).not.toContainText('旧购物车课程');
});

test('switching coupon tabs while loading fetches owned coupons',async({page})=>{
 const started=deferred(),release=deferred();
 await page.route('**/api/v2/services/promotion/coupons/list',async route=>{started.resolve();await release.promise;await respond(route,[{id:'available',name:'旧可领取优惠券',discountValue:100}]);});
 await page.route('**/api/v2/services/promotion/user-coupons/page?**',route=>respond(route,{list:[{id:'owned',name:'我的专属优惠券',discountValue:100}],total:1}));
 await page.goto('/coupons');await started.promise;
 await page.getByRole('button',{name:'我的优惠券',exact:true}).click();release.resolve();
 await expect(page.locator('.el-table__body-wrapper')).toContainText('我的专属优惠券');
 await expect(page.locator('.el-table__body-wrapper')).not.toContainText('旧可领取优惠券');
});

test('saving a new password clears password fields before the next profile edit',async({page})=>{
 const saves:Record<string,string>[]=[];
 await page.route('**/api/v2/services/user/users',async route=>{saves.push(route.request().postDataJSON());await respond(route,{});});
 await page.goto('/settings');
 const profile=page.getByRole('form',{name:'个人资料设置'});
 await profile.locator('input[autocomplete="current-password"]').fill('old-password');
 await profile.locator('input[autocomplete="new-password"]').fill('new-password');
 await profile.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByText('个人信息已保存',{exact:true})).toBeVisible();
 await expect(profile.locator('input[autocomplete="current-password"]')).toHaveValue('');
 await expect(profile.locator('input[autocomplete="new-password"]')).toHaveValue('');
 await profile.locator('.el-form-item').filter({hasText:'昵称'}).locator('input').fill('修改后的昵称');
 await profile.getByRole('button',{name:'保存',exact:true}).click();
 await expect.poll(()=>saves.length).toBe(2);
 expect(saves[1]?.oldPassword).toBe('');expect(saves[1]?.password).toBe('');
});
