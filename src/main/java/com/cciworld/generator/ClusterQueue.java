package com.cciworld.generator;

import com.cciworld.policy.PolicyChunkKey;
import com.cciworld.policy.PolicyQueueEntry;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-thread-only FIFO queue dedicated to the authoritative cluster generator.
 * Independent from {@link com.cciworld.policy.PolicyQueue}.
 */
public final class ClusterQueue {

    private static final ArrayDeque<PolicyQueueEntry> QUEUE = new ArrayDeque<>();
    private static final Set<PolicyChunkKey> QUEUED_KEYS = new HashSet<>();

    private ClusterQueue() {}

    public static boolean enqueue(PolicyQueueEntry entry) {
        PolicyChunkKey key = entry.key();
        if (QUEUED_KEYS.contains(key)) return false;
        QUEUE.add(entry);
        QUEUED_KEYS.add(key);
        return true;
    }

    public static PolicyQueueEntry poll() {
        PolicyQueueEntry entry = QUEUE.poll();
        if (entry != null) QUEUED_KEYS.remove(entry.key());
        return entry;
    }

    public static boolean isEmpty() { return QUEUE.isEmpty(); }
    public static int size() { return QUEUE.size(); }
}
