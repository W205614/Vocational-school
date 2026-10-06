<script setup lang="ts">
import {ref,onMounted} from 'vue';import {get} from './client';const environment=ref<{mode:string;payment:string;storage:string;video:string;sms:string}>();
onMounted(async()=>{try{environment.value=await get('/environment');}catch{}});
</script><template><el-alert v-if="environment?.mode==='SIMULATED'" title="本地验收环境 · 支付和短信使用持久化模拟器，视频与文件保存在隔离本地存储；不会真实扣款或发送短信" type="warning" :closable="false"/><el-alert v-else-if="environment?.payment==='UNCONFIGURED'" title="当前未配置第三方支付渠道。支付服务不会自动回退到模拟成功。" type="info" :closable="false"/></template>
