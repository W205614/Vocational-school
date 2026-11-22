export const studyDifficulty: Record<string, string> = {
  BEGINNER: '入门',
  INTERMEDIATE: '进阶',
  ADVANCED: '高阶',
};

export function formatStudyMinutes(minutes: number) {
  if (minutes < 60) return minutes + ' 分钟';
  const remainder = minutes % 60;
  return Math.floor(minutes / 60) + ' 小时' + (remainder ? ' ' + remainder + ' 分钟' : '');
}
