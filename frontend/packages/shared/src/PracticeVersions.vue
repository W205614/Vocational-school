<script setup lang="ts">
import type { PracticeVersion } from './practice';
defineProps<{ versions: PracticeVersion[] }>();
</script>

<template>
  <details v-if="versions.length" class="practice-versions">
    <summary>任务版本记录（{{ versions.length }}）</summary>
    <ol>
      <li v-for="version in versions" :key="version.version">
        <details>
          <summary>
            第 {{ version.version }} 版 · {{ version.title }} ·
            {{ version.effectiveAt.replace('T', ' ') }}
          </summary>
          <p v-if="version.version > 1" class="practice-version-text">
            更正说明 · {{ version.changeReason }}
          </p>
          <p v-else>首次发布</p>
          <p class="practice-version-text">{{ version.instructions }}</p>
        </details>
      </li>
    </ol>
  </details>
</template>

<style scoped>
.practice-versions {
  margin-block: 1rem;
}
.practice-versions ol {
  display: grid;
  gap: 0.5rem;
  padding-inline-start: 1.5rem;
}
.practice-version-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
