<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { get, write, downloadFile, newKey } from './client';
import { useTask, type Row } from './ui';
import { useSession } from './session';
import Status from './Status.vue';
const props = defineProps<{ requestId?: string }>();
const task = useTask(),
  session = useSession(),
  rows = ref<Row[]>([]),
  selected = ref<Row>(),
  page = ref(1),
  total = ref(0),
  password = ref('');
let downloadAbort: AbortController | undefined;
const downloading = ref(false);
const creationKeys = new Map<string, string>();
async function load() {
  await task.run(async () => {
    const result = await get<{ items: Row[]; total: string | number }>(
      '/services/user/account-privacy/tasks?page=' + page.value + '&size=20',
    );
    rows.value = result.items;
    total.value = Number(result.total);
    if (selected.value)
      selected.value = await get<Row>('/services/user/account-privacy/tasks/' + selected.value.id);
  });
}
async function create(requestId = props.requestId) {
  if (!requestId) return;
  await task.run(async () => {
    const key = creationKeys.get(requestId) || newKey();
    creationKeys.set(requestId, key);
    selected.value = await write<Row>(
      '/services/user/account-privacy/tasks',
      'POST',
      { requestId },
      key,
    );
    creationKeys.delete(requestId);
  });
  await load();
}
async function open(row: Row) {
  await task.run(
    async () => (selected.value = await get<Row>('/services/user/account-privacy/tasks/' + row.id)),
  );
}
async function act(action: 'approve' | 'retry') {
  if (!selected.value) return;
  await task.run(async () => {
    const value = selected.value!;
    await write('/services/user/account-privacy/tasks/' + value.id + '/' + action, 'POST', {
      version: value.version,
      planHash: value.plan_hash,
    });
  });
  await load();
}
async function download() {
  if (!selected.value || !password.value) return;
  await task.run(async () => {
    downloadAbort = new AbortController();
    downloading.value = true;
    try {
      const blob = await downloadFile(
        '/services/user/account-privacy/tasks/' + selected.value!.id + '/download',
        { password: password.value },
        downloadAbort.signal,
      );
      password.value = '';
      const url = URL.createObjectURL(blob),
        link = document.createElement('a');
      link.href = url;
      link.download = 'privacy-' + selected.value!.id + '.zip';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } finally {
      downloading.value = false;
      downloadAbort = undefined;
      password.value = '';
    }
  });
}
defineExpose({ create, load });
onMounted(load);
onBeforeUnmount(() => {
  password.value = '';
  downloadAbort?.abort();
});
</script>
<template>
  <section class="section">
    <h3>完整隐私处理任务</h3>
    <p>
      完整导出按业务域分页采集，并非全局同一时刻的数据库快照。文件就绪后24小时内可下载；部分域失败时不会显示完成。清理须由管理员核对影响清单并审批，保留的业务记录会单独列明。
    </p>
    <Status :busy="task.busy.value" :error="task.error.value" />
    <el-button v-if="requestId" :disabled="task.busy.value" @click="create()"
      >创建完整处理任务</el-button
    >
    <el-button :disabled="task.busy.value" @click="load">刷新任务</el-button>
    <el-table :data="rows">
      <el-table-column prop="id" label="任务编号" /><el-table-column
        prop="kind"
        label="类型"
      /><el-table-column prop="status" label="状态" />
      <el-table-column prop="error_code" label="错误标识" /><el-table-column
        prop="created_at"
        label="创建时间"
      />
      <el-table-column label="详情"
        ><template #default="scope"
          ><el-button @click="open(scope.row)">查看进度</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev,pager,next"
      @current-change="load"
    />
    <template v-if="selected">
      <p>当前任务：{{ selected.id }} · {{ selected.status }}</p>
      <template v-if="session.role === 1 && selected.delivery">
        <p>
          申请处理状态：{{ selected.delivery.status }}；记录时间：{{
            selected.delivery.reviewed_at || '尚未登记'
          }}；操作人：{{ selected.delivery.reviewed_by || '尚未登记' }}
        </p>
        <p>
          交付／清理证据引用：{{ selected.delivery.evidence_reference || '尚未登记' }}；处理依据：{{
            selected.delivery.resolution || '尚未登记'
          }}
        </p>
      </template>
      <el-table :data="selected.domains">
        <el-table-column prop="domain" label="业务域" /><el-table-column
          prop="status"
          label="状态"
        />
        <el-table-column prop="page_offset" label="已采集偏移" />
        <el-table-column v-if="selected.kind === 'CLEANUP'" label="审批影响清单"
          ><template #default="scope">
            <pre>{{ scope.row.inventory }}</pre>
          </template></el-table-column
        >
        <el-table-column label="结果／影响清单"
          ><template #default="scope">
            <pre>{{ scope.row.result }}</pre>
          </template></el-table-column
        >
      </el-table>
      <el-button
        v-if="selected.status === 'FAILED'"
        :disabled="task.busy.value"
        @click="act('retry')"
        >修复原因后继续任务</el-button
      >
      <el-popconfirm
        v-if="selected.status === 'APPROVAL_REQUIRED' && session.role === 1"
        title="确认已核对各域影响、保留说明及当前规则版本？"
        @confirm="act('approve')"
      >
        <template #reference
          ><el-button :disabled="task.busy.value" type="danger"
            >按此影响清单批准清理</el-button
          ></template
        >
      </el-popconfirm>
      <template v-if="selected.status === 'READY'">
        <p>交付包 SHA-256：{{ selected.artifact_sha256 }}；下载有效期：{{ selected.expires_at }}</p>
        <el-input
          v-model="password"
          type="password"
          autocomplete="current-password"
          aria-label="完整导出下载验证密码"
          placeholder="输入当前操作人密码"
        />
        <el-button :disabled="task.busy.value || !password" @click="download"
          >验证并下载完整导出包</el-button
        >
        <el-button v-if="downloading" @click="downloadAbort?.abort()">取消下载</el-button>
      </template>
    </template>
  </section>
</template>
