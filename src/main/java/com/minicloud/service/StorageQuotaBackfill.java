package com.minicloud.service;

import com.minicloud.repository.AppUserRepository;
import com.minicloud.storage.StorageQuotaPolicy;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StorageQuotaBackfill {

    private final AppUserRepository userRepository;

    public StorageQuotaBackfill(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void backfillMissingQuotas() {
        userRepository.backfillNullStorageQuotas(
                StorageQuotaPolicy.DEFAULT_QUOTA_BYTES);
    }
}
