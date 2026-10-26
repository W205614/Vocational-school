<script setup lang="ts">
import {ref,onMounted} from 'vue';import {get} from '../../../../packages/shared/src/client';import {useTask,type Row} from '../../../../packages/shared/src/ui';import Status from '../../../../packages/shared/src/Status.vue';
const policy=ref<Row>(),task=useTask();onMounted(()=>task.run(async()=>{policy.value=await get('/public/accounts/privacy');}));
</script>
<template><section class="card"><h1>隐私规则与账户权利</h1><Status :error="task.error.value" :busy="task.busy.value"/><template v-if="policy"><p>规则版本：{{policy.version}}</p><p>{{policy.notice}}</p><p>账户资料用于登录、课程权益、教学与售后。个人设置提供账户资料下载和注销申请；注销禁用登录并清除公开资料，交易、学习和安全记录保留供履约、审计及后续按运营规则处理。</p><p>账户资料下载只包含身份服务持有的资料与同意记录，完整跨业务数据导出须联系运营处理。正式运营前需发布适用的联系人、保留期限和完整隐私政策。</p><a v-if="policy.url&&policy.url!=='/privacy'&&/^https:\/\//.test(policy.url)" :href="policy.url" target="_blank" rel="noopener noreferrer">阅读运营方完整隐私政策</a></template><p><RouterLink to="/">返回课程</RouterLink></p></section></template>
