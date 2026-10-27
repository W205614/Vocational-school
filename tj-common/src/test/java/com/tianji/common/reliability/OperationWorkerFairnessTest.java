package com.tianji.common.reliability;

import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.autoconfigure.reliability.OperationWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationWorkerFairnessTest {
    private OperationHandler handler(String kind) {
        var handler=mock(OperationHandler.class);when(handler.kind()).thenReturn(kind);return handler;
    }
    @SuppressWarnings("unchecked")
    private ObjectProvider<OperationHandler> handlers(OperationHandler... values) {
        ObjectProvider<OperationHandler> provider=mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(call->Arrays.stream(values));return provider;
    }
    private ThreadPoolTaskExecutor pool(int queue) {
        var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(1);pool.setMaxPoolSize(1);
        pool.setQueueCapacity(queue);pool.initialize();return pool;
    }
    private OperationStore continuousBacklogs() {
        var store=mock(OperationStore.class);var sequence=new AtomicInteger();
        when(store.due(anyString(),anyInt())).thenAnswer(call->{String kind=call.getArgument(0);int limit=call.getArgument(1);
            return IntStream.range(0,limit).mapToObj(n->new OperationStore.Work(kind+sequence.incrementAndGet(),1,kind,"{}",0)).toList();});
        when(store.claim(anyString(),anyString())).thenReturn(true);return store;
    }
    private CountDownLatch occupyWorker(ThreadPoolTaskExecutor pool)throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        pool.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(3,TimeUnit.SECONDS));return release;
    }
    @Test void aSingleFreeSlotRotatesBetweenContinuouslyBackloggedKinds()throws Exception {
        var pool=pool(1);var release=occupyWorker(pool);var store=continuousBacklogs();
        OperationHandler first=handler("FIRST"),second=handler("SECOND");var worker=new OperationWorker(store,handlers(first,second),pool);
        try {
            for(int n=0;n<4;n++) {
                worker.poll();assertEquals(1,pool.getThreadPoolExecutor().getQueue().size());
                // Deterministically complete the queued operation while preserving
                // the occupied worker and exactly one free queue slot for each poll.
                Objects.requireNonNull(pool.getThreadPoolExecutor().getQueue().poll()).run();
            }
            verify(store,times(2)).execute(argThat(work->work.kind().equals("FIRST")),anyString(),same(first));
            verify(store,times(2)).execute(argThat(work->work.kind().equals("SECOND")),anyString(),same(second));
        } finally {release.countDown();pool.shutdown();}
    }
    @Test void eachKindGetsAShareWithoutExceedingTheWholePollBudget()throws Exception {
        var pool=pool(10);var release=occupyWorker(pool);var store=continuousBacklogs();
        OperationHandler first=handler("FIRST"),second=handler("SECOND");var worker=new OperationWorker(store,handlers(first,second),pool);
        ReflectionTestUtils.setField(worker,"batch",4);
        try {
            worker.poll();assertEquals(4,pool.getThreadPoolExecutor().getQueue().size());
            Runnable queued;while((queued=pool.getThreadPoolExecutor().getQueue().poll())!=null)queued.run();
            verify(store,times(2)).execute(argThat(work->work.kind().equals("FIRST")),anyString(),same(first));
            verify(store,times(2)).execute(argThat(work->work.kind().equals("SECOND")),anyString(),same(second));
        } finally {release.countDown();pool.shutdown();}
    }
    @Test void aFullQueuePollCannotSpendTheWaitingKindsNextTurn()throws Exception {
        var pool=pool(1);var release=occupyWorker(pool);var store=continuousBacklogs();
        OperationHandler first=handler("FIRST"),second=handler("SECOND");var worker=new OperationWorker(store,handlers(first,second),pool);
        try {
            worker.poll();assertEquals(1,pool.getThreadPoolExecutor().getQueue().size());
            worker.poll(); // No slot: SECOND must retain first choice for the next opportunity.
            Objects.requireNonNull(pool.getThreadPoolExecutor().getQueue().poll()).run();
            worker.poll();assertEquals(1,pool.getThreadPoolExecutor().getQueue().size());
            Objects.requireNonNull(pool.getThreadPoolExecutor().getQueue().poll()).run();
            verify(store).execute(argThat(work->work.kind().equals("FIRST")),anyString(),same(first));
            verify(store).execute(argThat(work->work.kind().equals("SECOND")),anyString(),same(second));
        } finally {release.countDown();pool.shutdown();}
    }
    @Test void rejectedSubmissionReleasesItsLeaseAndGivesTheNextKindFirstChoice() {
        var pool=spy(pool(1));var store=continuousBacklogs();
        doThrow(new RejectedExecutionException("injected saturation")).when(pool).execute(any(Runnable.class));
        var worker=new OperationWorker(store,handlers(handler("FIRST"),handler("SECOND")),pool);
        try {
            worker.poll();worker.poll();
            verify(store).releaseUnstarted(startsWith("FIRST"),anyString());
            verify(store).releaseUnstarted(startsWith("SECOND"),anyString());
            verify(store,never()).execute(any(),anyString(),any());
        } finally {pool.shutdown();}
    }
    @Test void duplicateKindsFailBeforeAnyTaskCanBeClaimed() {
        var pool=pool(1);var store=continuousBacklogs();
        try {
            var worker=new OperationWorker(store,handlers(handler("SAME"),handler("SAME")),pool);
            assertThrows(IllegalStateException.class,worker::poll);verifyNoInteractions(store);
        } finally {pool.shutdown();}
    }
}
