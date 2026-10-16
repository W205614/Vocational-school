import {request} from './client';

export interface CourseOption {courseId:string|number;courseName?:string;[key:string]:unknown}
export interface CourseOptionPage<T> {list:T[];total:number|string}

/** Load complete named choices without changing the server's page-size limit. */
export async function collectCourseOptions<T extends {courseId:string|number}>(
 fetchPage:(pageNo:number,pageSize:number,signal?:AbortSignal)=>Promise<CourseOptionPage<T>>,
 signal?:AbortSignal
):Promise<T[]>{
 const choices=new Map<string,T>();let received=0,pageNo=1;
 while(true){
  signal?.throwIfAborted();
  const page=await fetchPage(pageNo,100,signal);
  signal?.throwIfAborted();
  const total=Number(page.total);
  if(!Number.isSafeInteger(total)||total<0||!Array.isArray(page.list))throw new Error('课程选项加载不完整，请重试');
  if(!page.list.length&&received<total)throw new Error('课程选项加载不完整，请重试');
  for(const course of page.list)choices.set(String(course.courseId),course);
  received+=page.list.length;
  if(received>=total)return [...choices.values()];
  pageNo++;
 }
}

export function enrolledCourseOptions(signal?:AbortSignal){
 return collectCourseOptions((pageNo,pageSize,signal)=>request<CourseOptionPage<CourseOption>>(
  '/services/learning/lessons/page?pageNo='+pageNo+'&pageSize='+pageSize,{signal}
 ),signal);
}
