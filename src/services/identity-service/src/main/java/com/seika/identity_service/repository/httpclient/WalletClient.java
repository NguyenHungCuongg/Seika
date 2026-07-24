package com.seika.identity_service.repository.httpclient;

import com.seika.identity_service.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "wallet-service", url = "${app.services.wallet}", configuration = FeignClientConfig.class, fallback = WalletClientFallback.class)
public interface WalletClient {
    @GetMapping("/api/wallet/admin/total-circulation")
    String getTotalCirculation();
}