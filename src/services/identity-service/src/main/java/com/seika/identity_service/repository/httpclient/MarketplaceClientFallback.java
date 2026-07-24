package com.seika.identity_service.repository.httpclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MarketplaceClientFallback implements MarketplaceClient {
    @Override
    public Long countPendingProducts() {
        log.warn("Marketplace service is unavailable. Returning fallback 0 for pending products.");
        return 0L;
    }
}
