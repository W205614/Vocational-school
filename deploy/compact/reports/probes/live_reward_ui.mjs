import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';

let repositoryRoot=path.dirname(fileURLToPath(import.meta.url));
while(!['pom.xml','frontend/package.json','deploy/compact/setup.py'].every(marker=>fs.existsSync(path.join(repositoryRoot,marker)))){
 const parent=path.dirname(repositoryRoot);if(parent===repositoryRoot)throw Error('Repository root markers were not found; no runtime exercised');repositoryRoot=parent;
}
const {chromium}=await import(pathToFileURL(path.join(repositoryRoot,'frontend/node_modules/playwright/index.mjs')).href);

const args=Object.fromEntries(process.argv.slice(2).reduce((pairs,value,i,all)=>value.startsWith('--')?[...pairs,[value.slice(2),all[i+1]]]:pairs,[]));
const config=JSON.parse(fs.readFileSync(args.config,'utf8'));
const expected='8dcf8e7d44c6048318ad1e306e1425c8fd40fb70';
const require=(value,message)=>{if(!value)throw Error(message);};
require(config.sourceCommit===expected&&config.offset===1000&&/^tianji-recovery-final-upgrade-/.test(config.project),'Invalid verified recovery configuration');
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
require(hash(config.proofPath)===config.proofSha256,'Settlement proof changed after runtime validation');
const proof=JSON.parse(fs.readFileSync(config.proofPath,'utf8'));
require(proof.status==='PASSED'&&proof.sourceCommit===expected&&proof.actualRewardPurchased===true,'Successful exact-source purchase proof required');
const purchase=proof.rewardPurchase,accounts=JSON.parse(fs.readFileSync(path.join(config.home,'.local','accounts.json'),'utf8'));
const fixture=JSON.parse(fs.readFileSync(path.join(config.home,'.local','browser-fixture.json'),'utf8'));
require(fixture.course&&accounts.student?.username&&accounts.student?.password,'Owned fixture and private student credentials required');
const report={status:'RUNNING',sourceCommit:expected,environment:'fresh independent recovery after actual earned-ticket purchase',startedAt:new Date().toISOString(),uiChecks:[],httpChecks:[],businessWrites:[],pageErrors:[],apiErrors:[],screenshots:[],limitations:['Order UI shows paid status and actual payment, not a separate discount amount. Discount is independently verified from the real order-detail HTTP response.','Owned-coupon UI labels records 已领取 and does not show USED status or user-ticket identity. Used state is separately verified via the real status=2 filtered endpoint, correlated to the exact ticket by the prior successful SQL/HTTP settlement proof.','The user-coupon page returns coupon definitions rather than user-ticket ids; this supplemental HTTP check does not claim an explicit ticket-status field was returned.','Screenshots are private; account names and transaction identifiers may be visible. They must not be copied to public documentation.']};
let browser,page;
const check=(kind,name,value,details={})=>{report[kind].push({name,passed:!!value,...details});require(value,name);};
const money=value=>'¥'+(Number(value)/100).toFixed(2);
async function idle(){await page.waitForFunction(()=>![...document.querySelectorAll('[role="status"]')].some(el=>/正在处理|加载中/.test(el.textContent||'')));await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));}
async function get(endpoint){
 const token=await page.evaluate(()=>sessionStorage.getItem('school-token'));
 const response=await page.request.get(new URL('/api/v2'+endpoint,config.studentUrl).href,{headers:{Authorization:'Bearer '+token},timeout:15000});
 require(response.ok(),'Supplemental authenticated GET failed');const body=await response.json();require(body.code===200,'Supplemental GET returned business failure');return body.data;
}
async function findPaged(endpoint,predicate,size=20){
 let total=Infinity;
 for(let number=1;number===1||number<=Math.ceil(total/size);number++){
  const data=await get(endpoint+(endpoint.includes('?')?'&':'?')+'pageNo='+number+'&pageSize='+size);total=Number(data.total);require(Number.isSafeInteger(total)&&total>=0&&Array.isArray(data.list),'Invalid real pagination contract');
  const index=data.list.findIndex(predicate);if(index>=0)return {number,index,data,item:data.list[index]};
  require(data.list.length>0||total===0,'Unexpected empty intermediate data page');
 }
 return null;
}
function uiResponse(endpoint,number){return page.waitForResponse(response=>{const url=new URL(response.url());return response.request().method()==='GET'&&url.pathname==='/api/v2'+endpoint&&Number(url.searchParams.get('pageNo'))===number;},{timeout:15000});}
async function body(response){require(response.ok(),'UI own GET failed');const value=await response.json();require(value.code===200,'UI own GET business failure');return value.data;}
async function shot(name){await page.screenshot({path:path.join(config.out,name),fullPage:true});report.screenshots.push({file:name,privacy:'private transaction/account UI; not approved for publication'});}
try{
 browser=await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL||'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:950}});
 await context.route('**/api/v2/**',route=>{
  const request=route.request(),url=new URL(request.url());
  if(!['GET','HEAD','OPTIONS'].includes(request.method())&&!(request.method()==='POST'&&url.pathname==='/api/v2/auth/accounts/login')){report.businessWrites.push({method:request.method(),path:url.pathname,blocked:true});return route.abort();}
  return route.continue();
 });
 page=await context.newPage();page.on('pageerror',error=>report.pageErrors.push(error.message));
 page.on('response',response=>{const url=new URL(response.url());if(url.pathname.startsWith('/api/v2/')&&response.status()>=400)report.apiErrors.push({path:url.pathname,status:response.status()});});
 await page.goto(config.studentUrl+'/login?redirect='+encodeURIComponent('/points?tab=rewards'));
 await page.locator('[autocomplete=username]').fill(accounts.student.username);await page.locator('[autocomplete=current-password]').fill(accounts.student.password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(url=>url.pathname==='/points');await page.locator('.sidebar').waitFor();await idle();
 const identity=await get('/services/user/users/me');check('httpChecks','Authenticated student is the private recovery account',String(identity.id)===String(accounts.student.id));
 const reward=await findPaged('/learning-hub/rewards',item=>String(item.requestId)===String(proof.requestId));
 check('httpChecks','Exact settlement request is fulfilled and correlated to the purchased ticket',!!reward&&reward.item.status==='FULFILLED'&&String(reward.item.userCouponId)===String(purchase.userCouponId)&&String(reward.item.couponId)===String(purchase.couponId)&&String(reward.item.userId)===String(accounts.student.id)&&String(reward.item.seasonMonth)===String(proof.month));
 let observed=uiResponse('/learning-hub/rewards',reward.number);await page.goto(config.studentUrl+'/points?tab=rewards&rewardPage='+reward.number+'&month='+proof.month);let data=await body(await observed);await page.getByRole('heading',{name:'赛季奖励',exact:true}).waitFor();await idle();
 const rewardIndex=data.list.findIndex(item=>String(item.requestId)===String(proof.requestId));require(rewardIndex>=0,'UI own reward response lost the exact request');
 const rewardRows=page.locator('.points-page .el-table__body-wrapper .el-table__row');const rewardRow=rewardRows.nth(rewardIndex);await rewardRow.waitFor();const rewardCells=rewardRow.locator('td .cell');
 check('uiChecks','Actual reward row visibly shows its season, rank and delivered state',await rewardRows.count()===data.list.length&&(await rewardCells.nth(0).innerText()).trim()===String(proof.month)&&(await rewardCells.nth(1).innerText()).trim()===String(reward.item.rankNo)&&(await rewardCells.nth(2).innerText()).trim()==='已发放',{mappedFromUiOwnResponse:true});await shot('private-earned-reward.png');
 const order=await get('/orders/'+purchase.orderId);
 check('httpChecks','Exact paid order has the recorded total, discount and actual payment',String(order.id)===String(purchase.orderId)&&Number(order.status)===2&&Number(order.totalAmount)===Number(purchase.totalAmount)&&Number(order.discountAmount)===Number(purchase.discountAmount)&&Number(order.realAmount)===Number(purchase.paidAmount),{totalCents:Number(order.totalAmount),discountCents:Number(order.discountAmount),paidCents:Number(order.realAmount)});
 check('httpChecks','Exact paid course detail belongs to the recorded order and amount',(order.details||[]).some(item=>String(item.id)===String(purchase.orderDetailId)&&String(item.courseId)===String(purchase.courseId)&&Number(item.realPayAmount)===Number(purchase.paidAmount)));
 const ownedOrder=await findPaged('/orders',item=>String(item.id)===String(purchase.orderId));require(ownedOrder,'Purchased order not present in paged history');
 observed=uiResponse('/orders',1);await page.goto(config.studentUrl+'/orders');data=await body(await observed);await page.getByRole('heading',{name:'我的订单',exact:true}).waitFor();await idle();
 for(let number=2;number<=ownedOrder.number;number++){observed=uiResponse('/orders',number);await page.locator('.commerce-page .el-pagination').getByRole('button',{name:'下一页',exact:true}).click();data=await body(await observed);await idle();}
 const orderIndex=data.list.findIndex(item=>String(item.id)===String(purchase.orderId));require(orderIndex>=0,'UI own order response lost the exact paid order');const orderRow=page.locator('.commerce-page .el-table__body-wrapper .el-table__row').nth(orderIndex);await orderRow.waitFor();const orderCells=orderRow.locator('td .cell');
 check('uiChecks','Exact purchased order visibly shows paid status and discounted actual payment',(await orderCells.nth(1).innerText()).trim()===String(purchase.orderId)&&(await orderCells.nth(2).innerText()).trim()==='已支付'&&(await orderCells.nth(3).innerText()).trim()===money(purchase.paidAmount),{mappedFromUiOwnResponse:true,discountAmountSeparatelyVisible:false});
 observed=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v2/orders/'+purchase.orderId&&response.request().method()==='GET');await orderRow.getByRole('button',{name:'详情',exact:true}).click();await body(await observed);const dialog=page.getByRole('dialog',{name:'订单详情',exact:true});await dialog.waitFor();
 const dialogText=await dialog.innerText();check('uiChecks','Real order detail visibly retains paid state and course actual amount',dialogText.includes(String(purchase.orderId))&&dialogText.includes('已支付')&&dialogText.includes('实付 '+money(purchase.paidAmount))&&await dialog.getByRole('link',{name:'查看课程 →',exact:true}).getAttribute('href')==='/courses/'+purchase.courseId);await shot('private-paid-order-detail.png');
 const used=await findPaged('/services/promotion/user-coupons/page?status=2',item=>String(item.id)===String(purchase.couponId),100);
 check('httpChecks','Owned used-status query includes the rewarded coupon definition',!!used&&!!used.item.name,{statusFilter:2,exactTicketIdentityProvidedByPriorSettlementProof:true,explicitTicketStatusFieldReturned:false});
 const unused=await findPaged('/services/promotion/user-coupons/page?status=1',item=>String(item.id)===String(purchase.couponId),100);
 check('httpChecks','Rewarded coupon definition is absent from unused-status query',unused===null,{statusFilter:1});
 await page.goto(config.studentUrl+'/coupons');await page.getByRole('heading',{name:'优惠券',exact:true}).waitFor();await idle();observed=uiResponse('/services/promotion/user-coupons/page',1);await page.getByRole('button',{name:'我的优惠券',exact:true}).click();data=await body(await observed);await idle();
 const couponIndex=data.list.findIndex(item=>String(item.id)===String(purchase.couponId));require(couponIndex>=0,'Reward coupon is not visible in current owned-coupon UI page');const couponRow=page.locator('.commerce-page .el-table__body-wrapper .el-table__row').nth(couponIndex);await couponRow.waitFor();const couponText=await couponRow.innerText();
 check('uiChecks','Owned-coupon UI visibly retains the rewarded coupon name',couponText.includes(used.item.name)&&couponText.includes('已领取')&&await couponRow.getByRole('button',{name:'领取',exact:true}).count()===0,{labelMeansOwnershipRecord:true,usedStatusVisible:false});await shot('private-owned-reward-coupon.png');
 check('uiChecks','No business writes were attempted',report.businessWrites.length===0);check('uiChecks','No unhandled browser errors or failing real API responses',report.pageErrors.length===0&&report.apiErrors.length===0);
 require(hash(config.proofPath)===config.proofSha256,'Input settlement proof changed during the UI check');report.status='PASSED';
}catch(error){report.status='FAILED';report.failureType=error.name;report.privateFailure=error.message;if(page)await page.screenshot({path:path.join(config.out,'private-failure.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}
finally{await browser?.close();report.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(config.out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log('Read-only recovery reward UI '+report.status+'; private report retained.');}
