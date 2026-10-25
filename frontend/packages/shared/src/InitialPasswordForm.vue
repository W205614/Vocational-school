<script setup lang="ts">
import {ref,onBeforeUnmount} from 'vue';import {write} from './client';import {useTask} from './ui';import Status from './Status.vue';
const props=defineProps<{username:string;temporaryPassword:string;audience:'student'|'admin'}>();
const emit=defineEmits<{completed:[];cancel:[]}>(),task=useTask(),password=ref(''),confirm=ref('');
onBeforeUnmount(()=>{password.value='';confirm.value='';});
async function save(){await task.run(async()=>{
 if(password.value!==confirm.value)throw new Error('两次输入的新密码不一致');
 if(password.value.length<8||new TextEncoder().encode(password.value).length>72)throw new Error('新密码至少8字符，UTF-8最多72字节');
 if(password.value===props.temporaryPassword)throw new Error('新密码不能与临时密码相同');
 await write('/auth/accounts/password/initial','POST',{username:props.username,oldPassword:props.temporaryPassword,newPassword:password.value,audience:props.audience});
 password.value='';confirm.value='';emit('completed');
});}
</script>
<template><section><h2>设置自己的登录密码</h2><p>临时密码仅用于本次设置。完成后请用新密码重新登录。</p><Status :busy="task.busy.value" :error="task.error.value"/><el-form label-position="top" @submit.prevent="save"><el-form-item label="新密码"><el-input v-model="password" type="password" autocomplete="new-password" aria-label="新密码" show-password/></el-form-item><el-form-item label="确认新密码"><el-input v-model="confirm" type="password" autocomplete="new-password" aria-label="确认新密码" show-password/></el-form-item><div class="toolbar"><el-button type="primary" native-type="submit" :disabled="task.busy.value||!password||!confirm">保存新密码</el-button><el-button :disabled="task.busy.value" @click="emit('cancel')">返回登录</el-button></div></el-form></section></template>
