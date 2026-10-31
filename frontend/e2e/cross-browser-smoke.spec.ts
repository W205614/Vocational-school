import {test,expect} from '@playwright/test';
import {api,fixture,login} from './helpers';

test('cross-browser core: sessions, enrollment and persistent workspaces',async({page,browser},info)=>{
 test.setTimeout(120000);
 const student=info.project.name==='student';
 await login(page,student?'student':'admin',student?'/courses/'+fixture.course:'/courses');

 // Damage only the access signature. A real backend rejection must recover
 // with the HttpOnly refresh cookie; no API responses are intercepted here.
 await page.evaluate(()=>{
  const parts=sessionStorage.getItem('school-token')!.split('.');
  parts[2]=(parts[2]![0]==='a'?'b':'a')+parts[2]!.slice(1);
  sessionStorage.setItem('school-token',parts.join('.'));
 });
 const refreshed=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v2/auth/accounts/refresh');
 await page.reload();
 const refresh=await refreshed;
 expect(refresh.ok()).toBeTruthy();expect((await refresh.json()).code).toBe(200);
 await expect(page.locator('.sidebar')).toBeVisible();
 await expect(page.locator('input[autocomplete="username"]')).toHaveCount(0);

 if(student){
  await expect(page.getByRole('heading',{name:fixture.name,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'加入购物车',exact:true}).click();
  await expect(page.getByText('已加入购物车',{exact:true})).toBeVisible();
  await page.goto('/cart');
  const cart=page.locator('.el-table__row').filter({hasText:fixture.name});
  await expect(cart).toBeVisible();await cart.locator('.el-checkbox').click();
  const confirmed=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v2/orders/confirmation');
  await page.getByRole('button',{name:/确认选购 1 门课程/}).click();
  const orderId=String((await (await confirmed).json()).data.orderId);
  await page.getByRole('button',{name:'提交订单',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'订单创建成功'})).toBeVisible();
  await page.getByRole('button',{name:'立即付款',exact:true}).click();
  await page.getByRole('link',{name:'打开本地模拟支付 →',exact:true}).click();
  await page.getByRole('button',{name:'确认模拟支付成功',exact:true}).click();
  await expect.poll(async()=>(await api(page,'GET','/orders/'+orderId)).status,{timeout:30000}).toBe(2);
  await expect.poll(async()=>(await api(page,'GET','/services/learning/learning-records/course/'+fixture.course))?.id,{timeout:30000}).toBeTruthy();
  await page.goto('/courses/'+fixture.course);await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeEnabled();
  await page.reload();await expect(page.getByRole('button',{name:'继续学习',exact:true})).toBeEnabled();

  const note='Cross-browser note '+crypto.randomUUID();
  const operation=await api(page,'POST','/notes',{courseId:fixture.course,content:note},crypto.randomUUID());
  await expect.poll(async()=>(await api(page,'GET','/operations/learning/'+operation.operationId)).status,{timeout:30000}).toBe('SUCCEEDED');
  await page.goto('/notes');await expect(page.getByText(note,{exact:true})).toBeVisible();
  const entry=page.locator('.journal-entry').filter({hasText:note});
  await entry.getByRole('button',{name:'编辑',exact:true}).click();
  const editor=page.getByRole('dialog',{name:'编辑笔记',exact:true});
  await editor.locator('textarea').fill(note+' updated');
  await editor.getByRole('button',{name:'保存',exact:true}).click();
  await expect(editor).not.toBeVisible();await page.reload();
  await expect(page.getByText(note+' updated',{exact:true})).toBeVisible();
 }else{
  await expect(page.getByRole('combobox',{name:'课程状态',exact:true})).toBeEnabled();
  await page.goto('/reliability');
  await expect(page.getByRole('heading',{name:'事件与业务冲突处理',exact:true})).toBeVisible();
  await page.reload();await expect(page.getByRole('heading',{name:'事件与业务冲突处理',exact:true})).toBeVisible();
  const teacherContext=await browser.newContext({baseURL:info.project.use.baseURL});
  try{
   const teacher=await teacherContext.newPage();await login(teacher,'teacher','/teaching');
   await expect(teacher.getByRole('heading',{name:'教学学情与答疑',exact:true})).toBeVisible();
   await expect(teacher.getByRole('link',{name:'失败与补偿任务',exact:true})).toHaveCount(0);
   await expect(teacher.locator('.el-alert--error')).toHaveCount(0);
  }finally{await teacherContext.close();}
 }
 await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)).toBe(false);
 await expect(page.locator('.el-alert--error')).toHaveCount(0);
});
