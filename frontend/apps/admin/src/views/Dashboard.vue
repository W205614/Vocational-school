<script setup lang="ts">
import {ref,onMounted} from 'vue';import {get} from '../../../../packages/shared/src/client';import {useTask,money,type Row} from '../../../../packages/shared/src/ui';import Status from '../../../../packages/shared/src/Status.vue';
const task=useTask(),data=ref<Row>({});const labels:Record<string,string>={createdOrders:'今日创建订单',paidAmount:'今日已支付订单金额',refundAmount:'今日退款金额',openConflicts:'待处理支付工单',pendingEvents:'交易事件积压'};
function displayTime(value:unknown,dateOnly=false){
 if(typeof value!=='string' || !value)return '未加载';
 const time=new Date(value);if(Number.isNaN(time.valueOf()))return value;
 const options:Intl.DateTimeFormatOptions={timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',...(dateOnly?{}:{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false})};
 return new Intl.DateTimeFormat('zh-CN',options).format(time).replaceAll('/','-');
}
async function load(){await task.run(async()=>{data.value=await get<Row>('/admin/dashboard');});}onMounted(load);
</script><template><section class="card"><h2>数据看板</h2><el-button @click="load">刷新</el-button><Status :busy="task.busy.value" :error="task.error.value"/><p>取消后到账的款项单独进入支付冲突工单。</p><p class="muted">更新时间 {{displayTime(data.updatedAt)}}</p><div class="grid"><article v-for="(label,key) in labels" :key="key" class="card"><p>{{label}}</p><strong class="stat">{{data[key]===undefined?'未加载':key==='paidAmount'||key==='refundAmount'?money(data[key]):data[key]}}</strong></article></div><h3>近 30 天订单</h3><el-table :data="data.orderDays||[]"><el-table-column label="日期" width="110"><template #default="{row}">{{displayTime(row.day,true)}}</template></el-table-column><el-table-column prop="orders" label="创建订单"/><el-table-column label="当前已支付订单金额"><template #default="{row}">{{money(row.paid_amount)}}</template></el-table-column></el-table><RouterLink to="/reliability">查看事件失败、消费失败和支付冲突</RouterLink></section></template>
