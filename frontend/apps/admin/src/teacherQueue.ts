import { get } from '../../../packages/shared/src/client';
import { query, type Row } from '../../../packages/shared/src/ui';

const paths = {
  questions: '/learning-hub/teaching/questions',
  grading: '/teacher/exam-attempts/page',
  practice: '/teacher/learning-hub/practice/pending',
};

export type TeacherQueue = keyof typeof paths;

export function teacherQueuePath(queue: TeacherQueue, filters: {
  pageNo: number;
  pageSize: number;
  courseId?: string;
  studentId?: string;
  pendingOnly?: boolean;
}) {
  return paths[queue] + '?' + query(filters);
}

export async function nextTeacherTask(queue: TeacherQueue, courseId: string, studentId?: string) {
  const data = await get<{ list: Row[] }>(teacherQueuePath(queue, {
    courseId, studentId, pageNo: 1, pageSize: 1,
    pendingOnly: queue === 'questions' ? true : undefined,
  }));
  return data.list[0];
}
