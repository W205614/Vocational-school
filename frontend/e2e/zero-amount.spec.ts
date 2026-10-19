import {test,expect} from '@playwright/test';
import {api,fixture,login} from './helpers';

test('full coupon discount settles without payment and approved withdrawal keeps the coupon consumed',async({page,browser},info)=>{
 test.skip(info.project.name!=='student');test.setTimeout(150000);
 const context=await browser.newContext({baseURL:process.env.TJ_ADMIN_URL||'http://127.0.0.1:23501'}),admin=await context.newPage();
 const name='Zero '+crypto.randomUUID().slice(0,8);let coupon:string|undefined;
 let paymentRequests=0;page.on('request',request=>{if(request.method()==='POST' && new URL(request.url()).pathname.endsWith('/services/trade/pay/order'))paymentRequests++;});
 try {
  await login(page,'student','/courses/'+fixture.course);
  const quote=await api(page,'GET','/orders/confirmation?courseIds='+fixture.course);expect(Number(quote.totalAmount)).toBeGreaterThan(0);
  await login(admin,'admin','/coupons');
  await api(admin,'POST','/admin/promotion/coupons',{name,specific:false,discountType:3,thresholdAmount:0,discountValue:Number(quote.totalAmount),maxDiscountAmount:Number(quote.totalAmount),totalNum:2,userLimit:1,obtainWay:1},crypto.randomUUID());
  const created=await api(admin,'GET','/admin/promotion/coupons/page?name='+encodeURIComponent(name)+'&pageNo=1&pageSize=20');expect(created.list).toHaveLength(1);coupon=String(created.list[0].id);
  await api(admin,'PUT','/admin/promotion/coupons/'+coupon+'/issue',{id:coupon,issueEndTime:new Date(Date.now()+86400000).toISOString().slice(0,19),termDays:7},crypto.randomUUID());
  await page.goto('/coupons');await page.locator('.el-table__row').filter({hasText:name}).getByRole('button',{name:'领取',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'领取成功'})).toBeVisible();
  await page.goto('/courses/'+fixture.course);await page.getByRole('button',{name:'加入购物车',exact:true}).click();await expect(page.getByText('已加入购物车',{exact:true})).toBeVisible();
  await page.goto('/cart');const line=page.locator('.el-table__row').filter({hasText:fixture.name});await expect(line).toBeVisible();await line.locator('.el-checkbox').click();
  await page.getByRole('button',{name:/确认选购 1 门课程/}).click();
  const confirmation=page.getByRole('dialog',{name:'确认订单',exact:true});const couponOption=confirmation.locator('.el-checkbox').filter({hasText:name});await couponOption.click();await expect(couponOption.getByRole('checkbox')).toBeChecked();
  await confirmation.getByRole('button',{name:'计算所选优惠券',exact:true}).click();
  const solution=confirmation.locator('.el-radio').filter({hasText:'无门槛'});await expect(solution).toHaveCount(1);await solution.click();await expect(solution.getByRole('radio')).toBeChecked();
  await expect(confirmation.locator('.checkout-total')).toContainText('0.00');await confirmation.getByRole('button',{name:'提交订单',exact:true}).click();
  const done=page.getByRole('dialog',{name:'订单创建成功',exact:true});await expect(done).toContainText('订单已成交，无需付款');
  await expect(done.getByRole('button',{name:'立即付款',exact:true})).toHaveCount(0);await expect(done.getByRole('button',{name:'查看我的学习',exact:true})).toBeVisible();
  const order=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('school-operations')||'[]').find((item:any)=>item.label==='创建课程订单').operation.result.orderId);
  expect(paymentRequests).toBe(0);
  await expect.poll(async()=>(await api(page,'GET','/orders/'+order)).status).toBe(2);
  const purchase=await api(page,'GET','/orders/'+order);expect(purchase.payChannel).toBe('ZERO');expect(purchase.realAmount).toBe(0);expect(purchase.payOrderNo).toBeFalsy();
  await expect.poll(async()=>(await api(page,'GET','/services/promotion/user-coupons/page?status=2&pageNo=1&pageSize=100')).list.some((item:any)=>String(item.id)===coupon),{timeout:30000}).toBe(true);
  await expect.poll(async()=>(await api(page,'GET','/services/learning/learning-records/course/'+fixture.course))?.id,{timeout:30000}).toBeTruthy();
  await done.getByRole('button',{name:'关闭',exact:true}).click();await page.goto('/orders');const stored=page.locator('.el-table__row').filter({hasText:String(order)});await expect(stored).toContainText('零元成交');await expect(stored.getByRole('button',{name:'支付',exact:true})).toHaveCount(0);
  await stored.getByRole('button',{name:'详情',exact:true}).click();await page.getByRole('button',{name:'申请退款',exact:true}).click();await page.getByPlaceholder('请说明退款原因').fill('全额抵扣订单退课，保留已使用券');await page.getByRole('button',{name:'提交退款申请',exact:true}).click();await expect(page.getByText('退款申请已提交',{exact:true})).toBeVisible();
  await admin.goto('/refunds');const refund=admin.locator('.el-table__row').filter({hasText:String(order)});await refund.getByRole('button',{name:'同意退款',exact:true}).click();await admin.getByRole('button',{name:'确定',exact:true}).click();await expect(admin.getByText('同意退款已完成',{exact:true})).toBeVisible();
  await expect.poll(async()=>(await api(page,'GET','/orders/'+order)).status,{timeout:30000}).toBe(7);
  expect((await api(page,'GET','/services/promotion/user-coupons/page?status=2&pageNo=1&pageSize=100')).list.some((item:any)=>String(item.id)===coupon)).toBe(true);
  expect(paymentRequests).toBe(0);
 } finally {
  if(coupon)await api(admin,'PUT','/admin/promotion/coupons/'+coupon+'/pause',undefined,crypto.randomUUID());
  await context.close();
 }
});
