<script setup lang="ts">
import { computed } from 'vue';
import type { Row } from './ui';
import { formatStudyMinutes, studyDifficulty } from './courseStudyFacts';

const props = defineProps<{ course: Row }>();
const durations = computed(() => {
  const videos: Row[] = (props.course.chapters || [])
    .filter((chapter: Row) => Number(chapter.type) === 1)
    .flatMap((chapter: Row) => chapter.sections || [])
    .filter((item: Row) => Number(item.type) === 2);
  const seconds = videos.map((item) => Number(item.mediaDuration));
  const known = seconds.filter((value) => Number.isFinite(value) && value > 0);
  return {
    total: videos.length,
    known: known.length,
    minutes: Math.ceil(known.reduce((sum, value) => sum + value, 0) / 60),
  };
});
const videoDuration = computed(() => {
  const { total, known, minutes } = durations.value;
  if (!total) return '无视频小节';
  if (!known) return '时长待补充';
  const duration = formatStudyMinutes(minutes);
  return known < total ? '已知约 ' + duration + '，部分小节时长待补充' : '约 ' + duration;
});
const estimatedDuration = computed(() => {
  const manual = Number(props.course.estimatedStudyMinutes);
  if (manual > 0) return '约 ' + formatStudyMinutes(manual);
  const { total, known, minutes } = durations.value;
  return total > 0 && known === total ? '约 ' + formatStudyMinutes(minutes) + '（按视频时长估算）' : '未填写';
});
</script>

<template>
  <dl class="course-facts">
    <div><dt>学习成果</dt><dd>{{ course.learningOutcomes?.trim() || '未填写' }}</dd></div>
    <div><dt>先修要求</dt><dd>{{ course.prerequisites?.trim() || '未填写' }}</dd></div>
    <div><dt>难度</dt><dd>{{ studyDifficulty[course.difficulty] || '未填写' }}</dd></div>
    <div><dt>预计学习时长</dt><dd>{{ estimatedDuration }}</dd></div>
    <div><dt>视频总时长</dt><dd>{{ videoDuration }}</dd></div>
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
