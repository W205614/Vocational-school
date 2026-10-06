<script setup lang="ts">
import {useOperations} from './operations';import {useTask} from './ui';
const operations=useOperations(),task=useTask();
</script>
<template><section class="card"><h2>操作记录</h2><p>处理中可以离开页面，刷新后会继续查询。网络异常时重试会沿用原请求。</p>
<el-alert v-if="task.error.value" :title="task.error.value" type="error" :closable="false"/>
<el-empty v-if="!operations.items.length" description="暂无操作"/>
<article v-for="item in operations.items" :key="item.key" class="operation"><strong>{{item.label}}</strong>
<el-tag :type="item.operation?.status==='SUCCEEDED'?'success':item.operation?.status==='FAILED'?'danger':'warning'">{{item.operation?.status||'请求待确认'}}</el-tag>
<p v-if="item.error">{{item.error}}</p><small>请求：{{item.key}}</small>
<el-button v-if="!item.operation || item.operation.status==='PENDING'" @click="task.run(()=>operations.run(item))">继续查询 / 原请求重试</el-button>
<el-button v-if="item.operation?.status!=='PENDING'" @click="operations.remove(item.key)">移除记录</el-button></article></section></template>
