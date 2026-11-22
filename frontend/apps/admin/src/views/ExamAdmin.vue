<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useSession } from '../../../../packages/shared/src/session';
import { get } from '../../../../packages/shared/src/client';
import { useOperations } from '../../../../packages/shared/src/operations';
import { useTask, examStatus, type Row } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import EntityPicker from '../components/EntityPicker.vue';
const route = useRoute(),
  session = useSession(),
  task = useTask(),
  ops = useOperations(),
  course = ref(''),
  sections = ref<Row[]>([]),
  lectureSections = ref<Row[]>([]),
  explanationSectionIds = ref<Record<string, string>>({}),
  explanationMoments = ref<Record<string, number | undefined>>({}),
  section = ref(''),
  ids = ref<string[]>([]),
  graders = ref<string[]>([]),
  questionRows = ref<Row[]>([]),
  percent = ref(60),
  pending = ref<Row[]>([]),
  attempt = ref<Row>(),
  scores = ref<Record<string, number>>({}),
  feedback = ref<Record<string, string>>({});
const page = ref(1),
  total = ref(0),
  filterCourse = ref(''),
  filterStudent = ref('');
function validId(value: unknown) {
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) ? value : '';
}
function validPage(value: unknown) {
  return typeof value === 'string' && /^[1-9][0-9]{0,3}$/.test(value) ? Number(value) : 1;
}
const returnToWork = computed(() => ({
  path: '/teaching',
  hash: '#teacher-tasks-heading',
  query: {
    workCourse: validId(route.query.pendingCourseId) || undefined,
    questionPage: validPage(route.query.questionPage),
    gradePage: validPage(route.query.pendingPage),
    pendingOnly: route.query.pendingOnly === 'false' ? 'false' : 'true',
  },
}));
const totalScore = computed(() =>
    questionRows.value.reduce((sum, row) => sum + Number(row.score || 0), 0),
  ),
  subjective = computed(() => questionRows.value.some((row) => Number(row.type) === 5));
async function selectCourse(selected: Row[] = [], preselect = '') {
  section.value = '';
  sections.value = [];
  lectureSections.value = [];
  explanationSectionIds.value = {};
  explanationMoments.value = {};
  if (!course.value) return;
  await task.run(async () => {
    const status = Number(selected[0]?.status || (preselect ? 1 : 2));
    const chapters = await get<Row[]>(
      '/admin/course/courses/catas/' + course.value +
        '?see=' + ([1, 3].includes(status) ? 'false' : 'true') + '&withPractice=true',
    );
    const catalog = chapters.flatMap((chapter) =>
      (chapter.sections || []).map((s: Row) => ({ ...s, label: chapter.name + ' / ' + s.name })),
    ) as Row[];
    sections.value = catalog.filter((s) => Number(s.type) === 3);
    lectureSections.value = catalog.filter((s) => Number(s.type) === 2);
    section.value = sections.value.some((s) => String(s.id) === preselect) ? preselect : '';
  });
}
function explanationDuration(questionId: string) {
  const id = explanationSectionIds.value[questionId];
  return Number(lectureSections.value.find((row) => String(row.id) === id)?.mediaDuration || 0);
}
async function publish() {
  await task.run(async () => {
    const explanations = Object.fromEntries(
      ids.value
        .filter((id) => explanationSectionIds.value[id])
        .map((id) => [
          id,
          { sectionId: explanationSectionIds.value[id], moment: explanationMoments.value[id] },
        ]),
    );
    const result = await ops.begin('exam', '发布新试卷版本', '/admin/exam-papers', {
      courseId: course.value,
      sectionId: section.value,
      questionIds: ids.value,
      passPercent: percent.value,
      graders: graders.value,
      explanations,
    });
    task.message.value = result.status === 'SUCCEEDED' ? '试卷版本已发布' : '试卷发布处理中';
  });
}
async function loadPending() {
  const params = new URLSearchParams({ pageNo: String(page.value), pageSize: '20' });
  if (filterCourse.value) params.set('courseId', filterCourse.value);
  if (filterStudent.value) params.set('studentId', filterStudent.value);
  let result = await get<Row>('/teacher/exam-attempts/page?' + params);
  const lastPage = Math.max(1, Math.ceil(Number(result.total) / 20));
  if (page.value > lastPage) {
    page.value = lastPage;
    params.set('pageNo', String(page.value));
    result = await get<Row>('/teacher/exam-attempts/page?' + params);
  }
  pending.value = result.list;
  total.value = Number(result.total);
}
async function load() {
  await task.run(loadPending);
}
async function view(id: string) {
  await task.run(async () => {
    attempt.value = await get<Row>('/teacher/exam-attempts/' + id);
    scores.value = {};
    feedback.value = {};
    await nextTick();
    document.getElementById('grading-attempt-heading')?.focus();
  });
}
async function returnToGradingQueue() {
  const id = attempt.value?.id;
  attempt.value = undefined;
  await nextTick();
  const control = document.getElementById('pending-exam-' + id);
  (control || document.getElementById('grading-queue-heading'))?.focus();
}
function validScore(answer: Row) {
  const score = scores.value[answer.question_id],
    max = attempt.value?.questions.find((q: Row) => q.question_id === answer.question_id)?.score;
  return typeof score === 'number' && Number.isInteger(score) && score >= 0 && score <= Number(max);
}
async function grade(answer: Row) {
  if (!validScore(answer)) return;
  await task.run(async () => {
    if (!attempt.value) return;
    const result = await ops.begin(
      'exam',
      '教师评分',
      '/teacher/exam-attempts/' + attempt.value.id + '/grades',
      {
        questionId: answer.question_id,
        score: scores.value[answer.question_id],
        version: answer.version,
        feedback: feedback.value[answer.question_id] || '',
      },
    );
    if (result.status === 'SUCCEEDED') {
      attempt.value = await get<Row>('/teacher/exam-attempts/' + attempt.value.id);
      await loadPending();
    }
  });
}
async function openRoute() {
  const draftCourse = validId(route.query.courseId);
  const draftSection = validId(route.query.sectionId);
  if (session.role === 1 && draftCourse && draftSection) {
    course.value = draftCourse;
    await selectCourse([], draftSection);
  }
  filterCourse.value = validId(route.query.pendingCourseId);
  page.value = validPage(route.query.pendingPage);
  await load();
  const pendingId = validId(route.query.pendingAttemptId);
  if (pendingId) await view(pendingId);
}
onMounted(openRoute);
watch(() => route.query, () => void openRoute());
</script>
<template>
  <section class="card">
    <h2>试卷版本与教师评分</h2>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <el-form v-if="session.role === 1" label-position="top" class="exam-publish-form">
      <p class="muted">
        选择课程与考试小节，从题库组卷。发布后生成独立快照，题库后续修改不会影响已经发布的试卷。
      </p>
      <div class="grid">
        <el-form-item label="考试课程"
          ><EntityPicker
            v-model="course"
            kind="courses"
            label="选择考试课程"
            :course-statuses="[1, 2, 3, 4]"
            :disabled="task.busy.value"
            @change="selectCourse"
        /></el-form-item>
        <el-form-item label="考试小节"
          ><el-select
            v-model="section"
            aria-label="选择考试小节"
            :disabled="!course || task.busy.value"
            placeholder="先选择课程，再选择考试小节"
            ><el-option
              v-for="item in sections"
              :key="item.id"
              :value="String(item.id)"
              :label="item.label" /></el-select
          ><small v-if="course && !sections.length && !task.busy.value" class="muted"
            >该课程没有考试小节，请先配置课程目录。</small
          ></el-form-item
        >
        <el-form-item label="试卷题目"
          ><EntityPicker
            v-model="ids"
            kind="questions"
            label="选择试卷题目"
            multiple
            :disabled="task.busy.value"
            @change="questionRows = $event"
        /></el-form-item>
        <el-form-item label="评分教师"
          ><EntityPicker
            v-model="graders"
            kind="teachers"
            label="选择评分教师"
            multiple
            :limit="20"
            :disabled="task.busy.value"
          /><small class="muted">{{
            subjective ? '包含主观题，请指定评分教师' : '纯客观题会自动评分'
          }}</small></el-form-item
        >
        <el-form-item label="及格百分比"
          ><el-input-number v-model="percent" aria-label="及格百分比" :min="1" :max="100"
        /></el-form-item>
      </div>
      <p v-if="ids.length" class="selection-summary">
        共 {{ ids.length }} 题 · 总分 {{ totalScore }} ·
        {{ Math.ceil((totalScore * percent) / 100) }} 分及格
      </p>
      <details v-if="questionRows.length && lectureSections.length" class="lecture-settings">
        <summary>为错题关联讲解视频（可选）</summary>
        <fieldset v-for="question in questionRows" :key="question.id" class="lecture-setting">
          <legend>{{ question.name }}</legend>
          <el-select
            v-model="explanationSectionIds[String(question.id)]"
            clearable
            filterable
            :aria-label="'讲解视频：' + question.name"
            placeholder="选择本课程视频小节"
            @change="explanationMoments[String(question.id)] = undefined"
          >
            <el-option
              v-for="video in lectureSections"
              :key="video.id"
              :label="video.label"
              :value="String(video.id)"
            />
          </el-select>
          <el-input-number
            v-if="explanationDuration(String(question.id)) > 0"
            v-model="explanationMoments[String(question.id)]"
            :aria-label="'讲解时间（秒）：' + question.name"
            placeholder="讲解时间（可选）"
            :min="0"
            :max="explanationDuration(String(question.id)) - 1"
            :precision="0"
            :controls="false"
          />
        </fieldset>
      </details>
      <el-button
        type="primary"
        @click="publish"
        :disabled="
          !course || !section || !ids.length || (subjective && !graders.length) || task.busy.value
        "
        >发布不可变试卷版本</el-button
      ></el-form
    >
    <el-divider />
    <div class="section-heading">
      <h3 id="grading-queue-heading" tabindex="-1">待评分考试</h3>
      <RouterLink :to="returnToWork">返回教学待办 →</RouterLink>
    </div>
    <div class="toolbar">
    <el-input v-model="filterCourse" aria-label="待评分课程编号" placeholder="课程编号筛选" /><el-input
      v-model="filterStudent"
      aria-label="待评分学生编号"
      placeholder="学生编号筛选"
    /><el-button
      @click="
        page = 1;
        load();
      "
      :disabled="task.busy.value"
      >刷新待评分列表</el-button
    ></div><el-table :data="pending" empty-text="当前范围没有待评分答卷"
      ><el-table-column prop="id" label="考试记录" /><el-table-column
        prop="user_id"
        label="学生编号"
      /><el-table-column prop="course_id" label="课程编号" />
      <el-table-column label="提交时间" min-width="175">
        <template #default="{ row }">{{ row.submitted_at ? String(row.submitted_at).replace('T', ' ') : '暂无记录' }}</template>
      </el-table-column><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button :id="'pending-exam-' + row.id" @click="view(row.id)">查看答卷</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load"
    />
    <article v-if="attempt">
      <div class="section-heading">
        <h3 id="grading-attempt-heading" tabindex="-1">考试 {{ attempt.id }} · {{ examStatus(attempt.status) }}</h3>
        <el-button @click="returnToGradingQueue">回到待评分列表</el-button>
      </div>
      <div v-for="answer in attempt.answers" :key="answer.question_id" class="section">
        <h4>
          {{ attempt.questions.find((q: Row) => q.question_id === answer.question_id)?.name }}
        </h4>
        <p class="content">{{ answer.response }}</p>
        <p v-if="answer.score !== null">已评分 {{ answer.score }} · {{ answer.feedback }}</p>
        <template v-else
          ><el-input-number
            v-model="scores[answer.question_id]"
            :min="0"
            :max="
              attempt.questions.find((q: Row) => q.question_id === answer.question_id)?.score || 0
            "
          /><el-input v-model="feedback[answer.question_id]" placeholder="评分反馈" /><el-button
            @click="grade(answer)"
            :disabled="task.busy.value || !validScore(answer)"
            >提交评分</el-button
          ></template
        >
      </div>
    </article>
  </section>
</template>

<style scoped>
.lecture-settings { margin-block: 1rem; }
.lecture-setting { margin-block: 0.75rem; padding: 0.75rem; border: 1px solid var(--el-border-color); }
.lecture-setting legend { overflow-wrap: anywhere; }
.lecture-setting .el-select { width: min(100%, 25rem); margin: 0.25rem; }
.lecture-setting .el-input-number { width: min(100%, 12rem); margin: 0.25rem; }
</style>
