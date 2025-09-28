package com.yohann.ocihelper.utils;


import jakarta.validation.constraints.NotNull;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * @ClassName DelegatingVirtualTaskScheduler
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-07-29 17:24
 **/
public class DelegatingVirtualTaskScheduler implements TaskScheduler {

    private final TaskScheduler delegate;
    private final ExecutorService virtualExecutor;

    public DelegatingVirtualTaskScheduler(TaskScheduler delegate, ExecutorService virtualExecutor) {
        this.delegate = delegate;
        this.virtualExecutor = virtualExecutor;
    }

    @Override
    public ScheduledFuture<?> schedule(@NotNull Runnable task, @NotNull Trigger trigger) {
        return delegate.schedule(() -> virtualExecutor.execute(task), trigger);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> schedule(@NotNull Runnable task, @NotNull Instant startTime) {
        return delegate.schedule(() -> virtualExecutor.execute(task), startTime);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable task, @NotNull Instant startTime, @NotNull Duration period) {
        return delegate.scheduleAtFixedRate(() -> virtualExecutor.execute(task), startTime, period);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable task, @NotNull Duration period) {
        return delegate.scheduleAtFixedRate(() -> virtualExecutor.execute(task), period);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable task, @NotNull Instant startTime, @NotNull Duration delay) {
        return delegate.scheduleWithFixedDelay(() -> virtualExecutor.execute(task), startTime, delay);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable task, @NotNull Duration delay) {
        return delegate.scheduleWithFixedDelay(() -> virtualExecutor.execute(task), delay);
    }
}
