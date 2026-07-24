package com.seika.identity_service.repository.httpclient;

import com.seika.identity_service.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "marketplace-service", url = "${app.services.marketplace}", configuration = FeignClientConfig.class, fallback = MarketplaceClientFallback.class)
public interface MarketplaceClient {
    @GetMapping("/api/marketplace/admin/products/count-pending")
    Long countPendingProducts();
}