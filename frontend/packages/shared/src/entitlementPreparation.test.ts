import {afterEach,expect,test,vi} from 'vitest';
import {entitlementPreparation} from './entitlementPreparation';
afterEach(()=>vi.useRealTimers());

test('waits for the actual entitlement and coalesces repeated queries',async()=>{
 vi.useFakeTimers();const read=vi.fn().mockResolvedValueOnce(null).mockResolvedValue({id:'lesson'});
 const preparation=entitlementPreparation<{id:string}|null>(()=> 'student/course',read,value=>!!value?.id);
 const first=preparation.start(),again=preparation.start();expect(read).toHaveBeenCalledTimes(1);
 await vi.advanceTimersByTimeAsync(1000);expect(await first).toEqual({id:'lesson'});expect(await again).toEqual({id:'lesson'});expect(preparation.state.value).toBe('ready');preparation.reset();
});
test('stops after the deadline and permits an explicit retry without placing another order',async()=>{
 vi.useFakeTimers();const read=vi.fn().mockResolvedValue(null);const preparation=entitlementPreparation(()=> 'student/course',read,value=>!!value,3000);
 const pending=preparation.start();await vi.advanceTimersByTimeAsync(3000);expect(await pending).toBeUndefined();expect(preparation.state.value).toBe('timeout');
 read.mockResolvedValue({id:'ready'});expect(await preparation.start()).toEqual({id:'ready'});expect(preparation.state.value).toBe('ready');preparation.reset();
});
test('discarded account or course requests cannot update the new context',async()=>{
 let finish!:(value:{id:string})=>void,scope='student/course-one';let signal:AbortSignal|undefined;
 const preparation=entitlementPreparation(()=>scope,async selected=>{signal=selected;return new Promise<{id:string}>(resolve=>{finish=resolve;});},value=>!!value.id);
 const pending=preparation.start();scope='other-student/course-two';preparation.reset();finish({id:'stale'});
 expect(await pending).toBeUndefined();expect(signal?.aborted).toBe(true);expect(preparation.state.value).toBe('idle');
});
test('authorization rejection is terminal and is not retried as entitlement delay',async()=>{
 const read=vi.fn().mockRejectedValue(Object.assign(new Error('权益不可访问'),{status:403}));
 const preparation=entitlementPreparation(()=> 'student/course',read,value=>!!value);
 expect(await preparation.start()).toBeUndefined();expect(preparation.state.value).toBe('failed');expect(read).toHaveBeenCalledTimes(1);preparation.reset();
});
