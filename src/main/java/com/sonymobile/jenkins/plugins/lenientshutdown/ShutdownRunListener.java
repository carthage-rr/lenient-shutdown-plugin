/*
 *  The MIT License
 *
 *  Copyright (c) 2014 Sony Mobile Communications Inc. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.sonymobile.jenkins.plugins.lenientshutdown;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Listens for completed builds and sets nodes as offline when they are
 * finished if they have "set as temp. offline leniently" activated.
 *
 * @param <R> run type
 *
 * @author Fredrik Persson &lt;fredrik6.persson@sonymobile.com&gt;
 */
@Extension(ordinal = Double.MAX_VALUE)
public class ShutdownRunListener<R extends Run<?, ?>> extends RunListener<R> {

    private static final int TASK_DELAY_SECONDS = 10;

    private static final Logger logger = Logger.getLogger(ShutdownRunListener.class.getName());

    @Override
    public void onFinalized(final R r) {
        final PluginImpl plugin = PluginImpl.getInstance();

        if (r instanceof WorkflowRun) {
            // WorkflowRuns are performed on one-off executors (usually on the master node),
            // so we must check all computers
            for (String nodeName : plugin.getLenientOfflineNodes()) {
                checkForLenientShutdown(nodeName);
            }
        }

        Executor executor = r.getExecutor();
        if (executor != null) {
            final Computer computer = executor.getOwner();
            final String nodeName = computer.getName();

            if (plugin.isNodeShuttingDown(nodeName)) {
                //Schedule checking if all builds are completed on the build node after a delay
                Runnable isNodeIdleTask = () -> {
                    if (plugin.isNodeShuttingDown(nodeName) && !computer.isTemporarilyOffline()
                            && !QueueUtils.isBuilding(computer)) {
                        logger.log(Level.INFO, "Node {0} idle; setting offline since lenient "
                                + "shutdown was active for this node", nodeName);

                        User user = plugin.getOfflineByUser(nodeName);
                        computer.setTemporarilyOffline(true, new LenientOfflineCause(user));
                    }
                };
                Timer.get().schedule(isNodeIdleTask, TASK_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }

        ShutdownManageLink shutdownManageLink = ShutdownManageLink.getInstance();
        boolean isGoingToShutdown = shutdownManageLink.isGoingToShutdown();

        if (isGoingToShutdown) {
            shutdownManageLink.removeActiveQueueId(r.getQueueId());
        }
    }

    private void checkForLenientShutdown(String nodeName) {
        final PluginImpl plugin = PluginImpl.getInstance();
        Computer computer = Jenkins.get().getComputer(nodeName);
        if (plugin.isNodeShuttingDown(nodeName)) {
            //Schedule checking if all builds are completed on the build node after a delay
            Runnable isNodeIdleTask = () -> {
                if (plugin.isNodeShuttingDown(nodeName)
                        && computer != null
                        && !computer.isTemporarilyOffline()
                        && !QueueUtils.isBuilding(computer)) {
                    logger.log(Level.INFO, "Node {0} idle; setting offline since lenient "
                            + "shutdown was active for this node", nodeName);

                    User user = plugin.getOfflineByUser(nodeName);
                    computer.setTemporarilyOffline(true, new LenientOfflineCause(user));
                }
            };
            Timer.get().schedule(isNodeIdleTask, TASK_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }
}
