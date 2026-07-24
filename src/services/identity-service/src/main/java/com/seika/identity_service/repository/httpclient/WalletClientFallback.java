package com.seika.identity_service.repository.httpclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WalletClientFallback implements WalletClient {
    @Override
    public String getTotalCirculation() {
        log.warn("Wallet service is unavailable. Returning fallback N/A for total circulation.");
        return "N/A";
    }
}
