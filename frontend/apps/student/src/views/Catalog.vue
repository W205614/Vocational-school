<script setup lang="ts">
import {ref,onMounted} from 'vue';import {get} from '../../../../packages/shared/src/client';import {useTask,money,query,type Row} from '../../../../packages/shared/src/ui';import type {components} from '../../../../packages/shared/src/generated/search';import Status from '../../../../packages/shared/src/Status.vue';import {titleParts,plainTitle} from '../../../../packages/shared/src/highlight';
import {useSession} from '../../../../packages/shared/src/session';
import {useRoute,useRouter} from 'vue-router';
const route=useRoute(),router=useRouter(),session=useSession();
const interfaceExamples=[{image:'learning-plan',label:'每周学习计划',to:'/learning'},{image:'curriculum',label:'按课程章节学习',to:'/courses/1'},{image:'calendar',label:'签到与学习记录',to:'/points'},{image:'review-filter',label:'按课程整理错题',to:'/review'}];
const initial=(key:string)=>String(route.query[key]||'');
type Course=components['schemas']['CourseVO'];type CoursePage=components['schemas']['PageDTOCourseVO'];
const appliedKeyword=ref('');const failedCovers=ref(new Set<string>()),task=useTask(),keyword=ref(initial('keyword')),page=ref(Math.max(1,Number(route.query.pageNo)||1)),rows=ref<Course[]>([]),total=ref(0),sort=ref(initial('sortBy')),categories=ref<Row[]>([]),category=ref(initial('categoryIdLv1')),free=ref(initial('free')),categoryError=ref('');
// Keep one request in flight and coalesce rapid filter changes into the latest query.
let pendingLoad=false;
async function load(){
 void router.replace({query:Object.fromEntries(Object.entries({keyword:keyword.value,pageNo:page.value>1?String(page.value):'',sortBy:sort.value,categoryIdLv1:category.value,free:free.value}).filter(([,value])=>value!==''))});
 pendingLoad=true;
 if(task.busy.value)return;
 await task.run(async()=>{
  while(pendingLoad){
   pendingLoad=false;
   const requestedKeyword=keyword.value;const result=await get<CoursePage>((session.token?'/services/search/courses/portal?':'/public/courses?')+query({keyword:requestedKeyword,pageNo:page.value,pageSize:20,sortBy:sort.value,isAsc:sort.value==='price',categoryIdLv1:category.value,free:free.value}));
   if(!pendingLoad){appliedKeyword.value=requestedKeyword;rows.value=result.list||[];total.value=Number(result.total||0);}
  }
 });
 if(pendingLoad)void load();
}
function filterCategory(id:string){category.value=id;page.value=1;void load();}
function reset(){keyword.value='';category.value='';free.value='';sort.value='';page.value=1;void load();}
onMounted(async()=>{void load();try{categories.value=await get<Row[]>(session.token?'/services/course/categorys/list?status=1':'/public/categories');}catch{categoryError.value='课程分类暂时无法加载，仍可搜索课程。';}});
</script>
<template><section class="editorial-hero"><div class="editorial-lead"><p>从课程目录出发，找到下一步要学的内容。</p><a href="#courses" class="hero-cta">浏览课程 →</a><p class="hero-aside">在天机学堂，学习之后还有笔记、课程考试与错题复习。</p></div><div class="editorial-title"><h1><span>把所学</span><span>变成所得。</span></h1><div v-if="rows.filter(c=>c.coverUrl).length>=4" class="course-mosaic"><RouterLink v-for="course in rows.filter(c=>c.coverUrl).slice(0,4)" :key="course.id" :to="'/courses/'+course.id"><img :src="course.coverUrl" :alt="plainTitle(course.name,!!appliedKeyword.trim())" width="500" height="280"/><span>{{plainTitle(course.name,!!appliedKeyword.trim())}}</span></RouterLink></div><div v-else class="course-mosaic interface-mosaic"><RouterLink v-for="example in interfaceExamples" :key="example.image" :to="example.to"><img :src="'/interface/'+example.image+'.png'" :alt="example.label+'，实际功能界面示例'" width="600" height="300"/><span>{{example.label}} · 界面示例</span></RouterLink></div></div></section>
<section class="editorial-category"><h2>你想学习什么？</h2><div class="category-index"><button class="category-button" :class="{chosen:!category}" @click="filterCategory('')">全部课程 →</button><button v-for="item in categories" :key="item.id" class="category-button" :class="{chosen:category===String(item.id)}" @click="filterCategory(String(item.id))">{{item.name}} →<small>{{(item.children||[]).slice(0,3).map((c:Row)=>c.name).join(' · ')}}</small></button></div><p v-if="categoryError">{{categoryError}}</p></section>
<section id="courses" class="card catalog-content"><div class="section-heading"><div><h2>课程中心</h2></div><span class="muted">共 {{total}} 门课程</span></div><div class="catalog-search"><el-input v-model="keyword" placeholder="搜索课程" aria-label="搜索课程" clearable @keyup.enter="page=1;load()"/><el-select v-model="sort" aria-label="课程排序" placeholder="排序" style="width:150px" @change="page=1;load()"><el-option label="综合排序" value=""/><el-option label="价格由低到高" value="price"/><el-option label="学习人数最多" value="sold"/><el-option label="最新上架" value="publishTime"/></el-select><el-button type="primary" @click="page=1;load()">搜索</el-button></div>
<div class="course-filters"><button :class="{chosen:free===''}" @click="free='';page=1;load()">全部课程</button><button :class="{chosen:free==='true'}" @click="free='true';page=1;load()">免费课程</button><button :class="{chosen:free==='false'}" @click="free='false';page=1;load()">付费课程</button><el-button v-if="keyword || category || free" text @click="reset">清除筛选</el-button></div><Status :busy="task.busy.value" :error="task.error.value"/>
<div class="grid course-grid"><article v-for="(course,i) in rows" :key="course.id" class="course-tile"><RouterLink :to="'/courses/'+course.id" class="cover-link" :aria-label="'查看课程：'+plainTitle(course.name,!!appliedKeyword.trim())"><img v-if="course.coverUrl && !failedCovers.has(course.id)" :src="course.coverUrl" class="course-cover" loading="lazy" :alt="plainTitle(course.name,!!appliedKeyword.trim())" @error="failedCovers.add(course.id)"/><div v-else class="course-cover designed-cover" :class="'cover-tone-'+i%4"><small>天机学堂</small><strong>{{plainTitle(course.name,!!appliedKeyword.trim())}}</strong><span>查看课程目录 →</span></div></RouterLink><div class="course-tile-body"><h3><RouterLink :to="'/courses/'+course.id"><template v-for="(part,index) in titleParts(course.name,!!appliedKeyword.trim())" :key="index"><mark v-if="part.highlight">{{part.text}}</mark><span v-else>{{part.text}}</span></template></RouterLink></h3><p class="muted">讲师：{{course.teacher||'课程团队'}}</p><p class="course-meta">{{course.sections ?? 0}} 小节 <span>{{course.sold||0}} 人学习</span></p><div class="tile-bottom"><strong class="price">{{Number(course.price)===0?'免费':money(course.price)}}</strong><RouterLink :to="'/courses/'+course.id">查看课程 →</RouterLink></div></div></article></div><el-empty v-if="!task.busy.value && !rows.length && !task.error.value" description="没有找到课程"><el-button @click="reset">查看全部课程</el-button></el-empty><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next" @current-change="load"/></section>
</template>
