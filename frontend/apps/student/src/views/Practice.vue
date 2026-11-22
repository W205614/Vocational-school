<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, newKey, write } from '../../../../packages/shared/src/client';
import { useTask } from '../../../../packages/shared/src/ui';
import type { PracticeAssignment, PracticeSubmission } from '../../../../packages/shared/src/practice';
import PracticeHistory from '../../../../packages/shared/src/PracticeHistory.vue';
import Status from '../../../../packages/shared/src/Status.vue';

interface StudentView {
  assignment: PracticeAssignment;
  canSubmit: boolean;
  submissions: PracticeSubmission[];
}

const route = useRoute();
const task = useTask();
const assignments = ref<Pick<PracticeAssignment, 'courseId' | 'title'>[]>([]);
const selected = ref<StudentView>();
const submissions = computed(() => selected.value?.submissions ?? []);
const description = ref('');
const workUrl = ref('');
let submittedDraft: { description: string; workUrl: string; submissionKey: string } | undefined;
let selection = 0;
let reloadPending = false;

async function load(courseId: unknown) {
  const current = ++selection;
  if (task.busy.value) {
    reloadPending = true;
    return;
  }
  reloadPending = false;
  if (typeof courseId !== 'string') {
    selected.value = undefined;
    await task.run(async () => {
      const result = await get<typeof assignments.value>('/learning-hub/practice');
      if (current === selection) assignments.value = result;
    });
    return;
  }
  selected.value = undefined;
  await task.run(async () => {
    const result = await get<StudentView>('/learning-hub/practice/' + encodeURIComponent(courseId));
    if (current === selection) selected.value = result;
  });
}

watch(task.busy, (busy) => {
  if (!busy && reloadPending) void load(route.query.courseId);
});

watch(
  () => route.query.courseId,
  (courseId) => {
    description.value = '';
    workUrl.value = '';
    submittedDraft = undefined;
    void load(courseId);
  },
  { immediate: true },
);

async function submit() {
  if (!selected.value?.canSubmit) return;
  const course = selected.value.assignment.courseId;
  const text = description.value.trim();
  const url = workUrl.value.trim();
  if (!submittedDraft || submittedDraft.description !== text || submittedDraft.workUrl !== url)
    submittedDraft = { description: text, workUrl: url, submissionKey: newKey() };
  let saved = false;
  await task.run(async () => {
    const item = await write<PracticeSubmission>(
      '/learning-hub/practice/' + course + '/submissions',
      'POST',
      submittedDraft,
    );
    if (selected.value?.assignment.courseId !== course) return;
    selected.value = {
      ...selected.value,
      canSubmit: item.status === 'NEEDS_REVISION',
      submissions: [item, ...selected.value.submissions.filter((prior) => prior.id !== item.id)],
    };
    saved = true;
  });
  if (!saved) return;
  description.value = '';
  workUrl.value = '';
  submittedDraft = undefined;
  task.message.value = '作品已提交，可在下方查看提交和反馈。';
}
</script>

<template>
  <section class="practice-page">
    <div class="section-heading">
      <h1>实操作业</h1>
      <RouterLink v-if="route.query.courseId" to="/practice">全部作业</RouterLink>
      <RouterLink v-else to="/learning">返回我的学习</RouterLink>
    </div>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <template v-if="selected">
      <article class="card">
        <h2>{{ selected.assignment.title }}</h2>
        <p class="practice-text">{{ selected.assignment.instructions }}</p>
        <RouterLink :to="'/courses/' + selected.assignment.courseId">查看课程</RouterLink>
      </article>
      <el-form
        v-if="selected.canSubmit"
        class="card"
        label-position="top"
        aria-label="提交作品"
        @submit.prevent="submit"
      >
        <el-form-item label="作品说明" required>
          <el-input v-model="description" type="textarea" :rows="5" maxlength="5000" />
        </el-form-item>
        <el-form-item label="作品链接（可选）">
          <el-input v-model="workUrl" type="url" maxlength="2048" placeholder="https://" />
        </el-form-item>
        <el-button
          native-type="submit"
          type="primary"
          :disabled="task.busy.value || !description.trim()"
        >
          {{ selected.submissions.length ? '修改后重交' : '提交作品' }}
        </el-button>
      </el-form>
      <h2 v-if="submissions.length">提交与反馈</h2>
      <PracticeHistory :submissions="submissions" />
    </template>
    <template v-else-if="!route.query.courseId">
      <el-empty v-if="!task.busy.value && !task.error.value && !assignments.length" description="暂无实操作业" />
      <ul v-else class="practice-list">
        <li v-for="item in assignments" :key="item.courseId" class="card">
          <RouterLink :to="{ path: '/practice', query: { courseId: item.courseId } }">
            {{ item.title }}
          </RouterLink>
        </li>
      </ul>
    </template>
    <el-button v-if="task.error.value" @click="load(route.query.courseId)">
      重新加载
    </el-button>
  </section>
</template>

<style scoped>
.practice-page > .card,
.practice-page > form {
  margin-block: 1rem;
}
.practice-list {
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
