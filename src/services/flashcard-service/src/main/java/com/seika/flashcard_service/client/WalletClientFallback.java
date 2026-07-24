package com.seika.flashcard_service.client;

import com.seika.flashcard_service.dto.SystemConfigDTO;
import com.seika.flashcard_service.dto.WalletDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WalletClientFallback implements WalletClient {

    @Override
    public Map<String, String> spend(String token, WalletDTO req) {
        log.error("Wallet service is unavailable. Cannot process spend request.");
        throw new RuntimeException("Payment processing is currently unavailable. Please try again later.");
    }

    @Override
    public ResponseEntity<?> deposit(String token, WalletDTO req) {
        log.error("Wallet service is unavailable. Cannot process deposit request.");
        throw new RuntimeException("Deposit processing is currently unavailable. Please try again later.");
    }

    @Override
    public List<SystemConfigDTO> getConfigs() {
        log.warn("Wallet service is unavailable. Returning empty configs list.");
        return List.of();
    }
}
