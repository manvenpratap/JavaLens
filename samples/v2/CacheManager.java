package com.example;

public class CacheManager {
    public static final int MAX_ENTRIES = 1000;
    private int cacheSize;
    private boolean compressionEnabled;

    // START_MERGE
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
        System.out.println("Storing entry: " + key + " => " + value);
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
