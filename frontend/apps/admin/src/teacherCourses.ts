import { get } from '../../../packages/shared/src/client';
import type { Row } from '../../../packages/shared/src/ui';

export async function teacherCourses() {
  const courses: Row[] = [];
  let result: { list: Row[]; total: string | number };
  let page = 1;
  do {
    result = await get('/teacher/courses?pageNo=' + page + '&pageSize=100');
    courses.push(...result.list);
    page++;
  } while (result.list.length && courses.length < Number(result.total));
  return courses;
}
