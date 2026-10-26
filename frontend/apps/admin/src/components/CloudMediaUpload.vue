<script setup lang="ts">
import {ref,onBeforeUnmount} from 'vue';
import {get,write,newKey} from '../../../../packages/shared/src/client';
import {useTask} from '../../../../packages/shared/src/ui';
import Status from '../../../../packages/shared/src/Status.vue';
type UploadTask={id:string;state:'UPLOADING'|'PENDING'|'PROCESSING'|'READY'|'FAILED';filename:string;mediaId?:string;duration?:number;errorCode?:string;uploadUrl?:string;uploadHeaders?:Record<string,string>;uploadExpiresAt?:number};
const emit=defineEmits<{saved:[]}>(),task=useTask(),file=ref<File>(),current=ref<UploadTask>(),key=ref(newKey()),controller=new AbortController();
const labels:Record<UploadTask['state'],string>={UPLOADING:'等待上传',PENDING:'等待安全检查',PROCESSING:'扫描与转码中',READY:'已就绪，可关联课程',FAILED:'处理失败'};
function choose(event:Event){file.value=(event.target as HTMLInputElement).files?.[0];current.value=undefined;key.value=newKey();}
async function wait(){return new Promise<void>((resolve,reject)=>{const timer=setTimeout(()=>{controller.signal.removeEventListener('abort',abort);resolve();},1500);const abort=()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'));};controller.signal.addEventListener('abort',abort,{once:true});if(controller.signal.aborted)abort();});}
async function refresh(){if(!current.value)return;const value=await get<UploadTask>('/admin/media-upload-tasks/'+current.value.id);if(controller.signal.aborted)return;const firstReady=current.value.state!=='READY'&&value.state==='READY';current.value=value;if(firstReady){task.message.value='视频已完成扫描和转码，现可关联课程。';emit('saved');}}
async function poll(){for(let count=0;count<40&&!controller.signal.aborted;count++){if(!current.value||['READY','FAILED'].includes(current.value.state))return;await wait();await refresh();}if(!controller.signal.aborted&&current.value&&!['READY','FAILED'].includes(current.value.state))task.message.value='视频仍在后台处理，可以保留任务编号并稍后查询。';}
async function upload(){await task.run(async()=>{
 const selected=file.value;if(!selected)return;
 if(selected.size<1||selected.size>200*1024*1024||!/^.+\.(mp4|webm)$/i.test(selected.name))throw new Error('请选择不超过200 MiB的MP4或WebM视频。');
 if(!crypto.subtle)throw new Error('云上传需要通过HTTPS访问。');
 task.message.value='正在计算文件校验值。';
 const sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await selected.arrayBuffer())),byte=>byte.toString(16).padStart(2,'0')).join('');
 const created=await write<UploadTask>('/admin/media-upload-tasks','POST',{filename:selected.name,size:selected.size,sha256},key.value);if(controller.signal.aborted)return;current.value=created;
 if(created.state==='UPLOADING'){
  if(!created.uploadUrl||!created.uploadExpiresAt||created.uploadExpiresAt<=Date.now())throw new Error('上传授权已过期，请重新选择文件。');
  task.message.value='正在上传至私有云存储。';
  const response=await fetch(created.uploadUrl,{method:'PUT',headers:created.uploadHeaders,body:selected,credentials:'omit',referrerPolicy:'no-referrer',signal:controller.signal});
  if(!response.ok)throw new Error('云存储上传未完成，请检查网络或由管理员核对存储桶CORS配置。');
  current.value=await write<UploadTask>('/admin/media-upload-tasks/'+created.id+'/complete','POST');
 }
 task.message.value='上传完成，后台正在扫描与转码；处理完成前不能发布此视频。';await poll();
});}
async function retry(){await task.run(async()=>{if(!current.value)return;current.value=await write<UploadTask>('/admin/media-upload-tasks/'+current.value.id+'/retry','POST');await poll();});}
onBeforeUnmount(()=>controller.abort());
</script>
<template><div><p>文件先进入私有隔离区，通过校验、恶意文件扫描与转码后才会登记为可用媒资。处理期间可以离开页面。</p><div class="toolbar"><input type="file" aria-label="选择云视频" accept="video/mp4,video/webm" :disabled="task.busy.value" @change="choose"/><el-button type="primary" :disabled="!file||task.busy.value" @click="upload">上传并处理</el-button></div><Status :busy="task.busy.value" :error="task.error.value" :message="task.message.value"/><template v-if="current"><p>任务编号：<code>{{current.id}}</code></p><p>状态：{{labels[current.state]}}<template v-if="current.duration"> · {{current.duration.toFixed(2)}} 秒</template></p><p v-if="current.state==='FAILED'">本次文件没有发布。错误标识：{{current.errorCode||'MEDIA_PROCESSING_FAILED'}}。请先排查源文件或处理服务，再重试。</p><div class="toolbar"><el-button :disabled="task.busy.value" @click="task.run(refresh)">查询处理进度</el-button><el-button v-if="current.state==='FAILED'" :disabled="task.busy.value" @click="retry">重试处理</el-button></div></template></div></template>
