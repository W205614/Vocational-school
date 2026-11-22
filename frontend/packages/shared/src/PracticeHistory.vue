<script setup lang="ts">
import { computed } from 'vue';
import { practiceStatus, type PracticeSubmission, type PracticeVersion } from './practice';
const props = defineProps<{
  submissions: PracticeSubmission[];
  versions: PracticeVersion[];
  highlightedId?: string;
}>();
const byVersion = computed(() => new Map(props.versions.map((version) => [version.version, version])));
</script>

<template>
  <ol v-if="submissions.length" class="practice-history">
    <li
      v-for="item in submissions"
      :id="'practice-submission-' + item.id"
      :key="item.id"
      class="card"
      :class="{ 'selected-work': item.id === highlightedId }"
      tabindex="-1"
    >
      <h3>第 {{ item.revision }} 次提交 · {{ practiceStatus[item.status] }}</h3>
      <time :datetime="item.submittedAt">{{ item.submittedAt.replace('T', ' ') }}</time>
      <details v-if="byVersion.has(item.assignmentVersion)">
        <summary>提交时的任务说明 · 第 {{ item.assignmentVersion }} 版</summary>
        <h4>{{ byVersion.get(item.assignmentVersion)?.title }}</h4>
        <p class="practice-text">{{ byVersion.get(item.assignmentVersion)?.instructions }}</p>
      </details>
      <p class="practice-text">{{ item.description }}</p>
      <a v-if="item.workUrl" :href="item.workUrl" target="_blank" rel="noopener noreferrer">
        打开作品（新窗口）
      </a>
      <div v-if="item.feedback">
        <h4>教师反馈</h4>
        <p class="practice-text">{{ item.feedback }}</p>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.practice-history {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}
.practice-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.selected-work {
  border-inline-start: 3px solid var(--el-color-primary);
}
</style>
