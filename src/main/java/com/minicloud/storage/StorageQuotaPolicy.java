package com.minicloud.storage;

public final class StorageQuotaPolicy {

    public static final long DEFAULT_QUOTA_BYTES = 100L * 1024 * 1024;
    public static final long MIN_QUOTA_BYTES = 1L;
    public static final long MAX_QUOTA_BYTES = 1024L * 1024 * 1024 * 1024;

    private StorageQuotaPolicy() {
    }
}
