<script setup lang="ts">
import { computed } from 'vue';

type TaskText = { title: string; instructions: string };
const props = defineProps<{
  before: TaskText;
  after: TaskText;
  beforeLabel: string;
  afterLabel: string;
}>();

function difference(before: string, after: string) {
  if (before === after) return undefined;
  const oldText = [...before];
  const newText = [...after];
  const sharedLength = Math.min(oldText.length, newText.length);
  let start = 0;
  let end = 0;
  while (start < sharedLength && oldText[start] === newText[start]) start++;
  while (end < sharedLength - start &&
    oldText[oldText.length - 1 - end] === newText[newText.length - 1 - end]) end++;
  const parts = (text: string[]) => ({
    prefix: text.slice(0, start).join(''),
    change: text.slice(start, text.length - end).join(''),
    suffix: text.slice(text.length - end).join(''),
  });
  return [parts(oldText), parts(newText)];
}

const fields = computed(() => [
  { label: '作业标题', difference: difference(props.before.title, props.after.title) },
  { label: '任务说明', difference: difference(props.before.instructions, props.after.instructions) },
]);
</script>

<template>
  <section class="practice-comparison" aria-label="任务要求差异">
    <div v-for="field in fields" :key="field.label" class="comparison-field">
      <h4>{{ field.label }}{{ field.difference ? '有改动' : '相同' }}</h4>
      <div v-if="field.difference" class="comparison-grid">
        <div v-for="(part, index) in field.difference" :key="index" class="comparison-side">
          <strong>{{ index === 0 ? beforeLabel : afterLabel }}</strong>
          <p><span>{{ part.prefix }}</span><mark v-if="part.change">{{ part.change }}</mark><span>{{ part.suffix }}</span></p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.comparison-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr));
  gap: 0.75rem;
}
.comparison-side {
  border: 1px solid var(--el-border-color);
  padding: 0.75rem;
}
.comparison-side p {
  margin-bottom: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.comparison-side mark {
  background: var(--el-color-warning-light-7);
  color: inherit;
  font-weight: 600;
}
</style>
