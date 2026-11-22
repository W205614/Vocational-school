<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get, write, newKey } from '../../../../packages/shared/src/client';
import { query, useTask, type Row } from '../../../../packages/shared/src/ui';
import CourseFacts from '../../../../packages/shared/src/CourseFacts.vue';
import { formatStudyMinutes, studyDifficulty } from '../../../../packages/shared/src/courseStudyFacts';
import Status from '../../../../packages/shared/src/Status.vue';

const path = '/admin/course/study-information';
const labels = {
  OUTCOMES: '学习成果',
  PREREQUISITES: '先修要求',
  DIFFICULTY: '难度',
  ESTIMATED_TIME: '预计学习时长',
  PRACTICE: '实操内容',
};
type MissingField = keyof typeof labels;
type Level = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
type Information = {
  learningOutcomes?: string;
  prerequisites?: string;
  difficulty?: Level;
  estimatedStudyMinutes?: number;
  practiceOverview?: string;
};
type Course = Information & {
  id: string;
  name: string;
  status: 2 | 4;
  videoEstimateMinutes?: string;
  missingFields: MissingField[];
  counts?: { enrolled: string; started: string };
};
type FieldChange = {
  field: MissingField;
  previousValue?: string;
  newValue?: string;
  operatorId: string;
  operatorName?: string;
  changedAt: string;
};

const listTask = useTask();
const editTask = useTask();
const historyTask = useTask();
const keyword = ref('');
const missing = ref<MissingField | 'ANY' | 'ALL'>('ANY');
const sort = ref('NEWEST');
const page = ref(1);
const pageSize = 20;
const rows = ref<Course[]>([]);
const total = ref(0);
const selected = ref<Course>();
const dialog = ref(false);
const draft = ref<Information>({});
const preview = ref<Row>();
const historyOpen = ref(false);
const history = ref<FieldChange[]>([]);
const historyPage = ref(1);
const historyTotal = ref(0);

function choose(course: Course) {
  selected.value = course;
  draft.value = {
    learningOutcomes: course.learningOutcomes || '',
    prerequisites: course.prerequisites || '',
    difficulty: course.missingFields.includes('DIFFICULTY') ? undefined : course.difficulty,
    estimatedStudyMinutes: course.estimatedStudyMinutes || undefined,
    practiceOverview: course.practiceOverview || '',
  };
}

const changes = computed(() => {
  const current = selected.value;
  if (!current) return {};
  const fields = draft.value;
  const outcomes = fields.learningOutcomes?.trim();
  const prerequisites = fields.prerequisites?.trim();
  const practice = fields.practiceOverview?.trim();
  return {
    ...(outcomes && outcomes !== current.learningOutcomes?.trim() ? { learningOutcomes: outcomes } : {}),
    ...(prerequisites && prerequisites !== current.prerequisites?.trim() ? { prerequisites } : {}),
    ...(fields.difficulty && fields.difficulty !== current.difficulty ? { difficulty: fields.difficulty } : {}),
    ...(fields.estimatedStudyMinutes && fields.estimatedStudyMinutes !== current.estimatedStudyMinutes
      ? { estimatedStudyMinutes: fields.estimatedStudyMinutes } : {}),
    ...(practice && practice !== current.practiceOverview?.trim() ? { practiceOverview: practice } : {}),
  };
});

async function load(reset = false) {
  await listTask.run(async () => {
    if (reset) page.value = 1;
    const read = () => get<{ list: Course[]; total: string }>(
      path + '?' + query({ pageNo: page.value, pageSize, keyword: keyword.value.trim(),
        missing: missing.value, sort: sort.value }),
    );
    let result = await read();
    const lastPage = Math.max(1, Math.ceil(Number(result.total) / pageSize));
    if (page.value > lastPage) {
      page.value = lastPage;
      result = await read();
    }
    rows.value = result.list;
    total.value = Number(result.total);
  });
}

async function edit(course: Course) {
  await listTask.run(async () => {
    choose(await get<Course>(path + '/' + course.id));
    preview.value = undefined;
    historyOpen.value = false;
    history.value = [];
    editTask.error.value = '';
    editTask.message.value = '';
    dialog.value = true;
  });
}

async function save() {
  const course = selected.value;
  if (!course || !Object.keys(changes.value).length) return;
  const result = await editTask.run(async () => {
    const updated = await write<Course>(
      path + '/' + course.id, 'PATCH', { status: course.status, ...changes.value }, newKey(),
    );
    preview.value = undefined;
    choose(updated);
    editTask.message.value = '已更新学生可见的选课资料';
    return updated;
  });
  if (result) {
    await load();
    if (historyOpen.value) await loadChanges(true);
  }
}

async function checkPublicCourse() {
  const course = selected.value;
  if (!course) return;
  await editTask.run(async () => {
    preview.value = undefined;
    preview.value = await get<Row>('/public/courses/' + course.id);
  });
}

async function loadChanges(reset = false) {
  const course = selected.value;
  if (!course) return;
  if (reset) historyPage.value = 1;
  historyOpen.value = true;
  await historyTask.run(async () => {
    const result = await get<{ list: FieldChange[]; total: string }>(
      path + '/' + course.id + '/changes?' + query({ pageNo: historyPage.value, pageSize }),
    );
    if (course.id !== selected.value?.id) return;
    history.value = result.list;
    historyTotal.value = Number(result.total);
  });
}

function changeValue(field: MissingField, value?: string) {
  if (!value?.trim()) return '未填写';
  if (field === 'DIFFICULTY') return studyDifficulty[value] || value;
  if (field === 'ESTIMATED_TIME') return formatStudyMinutes(Number(value));
  return value;
}

onMounted(load);
</script>

<template>
  <section class="card">
    <h1>选课资料盘点</h1>
    <p class="muted">盘点已上架和已完结课程的学生可见资料。补录后，学生在课程详情页即可看到更新。</p>
    <form class="toolbar study-filters" @submit.prevent="load(true)">
      <el-input
        v-model="keyword" aria-label="课程名称" placeholder="搜索课程名称" maxlength="80"
        :disabled="listTask.busy.value" clearable @clear="load(true)"
      />
      <el-select
        v-model="missing" aria-label="缺失字段" :disabled="listTask.busy.value" @change="load(true)"
      >
        <el-option label="缺任意一项" value="ANY" />
        <el-option label="全部公开课程" value="ALL" />
        <el-option v-for="(label, value) in labels" :key="value" :label="'缺' + label" :value="value" />
      </el-select>
      <el-select v-model="sort" aria-label="盘点排序" :disabled="listTask.busy.value" @change="load(true)">
        <el-option label="课程编号倒序" value="NEWEST" />
        <el-option label="有效报名人数最多" value="ENROLLED" />
        <el-option label="已开始学习人数最多" value="STARTED" />
      </el-select>
      <el-button native-type="submit" :disabled="listTask.busy.value">查询</el-button>
    </form>
    <Status :busy="listTask.busy.value" :error="listTask.error.value" />
    <el-table :data="rows" row-key="id" empty-text="没有符合条件的公开课程">
      <el-table-column prop="name" label="课程" min-width="190" />
      <el-table-column label="课程状态" width="105">
        <template #default="{ row }">{{ row.status === 2 ? '已上架' : '已完结' }}</template>
      </el-table-column>
      <el-table-column label="缺失字段" min-width="225">
        <template #default="{ row }">
          <div v-if="row.missingFields.length" class="missing-fields">
            <el-tag v-for="field in (row.missingFields as MissingField[])" :key="field" type="warning">
              {{ labels[field] }}
            </el-tag>
          </div>
          <span v-else>资料完整</span>
        </template>
      </el-table-column>
      <el-table-column label="预计学习时长" min-width="155">
        <template #default="{ row }">
          <span v-if="row.estimatedStudyMinutes">{{ row.estimatedStudyMinutes }} 分钟（人工填写）</span>
          <span v-else-if="row.videoEstimateMinutes">{{ row.videoEstimateMinutes }} 分钟（按视频时长估算）</span>
          <span v-else>待补录</span>
        </template>
      </el-table-column>
      <el-table-column label="有效报名" width="105">
        <template #default="{ row }">{{ row.counts.enrolled }} 人</template>
      </el-table-column>
      <el-table-column label="已开始学习" width="115">
        <template #default="{ row }">{{ row.counts.started }} 人</template>
      </el-table-column>
      <el-table-column label="操作" width="115">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="listTask.busy.value" @click="edit(row)">补录资料</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page" :page-size="pageSize" :total="total" :disabled="listTask.busy.value"
      layout="total, prev, pager, next" @current-change="load()"
    />
    <el-dialog
      v-model="dialog" title="补录选课资料" width="min(660px, calc(100vw - 2rem))"
      :close-on-click-modal="!editTask.busy.value" :close-on-press-escape="!editTask.busy.value"
      :show-close="!editTask.busy.value"
    >
      <template v-if="selected">
        <p><strong>{{ selected.name }}</strong></p>
        <p class="muted">只提交本次填写的改动。无先修要求或实操时可填写「无」。</p>
        <Status :busy="editTask.busy.value" :error="editTask.error.value" :message="editTask.message.value" />
        <el-form label-position="top" :disabled="editTask.busy.value">
          <el-form-item label="学习成果">
            <el-input v-model="draft.learningOutcomes" type="textarea" :rows="2" maxlength="500" aria-label="学习成果" />
          </el-form-item>
          <el-form-item label="先修要求">
            <el-input v-model="draft.prerequisites" type="textarea" :rows="2" maxlength="500" aria-label="先修要求" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="draft.difficulty" aria-label="课程难度">
              <el-option label="入门" value="BEGINNER" />
              <el-option label="进阶" value="INTERMEDIATE" />
              <el-option label="高阶" value="ADVANCED" />
            </el-select>
          </el-form-item>
          <el-form-item label="预计学习时长（分钟）">
            <el-input-number
              v-model="draft.estimatedStudyMinutes" :min="1" :max="100000" :precision="0" :controls="false"
              aria-label="预计学习时长（分钟）"
            />
            <div v-if="selected.videoEstimateMinutes" class="muted">当前视频约 {{ selected.videoEstimateMinutes }} 分钟。</div>
            <div v-else class="muted">可结合课程内容和练习时间填写。</div>
          </el-form-item>
          <el-form-item label="实操内容">
            <el-input v-model="draft.practiceOverview" type="textarea" :rows="2" maxlength="500" aria-label="实操内容" />
          </el-form-item>
        </el-form>
        <section v-if="preview" aria-label="学生课程详情">
          <h3>学生课程详情</h3>
          <CourseFacts :course="preview" />
        </section>
        <section v-if="historyOpen" aria-label="字段变更记录">
          <h3>字段变更记录</h3>
          <Status :busy="historyTask.busy.value" :error="historyTask.error.value" />
          <el-button v-if="historyTask.error.value" text @click="loadChanges()">重试</el-button>
          <p v-else-if="!historyTask.busy.value && !history.length" class="muted">暂无已记录的变更</p>
          <dl v-for="(entry, index) in history" :key="index" class="field-change">
            <dt>{{ labels[entry.field] }} · {{ entry.changedAt.replace('T', ' ') }}</dt>
            <dd>操作者 {{ entry.operatorName || `账号 ${entry.operatorId}` }}</dd>
            <dd>原值 {{ changeValue(entry.field, entry.previousValue) }}</dd>
            <dd>新值 {{ changeValue(entry.field, entry.newValue) }}</dd>
          </dl>
          <el-pagination v-if="historyTotal > pageSize" v-model:current-page="historyPage"
            :page-size="pageSize" :total="historyTotal" :disabled="historyTask.busy.value"
            layout="prev, pager, next" @current-change="loadChanges()" />
        </section>
      </template>
      <template #footer>
      <el-button :disabled="historyTask.busy.value" @click="loadChanges(true)">查看字段变更</el-button>
        <el-button :disabled="editTask.busy.value" @click="checkPublicCourse">查看学生页资料</el-button>
        <el-button type="primary" :disabled="editTask.busy.value || !Object.keys(changes).length" @click="save">
          保存补录
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.study-filters .el-input { width: min(18rem, 100%); }
.study-filters .el-select { width: min(12rem, 100%); }
.missing-fields { display: flex; flex-wrap: wrap; gap: .25rem; }
.field-change { border-bottom: 1px solid var(--el-border-color); padding-block: .5rem; }
.field-change dd { margin: .25rem 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
