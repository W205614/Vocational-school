<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { get, write } from '../../../../packages/shared/src/client';
import { query, type Row, useTask } from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';

type Queue = 'refunds' | 'entitlements' | 'media';
type Cursor = { paidAt: string; detailId: string };
const tabs: { name: Queue; label: string }[] = [
  { name: 'refunds', label: '待审退款' },
  { name: 'entitlements', label: '待开通权益' },
  { name: 'media', label: '媒资处理失败' },
];
const active = ref<Queue>('refunds'), rows = ref<Row[]>([]), total = ref(0), page = ref(1);
const cursor = ref<Cursor>(), nextCursor = ref<Cursor>(), previous = ref<(Cursor | undefined)[]>([]);
const busy = ref(false), error = ref(''), action = useTask();
let revision = 0;

async function load() {
  const current = ++revision, selected = active.value;
  busy.value = true;
  error.value = '';
  const path = {
    refunds: '/admin/trade/refund-apply/page?' + query({ pageNo: page.value, pageSize: 20, refundStatus: 1, sortBy: 'create_time', isAsc: true }),
    entitlements: '/admin/dashboard/pending-entitlements?' + query({ afterPaidAt: cursor.value?.paidAt, afterDetailId: cursor.value?.detailId }),
    media: '/admin/media/processing-failures?' + query({ pageNo: page.value, pageSize: 20 }),
  }[selected];
  try {
    const values = await get<Row>(path);
    if (current !== revision) return;
    rows.value = values.list;
    total.value = Number(values.total || 0);
    nextCursor.value = values.next;
    if (selected !== 'entitlements' && page.value > 1 && rows.value.length === 0) {
      page.value = Math.max(1, Math.ceil(total.value / 20));
      void load();
    }
  } catch (cause) {
    if (current === revision) error.value = cause instanceof Error ? cause.message : '待办加载失败';
  } finally {
    if (current === revision) busy.value = false;
  }
}
function reset() {
  page.value = 1;
  cursor.value = undefined;
  nextCursor.value = undefined;
  previous.value = [];
  rows.value = [];
  total.value = 0;
  action.error.value = '';
  action.message.value = '';
  void load();
}
function readLater() {
  if (!nextCursor.value) return;
  previous.value.push(cursor.value);
  cursor.value = nextCursor.value;
  void load();
}
function readEarlier() {
  cursor.value = previous.value.pop();
  void load();
}
function age(value: unknown, since?: string) {
  const started = since ? new Date(since.replace(' ', 'T') + '+08:00').valueOf() : NaN;
  const seconds = Number(value ?? Math.floor((Date.now() - started) / 1000));
  if (!Number.isFinite(seconds)) return '等待时间未知';
  if (seconds >= 86400) return Math.floor(seconds / 86400) + ' 天 ' + Math.floor((seconds % 86400) / 3600) + ' 小时';
  if (seconds >= 3600) return Math.floor(seconds / 3600) + ' 小时 ' + Math.floor((seconds % 3600) / 60) + ' 分钟';
  return seconds >= 60 ? Math.floor(seconds / 60) + ' 分钟' : '不到 1 分钟';
}
async function retryMedia(row: Row) {
  const changed = await action.run(async () => {
    if (row.kind === 'TRIAL') {
      await write('/admin/media/media-trials/' + row.taskId + '/retry', 'POST', { version: row.version });
    } else {
      await write('/admin/media-upload-tasks/' + row.taskId + '/retry', 'POST');
    }
    return true;
  });
  if (changed && active.value === 'media') {
    action.message.value = '重新处理已提交';
    await load();
  }
}
watch(active, reset);
onMounted(load);
</script>

<template>
  <section class="card queue" aria-label="运营待办">
    <div class="section-heading">
      <h2>运营待办</h2>
      <el-button :disabled="busy || action.busy.value" @click="reset">刷新待办</el-button>
    </div>
    <el-tabs v-model="active" aria-label="待办类型">
      <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name" :label="tab.label" />
    </el-tabs>
    <Status :busy="busy || action.busy.value" :error="error || action.error.value" :message="action.message.value" />
    <template v-if="!error">
      <el-empty v-if="!busy && !rows.length"
        :description="active === 'entitlements' && nextCursor ? '本批已核对的订单均已开通，可继续查询' : '暂无此类待办'" />
      <el-table v-else-if="rows.length" :data="rows">
        <el-table-column label="业务对象" min-width="200">
          <template #default="{ row }">
            <template v-if="active === 'refunds'">
              <strong>退款单 {{ row.id }}</strong><br />订单 {{ row.orderId }}
            </template>
            <template v-else-if="active === 'entitlements'">
              <strong>{{ row.courseName }}</strong><br />订单 {{ row.orderId }}
            </template>
            <template v-else>
              <strong>{{ row.kind === 'TRIAL' ? '试看媒资 ' + row.subject : row.subject }}</strong><br />{{ row.errorCode }}
            </template>
          </template>
        </el-table-column>
        <el-table-column label="等待时间" min-width="105">
          <template #default="{ row }">{{ age(row.waitSeconds, row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="145">
          <template #default>
            {{ active === 'refunds' ? '待审核' : active === 'entitlements' ? '已付款，等待开通' : '处理失败' }}
          </template>
        </el-table-column>
        <el-table-column label="处理" min-width="220">
          <template #default="{ row }">
            <RouterLink v-if="active === 'refunds'" :to="{ path: '/refunds', query: { id: row.id } }">
              审核此退款 →
            </RouterLink>
            <div v-else-if="active === 'entitlements'" class="queue-actions">
              <RouterLink :to="{ path: '/orders', query: { id: row.detailId } }">查看订单条目 →</RouterLink>
              <RouterLink :to="{ path: '/reliability', query: { identifier: 'order:' + row.orderId + ':paid' } }">
                查询支付事件 →
              </RouterLink>
            </div>
            <div v-else class="queue-actions">
              <el-button v-if="row.canRetry" text :disabled="busy || action.busy.value" @click="retryMedia(row)">
                重新处理
              </el-button>
              <RouterLink to="/media">打开媒资管理 →</RouterLink>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>
    <div v-if="active === 'entitlements' && (previous.length || nextCursor)" class="toolbar queue-paging">
      <el-button :disabled="busy || !previous.length" @click="readEarlier">上一批</el-button>
      <el-button :disabled="busy || !nextCursor" @click="readLater">继续查询</el-button>
    </div>
    <el-pagination v-else-if="active !== 'entitlements' && total > 20"
      v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" @current-change="load" />
  </section>
</template>

<style scoped>
.queue {
  margin-block: 1rem;
}
.queue-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  align-items: center;
}
.queue-actions .el-button {
  padding-inline: 0;
}
.queue-paging,
.el-pagination {
  margin-top: 0.75rem;
}
</style>
