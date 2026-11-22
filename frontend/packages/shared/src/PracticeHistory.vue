<script setup lang="ts">
import { practiceStatus, type PracticeSubmission } from './practice';
defineProps<{ submissions: PracticeSubmission[] }>();
</script>

<template>
  <ol v-if="submissions.length" class="practice-history">
    <li v-for="item in submissions" :key="item.id" class="card">
      <h3>第 {{ item.revision }} 次提交 · {{ practiceStatus[item.status] }}</h3>
      <time :datetime="item.submittedAt">{{ item.submittedAt.replace('T', ' ') }}</time>
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
</style>
