package com.minicloud.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class JobExecutionConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService jobWorkerExecutor(
            @Value("${app.jobs.worker-threads:2}") int workerThreads) {

        int threads = Math.max(1, workerThreads);
        AtomicInteger index = new AtomicInteger(1);

        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("job-worker-" + index.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
