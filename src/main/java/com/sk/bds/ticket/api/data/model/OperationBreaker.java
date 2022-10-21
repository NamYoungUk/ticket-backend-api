package com.sk.bds.ticket.api.data.model;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OperationBreaker {
    private boolean canceled;
    private final Object locker;
    private boolean waiting;
    private boolean operationTerminated;

    public OperationBreaker() {
        canceled = false;
        waiting = false;
        operationTerminated = false;
        locker = new Object();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isOperationTerminated() {
        return operationTerminated;
    }

    public void cancel() {
        log.info("cancel no waiting.");
        canceled = true;
        waiting = false;
    }

    public void cancelWait() {
        log.info("");
        canceled = true;
        waiting = true;
        synchronized (locker) {
            try {
                locker.wait();
                log.info("released waiting.");
            } catch (InterruptedException e) {
                log.error("Waiting error. {}", e);
            }
        }
    }

    public void cancelWait(long timeout) {
        log.info("timeout: {}", timeout);
        canceled = true;
        waiting = true;
        synchronized (locker) {
            try {
                locker.wait(timeout);
                log.info("released waiting.");
            } catch (InterruptedException e) {
                log.error("Waiting error. {}", e);
            }
        }
    }

    public void onCanceled() {
        log.info("waiting: {}", waiting);
        operationTerminated = true;
        if (waiting) {
            synchronized (locker) {
                log.info("release waiting.");
                locker.notifyAll();
            }
        }
    }
}
