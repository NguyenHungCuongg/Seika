package com.seika.quiz_service.client;

import com.seika.quiz_service.dto.SystemConfigDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "wallet-service", fallback = WalletClientFallback.class)
public interface WalletClient {
    @GetMapping("/api/wallet/configs")
    List<SystemConfigDTO> getConfigs();
}
