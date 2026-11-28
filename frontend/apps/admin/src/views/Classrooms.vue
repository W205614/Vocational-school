<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { downloadCsv, get, write } from '../../../../packages/shared/src/client';
import { useSession } from '../../../../packages/shared/src/session';
import { useTask } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import EntityPicker from '../components/EntityPicker.vue';
import {
  activityName, classStateName, classTime, gradeStateName, memberStateName,
  type Activity, type ActivityKind, type Classroom, type ClassState, type Discussion, type Duty,
  type GradeItem, type GradeReport, type MemberState, type Submission, type Version,
} from '../../../../packages/shared/src/classroom';

interface Staff { userId: string; duty: Duty; name: string }
interface Roster { memberId: string; userId: string; name: string; state: MemberState; learnedSections: number; courseSections?: number }
interface Pending {
  assignments: number; exams: number; questions: number; grades: number;
  assignmentSubmitted: number; assignmentExpected: number; examSubmitted: number; examExpected: number;
}
interface Paper { id: string; sectionId: string; version: number; totalScore: number }
interface ExamQueue { id: string; user_id: string; submitted_at: string }
interface CourseSection { id: string; name: string }

const adminPath = '/admin/learning-hub/classrooms';
const staffPath = '/teacher/learning-hub/classrooms';
const session = useSession();
const route = useRoute();
const router = useRouter();
const task = useTask();
const classes = ref<Classroom[]>([]);
const staff = ref<Staff[]>([]);
const roster = ref<Roster[]>([]);
const pending = ref<Pending>();
const activities = ref<Activity[]>([]);
const sections = ref<CourseSection[]>([]);
const papers = ref<Paper[]>([]);
const work = ref<Submission[]>([]);
const versions = ref<Version[]>([]);
const examQueue = ref<ExamQueue[]>([]);
const discussion = ref<Discussion[]>([]);
const reports = ref<GradeReport[]>([]);
const chosen = ref<Activity>();
const editing = ref(false);
const editingId = ref<string>();
const arranging = ref(false);
const dialog = ref<'correction' | 'extension' | 'review' | 'grade' | 'move'>();
const gradingItem = ref<GradeItem>();
const chosenMember = ref('');
const chosenWork = ref<Submission>();
const memberTarget = ref('');
const decision = ref<'REVIEWED' | 'NEEDS_REVISION'>('REVIEWED');
const value = ref(0);
const reason = ref('');
const feedback = ref('');
const correctedTitle = ref('');
const correctedInstructions = ref('');
const newDeadline = ref('');
const staffUser = ref('');
const staffDuty = ref<Duty>('TEACHER');
const studentUser = ref('');
const addReason = ref('');
const source = ref('');
const sourceExams = ref<Activity[]>([]);
const replacementPapers = ref<Record<string, string>>({});
const response = ref('');
const replyTo = ref('');
const search = ref('');
const form = reactive({
  courseId: '', name: '', registrationOpens: '', registrationCloses: '',
  startsAt: '', endsAt: '', capacity: 30, passPercent: 60,
});
const activityForm = reactive({
  kind: 'ASSIGNMENT' as ActivityKind, sectionId: '', paperId: '', title: '',
  scheduledAt: '', closesAt: '', weight: 10, maxAttempts: 1, instructions: '',
});
const isAdmin = computed(() => session.role === 1);
const room = computed(() => classes.value.find((item) => String(item.id) === route.query.classId));
const canTeach = computed(() => isAdmin.value ||
  staff.value.some((item) => item.duty === 'TEACHER' && String(item.userId) === session.userId));
const editable = computed(() => room.value && !['ARCHIVED', 'CANCELED'].includes(room.value.state));
const tab = computed({
  get: () => String(route.query.tab || 'overview'),
  set: (name: string) => { void router.replace({ query: { ...route.query, tab: name } }); },
});
const filtered = computed(() => classes.value.filter((item) =>
  !search.value.trim() || (item.name + ' ' + item.courseId).includes(search.value.trim())));
const otherClasses = computed(() => classes.value.filter((item) =>
  item.courseId === room.value?.courseId && item.id !== room.value?.id));
const weighted = computed(() => ['ASSIGNMENT', 'EXAM'].includes(activityForm.kind));
const path = () => staffPath + '/' + room.value!.id;
const admin = () => adminPath + '/' + room.value!.id;
const activityPath = (id: string) => path() + '/activities/' + id;
const name = (member: string, user: string) => roster.value.find((item) => String(item.memberId) === String(member))?.name || user;

async function refresh() {
  classes.value = await get<Classroom[]>(isAdmin.value ? adminPath : staffPath);
  if (room.value) await loadRoom();
}

async function loadRoom() {
  const selected = room.value;
  if (!selected) return;
  [staff.value, roster.value, pending.value] = await Promise.all([
    get<Staff[]>(path() + '/staff'), get<Roster[]>(path() + '/members'), get<Pending>(path() + '/pending'),
  ]);
  if (tab.value === 'activities') await loadActivities();
  if (tab.value === 'discussion') discussion.value = await get<Discussion[]>(path() + '/discussion');
  if (tab.value === 'grades') reports.value = await get<GradeReport[]>(path() + '/grades');
}

async function loadActivities() {
  activities.value = await get<Activity[]>(path() + '/activities');
  if (canTeach.value && editable.value) {
    const [paperOptions, courseSections] = await Promise.all([
      get<Paper[]>(path() + '/papers'),
      get<CourseSection[]>(path() + '/sections'),
    ]);
    papers.value = paperOptions;
    sections.value = courseSections;
  }
}

watch(() => [route.query.classId, route.query.tab], () => {
  chosen.value = undefined;
  arranging.value = false;
  void task.run(refresh);
}, { immediate: true });

function edit(current?: Classroom) {
  editingId.value = current?.id;
  Object.assign(form, current ? {
    courseId: current.courseId, name: current.name, registrationOpens: current.registrationOpens,
    registrationCloses: current.registrationCloses, startsAt: current.startsAt, endsAt: current.endsAt,
    capacity: current.capacity, passPercent: current.passPercent,
  } : { courseId: '', name: '', registrationOpens: '', registrationCloses: '',
    startsAt: '', endsAt: '', capacity: 30, passPercent: 60 });
  editing.value = true;
}

async function saveRoom() {
  await task.run(async () => {
    const id = editingId.value;
    const saved = await write<Classroom>(id ? adminPath + '/' + id : adminPath, id ? 'PUT' : 'POST', { ...form });
    editing.value = false;
    await router.push({ query: { classId: saved.id } });
    await refresh();
    task.message.value = '班级设置已保存';
  });
}

async function changeState(state: ClassState) {
  const label = { OPEN: '发布班级', CLOSED: '停止招生', CANCELED: '取消开班' }[state as 'OPEN' | 'CLOSED' | 'CANCELED'];
  let cancellationReason: string | undefined;
  if (state === 'CANCELED') {
    const result = await ElMessageBox.prompt('请输入取消开班的原因', label).catch(() => undefined);
    if (!result) return;
    cancellationReason = result.value;
  } else if (!(await ElMessageBox.confirm('确认' + label + '？', label).catch(() => undefined))) return;
  await task.run(async () => {
    await write(admin() + '/state', 'PUT', { state, reason: cancellationReason });
    await refresh();
    task.message.value = '班级状态已更新';
  });
}

async function setStaff(user: string, duty?: Duty) {
  if (!user) return;
  await task.run(async () => {
    await write(admin() + '/staff/' + user, 'PUT', { duty: duty ?? null });
    staff.value = await get<Staff[]>(path() + '/staff');
    staffUser.value = '';
    task.message.value = '教学人员授权已更新';
  });
}

async function enroll() {
  await task.run(async () => {
    await write(admin() + '/members', 'POST', { userId: studentUser.value, reason: addReason.value });
    studentUser.value = '';
    addReason.value = '';
    await loadRoom();
    task.message.value = '学员已入班';
  });
}

async function copy() {
  await task.run(async () => {
    await write(admin() + '/copy-schedule', 'POST', {
      sourceClassId: source.value,
      replacementPapers: Object.fromEntries(Object.entries(replacementPapers.value).filter(([, paper]) => paper)),
    });
    source.value = '';
    task.message.value = '教学安排已复制';
    tab.value = 'activities';
  });
}

async function selectSource() {
  sourceExams.value = [];
  replacementPapers.value = {};
  if (!source.value) return;
  await task.run(async () => {
    const [items, paperOptions] = await Promise.all([
      get<Activity[]>(staffPath + '/' + source.value + '/activities'), get<Paper[]>(path() + '/papers'),
    ]);
    sourceExams.value = items.filter((item) => item.kind === 'EXAM');
    papers.value = paperOptions;
  });
}

async function addActivity() {
  await task.run(async () => {
    await write(path() + '/activities', 'POST', {
      ...activityForm, sectionId: activityForm.kind === 'EXAM' ? undefined : activityForm.sectionId || undefined,
      paperId: activityForm.kind === 'EXAM' ? activityForm.paperId : undefined,
      closesAt: weighted.value ? activityForm.closesAt : undefined,
      weight: weighted.value ? activityForm.weight : 0,
      maxAttempts: activityForm.kind === 'EXAM' ? activityForm.maxAttempts : undefined,
    });
    arranging.value = false;
    await loadActivities();
    task.message.value = '教学安排已保存';
  });
}

async function openActivity(item: Activity) {
  await task.run(async () => {
    chosen.value = item;
    versions.value = await get<Version[]>(activityPath(item.id) + '/versions');
    work.value = item.kind === 'ASSIGNMENT' ? await get<Submission[]>(activityPath(item.id) + '/submissions') : [];
    examQueue.value = item.kind === 'EXAM' ? await get<ExamQueue[]>('/teacher/classroom-exams/' + item.id + '/attempts') : [];
  });
}

function openDialog(kind: typeof dialog.value, member = '') {
  reason.value = '';
  feedback.value = '';
  chosenMember.value = member;
  memberTarget.value = '';
  newDeadline.value = '';
  decision.value = 'REVIEWED';
  value.value = 0;
  correctedTitle.value = chosen.value?.title || '';
  correctedInstructions.value = chosen.value?.instructions || '';
  dialog.value = kind;
}

function openGrade(report: GradeReport, item: GradeItem) {
  openDialog('grade', report.memberId);
  gradingItem.value = item;
  value.value = item.score ?? 0;
  feedback.value = item.feedback || '';
}

async function saveDialog() {
  await task.run(async () => {
    if (dialog.value === 'move') {
      await write(admin() + '/members/' + chosenMember.value, 'PUT', {
        targetClassId: memberTarget.value || null, reason: reason.value,
      });
    } else if (dialog.value === 'grade' && gradingItem.value) {
      const item = gradingItem.value;
      const endpoint = activityPath(item.activityId) + '/grades';
      if (item.state === 'PUBLISHED')
        await write(endpoint, 'PUT', { memberId: chosenMember.value, expectedScore: item.score,
          score: value.value, feedback: feedback.value, reason: reason.value });
      else await write(endpoint, 'POST', { memberId: chosenMember.value, feedback: feedback.value || undefined });
    } else if (chosen.value) {
      const item = chosen.value;
      const base = activityPath(item.id);
      if (dialog.value === 'correction') {
        chosen.value = await write<Activity>(base + '/correction', 'PUT', {
          expectedVersion: item.currentVersion, title: correctedTitle.value, instructions: correctedInstructions.value, reason: reason.value,
        });
      } else if (dialog.value === 'extension') {
        await write(base + '/extension', 'PUT', { memberId: chosenMember.value, closesAt: newDeadline.value, reason: reason.value });
      } else if (dialog.value === 'review' && chosenWork.value) {
        await write(base + '/submissions/' + chosenWork.value.id, 'PUT', {
          state: decision.value, score: decision.value === 'REVIEWED' ? value.value : undefined, feedback: feedback.value,
        });
      }
    }
    dialog.value = undefined;
    await loadRoom();
    if (chosen.value) {
      const latest = activities.value.find((item) => item.id === chosen.value?.id);
      if (latest) await openActivity(latest);
    }
    task.message.value = '操作已保存';
  });
}

async function reply() {
  await task.run(async () => {
    await write(path() + '/discussion', 'POST', { parentId: replyTo.value, body: response.value });
    response.value = '';
    replyTo.value = '';
    discussion.value = await get<Discussion[]>(path() + '/discussion');
  });
}

async function exportGrades() {
  await task.run(async () => {
    const blob = await downloadCsv(path() + '/grades/export');
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'classroom-' + room.value!.id + '-grades.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
}

async function archive() {
  const confirmed = await ElMessageBox.confirm('结班会计算总评、发布待确认成绩并归档班级。', '确认结班').catch(() => undefined);
  if (!confirmed) return;
  await task.run(async () => {
    await write(path() + '/archive', 'POST');
    await refresh();
    task.message.value = '班级已结班';
  });
}
</script>

<template>
  <section class="card">
    <div class="section-heading"><h2>班级教务</h2>
      <el-button v-if="isAdmin" type="primary" @click="edit()">新建班级</el-button>
    </div>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <div class="toolbar">
      <el-input v-model="search" aria-label="筛选班级" placeholder="班级名称或课程编号" clearable />
      <el-button @click="task.run(refresh)">刷新</el-button>
    </div>
    <div v-for="item in filtered" :key="item.id" class="toolbar">
      <RouterLink :to="{ query: { classId: item.id } }">{{ item.name }}</RouterLink>
      <span>{{ classStateName[item.state] }}</span>
      <span class="muted">开课 {{ classTime(item.startsAt) }} · 余位 {{ item.remaining }}/{{ item.capacity }}</span>
    </div>
    <p v-if="!filtered.length && !task.busy.value" class="muted">没有匹配的班级。</p>
    <template v-if="room">
      <div class="section-heading"><h3>{{ room.name }}</h3>
        <div class="toolbar" v-if="isAdmin && editable">
          <el-button @click="edit(room)">班级设置</el-button>
          <el-button v-if="room.state === 'DRAFT'" type="primary" @click="changeState('OPEN')">发布班级</el-button>
          <el-button v-if="room.state === 'OPEN'" @click="changeState('CLOSED')">停止招生</el-button>
          <el-button @click="changeState('CANCELED')">取消开班</el-button>
        </div>
      </div>
      <div v-if="pending" class="toolbar">
        <el-button text @click="tab = 'activities'">待批作业 {{ pending.assignments }} · 待阅试卷 {{ pending.exams }}</el-button>
        <el-button text @click="tab = 'discussion'">待答疑 {{ pending.questions }}</el-button>
        <el-button text @click="tab = 'grades'">待发布成绩 {{ pending.grades }}</el-button>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="人员与学情" name="overview">
          <p>招生 {{ classTime(room.registrationOpens) }} 至 {{ classTime(room.registrationCloses) }}</p>
          <p>教学 {{ classTime(room.startsAt) }} 至 {{ classTime(room.endsAt) }} · 及格线 {{ room.passPercent }}%</p>
          <p v-if="pending">作业提交 {{ pending.assignmentSubmitted }}/{{ pending.assignmentExpected }} 人次 ·
            考试交卷 {{ pending.examSubmitted }}/{{ pending.examExpected }} 人次</p>
          <h4>教学人员</h4>
          <div v-for="person in staff" :key="person.userId" class="toolbar">
            <span>{{ person.name || person.userId }} · {{ person.duty === 'TEACHER' ? '教师' : '助教' }}</span>
            <el-button v-if="isAdmin" text @click="setStaff(person.userId)">移除授权</el-button>
          </div>
          <div v-if="isAdmin && room.state !== 'CANCELED'" class="toolbar">
            <EntityPicker v-model="staffUser" kind="teachers" label="选择教学人员" />
            <el-select v-model="staffDuty" aria-label="班级职责"><el-option label="教师" value="TEACHER" /><el-option label="助教" value="ASSISTANT" /></el-select>
            <el-button :disabled="!staffUser" @click="setStaff(staffUser, staffDuty)">保存授权</el-button>
          </div>
          <div v-if="isAdmin && room.state === 'DRAFT'" class="toolbar">
            <el-select v-model="source" placeholder="选择往期班级" aria-label="教学安排来源" @change="selectSource">
              <el-option v-for="item in otherClasses" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-button :disabled="!source" @click="copy">复制教学安排</el-button>
          </div>
          <div v-for="item in sourceExams" v-show="isAdmin && room.state === 'DRAFT' && source" :key="item.id" class="toolbar">
            <span>{{ item.title }} · 旧解析已开放时请选择新试卷</span>
            <el-select v-model="replacementPapers[item.id]" clearable :aria-label="item.title + '替换试卷'">
              <el-option v-for="paper in papers.filter(row => row.id !== item.paperId)" :key="paper.id"
                :value="paper.id" :label="'小节 ' + paper.sectionId + ' · 第 ' + paper.version + ' 版'" />
            </el-select>
          </div>
          <h4>学员名单</h4>
          <div v-if="isAdmin && ['OPEN', 'CLOSED'].includes(room.state)" class="toolbar">
            <EntityPicker v-model="studentUser" kind="users" label="选择学员" />
            <el-input v-model="addReason" aria-label="补录原因" placeholder="补录原因" maxlength="500" />
            <el-button :disabled="!studentUser || !addReason.trim()" @click="enroll">补录入班</el-button>
          </div>
          <el-table :data="roster">
            <el-table-column prop="name" label="学员" />
            <el-table-column label="班籍"><template #default="{ row }">{{ memberStateName[(row as Roster).state] }}</template></el-table-column>
            <el-table-column label="课程进度"><template #default="{ row }">{{ row.state === 'ACTIVE' ? row.learnedSections + ' / ' + (row.courseSections || '—') : '—' }}</template></el-table-column>
            <el-table-column v-if="isAdmin && editable" label="操作">
              <template #default="{ row }"><el-button v-if="row.state === 'ACTIVE'" text @click="openDialog('move', row.memberId)">退班或转班</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="教学安排" name="activities">
          <el-button v-if="canTeach && editable" type="primary" @click="arranging = true">新增教学安排</el-button>
          <p v-if="!activities.length" class="muted">暂无教学安排。</p>
          <div v-for="item in activities" :key="item.id" class="toolbar">
            <el-button @click="openActivity(item)">{{ activityName[item.kind] }} · {{ item.title }}</el-button>
            <span>{{ classTime(item.scheduledAt) }}<template v-if="item.closesAt"> 至 {{ classTime(item.closesAt) }} · {{ item.weight }}%</template></span>
          </div>
          <article v-if="chosen" class="section">
            <h4>{{ chosen.title }}</h4><p style="white-space: pre-wrap">{{ chosen.instructions }}</p>
            <div v-if="canTeach && editable" class="toolbar">
              <el-button @click="openDialog('correction')">更正教学说明</el-button>
              <el-button v-if="chosen.closesAt" @click="openDialog('extension')">设置个人延期</el-button>
            </div>
            <details v-if="versions.length > 1"><summary>更正历史</summary>
              <div v-for="entry in versions" :key="entry.version"><h5>第 {{ entry.version }} 版 · {{ entry.title }}</h5><p>{{ entry.reason }}</p><p>{{ entry.instructions }}</p></div>
            </details>
            <div v-for="entry in work" :key="entry.id" class="section">
              <strong>{{ name(entry.memberId, entry.userId) }} · 第 {{ entry.revision }} 次提交</strong>
              <p>{{ entry.description }}</p>
              <a v-if="entry.workUrl" :href="entry.workUrl" target="_blank" rel="noreferrer">打开作品链接</a>
              <p v-if="entry.feedback">{{ entry.feedback }}<template v-if="entry.score != null"> · {{ entry.score }} 分</template></p>
              <el-button v-if="editable && ['SUBMITTED', 'NEEDS_REVISION'].includes(entry.state)"
                text @click="chosenWork = entry; openDialog('review')">批改作业</el-button>
            </div>
            <div v-for="entry in examQueue" :key="entry.id" class="toolbar">
              <RouterLink :to="{ path: '/exams', query: { pendingAttemptId: entry.id, classroom: room.id } }">
                阅卷 · {{ name(entry.user_id, entry.user_id) }}
              </RouterLink><span>{{ classTime(entry.submitted_at) }}</span>
            </div>
          </article>
        </el-tab-pane>
        <el-tab-pane label="班级答疑" name="discussion">
          <p v-if="!discussion.length" class="muted">暂无班级提问。</p>
          <article v-for="item in discussion.filter(post => !post.parentId)" :key="item.id" class="section">
            <h4>{{ item.title }}</h4><p>{{ item.body }}</p>
            <p v-for="answer in discussion.filter(post => String(post.parentId) === String(item.id)).reverse()" :key="answer.id">
              {{ answer.staffReply ? '教学人员' : '学员' }} · {{ answer.body }}
            </p>
            <el-button v-if="editable && replyTo !== item.id" text @click="replyTo = item.id">回复</el-button>
            <el-form v-if="editable && replyTo === item.id" @submit.prevent="reply">
              <el-form-item label="回复内容"><el-input v-model="response" type="textarea" maxlength="8000" /></el-form-item>
              <el-button type="primary" native-type="submit" :disabled="!response.trim()">发送</el-button>
              <el-button @click="replyTo = ''">取消</el-button>
            </el-form>
          </article>
        </el-tab-pane>
        <el-tab-pane label="班级成绩" name="grades">
          <div v-if="canTeach" class="toolbar">
            <el-button @click="exportGrades">导出已发布成绩</el-button>
            <el-button v-if="editable && room.state !== 'DRAFT'" type="primary" @click="archive">结班并计算总评</el-button>
          </div>
          <article v-for="report in reports" :key="report.memberId" class="section">
            <h4>{{ report.name || report.userId }} · {{ memberStateName[report.membershipState] }}
              <small v-if="report.result">总评 {{ report.result.totalPercent }} 分</small></h4>
            <el-table :data="report.items">
              <el-table-column prop="title" label="考核项" />
              <el-table-column label="状态"><template #default="{ row }">{{ gradeStateName[(row as GradeItem).state] }}</template></el-table-column>
              <el-table-column prop="score" label="分数" />
              <el-table-column v-if="canTeach" label="操作">
                <template #default="{ row }">
                  <el-button v-if="(row.state === 'READY' && editable) || row.state === 'PUBLISHED'" text
                    @click="openGrade(report, row)">{{ row.state === 'PUBLISHED' ? '更正' : '确认发布' }}</el-button>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </el-tab-pane>
      </el-tabs>
    </template>
  </section>

  <el-dialog v-model="editing" :title="editingId ? '班级设置' : '新建班级'" width="min(640px, 94vw)">
    <el-form label-position="top" @submit.prevent="saveRoom">
      <el-form-item label="课程"><EntityPicker v-model="form.courseId" kind="courses" label="选择课程" :disabled="Boolean(editingId)" /></el-form-item>
      <el-form-item label="班级名称"><el-input v-model="form.name" maxlength="120" /></el-form-item>
      <el-form-item v-for="[key, label] in ([['registrationOpens', '报名开始'], ['registrationCloses', '报名截止'], ['startsAt', '开课时间'], ['endsAt', '结课时间']] as const)" :key="key" :label="label">
        <el-input v-model="form[key]" type="datetime-local" step="1" />
      </el-form-item>
      <el-form-item label="班级容量"><el-input-number v-model="form.capacity" :min="1" :max="1000" /></el-form-item>
      <el-form-item label="及格线（百分比）"><el-input-number v-model="form.passPercent" :min="0" :max="100" /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="task.busy.value">保存</el-button>
    </el-form>
  </el-dialog>

  <el-dialog v-model="arranging" title="新增教学安排" width="min(640px, 94vw)">
    <el-form label-position="top" @submit.prevent="addActivity">
      <el-form-item label="类型"><el-select v-model="activityForm.kind">
        <el-option v-for="(label, kind) in activityName" :key="kind" :value="kind" :label="label" />
      </el-select></el-form-item>
      <el-form-item label="标题"><el-input v-model="activityForm.title" maxlength="120" /></el-form-item>
      <el-form-item v-if="activityForm.kind !== 'EXAM' && activityForm.kind !== 'ANNOUNCEMENT'" label="关联课程小节">
        <el-select v-model="activityForm.sectionId" clearable>
          <el-option v-for="item in sections" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="activityForm.kind === 'EXAM'" label="班级专用试卷">
        <el-select v-model="activityForm.paperId">
          <el-option v-for="item in papers" :key="item.id" :label="'小节 ' + item.sectionId + ' · 第 ' + item.version + ' 版 · ' + item.totalScore + ' 分'" :value="item.id" />
        </el-select>
        <RouterLink v-if="isAdmin && room" :to="{ path: '/exams', query: { courseId: room.courseId } }">发布班级专用试卷</RouterLink>
      </el-form-item>
      <el-form-item label="开始时间"><el-input v-model="activityForm.scheduledAt" type="datetime-local" step="1" /></el-form-item>
      <el-form-item v-if="weighted" label="截止时间"><el-input v-model="activityForm.closesAt" type="datetime-local" step="1" /></el-form-item>
      <el-form-item v-if="weighted" label="总评权重（百分比）"><el-input-number v-model="activityForm.weight" :min="1" :max="100" /></el-form-item>
      <el-form-item v-if="activityForm.kind === 'EXAM'" label="允许作答次数"><el-input-number v-model="activityForm.maxAttempts" :min="1" :max="10" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="activityForm.instructions" type="textarea" maxlength="8000" /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="task.busy.value">保存教学安排</el-button>
    </el-form>
  </el-dialog>

  <el-dialog :model-value="Boolean(dialog)" title="处理教学事务" width="min(640px, 94vw)" @close="dialog = undefined">
    <el-form v-if="dialog" label-position="top" @submit.prevent="saveDialog">
      <template v-if="dialog === 'move'">
        <el-form-item label="目标班级"><el-select v-model="memberTarget" clearable placeholder="留空为退班">
          <el-option v-for="item in otherClasses.filter(row => ['OPEN', 'CLOSED'].includes(row.state))" :key="item.id" :value="item.id" :label="item.name" />
        </el-select></el-form-item>
      </template>
      <template v-if="dialog === 'correction'">
        <el-form-item label="标题"><el-input v-model="correctedTitle" maxlength="120" /></el-form-item>
        <el-form-item label="新版说明"><el-input v-model="correctedInstructions" type="textarea" maxlength="8000" /></el-form-item>
      </template>
      <template v-if="dialog === 'extension'">
        <el-form-item label="学员"><el-select v-model="chosenMember">
          <el-option v-for="item in roster.filter(row => row.state === 'ACTIVE')" :key="item.memberId" :value="item.memberId" :label="item.name || item.userId" />
        </el-select></el-form-item>
        <el-form-item label="个人截止时间"><el-input v-model="newDeadline" type="datetime-local" step="1" /></el-form-item>
      </template>
      <template v-if="dialog === 'review'">
        <el-form-item label="批改结果"><el-select v-model="decision"><el-option label="已初评" value="REVIEWED" /><el-option v-if="chosenWork?.state !== 'NEEDS_REVISION'" label="需修改重交" value="NEEDS_REVISION" /></el-select></el-form-item>
      </template>
      <el-form-item v-if="(dialog === 'review' && decision === 'REVIEWED') || (dialog === 'grade' && gradingItem?.state === 'PUBLISHED')" label="分数">
        <el-input-number v-model="value" :min="0" :max="100" :precision="0" />
      </el-form-item>
      <p v-if="dialog === 'grade' && gradingItem?.state !== 'PUBLISHED'">确认发布 {{ gradingItem?.score }} 分。</p>
      <el-form-item v-if="dialog === 'review' || dialog === 'grade'" label="评语"><el-input v-model="feedback" type="textarea" maxlength="5000" /></el-form-item>
      <el-form-item v-if="['move', 'correction', 'extension'].includes(dialog) || (dialog === 'grade' && gradingItem?.state === 'PUBLISHED')" label="原因">
        <el-input v-model="reason" type="textarea" maxlength="500" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="task.busy.value">确认保存</el-button>
    </el-form>
  </el-dialog>
</template>
