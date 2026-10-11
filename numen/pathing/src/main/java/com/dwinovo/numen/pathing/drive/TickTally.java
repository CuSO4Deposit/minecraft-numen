package com.dwinovo.numen.pathing.drive;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * 一刻里主线程上寻路做的活用了多久:段状态机每刻开头 {@link #begin}、结尾 {@link #end},中间把拷快照、组成本模型、
 * 复核前提、经宿主的手动手各记一笔。整刻超过 {@link PathLog#MAIN_THREAD_WARN_NANOS} 就记一行 WARN,说清这一刻做了哪几件、
 * 各用了多久;余下的是认步、按键、转头、准星与拿东西到手上,以及垃圾回收停下整个 JVM 的那几毫秒:余下的里有多少是回收停的,
 * 同一行里写出(次数与毫秒),分得清是寻路自己慢还是被回收拖住。
 */
final class TickTally {

    private long start;
    private long snapshot;
    private long model;
    private long recheck;
    private long acted;
    private int searches;
    private int rechecks;
    private long gcMillis;
    private long gcCount;

    /** 回收器的停顿 bean:并发阶段("Concurrent"、ZGC 的 "Cycles")不停任何线程,不算。 */
    private static final GarbageCollectorMXBean[] PAUSING = ManagementFactory.getGarbageCollectorMXBeans().stream()
            .filter(bean -> !bean.getName().contains("Concurrent") && !bean.getName().contains("Cycles"))
            .toArray(GarbageCollectorMXBean[]::new);

    private static long gcMillisNow() {
        long sum = 0;
        for (GarbageCollectorMXBean bean : PAUSING) {
            sum += bean.getCollectionTime();
        }
        return sum;
    }

    private static long gcCountNow() {
        long sum = 0;
        for (GarbageCollectorMXBean bean : PAUSING) {
            sum += bean.getCollectionCount();
        }
        return sum;
    }

    void begin() {
        gcMillis = gcMillisNow();
        gcCount = gcCountNow();
        start = System.nanoTime();
        snapshot = 0;
        model = 0;
        recheck = 0;
        acted = 0;
        searches = 0;
        rechecks = 0;
    }

    /** 派了一次搜索:拷快照与组成本模型各用了多久。 */
    void dispatched(long snapshotNanos, long modelNanos) {
        snapshot += snapshotNanos;
        model += modelNanos;
        searches++;
    }

    /** 复核了一步的前提(连同为它组的成本模型),用了多久。 */
    void rechecked(long nanos) {
        recheck += nanos;
        rechecks++;
    }

    /** 经宿主的手动了一下(挖、放、开门、倒水),用了多久。 */
    void acted(long nanos) {
        acted += nanos;
    }

    void end(String who) {
        long total = System.nanoTime() - start;
        if (total > PathLog.MAIN_THREAD_WARN_NANOS) {
            PathLog.warn("{} 主线程这一刻寻路用了 {}(阈值 {}):派搜索 {} 次(拷快照 {} 组成本模型 {}) 复核 {} 次 {} 动手 {} 其余 {} 回收停顿 {} 次共 {} 毫秒",
                    who, PathLog.ms(total), PathLog.ms(PathLog.MAIN_THREAD_WARN_NANOS), searches, PathLog.ms(snapshot),
                    PathLog.ms(model), rechecks, PathLog.ms(recheck), PathLog.ms(acted),
                    PathLog.ms(total - snapshot - model - recheck - acted), gcCountNow() - gcCount, gcMillisNow() - gcMillis);
        }
    }
}
