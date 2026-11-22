<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, newKey, write } from '../../../../packages/shared/src/client';
import { useTask } from '../../../../packages/shared/src/ui';
import type {
  PracticeAssignment,
  PracticeSubmission,
  PracticeVersion,
} from '../../../../packages/shared/src/practice';
import PracticeHistory from '../../../../packages/shared/src/PracticeHistory.vue';
import PracticeComparison from '../../../../packages/shared/src/PracticeComparison.vue';
import PracticeVersions from '../../../../packages/shared/src/PracticeVersions.vue';
import Status from '../../../../packages/shared/src/Status.vue';

interface StudentView {
  assignment: PracticeAssignment;
  versions: PracticeVersion[];
  historyOnly: boolean;
  canSubmit: boolean;
  submissions: PracticeSubmission[];
}

const route = useRoute();
const task = useTask();
const assignments = ref<Pick<PracticeAssignment, 'courseId' | 'title'>[]>([]);
const selected = ref<StudentView>();
const submissions = computed(() => selected.value?.submissions ?? []);
const currentVersion = computed(() => selected.value?.historyOnly ? undefined :
  selected.value?.versions.find((version) => version.version === selected.value?.assignment.version));
const requestedVersion = computed(() => {
  const version = route.query.version;
  if (typeof version === 'string' && /^[1-9][0-9]{0,9}$/.test(version) && Number(version) > 1)
    return Number(version);
});
const correction = computed(() => selected.value?.versions.find((version) => version.version === requestedVersion.value));
const previous = computed(() => {
  const version = correction.value;
  if (version) return selected.value?.versions.find((item) => item.version === version.version - 1);
});
const description = ref('');
const workUrl = ref('');
let submittedDraft:
  | { description: string; workUrl: string; submissionKey: string; assignmentVersion: number }
  | undefined;
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

watch([() => selected.value?.assignment.courseId, requestedVersion], async ([course, version]) => {
  if (!course || !version) return;
  await nextTick();
  document.getElementById('practice-correction-heading')?.focus();
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
  const current = selected.value;
  if (!current?.canSubmit || current.assignment.version === null) return;
  const { courseId: course, version: assignmentVersion } = current.assignment;
  const text = description.value.trim();
  const url = workUrl.value.trim();
  if (
    !submittedDraft ||
    submittedDraft.description !== text ||
    submittedDraft.workUrl !== url ||
    submittedDraft.assignmentVersion !== assignmentVersion
  )
    submittedDraft = { description: text, workUrl: url, submissionKey: newKey(), assignmentVersion };
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
      <section v-if="requestedVersion" class="card" aria-labelledby="practice-correction-heading">
        <h2 id="practice-correction-heading" tabindex="-1">第 {{ requestedVersion }} 版更正</h2>
        <template v-if="correction">
          <h3>{{ correction.title }}</h3>
          <p class="practice-text">更正说明 · {{ correction.changeReason }}</p>
          <PracticeComparison v-if="previous" :before="previous" :after="correction"
            :before-label="'更正前 · 第 ' + previous.version + ' 版'"
            :after-label="'更正后 · 第 ' + correction.version + ' 版'" />
          <p v-else class="practice-text">{{ correction.instructions }}</p>
        </template>
        <p v-else>当前无法查看这次更正。</p>
      </section>
      <article class="card">
        <h2>{{ selected.assignment.title }}</h2>
        <p v-if="selected.historyOnly">学习权益已失效，以下为你提交时的任务说明。</p>
        <p>
          {{ selected.historyOnly ? '最近提交使用的版本' : '当前任务' }} · 第
          {{ selected.assignment.version }} 版
        </p>
        <p class="practice-text">{{ selected.assignment.instructions }}</p>
        <PracticeVersions :versions="selected.versions" />
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
      <PracticeHistory :submissions="submissions" :versions="selected?.versions ?? []" :current-version="currentVersion" />
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
