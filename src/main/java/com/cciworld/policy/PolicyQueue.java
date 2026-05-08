package com.cciworld.policy;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-thread-only FIFO queue with in-flight dedup.
 * Both enqueue and poll must be called from the server thread.
 */
public final class PolicyQueue {

    private static final ArrayDeque<PolicyQueueEntry> QUEUE = new ArrayDeque<>();
    // Tracks keys currently in the queue to prevent duplicate entries.
    private static final Set<PolicyChunkKey> QUEUED_KEYS = new HashSet<>();

    private PolicyQueue() {}

    /**
     * Enqueues entry if not already in queue.
     * @return true if added, false if already in queue
     */
    public static boolean enqueue(PolicyQueueEntry entry) {
        PolicyChunkKey key = entry.key();
        if (QUEUED_KEYS.contains(key)) return false;
        QUEUE.add(entry);
        QUEUED_KEYS.add(key);
        return true;
    }

    /** Polls the next entry, or null if empty. Removes its key from the dedup set. */
    public static PolicyQueueEntry poll() {
        PolicyQueueEntry entry = QUEUE.poll();
        if (entry != null) QUEUED_KEYS.remove(entry.key());
        return entry;
    }

    public static boolean isEmpty() { return QUEUE.isEmpty(); }

    public static int size() { return QUEUE.size(); }
}
