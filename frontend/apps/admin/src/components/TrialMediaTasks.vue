<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { get, write } from '../../../../packages/shared/src/client';
import { useTask, type Row } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
const task = useTask(),
  rows = ref<Row[]>([]),
  page = ref(1),
  total = ref(0);
const labels: Record<string, string> = {
  PENDING: '等待生成',
  PROCESSING: '处理中',
  READY: '已就绪',
  FAILED: '失败',
  REMOVED: '源媒资已删除',
};
async function load() {
  await task.run(async () => {
    const result = await get<Row>('/admin/media/media-trials?page=' + page.value + '&size=20');
    rows.value = result.list;
    total.value = Number(result.total);
  });
}
async function retry(row: Row) {
  const result = await task.run(async () => {
    await write('/admin/media/media-trials/' + row.id + '/retry', 'POST', { version: row.version });
    return true;
  });
  if (result) await load();
}
onMounted(load);
</script>
<template>
  <section aria-label="试看片段任务">
    <h3>试看片段任务</h3>
    <p>未报名用户首次请求试看时创建独立片段。失败任务排查源视频或处理工具后可重试。</p>
    <Status :busy="task.busy.value" :error="task.error.value" />
    <el-button :disabled="task.busy.value" @click="load">刷新试看任务</el-button>
    <el-table :data="rows">
      <el-table-column prop="media_id" label="媒资编号" />
      <el-table-column prop="duration_seconds" label="试看上限（秒）" />
      <el-table-column label="状态"
        ><template #default="scope">{{ labels[scope.row.state] }}</template></el-table-column
      >
      <el-table-column prop="error_code" label="失败标识" />
      <el-table-column label="处理"
        ><template #default="scope"
          ><el-button
            v-if="scope.row.state === 'FAILED'"
            :disabled="task.busy.value"
            @click="retry(scope.row)"
            >重新生成片段</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </section>
</template>
