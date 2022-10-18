package com.sk.bds.ticket.api.data.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

@Slf4j
public class TicketSyncRequestHandler {
    @Data
    public static class SyncRequest {
        String fdTicketId;
        SyncTriggerType triggerType;

        public SyncRequest(String fdTicketId, SyncTriggerType triggerType) {
            this.fdTicketId = fdTicketId;
            this.triggerType = triggerType;
        }
    }

    final Map<String, SyncTriggerType> externalRequests = Collections.synchronizedMap(new LinkedHashMap<>());
    final Queue<String> scheduledRequests = new ConcurrentLinkedQueue<>();
    final ConcurrentHashMap<String, Semaphore> ticketLockers = new ConcurrentHashMap<>();

    public boolean addExternalAuto(String fdTicketId) {
        log.debug("{}", fdTicketId);
        if (fdTicketId != null) {
            synchronized (externalRequests) {
                if (!externalRequests.containsKey(fdTicketId)) {
                    externalRequests.put(fdTicketId, SyncTriggerType.Auto);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean addExternalManual(String fdTicketId) {
        log.debug("{}", fdTicketId);
        if (fdTicketId != null) {
            synchronized (externalRequests) {
                if (!externalRequests.containsKey(fdTicketId)) {
                    externalRequests.put(fdTicketId, SyncTriggerType.Manual);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean addScheduled(String fdTicketId) {
        log.debug("{}", fdTicketId);
        if (fdTicketId != null) {
            synchronized (scheduledRequests) {
                if (!scheduledRequests.contains(fdTicketId)) {
                    scheduledRequests.offer(fdTicketId);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasExternalRequest() {
        synchronized (externalRequests) {
            return !externalRequests.isEmpty();
        }
    }

    public boolean hasScheduledRequest() {
        synchronized (scheduledRequests) {
            return !scheduledRequests.isEmpty();
        }
    }

    private boolean tryAcquire(String fdTicketId) {
        log.debug("{}", fdTicketId);
        synchronized (ticketLockers) {
            Semaphore semaphore = ticketLockers.get(fdTicketId);
            log.debug("{} -> semaphore:{}", fdTicketId, semaphore);
            if (semaphore == null) {
                semaphore = new Semaphore(1);
                ticketLockers.put(fdTicketId, semaphore);
            }
            log.debug("{} -> availablePermits:{}", fdTicketId, semaphore.availablePermits());
            return semaphore.tryAcquire();
        }
    }

    private void release(String fdTicketId) {
        log.debug("{}", fdTicketId);
        synchronized (ticketLockers) {
            Semaphore semaphore = ticketLockers.get(fdTicketId);
            if (semaphore != null) {
                semaphore.release();
            }
            ticketLockers.remove(fdTicketId);
        }
    }

    public SyncRequest nextExternalRequest() {
        synchronized (externalRequests) {
            if (!externalRequests.isEmpty()) {
                String availableTicketId = null;
                List<String> acquireFailedTickets = new ArrayList<>();
                for (String fdTicketId : externalRequests.keySet()) {
                    if (tryAcquire(fdTicketId)) {
                        availableTicketId = fdTicketId;
                        break;
                    } else {
                        acquireFailedTickets.add(fdTicketId);
                    }
                }
                //Delete unavailable ticket(Failure to acquire means that synchronization is currently in progress).
                if (acquireFailedTickets.size() > 0) {
                    for (String fdTicketId : acquireFailedTickets) {
                        externalRequests.remove(fdTicketId);
                    }
                }
                log.debug("{}", availableTicketId);
                if (availableTicketId != null) {
                    SyncTriggerType triggerType = externalRequests.get(availableTicketId);
                    externalRequests.remove(availableTicketId);
                    return new SyncRequest(availableTicketId, triggerType);
                }
            }
        }
        return null;
    }

    public SyncRequest nextScheduledRequest() {
        synchronized (scheduledRequests) {
            while (!scheduledRequests.isEmpty()) {
                String fdTicketId = scheduledRequests.poll();
                if (tryAcquire(fdTicketId)) {
                    log.debug("{}", fdTicketId);
                    return new SyncRequest(fdTicketId, SyncTriggerType.Schedule);
                }
            }
        }
        return null;
    }

    public boolean tryInstantSync(String fdTicketId) {
        if (fdTicketId != null) {
            return tryAcquire(fdTicketId);
        }
        return false;
    }

    public void onCompleted(String fdTicketId) {
        log.info("Synchronization Complete. {}", fdTicketId);
        //Delete request in External
        synchronized (externalRequests) {
            externalRequests.remove(fdTicketId);
        }
        //Delete request in Scheduled
        synchronized (scheduledRequests) {
            scheduledRequests.remove(fdTicketId);
        }
        //Delete locker
        release(fdTicketId);
    }

    public synchronized void clear() {
        log.info("cancel all synchronization tickets.");
        synchronized (externalRequests) {
            externalRequests.clear();
        }
        synchronized (scheduledRequests) {
            scheduledRequests.clear();
        }
        synchronized (ticketLockers) {
            ticketLockers.clear();
        }
    }
}
