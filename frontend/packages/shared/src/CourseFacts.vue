<script setup lang="ts">
import { computed } from 'vue';
import type { Row } from './ui';

const props = defineProps<{ course: Row }>();
const durations = computed(() => {
  const videos: Row[] = (props.course.chapters || [])
    .flatMap((chapter: Row) => chapter.sections || [])
    .filter((item: Row) => Number(item.type) === 2);
  const seconds = videos.map((item) => Number(item.mediaDuration));
  const known = seconds.filter((value) => Number.isFinite(value) && value > 0);
  if (!videos.length) return '无视频小节';
  if (!known.length) return '时长待补充';
  const minutes = Math.ceil(known.reduce((sum, value) => sum + value, 0) / 60);
  const duration = minutes < 60 ? minutes + ' 分钟' : Math.floor(minutes / 60) + ' 小时' +
    (minutes % 60 ? ' ' + (minutes % 60) + ' 分钟' : '');
  return known.length < videos.length ? '已知约 ' + duration + '，部分小节时长待补充' : '约 ' + duration;
});
const levels: Record<string, string> = { BEGINNER: '入门', INTERMEDIATE: '进阶', ADVANCED: '高阶' };
</script>

<template>
  <dl class="course-facts">
    <div><dt>学习成果</dt><dd>{{ course.learningOutcomes?.trim() || '未填写' }}</dd></div>
    <div><dt>先修要求</dt><dd>{{ course.prerequisites?.trim() || '未填写' }}</dd></div>
    <div><dt>难度</dt><dd>{{ levels[course.difficulty] || '未填写' }}</dd></div>
    <div><dt>视频总时长</dt><dd>{{ durations }}</dd></div>
    <div><dt>实操内容</dt><dd>{{ course.practiceOverview?.trim() || '未填写' }}</dd></div>
  </dl>
</template>

<style scoped>
.course-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr));
  gap: .55rem 1rem;
  margin: 1rem 0;
}
.course-facts dt { font-weight: 600; }
.course-facts dd { margin: 0; overflow-wrap: anywhere; white-space: pre-line; }
</style>
