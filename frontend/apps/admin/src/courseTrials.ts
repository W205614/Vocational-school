import { get, write } from '../../../packages/shared/src/client';

export const trialLabels = {
  UNAVAILABLE: '源视频不可用',
  MISSING: '待生成',
  PENDING: '等待生成',
  PROCESSING: '正在生成',
  READY: '已就绪',
  FAILED: '生成失败',
  REMOVED: '片段已移除',
};

export type Trial = {
  sectionId: string;
  sectionName: string;
  status: { state: keyof typeof trialLabels; taskId?: string; errorCode?: string; version?: string };
};

const path = '/admin/media/trial-batches';
export const canPrepareTrial = (trial: Trial) => ['MISSING', 'FAILED'].includes(trial.status.state);
export const isPreparingTrial = (trial: Trial) => ['PENDING', 'PROCESSING'].includes(trial.status.state);

export function publishedTrialStatuses(courseId: string) {
  return get<Trial[]>(`${path}?courseId=${courseId}&draft=false`);
}

export function prepareCourseTrials(courseId: string, draft: boolean, sectionIds: string[] = []) {
  return write<Trial[]>(path, 'POST', { courseId, draft, sectionIds });
}
