<script setup lang="ts">
import {ref,onBeforeUnmount} from 'vue';import {write} from './client';import {useTask} from './ui';import Status from './Status.vue';
const props=defineProps<{username:string,password:string}>(),emit=defineEmits<{completed:[]}>(),task=useTask(),secret=ref(''),code=ref('');
async function begin(){await task.run(async()=>{const value=await write<{secret:string}>('/auth/accounts/mfa/enroll','POST',{type:1,username:props.username,password:props.password});secret.value=value.secret;code.value='';});}
async function confirm(){await task.run(async()=>{await write('/auth/accounts/mfa/confirm','POST',{type:1,username:props.username,password:props.password,mfaCode:code.value});secret.value='';code.value='';emit('completed');});}
onBeforeUnmount(()=>{secret.value='';code.value='';});
</script>
<template><section class="section"><h3>设置管理员验证器</h3><p>使用已验证的账号密码生成密钥。仅首次设置可用；验证器遗失请联系安全管理员。</p><Status :busy="task.busy.value" :error="task.error.value"/><el-button v-if="!secret" :disabled="!username||!password||task.busy.value" @click="begin">生成验证器密钥</el-button><template v-else><p>在验证器中手动添加以下密钥（SHA-1、6位、30秒），不要向他人发送。</p><code aria-label="验证器设置密钥">{{secret}}</code><el-input v-model="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" aria-label="验证器设置验证码" placeholder="输入验证器中的6位验证码"/><el-button :disabled="!/^\d{6}$/.test(code)||task.busy.value" @click="confirm">验证并启用</el-button></template></section></template>
