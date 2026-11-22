<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError, get, newKey, write } from '../../../../packages/shared/src/client';
import { useSession } from '../../../../packages/shared/src/session';
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
import {
  clearConfirmedPracticeDraft, draftMatchesAttempt, readPracticeDraft, storePracticeDraft, type PracticeDraft,
} from '../practiceDraft';

interface StudentView {
  assignment: PracticeAssignment;
  versions: PracticeVersion[];
  historyOnly: boolean;
  canSubmit: boolean;
  submissions: PracticeSubmission[];
}

const route = useRoute();
const task = useTask();
const session = useSession();
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
const editor = ref<{ key: string; draft: PracticeDraft }>();
const draftDescription = computed(() => editor.value?.draft.description ?? '');
const draftLink = computed(() => editor.value?.draft.workUrl ?? '');
const storageUnavailable = ref(false);
const reviewNeeded = computed(() => !!editor.value &&
  editor.value.draft.reviewedVersion !== selected.value?.assignment.version);
let selection = 0;
let reloadPending = false;

function draftStorage<T>(action: () => T): T | undefined {
  try {
    const result = action();
    storageUnavailable.value = false;
    return result;
  } catch {
    storageUnavailable.value = true;
  }
}

function restoreEditor(view: StudentView, owner: string) {
  const { courseId, version } = view.assignment;
  const key = owner ? 'school-practice-draft-' + owner + ':' + courseId : '';
  let saved = editor.value?.key === key ? editor.value.draft :
    key ? draftStorage(() => readPracticeDraft(key)) : undefined;
  const attempt = saved?.attempt;
  if (saved && attempt && draftMatchesAttempt(saved, attempt) && view.submissions.some((item) =>
    item.revision > attempt.afterRevision && item.assignmentVersion === attempt.assignmentVersion &&
    item.description === attempt.description && (item.workUrl ?? '') === attempt.workUrl)) {
    if (key) draftStorage(() => clearConfirmedPracticeDraft(key, attempt));
    saved = undefined;
  }
  editor.value = view.canSubmit && version !== null ? {
    key, draft: saved ?? { description: '', workUrl: '', reviewedVersion: version },
  } : undefined;
}

function saveEditor() {
  const active = editor.value;
  if (active?.key) draftStorage(() => storePracticeDraft(active.key, active.draft));
}

function editDraft(field: 'description' | 'workUrl', value: string) {
  if (!editor.value) return;
  editor.value.draft[field] = value;
  saveEditor();
}

function confirmReview() {
  const version = selected.value?.assignment.version;
  if (!editor.value || !version) return;
  editor.value.draft.reviewedVersion = version;
  saveEditor();
}

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
  const owner = session.userId;
  await task.run(async () => {
    const result = await get<StudentView>('/learning-hub/practice/' + encodeURIComponent(courseId));
    if (current !== selection) return;
    selected.value = result;
    restoreEditor(result, owner);
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
  [() => route.query.courseId, () => session.userId],
  ([courseId]) => {
    selected.value = undefined;
    editor.value = undefined;
    storageUnavailable.value = false;
    void load(courseId);
  },
  { immediate: true },
);

async function submit() {
  const current = selected.value;
  const active = editor.value;
  if (!active || !current?.canSubmit || current.assignment.version === null ||
    reviewNeeded.value || task.busy.value || !active.draft.description.trim()) return;
  const { courseId: course, version: assignmentVersion } = current.assignment;
  const owner = session.userId;
  let attempt = active.draft.attempt;
  if (!attempt || !draftMatchesAttempt(active.draft, attempt)) {
    attempt = {
      description: active.draft.description.trim(), workUrl: active.draft.workUrl.trim(),
      submissionKey: newKey(), assignmentVersion, afterRevision: current.submissions[0]?.revision ?? 0,
    };
    active.draft.attempt = attempt;
    saveEditor();
  }
  const { description, workUrl, submissionKey } = attempt;
  let conflicted = false;
  await task.run(async () => {
    let item: PracticeSubmission;
    try {
      item = await write<PracticeSubmission>(
        '/learning-hub/practice/' + course + '/submissions', 'POST',
        { description, workUrl, submissionKey, assignmentVersion },
      );
    } catch (error) {
      conflicted = error instanceof ApiError && error.status === 409;
      throw error;
    }
    if (active.key) draftStorage(() => clearConfirmedPracticeDraft(active.key, attempt));
    if (editor.value !== active || selected.value?.assignment.courseId !== course) return;
    selected.value = {
      ...selected.value,
      canSubmit: item.status === 'NEEDS_REVISION',
      submissions: [item, ...selected.value.submissions.filter((prior) => prior.id !== item.id)],
    };
    editor.value = item.status === 'NEEDS_REVISION' ? {
      key: active.key, draft: { description: '', workUrl: '', reviewedVersion: assignmentVersion },
    } : undefined;
    task.message.value = '作品已提交，可在下方查看提交和反馈。';
  });
  if (conflicted && owner === session.userId && editor.value === active &&
    route.path === '/practice' && route.query.courseId === course) {
    const conflict = task.error.value;
    await load(course);
    if (owner === session.userId && route.path === '/practice' && route.query.courseId === course &&
      !reviewNeeded.value && !task.error.value)
      task.error.value = conflict;
  }
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
        v-if="selected.canSubmit && editor"
        class="card"
        label-position="top"
        aria-label="提交作品"
        :disabled="task.busy.value"
        @submit.prevent="submit"
      >
        <p v-if="!editor.key || storageUnavailable" role="status">当前浏览器无法保存草稿，离开前请复制作品内容。</p>
        <p v-else>草稿保存在当前浏览器，提交成功后清除。</p>
        <div v-if="reviewNeeded">
          <p role="status">任务说明已从第 {{ editor.draft.reviewedVersion }} 版更新到第 {{ selected.assignment.version }} 版。草稿已保留，请先核对新要求。</p>
          <el-button @click="confirmReview">已核对第 {{ selected.assignment.version }} 版要求</el-button>
        </div>
        <el-form-item label="作品说明" required>
          <el-input :model-value="draftDescription" @update:model-value="editDraft('description', $event)"
            type="textarea" :rows="5" maxlength="5000" />
        </el-form-item>
        <el-form-item label="作品链接（可选）">
          <el-input :model-value="draftLink" @update:model-value="editDraft('workUrl', $event)"
            type="url" maxlength="2048" placeholder="https://" />
        </el-form-item>
        <el-button
          native-type="submit"
          type="primary"
          :disabled="task.busy.value || reviewNeeded || !draftDescription.trim()"
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
