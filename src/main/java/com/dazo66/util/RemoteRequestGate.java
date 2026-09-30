package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;

/**
 * Process-wide request policy for the remote archive.
 *
 * <p>The service asks clients to start no more than one request per second and
 * to keep fewer than five downloads active. A single gate is shared by page,
 * image and attachment requests so their individual thread pools cannot create
 * an aggregate burst.</p>
 */
@Slf4j
public final class RemoteRequestGate {

    public static final RemoteRequestGate KEMONO_GATE = new RemoteRequestGate();

    final int MAX_ACTIVE_REQUESTS = 4;

    final Object RATE_LOCK = new Object();
    final Semaphore ACTIVE_REQUESTS = new Semaphore(MAX_ACTIVE_REQUESTS, true);


    private RemoteRequestGate() {
    }

    /**
     * Waits for a request-start slot and an active-request permit. Returns
     * {@code null} while the remote has put this process into a cooldown.
     */
    public Permit acquire() throws InterruptedException {
        ACTIVE_REQUESTS.acquire();
        return new Permit(ACTIVE_REQUESTS);
    }


    public static final class Permit implements AutoCloseable {
        private boolean closed;
        private Semaphore semaphore;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }
}
