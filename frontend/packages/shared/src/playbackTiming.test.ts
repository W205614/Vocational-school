import {expect,test,vi} from 'vitest';
import {playbackTiming,type PlaybackHeartbeat,type PlaybackView} from './playbackTiming';
const view=(lastSequence=0):PlaybackView=>({sessionId:'session',watchedMillis:0,creditedMillis:0,requiredMillis:1800,durationMillis:2000,finished:false,completionSource:null,lastSequence,active:true});
const context={owner:'student',lessonId:'lesson',sectionId:'section'};
const sample={positionMillis:0,rate:1,state:'PLAYING' as const};
function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(done=>{resolve=done;});return {promise,resolve};}

test('first playing sample and short-video tail are serialized with distinct sequence numbers',async()=>{
 const gate=deferred<PlaybackView>(),bodies:PlaybackHeartbeat[]=[];let active=0,max=0;
 const timing=playbackTiming(()=>context.owner,async()=>view(),async(body)=>{active++;max=Math.max(max,active);bodies.push(body);const result=body.sequence===1?await gate.promise:{...view(body.sequence),creditedMillis:1900,finished:true,active:false};active--;return result;});
 await timing.start(context);const first=timing.heartbeat(sample);const end=timing.heartbeat({...sample,positionMillis:1900,state:'ENDED'},true);await Promise.resolve();gate.resolve(view(1));
 expect(await Promise.all([first,end])).toEqual([true,true]);expect(bodies.map(x=>x.sequence)).toEqual([1,2]);expect(max).toBe(1);expect(timing.current.value?.finished).toBe(true);
});
test('a lost response retries the exact original body without overwriting the pending state',async()=>{
 const send=vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(view(1));const timing=playbackTiming(()=>context.owner,async()=>view(),send);await timing.start(context);
 expect(await timing.heartbeat(sample)).toBe(false);expect(await timing.heartbeat({...sample,positionMillis:500})).toBe(false);
 expect(await timing.retry()).toBe(true);expect(send).toHaveBeenCalledTimes(2);expect(send.mock.calls[0]![0]).toEqual(send.mock.calls[1]![0]);
});
test('replaced sessions stop retrying automatically',async()=>{
 const send=vi.fn().mockRejectedValue(Object.assign(new Error('other tab'),{status:409}));const timing=playbackTiming(()=>context.owner,async()=>view(),send);await timing.start(context);
 expect(await timing.heartbeat(sample)).toBe(false);expect(timing.replaced.value).toBe(true);expect(await timing.retry()).toBe(false);expect(send).toHaveBeenCalledTimes(1);
});
test('identity changes abort stale writes and cannot publish another users response',async()=>{
 const gate=deferred<PlaybackView>();let owner='student';let signal:AbortSignal|undefined;
 const timing=playbackTiming(()=>owner,async()=>view(),async(_body,_end,s)=>{signal=s;return gate.promise;});await timing.start(context);const first=timing.heartbeat(sample);await Promise.resolve();
 owner='another';timing.reset();expect(signal?.aborted).toBe(true);gate.resolve(view(1));expect(await first).toBe(false);expect(timing.current.value).toBeUndefined();
});
test('long counters serialized as strings are normalized before allocating the next sequence',async()=>{
 const wire=(sequence:number)=>({...view(sequence),lastSequence:String(sequence),watchedMillis:'900',creditedMillis:'1800',requiredMillis:'1800',durationMillis:'2000'}) as unknown as PlaybackView;
 const bodies:PlaybackHeartbeat[]=[];
 const timing=playbackTiming(()=>context.owner,async()=>wire(0),async body=>{bodies.push(body);return wire(body.sequence);});
 await timing.start(context);await timing.heartbeat(sample);await timing.heartbeat({...sample,positionMillis:1000});
 expect(bodies.map(body=>body.sequence)).toEqual([1,2]);expect(timing.current.value?.lastSequence).toBe(2);expect(timing.current.value?.creditedMillis).toBe(1800);
});
