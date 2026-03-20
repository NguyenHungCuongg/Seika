package com.seika.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/protected")
public class ProtectedController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping(Authentication authentication) {
        return ResponseEntity.ok(Map.of("message", "Authenticated as " + authentication.getName()));
    }
}
