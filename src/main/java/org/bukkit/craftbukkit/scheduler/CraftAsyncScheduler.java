/*
 * Copyright (c) 2018 Daniel Ennis (Aikar) MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.bukkit.craftbukkit.scheduler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class CraftAsyncScheduler extends CraftScheduler {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, Integer.MAX_VALUE,30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            new ThreadFactoryBuilder().setNameFormat("Craft Scheduler Thread - %1$d").build());
    private final Executor management = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("Craft Async Scheduler Management Thread").build());
    private final List<CraftTask> temp = new ArrayList<CraftTask>();

    CraftAsyncScheduler() {
        super(true);
        executor.allowCoreThreadTimeOut(true);
        executor.prestartAllCoreThreads();
    }

    @Override
    public void cancelTask(final int taskId) {
        this.management.execute(new Runnable() {
            @Override
            public void run() {
                CraftAsyncScheduler.this.removeTask(taskId);
            }
        });
    }

    private synchronized void removeTask(final int taskId) {
        parsePending();
        this.pending.removeIf(new Predicate<CraftTask>() {
            @Override
            public boolean test(CraftTask task) {
                if (task.getTaskId() == taskId) {
                    task.cancel0();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void mainThreadHeartbeat(final int currentTick) {
        this.currentTick = currentTick;
        this.management.execute(new Runnable() {
            @Override
            public void run() {
                CraftAsyncScheduler.this.runTasks(currentTick);
            }
        });
    }

    private synchronized void runTasks(final int currentTick) {
        parsePending();
        while (!this.pending.isEmpty() && this.pending.peek().getNextRun() <= currentTick) {
            CraftTask task = this.pending.remove();
            if (executeTask(task)) {
                final long period = task.getPeriod();
                if (period > 0) {
                    task.setNextRun(currentTick + period);
                    temp.add(task);
                }
            }
            parsePending();
        }
        this.pending.addAll(temp);
        temp.clear();
    }

    private boolean executeTask(CraftTask task) {
        if (isValid(task)) {
            this.runners.put(task.getTaskId(), task);
            //this.executor.execute(new ServerSchedulerReportingWrapper(task)); TODO
            return true;
        }
        return false;
    }

    @Override
    public synchronized void cancelTasks(Plugin plugin) {
        parsePending();
        for (Iterator<CraftTask> iterator = this.pending.iterator(); iterator.hasNext(); ) {
            CraftTask task = iterator.next();
            if (task.getTaskId() != -1 && (plugin == null || task.getOwner().equals(plugin))) {
                task.cancel0();
                iterator.remove();
            }
        }
    }

    @Override
    public synchronized void cancelAllTasks() {
        cancelTasks(null);
    }

    /**
     * Task is not cancelled
     * @param runningTask
     * @return
     */
    static boolean isValid(CraftTask runningTask) {
        return runningTask.getPeriod() >= -1L;
    }
}