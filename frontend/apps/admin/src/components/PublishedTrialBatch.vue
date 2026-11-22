<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useTask } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
import {
  canPrepareTrial, isPreparingTrial, prepareCourseTrials, publishedTrialStatuses, trialLabels, type Trial,
} from '../courseTrials';

const props = defineProps<{ courseId: string }>();
const task = useTask();
const trials = ref<Trial[]>([]);
const selected = ref<string[]>([]);
const eligible = computed(() => trials.value.filter(canPrepareTrial));
const selection = computed(() => eligible.value.filter((trial) => selected.value.includes(trial.sectionId)));
const groups = computed(() => ({
  ready: trials.value.filter((trial) => trial.status.state === 'READY').length,
  active: trials.value.filter(isPreparingTrial).length,
  failed: trials.value.filter((trial) => trial.status.state === 'FAILED').length,
}));

async function refresh() {
  await task.run(async () => {
    trials.value = await publishedTrialStatuses(props.courseId);
  });
}

async function prepare(all: boolean) {
  await task.run(async () => {
    await prepareCourseTrials(props.courseId, false, all ? [] : selection.value.map((trial) => trial.sectionId));
    selected.value = [];
    trials.value = await publishedTrialStatuses(props.courseId);
  });
}

watch([trials, task.busy, task.error], ([items, busy, error], _, onCleanup) => {
  if (!busy && !error && items.some(isPreparingTrial)) {
    const refreshTimer = setTimeout(refresh, 1500);
    onCleanup(() => clearTimeout(refreshTimer));
  }
});
onMounted(refresh);
</script>

<template>
  <p>为已经公开的课程生成独立片段，当前课程可继续学习。</p>
  <Status :busy="task.busy.value" :error="task.error.value" />
  <div class="toolbar">
    <el-button type="primary" :disabled="task.busy.value || !eligible.length" @click="prepare(true)">
      生成整门课程的试看
    </el-button>
    <el-button :disabled="task.busy.value || !selection.length" @click="prepare(false)">生成所选小节</el-button>
    <el-button :disabled="task.busy.value" @click="refresh">刷新小节状态</el-button>
  </div>
  <p v-if="trials.length" class="muted" aria-live="polite">
    共 {{ trials.length }} 个试看小节，{{ groups.ready }} 个已就绪，{{ groups.active }} 个生成中，{{ groups.failed }} 个失败。
  </p>
  <el-checkbox-group v-if="trials.length" v-model="selected" :disabled="task.busy.value" aria-label="选择已公开小节">
    <ul>
      <li v-for="trial in trials" :key="trial.sectionId">
        <el-checkbox v-if="canPrepareTrial(trial)" :value="trial.sectionId" :aria-label="'选择' + trial.sectionName" />
        <strong>{{ trial.sectionName }}</strong> · {{ trialLabels[trial.status.state] }}
        <span v-if="trial.status.errorCode"> · {{ trial.status.errorCode }}</span>
      </li>
    </ul>
  </el-checkbox-group>
  <el-empty v-else-if="!task.busy.value && !task.error.value" description="这门课程没有已启用试看的小节" />
</template>
