<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { get, write } from '../../../../packages/shared/src/client';
import { useSession } from '../../../../packages/shared/src/session';
import { useTask, type Row } from '../../../../packages/shared/src/ui';
import type { Page } from '../../../../packages/shared/src/learning';
import { practiceStatus, type PracticeAssignment, type PracticeSubmission } from '../../../../packages/shared/src/practice';
import PracticeHistory from '../../../../packages/shared/src/PracticeHistory.vue';
import Status from '../../../../packages/shared/src/Status.vue';
import EntityPicker from '../components/EntityPicker.vue';

interface Work {
  userId: string;
  name: string;
  latest: PracticeSubmission;
}

const session = useSession();
const task = useTask();
const courseId = ref('');
const courses = ref<Row[]>([]);
const title = ref('');
const instructions = ref('');
const publishedAt = ref('');
const work = ref<Page<Work>>({ list: [], total: 0 });
const page = ref(1);
const selected = ref<Work>();
const history = ref<PracticeSubmission[]>([]);
const feedback = ref('');
const adminPath = (course: string) => '/admin/learning-hub/practice/' + course;
const teacherPath = (course: string) => '/teacher/learning-hub/practice/' + course + '/submissions';

async function configuration() {
  const course = courseId.value;
  await task.run(async () => {
    const result = await get<{ assignment?: PracticeAssignment }>(adminPath(course));
    if (course !== courseId.value) return;
    title.value = result.assignment?.title || '';
    instructions.value = result.assignment?.instructions || '';
    publishedAt.value = result.assignment?.publishedAt || '';
  });
}

async function save(publish: boolean) {
  const course = courseId.value;
  await task.run(async () => {
    const result = await write<PracticeAssignment>(adminPath(course), 'PUT', {
      title: title.value,
      instructions: instructions.value,
      publish,
    });
    publishedAt.value = result.publishedAt || '';
    task.message.value = publish ? '作业已发布。' : '草稿已保存。';
  });
}

async function refreshWork() {
  const course = courseId.value;
  const result = await get<Page<Work>>(teacherPath(course) + '?pageNo=' + page.value);
  if (course === courseId.value) work.value = result;
}

async function open(row: Work) {
  const course = courseId.value;
  await task.run(async () => {
    const submissions = await get<PracticeSubmission[]>(teacherPath(course) + '/' + row.userId);
    if (course !== courseId.value || !submissions.length) return;
    const latest = submissions[0]!;
    selected.value = { ...row, latest };
    history.value = submissions;
    feedback.value = '';
  });
}

async function review(status: 'NEEDS_REVISION' | 'PASSED') {
  if (!selected.value) return;
  const course = courseId.value;
  const item = selected.value.latest;
  await task.run(async () => {
    const result = await write<PracticeSubmission>(
      teacherPath(course) + '/' + item.id + '/feedback',
      'PUT',
      { status, feedback: feedback.value },
    );
    const current = selected.value;
    if (current && current.latest.id === item.id) {
      selected.value = { ...current, latest: result };
      history.value = history.value.map((row) => (row.id === result.id ? result : row));
    }
    task.message.value = '反馈已保存。';
    await refreshWork();
  });
}

watch(courseId, async (course) => {
  page.value = 1;
  selected.value = undefined;
  work.value = { list: [], total: 0 };
  title.value = '';
  instructions.value = '';
  publishedAt.value = '';
  if (!course) return;
  if (session.role === 3) await task.run(refreshWork);
  else await configuration();
});

onMounted(async () => {
  if (session.role !== 3) return;
  await task.run(async () => {
    courses.value = await get<Row[]>('/teacher/courses');
  });
});
</script>

<template>
  <section>
    <h1>实操作业</h1>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <template v-if="session.role === 3">
      <el-select v-model="courseId" aria-label="实操课程" placeholder="选择授课课程">
        <el-option v-for="course in courses" :key="course.id" :value="String(course.id)" :label="course.name" />
      </el-select>
      <template v-if="courseId">
        <el-table :data="work.list" empty-text="暂无作品提交">
          <el-table-column prop="name" label="学生" />
          <el-table-column prop="latest.revision" label="提交次数" />
          <el-table-column label="状态">
            <template #default="{ row }">{{ practiceStatus[(row as Work).latest.status] }}</template>
          </el-table-column>
          <el-table-column label="提交时间">
            <template #default="{ row }">{{ String(row.latest.submittedAt).replace('T', ' ') }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button text :disabled="task.busy.value" @click="open(row)">查看作品</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="work.total > 20"
          v-model:current-page="page"
          :page-size="20"
          :total="work.total"
          layout="prev, pager, next"
          @current-change="task.run(refreshWork)"
        />
      </template>
      <el-dialog :model-value="!!selected" title="作品与反馈" @update:model-value="(open: boolean) => { if (!open) selected = undefined; }">
        <PracticeHistory :submissions="history" />
        <div v-if="selected?.latest.status === 'SUBMITTED'">
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
      </el-dialog>
    </template>
    <template v-else>
      <EntityPicker v-model="courseId" kind="courses" label="选择试点课程" :course-statuses="[2]" :disabled="task.busy.value" />
      <el-form v-if="courseId" label-position="top" class="practice-editor" aria-label="配置实操作业">
        <el-form-item label="作业标题">
          <el-input v-model="title" maxlength="120" :disabled="!!publishedAt" />
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="instructions" type="textarea" maxlength="8000" :rows="7" :disabled="!!publishedAt" />
        </el-form-item>
        <p v-if="publishedAt">已发布 · {{ publishedAt.replace('T', ' ') }}</p>
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
