<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import EnvironmentBanner from '../../../packages/shared/src/EnvironmentBanner.vue';
import {ref,onMounted,watch,computed} from 'vue';import {useRoute} from 'vue-router';
import {useSession} from '../../../packages/shared/src/session';import {useOperations} from '../../../packages/shared/src/operations';import {get} from '../../../packages/shared/src/client';import {useTask,type Row} from '../../../packages/shared/src/ui';import Status from '../../../packages/shared/src/Status.vue';
const route=useRoute(),session=useSession(),operations=useOperations(),task=useTask(),username=ref(''),password=ref(''),profile=ref<Row>({});
const links=[['/learning','我的学习'],['/favorites','我的收藏'],['/notes','私人笔记'],['/exams','课程考试'],['/coupons','优惠券'],['/orders','我的订单'],['/points','积分与签到'],['/messages','消息中心'],['/settings','个人设置'],['/operations','操作记录']];
const personal=computed(()=>!['/','/cart'].includes(route.path) && !route.path.startsWith('/courses/') && !route.path.startsWith('/simulate-payment/'));
async function welcome(){if(session.token){void operations.resume();try{profile.value=await get<Row>('/services/user/users/me');}catch{profile.value={};}}}
watch(()=>session.token,welcome);onMounted(welcome);
</script>
<template><el-config-provider :locale="zhCn">
<div v-if="!session.token" class="student-login">
<section class="login-story"><RouterLink to="/" class="brand"><img src="/brand-logo.png" alt="天机学堂"/><span>天机学堂<small>TIANJI ONLINE SCHOOL</small></span></RouterLink><p class="eyebrow">从一门课开始</p><h1>让每一次学习，<br/>都有所收获。</h1><p>发现课程，记录思考，检验所学。<br/>在这里，把知识变成自己的能力。</p><div class="story-cards" aria-hidden="true"><span>学习课程</span><span>记录笔记</span><span>完成考试</span></div></section>
<section class="login-panel"><div class="card login"><p class="eyebrow">欢迎回来</p><h2>登录天机学堂</h2><p class="muted">登录后继续你的学习旅程</p><Status :busy="task.busy.value" :error="task.error.value || session.expiredMessage"/>
<el-form label-position="top" @submit.prevent="task.run(()=>session.login(username,password,false))"><el-form-item label="账号"><el-input v-model="username" autocomplete="username" aria-label="账号" placeholder="请输入账号" size="large"/></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password aria-label="密码" placeholder="请输入密码" size="large"/></el-form-item><el-button class="login-submit" native-type="submit" type="primary" size="large" :disabled="task.busy.value || !username || !password">登录</el-button></el-form><p class="muted login-help">如需账号或登录帮助，请联系学校管理员。</p></div></section>
</div>
<div v-else class="student-shell">
<header class="student-header"><div class="header-inner"><RouterLink to="/" class="brand"><img src="/brand-logo.png" alt="天机学堂"/><span>天机学堂<small>TIANJI ONLINE SCHOOL</small></span></RouterLink><nav class="primary-nav"><RouterLink to="/" exact-active-class="active">课程中心</RouterLink><RouterLink to="/learning" active-class="active">学习中心</RouterLink></nav><div class="header-actions"><RouterLink to="/cart">购物车</RouterLink><RouterLink to="/messages" class="message-link">消息</RouterLink><RouterLink to="/settings" class="user-chip"><span class="avatar">{{String(profile.name||'同学').slice(0,1)}}</span><span>{{profile.name||'我的账户'}}</span></RouterLink><el-button text @click="task.run(()=>session.logout())">退出登录</el-button></div></div></header>
<nav class="sidebar student-subnav" aria-label="个人中心导航"><div><RouterLink v-for="[path,label] in links" :key="path" :to="path!" exact-active-class="selected">{{label}}</RouterLink></div></nav>
<main class="main student-main" :class="{'personal-main':personal}"><Status :error="task.error.value || session.expiredMessage"/><EnvironmentBanner/><RouterView/></main>
<footer class="student-footer"><strong>天机学堂</strong><span>学好一门课，完成一个目标</span><RouterLink to="/learning">继续学习 →</RouterLink></footer>
</div></el-config-provider></template>
