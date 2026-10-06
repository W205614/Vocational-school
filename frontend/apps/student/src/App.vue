<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
import EnvironmentBanner from '../../../packages/shared/src/EnvironmentBanner.vue';
import {ref,onMounted} from 'vue';import {useSession} from '../../../packages/shared/src/session';import {useOperations} from '../../../packages/shared/src/operations';import {useTask} from '../../../packages/shared/src/ui';import Status from '../../../packages/shared/src/Status.vue';
const session=useSession(),operations=useOperations(),task=useTask(),username=ref(''),password=ref('');
const links=[['/','课程中心'],['/learning','我的学习'],['/cart','购物车'],['/coupons','优惠券'],['/orders','订单'],['/favorites','收藏'],['/notes','私人笔记'],['/exams','考试'],['/points','积分与签到'],['/messages','消息'],['/settings','个人设置'],['/operations','操作记录']];
onMounted(()=>{if(session.token)void operations.resume();});
</script>
<template><el-config-provider :locale="zhCn"><div v-if="!session.token" class="card login"><h1>天机学堂</h1><p class="muted">学生登录</p><Status :busy="task.busy.value" :error="task.error.value"/>
<el-form @submit.prevent="task.run(()=>session.login(username,password,false))"><el-form-item label="账号"><el-input v-model="username" autocomplete="username" aria-label="账号"/></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password aria-label="密码"/></el-form-item><el-button native-type="submit" type="primary" :disabled="task.busy.value || !username || !password">登录</el-button></el-form></div>
<div v-else class="layout"><aside class="sidebar"><h1>天机学堂</h1><nav><RouterLink v-for="[path,label] in links" :key="path" :to="path!">{{label}}</RouterLink></nav></aside>
<main class="main"><header class="topbar"><span>学好一门课，完成一个目标</span><el-button @click="task.run(()=>session.logout())">退出登录</el-button></header><Status :error="task.error.value"/><EnvironmentBanner/><RouterView/></main></div></el-config-provider></template>