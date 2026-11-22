<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, write } from '../../../../packages/shared/src/client';
import { useSession } from '../../../../packages/shared/src/session';
import { useTask, type Row } from '../../../../packages/shared/src/ui';
import type { Page } from '../../../../packages/shared/src/learning';
import { practiceStatus, type PracticeAssignmentView, type PracticeSubmission } from '../../../../packages/shared/src/practice';
import PracticeHistory from '../../../../packages/shared/src/PracticeHistory.vue';
import PracticeVersions from '../../../../packages/shared/src/PracticeVersions.vue';
import Status from '../../../../packages/shared/src/Status.vue';
import EntityPicker from '../components/EntityPicker.vue';
import { teacherCourses } from '../teacherCourses';

interface Work {
  userId: string;
  name: string;
  latest: PracticeSubmission;
}

const session = useSession();
const route = useRoute();
const task = useTask();
const courseId = ref('');
const courses = ref<Row[]>([]);
const title = ref('');
const instructions = ref('');
const configuration = ref<PracticeAssignmentView>({ assignment: null, versions: [] });
const assignment = computed(() => configuration.value.assignment);
const versions = computed(() => configuration.value.versions);
const publishedAt = computed(() => assignment.value?.publishedAt);
const correcting = ref(false);
const reason = ref('');
const changed = computed(() => title.value.trim() !== assignment.value?.title ||
  instructions.value.trim() !== assignment.value?.instructions);
const work = ref<Page<Work>>({ list: [], total: 0 });
const page = ref(1);
const selected = ref<PracticeSubmission>();
const history = ref<PracticeSubmission[]>([]);
const feedback = ref('');
const latestSelected = computed(() => selected.value?.id === history.value[0]?.id);
const adminPath = (course: string) => '/admin/learning-hub/practice/' + course;
const teacherAssignmentPath = (course: string) => '/teacher/learning-hub/practice/' + course;
const teacherPath = (course: string) => teacherAssignmentPath(course) + '/submissions';
const idInQuery = (value: unknown) => typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) ? value : undefined;
const pageInQuery = (value: unknown) => typeof value === 'string' && /^[1-9][0-9]{0,3}$/.test(value) ? value : '1';
const returnToQueue = computed(() => route.query.from === 'teaching' ? {
  path: '/teaching',
  hash: '#teacher-practice-heading',
  query: {
    workCourse: idInQuery(route.query.workCourse),
    gradePage: pageInQuery(route.query.gradePage),
    questionPage: pageInQuery(route.query.questionPage),
    practicePage: pageInQuery(route.query.practicePage),
    pendingOnly: route.query.pendingOnly === 'false' ? 'false' : 'true',
  },
} : null);

function resetEditor() {
  title.value = assignment.value?.title || '';
  instructions.value = assignment.value?.instructions || '';
  reason.value = '';
  correcting.value = false;
}

async function loadConfiguration() {
  const course = courseId.value;
  await task.run(async () => {
    const result = await get<PracticeAssignmentView>(adminPath(course));
    if (course !== courseId.value) return;
    configuration.value = result;
    resetEditor();
  });
}

async function save(publish: boolean) {
  const course = courseId.value;
  await task.run(async () => {
    await write(adminPath(course), 'PUT', {
      title: title.value,
      instructions: instructions.value,
      publish,
    });
    configuration.value = await get<PracticeAssignmentView>(adminPath(course));
    resetEditor();
    task.message.value = publish ? '作业已发布。' : '草稿已保存。';
  });
}

async function correct() {
  const course = courseId.value;
  const expectedVersion = assignment.value?.version;
  if (!expectedVersion) return;
  await task.run(async () => {
    configuration.value = await write<PracticeAssignmentView>(adminPath(course) + '/corrections', 'PUT', {
      title: title.value,
      instructions: instructions.value,
      reason: reason.value,
      expectedVersion,
    });
    resetEditor();
    task.message.value = '新版本已发布。';
  });
}

async function refreshWork() {
  const course = courseId.value;
  const result = await get<Page<Work>>(teacherPath(course) + '?pageNo=' + page.value);
  if (course === courseId.value) work.value = result;
}

async function open(studentId: string, submissionId?: string) {
  const course = courseId.value;
  const submissions = await get<PracticeSubmission[]>(teacherPath(course) + '/' + studentId);
  if (course !== courseId.value) return;
  const target = submissionId ? submissions.find((entry) => entry.id === submissionId) : submissions[0];
  if (!target) throw new Error('此作品已不可查看，请返回教学待办刷新列表。');
  const knownVersions = new Set(versions.value.map((version) => version.version));
  if (submissions.some((entry) => !knownVersions.has(entry.assignmentVersion))) {
    configuration.value = await get<PracticeAssignmentView>(teacherAssignmentPath(course));
  }
  selected.value = target;
  history.value = submissions;
  feedback.value = '';
}

function showLatest() {
  selected.value = history.value[0];
  focusSubmission();
}

function focusSubmission() {
  if (selected.value) document.getElementById('practice-submission-' + selected.value.id)?.focus();
}

async function review(status: 'NEEDS_REVISION' | 'PASSED') {
  if (!selected.value || !latestSelected.value) return;
  const course = courseId.value;
  const item = selected.value;
  await task.run(async () => {
    const result = await write<PracticeSubmission>(
      teacherPath(course) + '/' + item.id + '/feedback',
      'PUT',
      { status, feedback: feedback.value },
    );
    const current = selected.value;
    if (current?.id === item.id) {
      selected.value = result;
      history.value = history.value.map((row) => (row.id === result.id ? result : row));
    }
    task.message.value = '反馈已保存。';
    await refreshWork();
  });
}

function requestedSubmission() {
  const course = idInQuery(route.query.courseId);
  const student = idInQuery(route.query.studentId);
  const submission = idInQuery(route.query.submissionId);
  if (course && student && submission) return { course, student, submission };
}

async function openRequested() {
  const requested = requestedSubmission();
  if (requested?.course === courseId.value) {
    await open(requested.student, requested.submission);
  }
}

async function syncRequestedCourse() {
  const requested = requestedSubmission();
  if (!requested) return;
  if (!courses.value.some((course) => String(course.id) === requested.course)) {
    task.error.value = '当前账号无法查看这门课程的作品。';
    return;
  }
  if (courseId.value === requested.course) await task.run(openRequested);
  else courseId.value = requested.course;
}

watch(courseId, async (course) => {
  page.value = 1;
  selected.value = undefined;
  work.value = { list: [], total: 0 };
  configuration.value = { assignment: null, versions: [] };
  resetEditor();
  if (!course) return;
  if (session.role === 3) {
    await task.run(async () => {
      const [result] = await Promise.all([get<PracticeAssignmentView>(teacherAssignmentPath(course)), refreshWork()]);
      if (course !== courseId.value) return;
      configuration.value = result;
      await openRequested();
    });
  } else {
    await loadConfiguration();
  }
});

watch(() => [route.query.courseId, route.query.studentId, route.query.submissionId], () => {
  if (session.role === 3 && courses.value.length) void syncRequestedCourse();
});

onMounted(async () => {
  if (session.role !== 3) return;
  await task.run(async () => {
    courses.value = await teacherCourses();
  });
  await syncRequestedCourse();
});
</script>

<template>
  <section>
    <h1>实操作业</h1>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <template v-if="session.role === 3">
      <p v-if="returnToQueue"><RouterLink :to="returnToQueue">返回教学待办 →</RouterLink></p>
      <el-select v-model="courseId" aria-label="实操课程" placeholder="选择授课课程"
        filterable :disabled="task.busy.value">
        <el-option v-for="course in courses" :key="course.id" :value="String(course.id)" :label="course.name" />
      </el-select>
      <template v-if="courseId">
        <div v-if="publishedAt && assignment">
          <h2>{{ assignment.title }}</h2>
          <p>当前任务 · 第 {{ assignment.version }} 版</p>
          <p class="practice-instructions">{{ assignment.instructions }}</p>
          <PracticeVersions :versions="versions" />
        </div>
        <el-table :data="work.list" empty-text="暂无作品提交">
          <el-table-column prop="name" label="学生" />
          <el-table-column prop="latest.revision" label="提交次数" />
          <el-table-column label="任务版本">
            <template #default="{ row }">第 {{ row.latest.assignmentVersion }} 版</template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="{ row }">{{ practiceStatus[(row as Work).latest.status] }}</template>
          </el-table-column>
          <el-table-column label="提交时间">
            <template #default="{ row }">{{ String(row.latest.submittedAt).replace('T', ' ') }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button text :disabled="task.busy.value" @click="task.run(() => open(row.userId))">查看作品</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="work.total > 20"
          v-model:current-page="page"
          :page-size="20"
          :total="work.total"
          :disabled="task.busy.value"
          layout="prev, pager, next"
          @current-change="task.run(refreshWork)"
        />
      </template>
      <el-dialog :model-value="!!selected" title="作品与反馈" @opened="focusSubmission"
        @update:model-value="(open: boolean) => { if (!open) selected = undefined; }">
        <Status :error="task.error.value" />
        <PracticeHistory :submissions="history" :versions="versions" :highlighted-id="selected?.id" />
        <div v-if="selected && !latestSelected">
          <p>当前打开的是历史提交。</p>
          <el-button @click="showLatest">查看最近提交</el-button>
        </div>
        <div v-if="latestSelected && selected?.status === 'SUBMITTED'">
          <el-input
            v-model="feedback"
            type="textarea"
            aria-label="教师反馈"
            placeholder="填写教师反馈"
            maxlength="5000"
          />
          <div class="practice-actions">
            <el-button :disabled="task.busy.value || !feedback.trim()" @click="review('NEEDS_REVISION')">
              需要修改
            </el-button>
            <el-button type="primary" :disabled="task.busy.value || !feedback.trim()" @click="review('PASSED')">
              通过作业
            </el-button>
          </div>
        </div>
        <template v-if="returnToQueue" #footer>
          <RouterLink :to="returnToQueue">返回教学待办 →</RouterLink>
        </template>
      </el-dialog>
    </template>
    <template v-else>
      <EntityPicker v-model="courseId" kind="courses" label="选择试点课程" :course-statuses="[2]" :disabled="task.busy.value" />
      <el-form v-if="courseId" label-position="top" class="practice-editor" aria-label="配置实操作业">
        <el-form-item label="作业标题">
          <el-input v-model="title" maxlength="120" :disabled="!!publishedAt && !correcting" />
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="instructions" type="textarea" maxlength="8000" :rows="7" :disabled="!!publishedAt && !correcting" />
        </el-form-item>
        <template v-if="publishedAt">
          <p>已发布 · 当前第 {{ assignment?.version }} 版</p>
          <PracticeVersions :versions="versions" />
          <template v-if="correcting">
            <el-form-item label="更正说明" required>
              <el-input v-model="reason" type="textarea" maxlength="1000" :rows="3" />
            </el-form-item>
            <div class="practice-actions">
              <el-button type="primary" :disabled="task.busy.value || !changed ||
                !title.trim() || !instructions.trim() || !reason.trim()" @click="correct">发布新版本</el-button>
              <el-button :disabled="task.busy.value" @click="resetEditor">取消更正</el-button>
              <el-button v-if="task.error.value" :disabled="task.busy.value" @click="loadConfiguration">载入最新版本</el-button>
            </div>
          </template>
          <el-button v-else :disabled="task.busy.value" @click="correcting = true">更正任务说明</el-button>
        </template>
        <div v-else class="practice-actions">
          <el-button :disabled="task.busy.value || !title.trim() || !instructions.trim()" @click="save(false)">保存草稿</el-button>
          <el-button type="primary" :disabled="task.busy.value || !title.trim() || !instructions.trim()" @click="save(true)">发布作业</el-button>
        </div>
      </el-form>
    </template>
  </section>
</template>

<style scoped>
.practice-editor {
  margin-block: 1rem;
  max-width: 48rem;
}
.practice-instructions {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-block: 1rem;
}
.practice-actions .el-button {
  margin-left: 0;
}
</style>
