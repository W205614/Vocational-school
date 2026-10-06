package com.tianji.learning.utils;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorTest {
    public static void main(String[] args) {
        //创建线程池，阿里规约：不推荐这种创建方式，容易出现OOM内存溢出
        //Executors内部也是使用new ThreadPoolExecutor()创建的，但是它在new XXXQueue的时候不传参
        //即默认队列大小为Integer.MAX_VALUE，相当于可以存储无限多的任务，但是这样容易导致OOM内存溢出

        //Executors.newFixedThreadPool(3);  //创建线程数固定的线程池，传参3表示3个核心线程，没有临时线程
        //Executors.newSingleThreadExecutor();  //创建单线程的线程池，无需传参，1个核心线程
        //Executors.newCachedThreadPool();  //创建缓存线程池，无需传参，无核心线程，全是临时线程
        //Executors.newScheduledThreadPool();  //创建可以延迟执行的线程池，底层用DelayQueue实现

        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(24,
                24,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(10));
        //建议1：如果任务是属于CPU运算型任务，推荐核心线程为CPU的核数
        //建议2：如果任务是属于IO型任务，推荐核心线程为CPU核数的2倍

        poolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                //任务
            }
        });
        poolExecutor.shutdown();
        try {
            if(!poolExecutor.awaitTermination(5,TimeUnit.SECONDS)) poolExecutor.shutdownNow();
        } catch(InterruptedException interrupted) {
            poolExecutor.shutdownNow();Thread.currentThread().interrupt();
        }
    }
}
