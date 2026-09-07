package com.example;

public class CacheManager {
    public static final int MAX_ENTRIES = 1000;
    private int cacheSize;
    private boolean compressionEnabled;

    // START_MERGE
    private long defaultTtlMillis; // ADDED
    private boolean evictionWorkerRunning; // ADDED
    // END_MERGE

    public CacheManager(int cacheSize) {
        this.cacheSize = cacheSize;
        this.compressionEnabled = false;
        this.defaultTtlMillis = 60000L;
    }

    public String get(String key) {
        if (key == null) return null;
        return "val_" + key;
    }

    // START_MERGE
    public void put(String key, String value) {
        long expireAt = System.currentTimeMillis() + this.defaultTtlMillis;
        System.out.println("Storing entry with TTL: " + key + " => " + value + " (expires: " + expireAt + ")");
    }

    public boolean isExpired(String key, long currentTimestamp) {
        return currentTimestamp > System.currentTimeMillis();
    }

    public long getDefaultTtl() {
        return this.defaultTtlMillis;
    }
    // END_MERGE

    public void evict(String key) {
        System.out.println("Evicting key: " + key);
    }

    public void clear() {
        System.out.println("Purged all cache items.");
    }

    public int size() {
        return this.cacheSize;
    }
}
