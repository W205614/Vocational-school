import {afterEach,expect,test,vi} from 'vitest';
import {playbackTicket,ticketExpiry,type PlaybackTicket} from './playbackTicket';

afterEach(()=>vi.useRealTimers());
function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(done=>{resolve=done;});return {promise,resolve};}
test('uses explicit milliseconds and supports the older signed-url seconds',()=>{
 expect(ticketExpiry({playUrl:'/video?expires=1000',expiresAt:'2000000'})).toBe(2000000);
 expect(ticketExpiry({playUrl:'/video?expires=1000'})).toBe(1000000);
 expect(ticketExpiry({playUrl:'/fixture.webm'})).toBeUndefined();
});
test('renews a long ticket before expiry and installs it once without creating a learning session',async()=>{
 vi.useFakeTimers();vi.setSystemTime(0);
 const fetch=vi.fn().mockResolvedValue({playUrl:'/new?expires=1200',expiresAt:1200000}),install=vi.fn().mockResolvedValue(undefined);
 const ticket=playbackTicket(()=> 'student/course/section',fetch,install);ticket.activate({playUrl:'/old',expiresAt:600000});
 await vi.advanceTimersByTimeAsync(539999);expect(fetch).not.toHaveBeenCalled();
 await vi.advanceTimersByTimeAsync(1);expect(fetch).toHaveBeenCalledTimes(1);expect(install).toHaveBeenCalledTimes(1);expect(ticket.error.value).toBe('');ticket.reset();
});
test('switching section aborts and discards an older renewal response',async()=>{
 const gate=deferred<PlaybackTicket>();let scope='one';let signal:AbortSignal|undefined;
 const install=vi.fn(),ticket=playbackTicket(()=>scope,async s=>{signal=s;return gate.promise;},install);
 ticket.activate({playUrl:'/one'});const pending=ticket.renew();scope='two';ticket.activate({playUrl:'/two'});
 expect(signal?.aborted).toBe(true);gate.resolve({playUrl:'/stale'});expect(await pending).toBe(false);expect(install).not.toHaveBeenCalled();ticket.reset();
});
test('coalesces repeated retry clicks and cleans timers on exit',async()=>{
 vi.useFakeTimers();vi.setSystemTime(0);const gate=deferred<PlaybackTicket>(),fetch=vi.fn().mockReturnValue(gate.promise),install=vi.fn().mockResolvedValue(undefined);
 const ticket=playbackTicket(()=> 'one',fetch,install);ticket.activate({playUrl:'/old',expiresAt:6000});
 const first=ticket.renew(),again=ticket.renew();expect(fetch).toHaveBeenCalledTimes(1);ticket.reset();gate.resolve({playUrl:'/new',expiresAt:10000});
 expect(await first).toBe(false);expect(await again).toBe(false);await vi.advanceTimersByTimeAsync(600000);expect(fetch).toHaveBeenCalledTimes(1);
});
test('authorization rejection stops automatic retries and reports the real status',async()=>{
 vi.useFakeTimers();vi.setSystemTime(0);const fetch=vi.fn().mockRejectedValue(Object.assign(new Error('权益失效'),{status:403})),failed=vi.fn();
 const ticket=playbackTicket(()=> 'one',fetch,async()=>{},failed);ticket.activate({playUrl:'/old',expiresAt:6000});
 expect(await ticket.renew()).toBe(false);expect(ticket.errorStatus.value).toBe(403);expect(failed).toHaveBeenCalledWith(403);
 await vi.advanceTimersByTimeAsync(60000);expect(fetch).toHaveBeenCalledTimes(1);ticket.reset();
});
test('transient failures retain the prior ticket and retry a bounded number of times',async()=>{
 vi.useFakeTimers();vi.setSystemTime(0);const fetch=vi.fn().mockRejectedValue(new Error('offline')),install=vi.fn();
 const ticket=playbackTicket(()=> 'one',fetch,install);ticket.activate({playUrl:'/old',expiresAt:6000});
 expect(await ticket.renew()).toBe(false);await vi.advanceTimersByTimeAsync(60000);
 expect(fetch).toHaveBeenCalledTimes(3);expect(install).not.toHaveBeenCalled();expect(ticket.error.value).toContain('offline');ticket.reset();
});
