package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.AppConstants;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class JobScheduler {
    //*    *     *    *     *     *      *
    //초   분    시    일    월    요일    년도(생략 가능)
    public static final String CronEveryDayChanged = "0 0 0 * * ?"; //0초 0분 0시 매일 매월 매 요일 => 매일 00시
    public static final String CronEveryHourChanged = "0 0 0/1 * * ?"; //매 시간마다
    public static final String CronEveryMinuteChanged = "0 0/1 * * * ?"; //매 1분마다

    public static ScheduledFuture schedule(Runnable jobTask, long periodInterval, long initialDelay) {
        if (jobTask != null) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.initialize();
            PeriodicTrigger trigger = new PeriodicTrigger(periodInterval, TimeUnit.MILLISECONDS);
            trigger.setInitialDelay(initialDelay);
            return scheduler.schedule(jobTask, trigger);
        }
        return null;
    }

    public static ScheduledFuture schedule(Runnable jobTask, String cronExpression) {
        if (jobTask != null && cronExpression != null) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.initialize();
            CronTrigger trigger = new CronTrigger(cronExpression, AppConstants.getLocalTimeZone());
            return scheduler.schedule(jobTask, trigger);
        }
        return null;
    }

    public static ScheduledFuture schedule(Runnable jobTask, long initialDelay) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        return executor.schedule(jobTask, initialDelay, TimeUnit.MILLISECONDS);
    }

    public static Future submit(Runnable jobTask) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        return executor.submit(jobTask);
    }

    public static void execute(Runnable jobTask) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(jobTask);
    }
}