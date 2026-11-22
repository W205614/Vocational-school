<script setup lang="ts">
import { computed, ref } from 'vue';
import { practiceStatus, type PracticeSubmission, type PracticeVersion } from './practice';
import PracticeComparison from './PracticeComparison.vue';
const props = defineProps<{
  submissions: PracticeSubmission[];
  versions: PracticeVersion[];
  currentVersion?: PracticeVersion;
  highlightedId?: string;
}>();
const byVersion = computed(() => new Map(props.versions.map((version) => [version.version, version])));
const history = computed(() => props.submissions.map((item) => ({ item, version: byVersion.value.get(item.assignmentVersion) })));
const comparedId = ref<string>();
</script>

<template>
  <ol v-if="submissions.length" class="practice-history">
    <li
      v-for="{ item, version } in history"
      :id="'practice-submission-' + item.id"
      :key="item.id"
      class="card"
      :class="{ 'selected-work': item.id === highlightedId }"
      tabindex="-1"
    >
      <h3>第 {{ item.revision }} 次提交 · {{ practiceStatus[item.status] }}</h3>
      <time :datetime="item.submittedAt">{{ item.submittedAt.replace('T', ' ') }}</time>
      <details v-if="version">
        <summary>提交时的任务说明 · 第 {{ item.assignmentVersion }} 版</summary>
        <h4>{{ version.title }}</h4>
        <p class="practice-text">{{ version.instructions }}</p>
      </details>
      <template v-if="version && currentVersion && version.version !== currentVersion.version">
        <el-button
          link type="primary" :aria-expanded="comparedId === item.id"
          :aria-controls="comparedId === item.id ? 'practice-compare-' + item.id : undefined"
          @click="comparedId = comparedId === item.id ? undefined : item.id"
        >{{ comparedId === item.id ? '收起版本对照' : '对照当前任务要求' }}</el-button>
        <PracticeComparison v-if="comparedId === item.id" :id="'practice-compare-' + item.id"
          :before="version" :after="currentVersion"
          :before-label="'提交使用 · 第 ' + version.version + ' 版'"
          :after-label="'当前要求 · 第 ' + currentVersion.version + ' 版'" />
      </template>
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
