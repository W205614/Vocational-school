<script setup lang="ts">
import {ref,computed,watch} from 'vue';
import {get} from '../../../../packages/shared/src/client';
import {query,type Row} from '../../../../packages/shared/src/ui';
const props=withDefaults(defineProps<{modelValue:string|string[];kind:'courses'|'questions'|'teachers'|'media';label:string;multiple?:boolean;limit?:number;courseStatuses?:number[];selectedRows?:Row[];disabled?:boolean}>(),{limit:100,courseStatuses:()=>[2,4]});
const emit=defineEmits<{ 'update:modelValue':[value:string|string[]];change:[rows:Row[]] }>();
const open=ref(false),keyword=ref(''),page=ref(1),total=ref(0),rows=ref<Row[]>([]),cache=ref<Record<string,Row>>({}),staged=ref<string[]>([]),busy=ref(false),error=ref(''),status=ref(props.courseStatuses[0]);let sequence=0;
const values=computed(()=>Array.isArray(props.modelValue)?props.modelValue:props.modelValue?[props.modelValue]:[]);
const name=(row:Row)=>String(row.name||row.filename||'未命名记录');
const selected=computed(()=>values.value.map(id=>cache.value[id]||{id,name:'已选记录'}));
watch(()=>props.selectedRows,list=>{for(const row of list||[])cache.value[String(row.id)]=row;},{immediate:true,deep:true});
const typeName=(value:unknown)=>({1:'单选',2:'多选',3:'不定项',4:'判断',5:'主观'}[Number(value)]||'题目');
function detail(row:Row){if(props.kind==='questions')return typeName(row.type)+' · '+row.score+' 分';if(props.kind==='media')return row.duration+' 秒';if(props.kind==='courses')return [row.categories||row.cateNames,row.sections?row.sections+' 节':'',row.updateTime?'更新 '+String(row.updateTime).replace('T',' '):''].filter(Boolean).join(' · ');return row.job||'';}
async function load(){
 const current=++sequence;busy.value=true;error.value='';
 const paths={courses:'/admin/course/courses/page',questions:'/admin/exam/questions/page',teachers:'/admin/user/teachers/page',media:'/admin/media/medias'};
 try{const result=await get<Row>(paths[props.kind]+'?'+query({pageNo:page.value,pageSize:20,name:['teachers','media'].includes(props.kind)?keyword.value:undefined,keyword:['questions','courses'].includes(props.kind)?keyword.value:undefined,status:props.kind==='courses'?status.value:props.kind==='teachers'?1:undefined}));if(current!==sequence)return;rows.value=result.list||[];total.value=Number(result.total||0);for(const row of rows.value)cache.value[String(row.id)]=row;}
 catch(e){if(current===sequence)error.value=e instanceof Error?e.message:'加载失败，请重试';}
 finally{if(current===sequence)busy.value=false;}
}
async function choose(){staged.value=[...values.value];open.value=true;page.value=1;await load();}
function commit(ids:string[]){emit('update:modelValue',props.multiple?ids:ids[0]||'');emit('change',ids.map(id=>cache.value[id]||{id}));}
function pick(row:Row){const id=String(row.id);if(!props.multiple){commit([id]);open.value=false;return;}if(staged.value.includes(id))staged.value=staged.value.filter(x=>x!==id);else if(staged.value.length<props.limit)staged.value.push(id);}
function remove(id:string){commit(values.value.filter(x=>x!==id));}
</script>
<template><div class="entity-picker">
 <div class="toolbar"><el-button :aria-label="label" :disabled="disabled" @click="choose">{{label}}</el-button><span v-if="multiple" class="muted">已选 {{values.length}}{{limit<100?' / '+limit:''}}</span></div>
 <div class="picker-chips"><el-tag v-for="row in selected" :key="row.id" :closable="!disabled" @close="remove(String(row.id))">{{name(row)}}</el-tag></div>
 <el-dialog v-model="open" :title="label" class="entity-dialog" append-to-body>
  <div class="toolbar"><el-input v-model="keyword" :aria-label="label+'关键词'" placeholder="输入名称关键词" clearable @keyup.enter="page=1;load()"/>
   <el-select v-if="kind==='courses' && courseStatuses.length>1" v-model="status" aria-label="筛选课程状态" style="width:140px" @change="page=1;load()"><el-option v-for="s in courseStatuses" :key="s" :value="s" :label="({1:'待上架',2:'已上架',3:'已下架',4:'已完结'} as Record<number,string>)[s]"/></el-select>
   <el-button @click="page=1;load()" :disabled="busy">查询</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <el-table :data="rows" v-loading="busy"><el-table-column label="名称" min-width="190"><template #default="{row}">{{name(row)}}</template></el-table-column><el-table-column label="说明" min-width="130"><template #default="{row}">{{detail(row)}}</template></el-table-column><el-table-column label="选择" width="100"><template #default="{row}"><el-button :type="staged.includes(String(row.id))?'primary':'default'" :disabled="multiple && !staged.includes(String(row.id)) && staged.length>=limit" @click="pick(row)">{{multiple?(staged.includes(String(row.id))?'已选':'添加'):'选用'}}</el-button></template></el-table-column></el-table>
  <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total,prev,pager,next" @current-change="load"/>
  <template #footer><span v-if="multiple" class="muted">已选 {{staged.length}} 项 · 跨页选择会保留</span><el-button @click="open=false">取消</el-button><el-button v-if="multiple" type="primary" @click="commit(staged);open=false">确认选择</el-button></template>
 </el-dialog>
</div></template>
