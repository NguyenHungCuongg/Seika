package com.seika.flashcard_service.client;

import com.seika.flashcard_service.dto.SystemConfigDTO;
import com.seika.flashcard_service.dto.WalletDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "wallet-service", fallback = WalletClientFallback.class)
public interface WalletClient {
    @PostMapping("/api/wallet/withdraw")
    Map<String, String> spend(@RequestHeader("Authorization") String token,
                              @RequestBody WalletDTO req);
    @PostMapping("/api/wallet/deposit")
    ResponseEntity<?> deposit(@RequestHeader("Authorization") String token,
                              @RequestBody WalletDTO req);
    @GetMapping("/api/wallet/configs")
    List<SystemConfigDTO> getConfigs();
}
