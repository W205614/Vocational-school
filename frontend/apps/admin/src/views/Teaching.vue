<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { useRoute } from 'vue-router';
import { get } from '../../../../packages/shared/src/client';
import { useSession } from '../../../../packages/shared/src/session';
import { useOperations } from '../../../../packages/shared/src/operations';
import { useTask, query, type Row } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import { teacherCourses } from '../teacherCourses';
import { nextTeacherTask, teacherQueuePath, type TeacherQueue } from '../teacherQueue';
const route = useRoute(),
  session = useSession(),
  task = useTask(),
  ops = useOperations(),
  courseId = ref(''),
  studentId = ref(''),
  examPage = ref(1),
  stats = ref<Row>({}),
  learning = ref<Row>({}),
  courses = ref<Row[]>([]),
  studentPage = ref(1),
  questions = ref<Row[]>([]),
  questionTotal = ref(0),
  questionPage = ref(pageInQuery(route.query.questionPage)),
  workCourse = ref(courseInQuery(route.query.workCourse)),
  pendingOnly = ref(route.query.pendingOnly !== 'false'),
  grades = ref<Row[]>([]),
  gradeCourseIds = ref<string[]>([]),
  gradePage = ref(pageInQuery(route.query.gradePage)),
  gradeTotal = ref(0),
  practices = ref<Row[]>([]),
  practicePage = ref(pageInQuery(route.query.practicePage)),
  practiceTotal = ref(0),
  current = ref<Row>(),
  processedQuestion = ref(''),
  nextQuestionMessage = ref(''),
  lastQuestionId = ref(''),
  replies = ref<Row[]>([]),
  reply = ref(''),
  replyPage = ref(1),
  replyTotal = ref(0),
  repliesReady = ref(false);
type Section = 'learning' | 'exams' | 'questions' | 'grading' | 'practice';
const queueNames: Record<TeacherQueue, string> = {
  grading: '待评分答卷', questions: '待答问题', practice: '待反馈实操作业',
};
const queueSummaries = reactive<Record<TeacherQueue, { total: number; oldest?: string }>>({
  grading: { total: 0 }, questions: { total: 0 }, practice: { total: 0 },
});
const workCourses = computed(() => {
  const options = new Map<string, string>(courses.value.map((course) => [String(course.id), course.name]));
  for (const id of gradeCourseIds.value)
    if (!options.has(id)) options.set(id, '课程 ' + id);
  return options;
});
function pageInQuery(value: unknown) {
  return typeof value === 'string' && /^[1-9][0-9]{0,3}$/.test(value) ? Number(value) : 1;
}
function courseInQuery(value: unknown) {
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) ? value : '';
}
const courseName = (id: unknown) => workCourses.value.get(String(id)) || '课程 ' + id;
const pendingQuery = computed(() => ({
  pendingCourseId: workCourse.value || undefined,
  pendingPage: gradePage.value,
  questionPage: questionPage.value,
  practicePage: practicePage.value,
  pendingOnly: String(pendingOnly.value),
  from: 'teaching',
}));
const practiceQuery = computed(() => ({
  workCourse: workCourse.value || undefined,
  gradePage: gradePage.value,
  questionPage: questionPage.value,
  practicePage: practicePage.value,
  pendingOnly: String(pendingOnly.value),
  from: 'teaching',
}));
const displayTime = (time: unknown) => time ? String(time).replace('T', ' ') : '暂无记录';
const reads = reactive({
  learning: { busy: false, error: '' },
  exams: { busy: false, error: '' },
  questions: { busy: false, error: '' },
  grading: { busy: false, error: '' },
  practice: { busy: false, error: '' },
});
const readsBusy = computed(() => reads.learning.busy || reads.exams.busy);
const revisions: Record<Section, number> = {
  learning: 0, exams: 0, questions: 0, grading: 0, practice: 0,
};
function clear(section: Section) {
  if (section === 'learning') learning.value = {};
  else if (section === 'exams') stats.value = {};
  else if (section === 'questions') {
    questions.value = [];
    questionTotal.value = 0;
  } else if (section === 'grading') {
    grades.value = [];
    gradeTotal.value = 0;
  } else {
    practices.value = [];
    practiceTotal.value = 0;
  }
}
function filter() {
  studentPage.value = 1;
  examPage.value = 1;
  void load();
  if (workCourses.value.has(courseId.value)) {
    workCourse.value = courseId.value;
    filterWork();
  }
}
function filterWork() {
  questionPage.value = 1;
  gradePage.value = 1;
  practicePage.value = 1;
  void loadWork();
}
function filterQuestions() {
  questionPage.value = 1;
  void loadSection('questions');
}
async function loadSection(section: Section) {
  const revision = ++revisions[section],
    isLearning = section === 'learning' || section === 'exams',
    course = isLearning ? courseId.value : workCourse.value,
    student = studentId.value;
  clear(section);
  if (!isLearning) queueSummaries[section] = { total: 0 };
  reads[section].error = '';
  reads[section].busy = true;
  if (
    (!course && isLearning) ||
    ((section === 'questions' || section === 'practice') &&
      course && !courses.value.some((row) => String(row.id) === course))
  ) {
    reads[section].busy = false;
    return;
  }
  const path =
    section === 'learning'
      ? '/teacher/learning-hub?' +
        query({ courseId: course, userId: student, pageNo: studentPage.value, pageSize: 20 })
      : section === 'exams'
        ? '/teacher/exam-statistics?' +
          query({ courseId: course, studentId: student, pageNo: examPage.value, pageSize: 20 })
        : teacherQueuePath(section, {
          courseId: course, pageSize: 20,
          pageNo: section === 'questions' ? questionPage.value : section === 'grading' ? gradePage.value : practicePage.value,
          pendingOnly: section === 'questions' ? pendingOnly.value : undefined,
        });
  try {
    const data = await get<Row>(path);
    if (revision !== revisions[section]) return;
    if (!isLearning) {
      queueSummaries[section] = {
        total: Number(section === 'questions' ? data.pendingTotal ?? (pendingOnly.value ? data.total : 0) : data.total),
        oldest: data.oldestPendingAt ?? undefined,
      };
    }
    if (section === 'learning') learning.value = data;
    else if (section === 'exams') stats.value = data;
    else if (section === 'questions') {
      const total = Number(data.total || 0);
      const last = Math.max(1, Math.ceil(total / 20));
      if (questionPage.value > last) {
        questionPage.value = last;
        await loadSection('questions');
        return;
      }
      questions.value = data.list || [];
      questionTotal.value = total;
    } else if (section === 'grading') {
      const total = Number(data.total || 0);
      const last = Math.max(1, Math.ceil(total / 20));
      if (gradePage.value > last) {
        gradePage.value = last;
        await loadSection('grading');
        return;
      }
      grades.value = data.list || [];
      gradeTotal.value = total;
      if (!course) gradeCourseIds.value = (data.courseIds || []).map(String);
      else if (!gradeCourseIds.value.includes(course)) gradeCourseIds.value.push(course);
    } else {
      const total = Number(data.total);
      const last = Math.max(1, Math.ceil(total / 20));
      if (practicePage.value > last) {
        practicePage.value = last;
        await loadSection('practice');
        return;
      }
      practices.value = data.list;
      practiceTotal.value = total;
    }
  } catch (error) {
    if (revision === revisions[section])
      reads[section].error = error instanceof Error ? error.message : '请求失败';
  } finally {
    if (revision === revisions[section]) reads[section].busy = false;
  }
}
async function load() {
  await Promise.allSettled([loadSection('learning'), loadSection('exams')]);
}
async function loadWork() {
  await Promise.allSettled([
    loadSection('questions'),
    loadSection('grading'),
    ...(session.role === 3 ? [loadSection('practice')] : []),
  ]);
}
onBeforeUnmount(() => {
  for (const name of Object.keys(revisions) as Section[]) revisions[name]++;
});
async function answers(row: Row) {
  if (task.busy.value) return;
  if (current.value?.id !== row.id) {
    replyPage.value = 1;
    reply.value = '';
    nextQuestionMessage.value = '';
  }
  current.value = row;
  lastQuestionId.value = String(row.id);
  replies.value = [];
  replyTotal.value = 0;
  repliesReady.value = false;
  await task.run(async () => {
    const r = await get<Row>(
      '/services/learning/replies/page?questionId=' +
        row.id +
        '&pageNo=' +
        replyPage.value +
        '&pageSize=20',
    );
    if (current.value?.id === row.id) {
      replies.value = r.list || [];
      replyTotal.value = Number(r.total || 0);
      repliesReady.value = true;
    }
  });
}
async function nextQuestion() {
  const result = await task.run(async () => ({ next: await nextTeacherTask('questions', workCourse.value) }));
  if (!result) return;
  if (!result.next) {
    nextQuestionMessage.value = '当前范围没有其他待答问题';
    return;
  }
  await answers(result.next);
}
function returnToQuestionQueue() {
  const control = document.getElementById('teacher-question-' + lastQuestionId.value);
  (control || document.getElementById('teacher-questions-heading'))?.focus();
}
async function respond() {
  if (!current.value || !repliesReady.value || !reply.value.trim()) return;
  const ok = await task.run(async () => {
    const op = await ops.begin('learning', '教师答疑', '/services/learning/replies', {
      questionId: current.value!.id,
      content: reply.value,
      anonymity: false,
    });
    return op.status === 'SUCCEEDED';
  });
  if (ok && current.value) {
    processedQuestion.value = String(current.value.id);
    reply.value = '';
    await Promise.all([answers(current.value), loadSection('questions')]);
  }
}
async function accept(row: Row) {
  const ok = await task.run(async () => {
    const op = await ops.begin(
      'learning',
      '采纳学生回答',
      '/learning-hub/questions/' + current.value!.id + '/accept',
      { replyId: row.id },
    );
    return op.status === 'SUCCEEDED';
  });
  if (ok && current.value) {
    processedQuestion.value = String(current.value.id);
    current.value = { ...current.value, acceptedReplyId: row.id };
    await Promise.all([load(), loadSection('questions')]);
    task.message.value = '回答已采纳，积分按规则到账';
  }
}
onMounted(async () => {
  await task.run(async () => {
    const all = await teacherCourses();
    courses.value = all;
    courseId.value = String(all[0]?.id || '');
  });
  await Promise.allSettled([load(), loadWork()]);
  if (['#teacher-tasks-heading', '#teacher-practice-heading'].includes(route.hash)) {
    await nextTick();
    document.getElementById(route.hash.slice(1))?.focus();
  }
});
</script>
<template>
  <section class="card teaching-page">
    <h1>教学学情与答疑</h1>
    <p>{{ session.role === 3
      ? '处理答疑、阅卷和待反馈作品，查看所授课程的学习进度。'
      : '处理待答问题和待评分答卷，查看课程学习进度。' }}</p>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <section aria-labelledby="teacher-tasks-heading">
      <div class="section-heading">
        <h2 id="teacher-tasks-heading" tabindex="-1">教学待办</h2>
        <RouterLink :to="{ path: '/exams', query: pendingQuery }">进入阅卷 →</RouterLink>
      </div>
      <div class="toolbar">
        <el-select
          v-model="workCourse"
          clearable
          filterable
          aria-label="筛选待办课程"
          placeholder="全部可处理课程"
          @change="filterWork"
        >
          <el-option v-for="[id, name] in workCourses" :key="id" :value="id" :label="name" />
        </el-select>
      </div>
      <dl v-if="session.role === 3" aria-label="待办概览" class="teacher-summary queue-summary">
        <div v-for="(label, queue) in queueNames" :key="queue">
          <dt>{{ label }}</dt>
          <dd v-if="reads[queue].busy">加载中</dd>
          <dd v-else-if="reads[queue].error">暂时无法获取</dd>
          <dd v-else>
            <strong>{{ queueSummaries[queue].total }} 项</strong>
            <small v-if="queueSummaries[queue].oldest">最早等待自 {{ displayTime(queueSummaries[queue].oldest) }}</small>
          </dd>
        </div>
      </dl>
      <h3>待评分答卷</h3>
      <Status :busy="reads.grading.busy" :error="reads.grading.error" />
      <el-button v-if="reads.grading.error" @click="loadSection('grading')">重试待评分列表</el-button>
      <article v-for="row in grades" :key="row.id" class="review-row">
        <div>
          <h4>{{ courseName(row.course_id) }}</h4>
          <p>提交于 {{ displayTime(row.submitted_at) }} · 学生 {{ row.user_id }}</p>
        </div>
        <RouterLink
          :to="{ path: '/exams', query: { ...pendingQuery, pendingAttemptId: row.id } }"
        >查看待评分答卷 →</RouterLink>
      </article>
      <el-empty
        v-if="!grades.length && !reads.grading.busy && !reads.grading.error"
        description="当前范围没有待评分答卷"
      />
      <el-pagination
        v-if="gradeTotal > 20"
        v-model:current-page="gradePage"
        :total="gradeTotal"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="loadSection('grading')"
      />
      <section v-if="session.role === 3" aria-labelledby="teacher-practice-heading">
        <h3 id="teacher-practice-heading" tabindex="-1">待反馈实操作业</h3>
        <Status :busy="reads.practice.busy" :error="reads.practice.error" />
        <el-button v-if="reads.practice.error" @click="loadSection('practice')">
          重试待反馈作品
        </el-button>
        <article v-for="row in practices" :key="row.submissionId" class="review-row">
          <div>
            <h4>{{ row.assignmentTitle }}</h4>
            <p>{{ courseName(row.courseId) }} · {{ row.studentName }} · 提交于 {{ displayTime(row.submittedAt) }}</p>
          </div>
          <RouterLink :to="{ path: '/practice', query: { ...practiceQuery,
            courseId: row.courseId, studentId: row.studentId, submissionId: row.submissionId } }">
            查看待反馈作品 →
          </RouterLink>
        </article>
        <el-empty
          v-if="!practices.length && !reads.practice.busy && !reads.practice.error"
          description="当前范围没有待反馈实操作业"
        />
        <el-pagination
          v-if="practiceTotal > 20"
          v-model:current-page="practicePage"
          :total="practiceTotal"
          :page-size="20"
          layout="prev,pager,next"
          @current-change="loadSection('practice')"
        />
      </section>
    <section aria-label="课程答疑">
      <Status :busy="reads.questions.busy" :error="reads.questions.error" /><el-button
        v-if="reads.questions.error"
        @click="loadSection('questions')"
        :disabled="reads.questions.busy"
        >重试课程答疑</el-button
      >
      <div class="section-heading">
        <h3 id="teacher-questions-heading" tabindex="-1">课程答疑</h3>
        <label class="question-filter">
          <input v-model="pendingOnly" type="checkbox" @change="filterQuestions" />
          只看未答问题
        </label>
      </div>
      <article v-for="row in questions" :key="row.id" class="review-row">
        <div>
          <h3>{{ row.title }}</h3>
          <p>
            {{ courseName(row.courseId) }} · {{ displayTime(row.createTime) }} ·
            {{ row.answerTimes || 0 }} 个回答<span v-if="row.acceptedReplyId"> · 已采纳</span>
          </p>
        </div>
        <el-button :id="'teacher-question-' + row.id" @click="answers(row)">查看与答疑</el-button>
      </article>
      <el-empty
        v-if="!questions.length && !reads.questions.busy && !reads.questions.error"
        :description="pendingOnly ? '当前范围没有待答问题' : '当前范围暂无问题'"
      /><el-pagination
        v-if="questionTotal > 20"
        v-model:current-page="questionPage"
        :total="questionTotal"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="loadSection('questions')"
      />
    </section>
    </section>
    <div class="toolbar">
      <el-select
        v-model="courseId"
        filterable
        aria-label="教学课程"
        placeholder="选择所授课程"
        @change="filter"
        :disabled="task.busy.value"
        ><el-option
          v-for="course in courses"
          :key="course.id"
          :value="String(course.id)"
          :label="course.name" /></el-select
      ><el-input
        v-model="studentId"
        aria-label="筛选学生编号"
        placeholder="学生编号（可选）"
      /><el-button
        type="primary"
        @click="filter"
        :disabled="!courseId.trim() || task.busy.value || readsBusy"
        >查看课程学情</el-button
      >
    </div>
    <p class="muted">授课范围与试卷评分范围分别验证；无权限的课程不能查看。</p>
    <section aria-label="课程学情">
      <Status :busy="reads.learning.busy" :error="reads.learning.error" /><el-button
        v-if="reads.learning.error"
        @click="loadSection('learning')"
        :disabled="reads.learning.busy"
        >重试课程学情</el-button
      ><template v-if="learning.summary"
        ><h2>{{ learning.courseName }}</h2>
        <p class="muted">
          时长为服务端记录的播放会话累计，历史完成记录可能没有计时数据，不作为实际观看或学时认证证明。
        </p>
        <div class="teacher-summary">
          <p>
            学习人数<strong>{{ learning.summary.students }}</strong>
          </p>
          <p>
            已完成课程<strong>{{ learning.summary.completedStudents }}</strong>
          </p>
          <p>
            课程完成率<strong>{{ learning.summary.completionRate }}%</strong>
          </p>
          <p>
            待回答问题<strong>{{ learning.summary.unansweredQuestions }}</strong>
          </p>
        </div>
        <el-table :data="learning.list || []" empty-text="暂无学习记录"
          ><el-table-column prop="name" label="学生" /><el-table-column label="学习进度"
            ><template #default="{ row }"
              >{{ row.learnedSections }} / {{ row.sections }} 小节<el-progress
                :percentage="Number(row.progressPercent || 0)" /></template></el-table-column
          ><el-table-column label="折算学习时长" min-width="150"
            ><template #default="{ row }"
              >{{ Math.floor(Number(row.creditedMillis || 0) / 60000) }} 分
              {{ Math.floor(Number(row.creditedMillis || 0) / 1000) % 60 }} 秒</template
            ></el-table-column
          ><el-table-column label="最近学习" min-width="180">
            <template #default="{ row }">{{ displayTime(row.latestLearnTime) }}</template>
          </el-table-column></el-table
        ><el-pagination
          v-if="learning.total > 20"
          v-model:current-page="studentPage"
          :total="Number(learning.total)"
          :page-size="20"
          layout="prev,pager,next"
          @current-change="loadSection('learning')"
        />
        <h3 v-if="learning.chapters?.length">章节完成情况</h3>
        <el-table v-if="learning.chapters?.length" :data="learning.chapters">
          <el-table-column prop="name" label="章节" />
          <el-table-column prop="finished" label="已完成小节次数" />
          <el-table-column prop="remaining" label="待完成小节次数" />
        </el-table>
      </template>
    </section>
    <section aria-label="考试统计">
      <Status :busy="reads.exams.busy" :error="reads.exams.error" /><el-button
        v-if="reads.exams.error"
        @click="loadSection('exams')"
        :disabled="reads.exams.busy"
        >重试考试统计</el-button
      ><template v-if="courseId && Number(stats.total || 0) > 0"
        ><h2>考试表现</h2>
        <el-table :data="stats.list || []" empty-text="当前评分范围暂无记录"
          ><el-table-column prop="course_id" label="课程编号" /><el-table-column
            prop="student_id"
            label="学生编号" /><el-table-column prop="finished" label="已评分" /><el-table-column
            prop="passed"
            label="已通过" /><el-table-column prop="pending_grading" label="待评分" /></el-table
        ><el-pagination
          v-if="stats.total > 20"
          v-model:current-page="examPage"
          :total="Number(stats.total)"
          :page-size="20"
          layout="prev,pager,next"
          @current-change="loadSection('exams')" /></template
      ><el-empty
        v-if="courseId && !reads.exams.busy && !reads.exams.error && !Number(stats.total || 0)"
        description="当前评分范围暂无记录"
      />
      <h3 v-if="stats.commonMistakes?.length">高频失分题</h3>
      <el-table v-if="stats.commonMistakes?.length" :data="stats.commonMistakes">
        <el-table-column prop="name" label="题目" />
        <el-table-column prop="students" label="失分学生人数" />
      </el-table>
    </section>
    <el-dialog
      :model-value="!!current"
      title="课程答疑"
      @closed="returnToQuestionQueue"
      @update:model-value="
        (v: boolean) => {
          if (!v) current = undefined;
        }
      "
      ><template v-if="current"
        ><h3>{{ current.title }}</h3>
        <p class="question-description">{{ current.description }}</p>
        <el-button v-if="!repliesReady" @click="answers(current)" :disabled="task.busy.value"
          >重新加载答疑</el-button
        >
        <article v-for="item in replies" :key="item.id" class="review-row">
          <p>{{ item.content }}</p>
          <el-button
            v-if="!current.acceptedReplyId"
            @click="accept(item)"
            :disabled="task.busy.value"
            >采纳回答</el-button
          ><span v-else-if="String(current.acceptedReplyId) === String(item.id)">已采纳</span>
        </article>
        <el-pagination
          v-if="replyTotal > 20"
          v-model:current-page="replyPage"
          :total="replyTotal"
          :page-size="20"
          layout="prev,pager,next"
          @current-change="answers(current!)"
        /><el-input
          v-model="reply"
          type="textarea"
          :rows="5"
          placeholder="写下教师解答"
        /><el-button
          type="primary"
          @click="respond"
          :disabled="!reply.trim() || !repliesReady || task.busy.value"
          >发布解答</el-button
        >
        <el-button v-if="processedQuestion === String(current.id)" @click="nextQuestion" :disabled="task.busy.value">
          处理下一条待答问题
        </el-button>
        </template
      ><Status :busy="task.busy.value" :error="task.error.value" :message="nextQuestionMessage"
    /></el-dialog>
  </section>
</template>

<style scoped>
.question-description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.queue-summary { margin-block: 1rem; }
.queue-summary dd { margin: .25rem 0 0; }
.queue-summary small { display: block; }
</style>
