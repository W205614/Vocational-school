import {expect,test,vi} from 'vitest';
import {learningProgress,type ProgressSnapshot} from './learningProgress';
const snapshot=(moment:number,sectionId='section'):ProgressSnapshot=>({owner:'student',courseId:'course',lessonId:'lesson',sectionId,moment,ended:false});
function deferred(){let resolve!:()=>void;const promise=new Promise<void>(done=>{resolve=done;});return {promise,resolve};}
test('forced tails are saved and pending progress coalesces without parallel writes',async()=>{
 const gate=deferred();let active=0,max=0;const moments:number[]=[];
 const queue=learningProgress(()=> 'student',async value=>{active++;max=Math.max(max,active);moments.push(value.moment);if(moments.length===1)await gate.promise;active--;});
 const first=queue.record(snapshot(4),true);const second=queue.record(snapshot(7),true);const third=queue.record(snapshot(9),true);gate.resolve();
 expect(await Promise.all([first,second,third])).toEqual([true,true,true]);expect(moments).toEqual([4,9]);expect(max).toBe(1);
});
test('a failed ended write is retained for explicit retry',async()=>{
 const save=vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined);const queue=learningProgress(()=> 'student',save);
 expect(await queue.record({...snapshot(2),ended:true},true)).toBe(false);expect(queue.error.value).toContain('offline');
 expect(await queue.retry()).toBe(true);expect(save).toHaveBeenCalledTimes(2);expect(save.mock.calls[1]![0]).toMatchObject({moment:2,ended:true});expect(queue.error.value).toBe('');
});
test('an account change aborts the old request and discards old pending snapshots',async()=>{
 const gate=deferred();let owner='student';const signals:AbortSignal[]=[];const save=vi.fn(async(_value:ProgressSnapshot,signal:AbortSignal)=>{signals.push(signal);await gate.promise;});
 const queue=learningProgress(()=>owner,save);const first=queue.record(snapshot(17));void queue.record(snapshot(4,'other'),true);
 owner='another';queue.reset();expect(signals[0]!.aborted).toBe(true);gate.resolve();await first;await queue.retry();expect(save).toHaveBeenCalledTimes(1);
 expect(await queue.record(snapshot(20),true)).toBe(false);
});
