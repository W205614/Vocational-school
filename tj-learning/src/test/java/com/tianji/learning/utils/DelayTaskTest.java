package com.tianji.learning.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DelayTaskTest {
    @Test
    void testDelayQueue() throws InterruptedException {
        // 1. 初始化延迟队列
        DelayQueue<DelayTask<String>> queue = new DelayQueue<>();
        // 2. 向队列中添加延迟执行的任务
        log.info("开始初始化延迟任务............");
        queue.add(new DelayTask<>("延迟任务3", Duration.ofMillis(30)));
        queue.add(new DelayTask<>("延迟任务1", Duration.ofMillis(10)));
        queue.add(new DelayTask<>("延迟任务2", Duration.ofMillis(20)));
        // 3. 尝试执行任务
        for (int i = 1; i <= 3; i++) {
            DelayTask<String> task = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(task);
            assertEquals("延迟任务" + i, task.getData());
        }
        assertTrue(queue.isEmpty());
    }
}
