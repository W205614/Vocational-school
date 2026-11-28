<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, write, newKey } from '../../../../packages/shared/src/client';
import { useOperations } from '../../../../packages/shared/src/operations';
import { examStatus, useTask, type Row } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import {
  classTime, activityName, memberStateName, gradeStateName,
  type Classroom, type Membership, type Activity, type Version,
  type Discussion, type GradeItem, type GradeReport, type Submission,
} from '../../../../packages/shared/src/classroom';

interface Enrollment { classroom: Classroom; member: Membership }
interface Study extends Enrollment { activities: Activity[] }
interface Attempt { id: string; memberId: string; status: string }

const api = '/learning-hub/classrooms';
const route = useRoute();
const router = useRouter();
const task = useTask();
const ops = useOperations();
const enrollments = ref<Enrollment[]>([]);
const study = ref<Study>();
const grades = ref<GradeReport>();
const discussion = ref<Discussion[]>([]);
const older = ref(false);
const selected = ref<Activity>();
const submissions = ref<Submission[]>([]);
const versions = ref<Version[]>([]);
const attempts = ref<Attempt[]>([]);
const description = ref('');
const workUrl = ref('');
const title = ref('');
const body = ref('');
const replyTo = ref<string>();
const questionSection = ref('');
const requestKey = ref(newKey());
const historic = ref<GradeItem>();
const active = computed(() => study.value?.member.state === 'ACTIVE');
const selectedClass = computed(() => enrollments.value.find((item) =>
  String(item.classroom.id) === route.query.classId
  && (route.query.memberId === undefined || String(item.member.id) === route.query.memberId)));

async function load() {
  study.value = undefined;
  grades.value = undefined;
  historic.value = undefined;
  selected.value = undefined;
  discussion.value = [];
  enrollments.value = await get<Enrollment[]>(api);
  const item = selectedClass.value;
  if (!item) return;
  const path = api + '/' + item.classroom.id;
  if (!['HELD', 'RELEASED'].includes(item.member.state)) {
    grades.value = await get<GradeReport>(path + '/members/' + item.member.id + '/grades');
  }
  if (item.member.state === 'ACTIVE' || item.member.state === 'COMPLETED') {
    study.value = await get<Study>(path);
    discussion.value = await get<Discussion[]>(path + '/discussion');
    older.value = discussion.value.length === 100;
  }
}

watch(() => route.query, () => void task.run(load), { immediate: true });

async function open(activity: Activity) {
  if (!study.value) return;
  await task.run(async () => {
    const path = api + '/' + study.value!.classroom.id + '/activities/' + activity.id;
    versions.value = await get<Version[]>(path + '/versions');
    submissions.value = activity.kind === 'ASSIGNMENT'
      ? await get<Submission[]>(path + '/submissions/' + study.value!.member.id) : [];
    attempts.value = activity.kind === 'EXAM'
      ? await get<Attempt[]>('/classroom-exams/' + activity.id + '/attempts') : [];
    description.value = '';
    workUrl.value = '';
    requestKey.value = newKey();
    selected.value = activity;
  });
}

async function submit() {
  const activity = selected.value;
  if (!study.value || !activity) return;
  const path = api + '/' + study.value.classroom.id + '/activities/' + activity.id + '/submissions';
  await task.run(async () => {
    const result = await write<Submission>(path, 'POST', {
      requestKey: requestKey.value, assignmentVersion: activity.currentVersion,
      description: description.value, workUrl: workUrl.value || undefined,
    });
    submissions.value = [result, ...submissions.value.filter((submission) => submission.id !== result.id)];
    description.value = '';
    workUrl.value = '';
    requestKey.value = newKey();
    task.message.value = '作品已提交';
  });
}

async function startExam() {
  if (!study.value || !selected.value) return;
  await task.run(async () => {
    const result = await ops.begin('exam', '开始班级考试', '/classroom-exams/' + selected.value!.id + '/attempts', undefined);
    if (result.status === 'SUCCEEDED') {
      const attempt = result.result as Row;
      await router.push({ path: '/exams', query: {
        attemptId: String(attempt.id), classroom: String(study.value!.classroom.id),
        memberId: String(study.value!.member.id),
      } });
    } else task.message.value = '考试正在创建，请在操作记录中查看';
  });
}

async function openHistory(item: GradeItem) {
  const current = selectedClass.value;
  if (!current) return;
  await task.run(async () => {
    const path = api + '/' + current.classroom.id + '/activities/' + item.activityId;
    submissions.value = item.kind === 'ASSIGNMENT'
      ? await get<Submission[]>(path + '/submissions/' + current.member.id) : [];
    attempts.value = item.kind === 'EXAM'
      ? (await get<Attempt[]>('/classroom-exams/' + item.activityId + '/attempts'))
          .filter((attempt) => String(attempt.memberId) === String(current.member.id)) : [];
    historic.value = item;
  });
}

async function post() {
  if (!study.value) return;
  await task.run(async () => {
    const result = await write<Discussion>(api + '/' + study.value!.classroom.id + '/discussion', 'POST', {
      parentId: replyTo.value,
      title: replyTo.value ? undefined : title.value,
      sectionId: replyTo.value ? undefined : questionSection.value || undefined,
      body: body.value,
    });
    discussion.value = [result, ...discussion.value];
    title.value = '';
    body.value = '';
    replyTo.value = undefined;
    questionSection.value = '';
  });
}

async function more() {
  const last = discussion.value.at(-1);
  if (!study.value || !last) return;
  await task.run(async () => {
    const items = await get<Discussion[]>(api + '/' + study.value!.classroom.id + '/discussion?before=' + last.id);
    discussion.value.push(...items);
    older.value = items.length === 100;
  });
}
</script>

<template>
  <main class="card">
    <div class="section-heading"><h2>我的班级</h2><RouterLink to="/">找课程</RouterLink></div>
    <Status :error="task.error.value" :busy="task.busy.value" :message="task.message.value" />
    <p v-if="!enrollments.length && !task.busy.value">从课程详情选择开课班级，报名后在这里查看。</p>
    <div v-for="item in enrollments" :key="item.member.id" class="toolbar">
      <RouterLink :to="{ path: '/classrooms', query: { classId: item.classroom.id, memberId: item.member.id } }">
        {{ item.classroom.name }}
      </RouterLink>
      <span>{{ memberStateName[item.member.state] }}</span>
      <span class="muted">{{ classTime(item.classroom.startsAt) }} 至 {{ classTime(item.classroom.endsAt) }}</span>
      <RouterLink v-if="item.member.state === 'HELD' && item.member.orderId"
        :to="{ path: '/orders', query: { pay: item.member.orderId } }">查看付款</RouterLink>
    </div>

    <template v-if="study">
      <h3>{{ study.classroom.name }} · 教学安排</h3>
      <p v-if="!study.activities.length">暂未发布教学安排。</p>
      <div v-for="activity in study.activities" :key="activity.id" class="section">
        <div class="toolbar">
          <el-button @click="open(activity)">{{ activityName[activity.kind] }} · {{ activity.title }}</el-button>
          <span>{{ classTime(activity.scheduledAt) }}<template v-if="activity.closesAt"> 至 {{ classTime(activity.closesAt) }}</template></span>
          <RouterLink v-if="activity.kind === 'LESSON' && activity.sectionId"
            :to="{ path: '/courses/' + study.classroom.courseId, query: { sectionId: activity.sectionId } }">打开课节</RouterLink>
        </div>
      </div>
      <section v-if="selected" class="section">
        <h4>{{ selected.title }}</h4>
        <p style="white-space: pre-wrap">{{ selected.instructions }}</p>
        <details v-if="versions.length > 1">
          <summary>要求与更正记录</summary>
          <article v-for="version in versions" :key="version.version">
            <h5>第 {{ version.version }} 版 · {{ version.title }}</h5>
            <p>{{ version.reason }}</p><p style="white-space: pre-wrap">{{ version.instructions }}</p>
          </article>
        </details>
        <template v-if="selected.kind === 'ASSIGNMENT'">
          <div v-for="work in submissions" :key="work.id" class="section">
            <p>第 {{ work.revision }} 次提交 · 作业第 {{ work.version }} 版</p>
            <p>{{ work.description }}</p><p v-if="work.feedback">{{ work.feedback }}</p>
          </div>
          <el-form v-if="active && (!submissions.length || submissions[0]?.state === 'NEEDS_REVISION')" label-position="top" @submit.prevent="submit">
            <el-form-item label="作品说明"><el-input v-model="description" type="textarea" maxlength="5000" /></el-form-item>
            <el-form-item label="作品链接（选填）"><el-input v-model="workUrl" type="url" maxlength="2048" /></el-form-item>
            <el-button type="primary" native-type="submit" :loading="task.busy.value" :disabled="!description.trim()">提交作业</el-button>
          </el-form>
        </template>
        <template v-if="selected.kind === 'EXAM'">
          <div v-for="attempt in attempts" :key="attempt.id" class="toolbar">
            <RouterLink :to="{ path: '/exams', query: { attemptId: attempt.id, classroom: study.classroom.id, memberId: attempt.memberId } }">
              {{ examStatus(attempt.status) }} · 查看答卷
            </RouterLink>
          </div>
          <el-button v-if="active" type="primary" :loading="task.busy.value" @click="startExam">进入考试</el-button>
        </template>
      </section>
      <section class="section">
        <h3>班级答疑</h3>
        <el-form v-if="active" label-position="top" @submit.prevent="post">
          <el-form-item v-if="!replyTo" label="问题标题"><el-input v-model="title" maxlength="120" /></el-form-item>
          <el-form-item v-if="!replyTo" label="关联课节（选填）">
            <el-select v-model="questionSection" clearable>
              <el-option v-for="activity in study.activities.filter(item => item.kind === 'LESSON' && item.sectionId)"
                :key="activity.id" :label="activity.title" :value="activity.sectionId!" />
            </el-select>
          </el-form-item>
          <el-form-item :label="replyTo ? '回复内容' : '问题说明'"><el-input v-model="body" type="textarea" maxlength="8000" /></el-form-item>
          <el-button type="primary" native-type="submit" :disabled="!body.trim()" :loading="task.busy.value">{{ replyTo ? '发送回复' : '提问' }}</el-button>
          <el-button v-if="replyTo" @click="replyTo = undefined">取消回复</el-button>
        </el-form>
        <article v-for="item in discussion.filter(post => !post.parentId)" :key="item.id" class="section">
          <h4>{{ item.title }}</h4><p>{{ item.body }}</p><span class="muted">{{ classTime(item.postedAt) }}</span>
          <el-button v-if="active" text @click="replyTo = item.id">回复</el-button>
          <p v-for="response in discussion.filter(post => String(post.parentId) === String(item.id)).reverse()" :key="response.id">
            {{ response.staffReply ? '教学人员' : '学员' }} · {{ response.body }}
          </p>
        </article>
        <el-button v-if="older" @click="more">较早的答疑</el-button>
      </section>
    </template>

    <section v-if="grades" class="section">
      <h3>我的班级成绩</h3>
      <p v-if="grades.result">总评 {{ grades.result.totalPercent }} 分 · {{ grades.result.passed ? '通过' : '未通过' }}</p>
      <el-table :data="grades.items">
        <el-table-column prop="title" label="考核项">
          <template #default="{ row }">
            <el-button v-if="!study" text @click="openHistory(row)">{{ row.title }}</el-button>
            <span v-else>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权重" width="80"><template #default="{ row }">{{ row.weight }}%</template></el-table-column>
        <el-table-column label="状态" width="130"><template #default="{ row }">{{ gradeStateName[(row as GradeReport['items'][number]).state] }}</template></el-table-column>
        <el-table-column prop="score" label="成绩" width="80" />
        <el-table-column prop="feedback" label="反馈" />
      </el-table>
      <article v-if="historic && selectedClass" class="section">
        <h4>{{ historic.title }} · 历史记录</h4>
        <p v-if="!attempts.length && !submissions.length">此项暂无提交记录。</p>
        <div v-for="work in submissions" :key="work.id">
          <p>第 {{ work.revision }} 次提交 · 作业第 {{ work.version }} 版</p>
          <p>{{ work.description }}</p><p v-if="work.feedback">{{ work.feedback }}</p>
        </div>
        <div v-for="attempt in attempts" :key="attempt.id">
          <RouterLink :to="{ path: '/exams', query: { attemptId: attempt.id, classroom: selectedClass.classroom.id, memberId: attempt.memberId } }">
            {{ examStatus(attempt.status) }} · 查看答卷
          </RouterLink>
        </div>
      </article>
    </section>
  </main>
</template>
