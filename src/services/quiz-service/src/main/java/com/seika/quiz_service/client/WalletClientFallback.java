package com.seika.quiz_service.client;

import com.seika.quiz_service.dto.SystemConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class WalletClientFallback implements WalletClient {
    @Override
    public List<SystemConfigDTO> getConfigs() {
        log.warn("Wallet service is unavailable. Returning empty configs list.");
        return List.of();
    }
}
