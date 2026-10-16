import {expect,test,vi} from 'vitest';
import {collectCourseOptions,type CourseOption} from './courseOptions';

const courses=(from:number,count:number):CourseOption[]=>Array.from({length:count},(_,i)=>({courseId:String(8000000000000000000n+BigInt(from+i)),courseName:'Course '+(from+i)}));

test('101st named course remains available after the first 100 choices',async()=>{
 const first=courses(1,100),last=courses(101,1);
 const fetchPage=vi.fn().mockResolvedValueOnce({list:first,total:'101'}).mockResolvedValueOnce({list:last,total:'101'});
 const result=await collectCourseOptions(fetchPage);
 expect(fetchPage.mock.calls.map(call=>call.slice(0,2))).toEqual([[1,100],[2,100]]);
 expect(result).toHaveLength(101);expect(result[100]).toEqual(last[0]);
});

test('overlapping pages retain exact long IDs and a single choice per course',async()=>{
 const first=courses(1,100),later=[...courses(100,1),...courses(101,2)];
 const fetchPage=vi.fn().mockResolvedValueOnce({list:first,total:102}).mockResolvedValueOnce({list:later,total:102});
 const result=await collectCourseOptions(fetchPage);
 expect(result).toHaveLength(102);expect(result.filter(c=>c.courseId===first[99]!.courseId)).toHaveLength(1);
 expect(result[101]).toEqual(later[2]);
});

test('a later page failure rejects the entire load instead of returning partial choices',async()=>{
 const failure=new Error('second page unavailable');
 const fetchPage=vi.fn().mockResolvedValueOnce({list:courses(1,100),total:101}).mockRejectedValueOnce(failure);
 await expect(collectCourseOptions(fetchPage)).rejects.toBe(failure);
 expect(fetchPage).toHaveBeenCalledTimes(2);
});

test('cancellation after the first response returns no partial choices or second request',async()=>{
 const controller=new AbortController();
 const fetchPage=vi.fn().mockImplementation(async(_pageNo:number,_pageSize:number,signal?:AbortSignal)=>{
  expect(signal).toBe(controller.signal);controller.abort();return {list:courses(1,100),total:101};
 });
 await expect(collectCourseOptions(fetchPage,controller.signal)).rejects.toMatchObject({name:'AbortError'});
 expect(fetchPage).toHaveBeenCalledTimes(1);
});

test('an unexpectedly empty later page fails visibly instead of claiming a complete list',async()=>{
 const fetchPage=vi.fn().mockResolvedValueOnce({list:courses(1,100),total:101}).mockResolvedValueOnce({list:[],total:101});
 await expect(collectCourseOptions(fetchPage)).rejects.toThrow('课程选项加载不完整');
});
