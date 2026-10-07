<script setup lang="ts">
import {ref,onMounted} from 'vue';import {get} from './client';const environment=ref<{mode:string;payment:string;storage:string;video:string;sms:string}>();
onMounted(async()=>{try{environment.value=await get('/environment');}catch{}});
</script><template><div v-if="environment?.mode==='SIMULATED'" class="environment-note" role="note"><span class="environment-dot"></span><strong>本地体验环境</strong><span>支付、短信为模拟服务，不会真实扣款或发送短信。</span></div><el-alert v-else-if="environment?.payment==='UNCONFIGURED'" title="支付服务暂未配置，请联系管理员。" type="info" :closable="false"/></template>
