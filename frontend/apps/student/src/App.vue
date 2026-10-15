<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import EnvironmentBanner from '../../../packages/shared/src/EnvironmentBanner.vue';
import {ref,onMounted,watch,computed} from 'vue';import {useRoute,useRouter} from 'vue-router';
import {useSession} from '../../../packages/shared/src/session';import {useOperations} from '../../../packages/shared/src/operations';import {get} from '../../../packages/shared/src/client';import {useTask,type Row} from '../../../packages/shared/src/ui';import Status from '../../../packages/shared/src/Status.vue';
const router=useRouter(),route=useRoute(),session=useSession(),operations=useOperations(),task=useTask(),profile=ref<Row>({});
const links=[['/learning','我的学习'],['/favorites','我的收藏'],['/notes','私人笔记'],['/exams','课程考试'],['/review','错题复习'],['/coupons','优惠券'],['/orders','我的订单'],['/points','积分与签到'],['/messages','消息中心'],['/settings','个人设置'],['/operations','操作记录']];
const personal=computed(()=>!['/','/cart'].includes(route.path) && !route.path.startsWith('/courses/') && !route.path.startsWith('/simulate-payment/'));
async function welcome(){if(session.token){void operations.resume();try{profile.value=await get<Row>('/services/user/users/me');}catch{profile.value={};}}}
watch(()=>session.token,()=>{void welcome();if(!session.token && route.path!=='/login' && (session.expiredMessage || route.path!=='/' && !route.path.startsWith('/courses/')))void router.replace({path:'/login',query:{redirect:route.fullPath}});});onMounted(welcome);
</script>
<template><el-config-provider :locale="zhCn">
<div class="student-shell">
<header class="student-header"><div class="header-inner"><RouterLink to="/" class="brand"><img src="/brand-logo.png" alt="天机学堂"/><span>天机学堂<small>TIANJI ONLINE SCHOOL</small></span></RouterLink><nav class="primary-nav"><RouterLink to="/" exact-active-class="active">课程中心</RouterLink><RouterLink to="/learning" active-class="active">学习中心</RouterLink></nav><div v-if="session.token" class="header-actions"><RouterLink to="/cart">购物车</RouterLink><RouterLink to="/messages" class="message-link">消息</RouterLink><RouterLink to="/settings" class="user-chip"><span class="avatar">{{String(profile.name||'同学').slice(0,1)}}</span><span>{{profile.name||'我的账户'}}</span></RouterLink><el-button text @click="task.run(()=>session.logout())">退出登录</el-button></div><RouterLink v-else class="public-login-link" :to="{path:'/login',query:{redirect:route.fullPath}}">登录学习 →</RouterLink></div></header>
<nav v-if="session.token" class="sidebar student-subnav" aria-label="个人中心导航"><div><RouterLink v-for="[path,label] in links" :key="path" :to="path!" exact-active-class="selected">{{label}}</RouterLink></div></nav>
<main class="main student-main" :class="{'personal-main':personal}"><Status :error="task.error.value || session.expiredMessage"/><EnvironmentBanner/><RouterView/></main>
<footer class="student-footer"><strong>天机学堂</strong><span>学好一门课，完成一个目标</span><RouterLink to="/learning">继续学习 →</RouterLink></footer>
</div></el-config-provider></template>
