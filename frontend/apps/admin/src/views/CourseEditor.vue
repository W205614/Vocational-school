<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { get, write, newKey } from '../../../../packages/shared/src/client';
import { useTask, money, plainTextDescription, type Row } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import EntityPicker from '../components/EntityPicker.vue';
import CourseForm from '../components/CourseForm.vue';
import CourseFacts from '../../../../packages/shared/src/CourseFacts.vue';
import { canPrepareTrial, isPreparingTrial, prepareCourseTrials, trialLabels, type Trial } from '../courseTrials';

type Issue = { area: string; sectionId?: string; message: string; blocking: boolean };
type Publication = { preview: Row; issues: Issue[]; trials: Trial[] };
const route = useRoute(),
  router = useRouter(),
  task = useTask(),
  publicationTask = useTask(),
  formsTask = useTask(),
  publication = ref<Publication>(),
  selectedTrials = ref<string[]>([]),
  trialPlayer = ref<{ sectionId: string; taskId: string; url: string }>(),
  previewOpen = ref(false),
  baseOpen = ref(false),
  pendingIssue = ref<Issue>(),
  id = ref(''),
  selectedCourse = ref<Row[]>([]),
  chapters = ref<Row[]>([]),
  teachers = ref<string[]>([]),
  teacherRows = ref<Row[]>([]),
  media = ref<Row>({ trailer: false }),
  mediaRows = ref<Row[]>([]),
  examSection = ref(''),
  questionIds = ref<string[]>([]),
  questionRows = ref<Row[]>([]),
  relations = ref<Row[]>([]),
  baseline = ref('');
const sections = computed<Row[]>(() =>
  chapters.value.flatMap((c) =>
    (c.sections || []).map((s: Row) => ({ ...s, label: c.name + ' / ' + s.name })),
  ),
);
const videos = computed(() => sections.value.filter((s) => s.id && Number(s.type) === 2)),
  exams = computed(() => sections.value.filter((s) => s.id && Number(s.type) === 3));
const dirty = computed(() => baseline.value !== JSON.stringify(chapters.value));
const preparableTrials = computed(() => (publication.value?.trials ?? []).filter(canPrepareTrial));
const preparationTargets = computed(() => preparableTrials.value.filter((trial) => selectedTrials.value.includes(trial.sectionId)));
async function load(next = id.value) {
  if (!next) return;
  if (id.value && dirty.value) {
    try {
      await ElMessageBox.confirm('章节有未保存修改，继续会放弃这些修改。', '切换课程', {
        confirmButtonText: '放弃并继续',
        cancelButtonText: '继续编辑',
      });
    } catch {
      return;
    }
  }
  await task.run(async () => {
    if (!/^[1-9][0-9]{0,18}$/.test(next)) throw new Error('课程编号无效');
    const [detail, catas, staff, subjects] = await Promise.all([
      get<Row>('/admin/course/courses/baseInfo/' + next + '?see=false'),
      get<Row[]>('/admin/course/courses/catas/' + next + '?see=false&withPractice=true'),
      get<Row[]>('/admin/course/courses/teachers/' + next + '?see=false'),
      get<Row[]>('/admin/course/courses/subjects/get/' + next),
    ]);
    id.value = next;
    selectedCourse.value = [detail];
    chapters.value = catas || [];
    baseline.value = JSON.stringify(chapters.value);
    teacherRows.value = staff || [];
    teachers.value = teacherRows.value.map((x) => String(x.id));
    relations.value = subjects || [];
    media.value = { trailer: false };
    mediaRows.value = [];
    examSection.value = '';
    questionIds.value = [];
    questionRows.value = [];
  });
}
async function showPublication() {
  publication.value = undefined;
  selectedTrials.value = [];
  trialPlayer.value = undefined;
  previewOpen.value = true;
  await refreshPublication();
}
async function readPublication() {
  publication.value = await get<Publication>('/admin/course/courses/publication/' + id.value);
  const playing = trialPlayer.value;
  if (playing && !publication.value.trials.some(
    (trial) => trial.sectionId === playing.sectionId && trial.status.state === 'READY'
      && trial.status.taskId === playing.taskId,
  )) trialPlayer.value = undefined;
}
async function refreshPublication() {
  await publicationTask.run(readPublication);
}
async function updateTrial(trial: Trial) {
  trialPlayer.value = undefined;
  await publicationTask.run(async () => {
    if (trial.status.state === 'FAILED') {
      await write('/admin/media/media-trials/' + trial.status.taskId + '/retry', 'POST', {
        version: trial.status.version,
      });
    } else {
      const ticket = await write<{ playUrl?: string; taskId: string }>(
        '/admin/media/trial-previews', 'POST', { courseId: id.value, sectionId: trial.sectionId },
      );
      if (previewOpen.value && ticket.playUrl) {
        trialPlayer.value = { sectionId: trial.sectionId, taskId: ticket.taskId, url: ticket.playUrl };
      }
    }
    await readPublication();
  });
}
async function prepareTrials(all: boolean) {
  trialPlayer.value = undefined;
  await publicationTask.run(async () => {
    await prepareCourseTrials(id.value, true, all ? [] : preparationTargets.value.map((trial) => trial.sectionId));
    selectedTrials.value = [];
    await readPublication();
  });
}
watch(
  [previewOpen, publication, publicationTask.busy, publicationTask.error],
  ([open, result, busy, error], _, onCleanup) => {
    if (!open || busy || error || !result?.trials.some(isPreparingTrial)) return;
    const poll = setTimeout(refreshPublication, 1500);
    onCleanup(() => clearTimeout(poll));
  },
);
async function publish() {
  if (!publication.value || publication.value.issues.some((issue) => issue.blocking)) return;
  const saved = await publicationTask.run(async () => {
    await write('/admin/course/courses/upShelf', 'POST', { id: id.value }, newKey());
    return true;
  });
  if (saved) await router.push({ path: '/courses' });
}
async function saveBase(body: Row) {
  const saved = await formsTask.run(async () => {
    await write('/admin/course/courses/baseInfo/save', 'POST', body, newKey());
    selectedCourse.value = [await get<Row>('/admin/course/courses/baseInfo/' + id.value + '?see=false')];
    return true;
  });
  if (saved) {
    baseOpen.value = false;
    task.message.value = '课程资料已保存';
  }
}
function goToIssue(issue: Issue) {
  if (issue.area === 'TRIAL') {
    document.getElementById('course-trial-' + issue.sectionId)?.focus();
    return;
  }
  pendingIssue.value = issue;
  previewOpen.value = false;
}
async function focusIssue() {
  const issue = pendingIssue.value;
  pendingIssue.value = undefined;
  if (!issue) return;
  if (issue.area === 'BASE') {
    baseOpen.value = true;
    return;
  }
  if (issue.area === 'EXAM') {
    await router.push({
      path: '/exams',
      query: { courseId: id.value, sectionId: issue.sectionId, draft: 'true' },
    });
    return;
  }
  const areas: Record<string, string> = {
    CHAPTER: 'course-chapters', MEDIA: 'course-media',
    QUESTIONS: 'course-questions', TEACHERS: 'course-teachers',
  };
  if (issue.area === 'MEDIA' && issue.sectionId) {
    media.value.cataId = videos.value.find((item) => String(item.id) === issue.sectionId)?.id;
    selectVideo();
  } else if (issue.area === 'QUESTIONS' && issue.sectionId) {
    examSection.value = issue.sectionId;
    selectExam();
  }
  await nextTick();
  const target = document.getElementById(areas[issue.area] || '');
  target?.focus();
}
function addChapter() {
  chapters.value.push({
    name: '',
    type: 1,
    canUpdate: true,
    index: chapters.value.length + 1,
    sections: [{ name: '', type: 2, canUpdate: true }],
  });
}
function editable(item: Row) {
  return !item.id || item.canUpdate === true;
}
async function save() {
  const saved = await task.run(async () => {
    const body = chapters.value.map((c, i) => ({
      ...c,
      index: i + 1,
      sections: (c.sections || []).map((s: Row, j: number) => ({ ...s, index: j + 1 })),
    }));
    await write('/admin/course/courses/catas/save/' + id.value + '/2', 'POST', body, newKey());
    baseline.value = JSON.stringify(chapters.value);
    return true;
  });
  if (saved) {
    await load();
    task.message.value = '章节已保存';
  }
}
function selectVideo() {
  const s = videos.value.find((x) => String(x.id) === String(media.value.cataId));
  media.value = {
    cataId: s?.id,
    mediaId: s?.mediaId,
    videoName: s?.mediaName,
    mediaDuration: s?.mediaDuration,
    trailer: !!s?.trailer,
  };
  mediaRows.value = s?.mediaId
    ? [{ id: s.mediaId, filename: s.mediaName, duration: s.mediaDuration }]
    : [];
}
function selectMedia(rows: Row[]) {
  const row = rows[0];
  media.value.videoName = row?.filename;
  media.value.mediaDuration = row ? Math.ceil(Number(row.duration)) : undefined;
}
async function saveMedia() {
  await task.run(async () => {
    await write('/admin/course/courses/media/save/' + id.value, 'POST', [media.value], newKey());
    const section = chapters.value
      .flatMap((c) => c.sections || [])
      .find((s: Row) => String(s.id) === String(media.value.cataId));
    if (section) {
      section.mediaId = media.value.mediaId;
      section.mediaName = media.value.videoName;
      section.mediaDuration = media.value.mediaDuration;
      section.trailer = media.value.trailer;
    }
    baseline.value = JSON.stringify(chapters.value);
    task.message.value = '视频关联已保存';
  });
}
function selectExam() {
  questionRows.value =
    relations.value.find((x) => String(x.cataId) === examSection.value)?.subjects || [];
  questionIds.value = questionRows.value.map((x) => String(x.id));
}
async function saveQuestions() {
  await task.run(async () => {
    await write(
      '/admin/course/courses/subjects/save/' + id.value,
      'POST',
      [{ cataId: examSection.value, subjectIds: questionIds.value }],
      newKey(),
    );
    relations.value = await get<Row[]>('/admin/course/courses/subjects/get/' + id.value);
    task.message.value = '考试题目关联已保存';
  });
}
async function saveTeachers() {
  await task.run(async () => {
    await write(
      '/admin/course/courses/teachers/save',
      'POST',
      { id: id.value, teachers: teachers.value.map((id) => ({ id, isShow: true })) },
      newKey(),
    );
    task.message.value = '授课教师已保存';
  });
}
onMounted(() => {
  if (route.query.courseId) void load(String(route.query.courseId));
});
</script>
<template>
  <section class="card">
    <h2>课程章节、视频与教师</h2>
    <p class="muted">
      选择待上架或已下架草稿进行配置。已上架课程请先在课程管理下架；已发布章节的修改限制仍由服务端校验。
    </p>
    <div class="toolbar">
      <EntityPicker
        :model-value="id"
        :selected-rows="selectedCourse"
        kind="courses"
        :course-statuses="[1, 3]"
        label="选择配置课程"
        :disabled="task.busy.value"
        @update:model-value="load(String($event))"
      /><el-button @click="addChapter" :disabled="!id || task.busy.value">新增章</el-button>
      <el-button v-if="id" @click="baseOpen = true" :disabled="task.busy.value">编辑课程资料</el-button>
      <el-button v-if="id" type="primary" @click="showPublication" :disabled="dirty || task.busy.value">
        预览与上架检查
      </el-button>
    </div>
    <Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value" />
    <template v-if="id">
      <h3 id="course-chapters" tabindex="-1">课程目录</h3>
      <article
        v-for="(chapter, index) in chapters"
        :key="chapter.id || index"
        class="card chapter-editor"
      >
        <div class="toolbar">
          <el-input
            v-model="chapter.name"
            placeholder="章名称"
            :disabled="!editable(chapter)"
          /><el-button @click="chapters.splice(index, 1)" :disabled="!editable(chapter)"
            >删除章</el-button
          ><small v-if="!editable(chapter)" class="muted">已发布章节</small>
        </div>
        <div
          v-for="(item, sectionIndex) in chapter.sections"
          :key="item.id || sectionIndex"
          class="toolbar"
        >
          <el-input
            v-model="item.name"
            placeholder="小节 / 考试名称"
            :disabled="!editable(item)"
          /><el-select v-model="item.type" style="width: 130px" :disabled="!editable(item)"
            ><el-option label="视频小节" :value="2" /><el-option
              label="考试"
              :value="3" /></el-select
          ><el-button @click="chapter.sections.splice(sectionIndex, 1)" :disabled="!editable(item)"
            >移除</el-button
          >
        </div>
        <el-button @click="chapter.sections.push({ name: '', type: 2, canUpdate: true })"
          >新增小节 / 考试</el-button
        >
      </article>
      <el-empty v-if="!chapters.length" description="尚未创建课程目录，请先新增章" /><el-button
        type="primary"
        @click="save"
        :disabled="
          !chapters.length ||
          chapters.some(
            (c) =>
              !c.name.trim() || !c.sections?.length || c.sections.some((s: Row) => !s.name.trim()),
          ) ||
          task.busy.value
        "
        >保存章节</el-button
      >
      <el-divider />
      <h3 id="course-media" tabindex="-1">关联视频</h3>
      <p class="muted">
        请先保存目录，再选择已登记的视频。名称与时长来自真实媒资记录，无须手工复制编号。
      </p>
      <el-form label-position="top"
        ><div class="grid">
          <el-form-item label="视频小节"
            ><el-select v-model="media.cataId" aria-label="选择视频小节" @change="selectVideo"
              ><el-option
                v-for="s in videos"
                :key="s.id"
                :value="s.id"
                :label="s.label" /></el-select></el-form-item
          ><el-form-item label="课程视频"
            ><EntityPicker
              v-model="media.mediaId"
              kind="media"
              label="选择课程视频"
              :selected-rows="mediaRows"
              :disabled="!media.cataId || task.busy.value"
              @change="selectMedia" /></el-form-item
          ><el-form-item label="允许试看"><el-switch v-model="media.trailer" /></el-form-item>
        </div>
        <p v-if="media.mediaId" class="muted">
          {{ media.videoName }} · {{ media.mediaDuration }} 秒
        </p>
        <el-button
          @click="saveMedia"
          :disabled="
            !media.cataId || !media.mediaId || !media.mediaDuration || dirty || task.busy.value
          "
          >保存视频关联</el-button
        ></el-form
      >
      <el-divider />
      <h3 id="course-questions" tabindex="-1">关联题库</h3>
      <div class="toolbar">
        <el-select
          v-model="examSection"
          aria-label="选择关联考试小节"
          placeholder="选择考试小节"
          @change="selectExam"
          ><el-option v-for="s in exams" :key="s.id" :value="String(s.id)" :label="s.label"
        /></el-select>
      </div>
      <EntityPicker
        v-model="questionIds"
        kind="questions"
        label="选择关联题目"
        multiple
        :selected-rows="questionRows"
        :disabled="!examSection || task.busy.value"
        @change="questionRows = $event"
      /><el-button
        @click="saveQuestions"
        :disabled="!examSection || !questionIds.length || dirty || task.busy.value"
        >关联题目</el-button
      >
      <el-divider />
      <h3 id="course-teachers" tabindex="-1">授课教师</h3>
      <EntityPicker
        v-model="teachers"
        kind="teachers"
        label="选择授课教师"
        multiple
        :limit="5"
        :selected-rows="teacherRows"
        :disabled="task.busy.value"
      /><el-button @click="saveTeachers" :disabled="!teachers.length || dirty || task.busy.value"
        >保存授课教师</el-button
      >
    </template>
    <el-dialog v-model="baseOpen" title="编辑课程资料" width="min(96vw, 56rem)" destroy-on-close>
      <Status :error="formsTask.error.value" />
      <CourseForm
        v-if="selectedCourse[0]"
        :model-value="selectedCourse[0]"
        :busy="formsTask.busy.value"
        @save="saveBase"
        @cancel="baseOpen = false"
      />
    </el-dialog>
    <el-dialog
      v-model="previewOpen"
      title="学生视角预览与上架检查"
      width="min(96vw, 56rem)"
      destroy-on-close
      @closed="trialPlayer = undefined; focusIssue()"
    >
      <Status :busy="publicationTask.busy.value" :error="publicationTask.error.value" />
      <template v-if="publication">
        <section aria-label="课程发布检查">
          <h3>上架检查</h3>
          <p v-if="!publication.issues.length">所有检查项均已通过。</p>
          <ul v-else class="publication-issues">
            <li v-for="issue in publication.issues" :key="issue.message">
              <span>{{ issue.blocking ? '需处理' : '提示' }} · {{ issue.message }}</span>
              <el-button link @click="goToIssue(issue)">前往处理</el-button>
            </li>
          </ul>
        </section>
        <section v-if="publication.trials.length" aria-label="小节试看片段">
          <h3>小节试看片段</h3>
          <div class="toolbar">
            <el-button :disabled="publicationTask.busy.value || !preparableTrials.length" @click="prepareTrials(true)">
              生成整门课程的试看
            </el-button>
            <el-button :disabled="publicationTask.busy.value || !preparationTargets.length" @click="prepareTrials(false)">
              生成所选小节
            </el-button>
          </div>
          <el-checkbox-group v-model="selectedTrials" :disabled="publicationTask.busy.value" aria-label="选择草稿小节">
           <ul class="publication-issues">
            <li v-for="trial in publication.trials" :id="'course-trial-' + trial.sectionId" :key="trial.sectionId" tabindex="-1">
              <el-checkbox v-if="canPrepareTrial(trial)" :value="trial.sectionId" :aria-label="'选择' + trial.sectionName" />
              <strong>{{ trial.sectionName }}</strong> · {{ trialLabels[trial.status.state] }}
              <el-button
                v-if="trial.status.state === 'MISSING' || trial.status.state === 'READY' || trial.status.state === 'FAILED'"
                link
                :disabled="publicationTask.busy.value"
                @click="updateTrial(trial)"
              >{{ trial.status.state === 'READY' ? '预览片段' : trial.status.state === 'FAILED' ? '重试生成' : '生成试看片段' }}</el-button>
              <p v-if="trial.status.state === 'FAILED'">
                请检查源视频或处理工具后重试。失败标识 {{ trial.status.errorCode }}
              </p>
              <p v-else-if="trial.status.state === 'UNAVAILABLE' || trial.status.state === 'REMOVED'">
                请在关联视频中选择已就绪的媒资，或关闭本小节试看后保存。
              </p>
              <video
                v-if="trialPlayer?.sectionId === trial.sectionId"
                :src="trialPlayer.url"
                :aria-label="'试看片段预览 · ' + trial.sectionName"
                controls
                playsinline
              />
            </li>
           </ul>
          </el-checkbox-group>
          <el-button :disabled="publicationTask.busy.value" @click="refreshPublication">刷新试看状态</el-button>
        </section>
        <section class="card publication-preview" aria-label="学生视角预览">
          <h3>{{ publication.preview.name }}</h3>
          <img v-if="publication.preview.coverUrl" :src="publication.preview.coverUrl" alt="课程封面预览" />
          <p>{{ plainTextDescription(publication.preview.introduce) }}</p>
          <p>{{ Number(publication.preview.price) ? money(publication.preview.price) : '免费课程' }}</p>
          <CourseFacts :course="publication.preview" />
          <p v-if="publication.preview.usePeople">适用人群 · {{ publication.preview.usePeople }}</p>
          <h4>课程目录</h4>
          <div v-for="chapter in publication.preview.chapters" :key="chapter.id">
            <strong>{{ chapter.name }}</strong>
            <ul>
              <li v-for="item in chapter.sections" :key="item.id">
                {{ item.name }} · {{ Number(item.type) === 3 ? '考试' : item.trailer ? '可试看' : '报名后学习' }}
              </li>
            </ul>
          </div>
          <details v-if="publication.preview.handout">
            <summary>报名后可见的随堂讲义</summary>
            <p>{{ publication.preview.handout }}</p>
          </details>
        </section>
      </template>
      <template #footer>
        <el-button @click="previewOpen = false">关闭</el-button>
        <el-button
          v-if="publicationTask.error.value"
          :disabled="publicationTask.busy.value"
          @click="refreshPublication"
        >重新检查</el-button>
        <el-button
          type="primary"
          :disabled="publicationTask.busy.value || !!publicationTask.error.value || !publication || publication.issues.some((issue) => issue.blocking)"
          @click="publish"
        >确认上架</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.publication-issues { padding-left: 1.25rem; }
.publication-issues li { margin: .35rem 0; overflow-wrap: anywhere; }
.publication-issues video { display: block; width: min(100%, 28rem); margin-top: .5rem; }
.publication-preview { padding: 1.25rem; overflow-wrap: anywhere; }
.publication-preview img { max-width: min(100%, 15rem); height: auto; }
.publication-preview details p { white-space: pre-wrap; }
</style>
