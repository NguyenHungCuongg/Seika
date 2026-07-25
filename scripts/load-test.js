import http from "k6/http";
import { check, sleep, group } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

// =====================================================================
// CUSTOM METRICS
// =====================================================================
export const errorRate = new Rate("errors");
export const rateLimitedRate = new Rate("rate_limited");
export const loginLatency = new Trend("login_duration");
export const flashcardLatency = new Trend("flashcard_duration");
export const rateLimitHits = new Counter("rate_limit_429_total");

// =====================================================================
// SCENARIOS & THRESHOLDS
// =====================================================================
export const options = {
  scenarios: {
    // Scenario 1: Tải thông thường (Normal Load)
    normal_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "20s", target: 10 }, // Warm-up
        { duration: "40s", target: 30 }, // Normal load
        { duration: "20s", target: 50 }, // Peak load
        { duration: "20s", target: 0 }, // Cool-down
      ],
      exec: "normalUserFlow",
      tags: { scenario: "normal_load" },
    },

    // Scenario 2: Kiểm tra Rate Limiting (Bắn nhanh liên tục vào /api/auth/**)
    rate_limit_test: {
      executor: "constant-arrival-rate",
      rate: 50, // 50 requests/giây (vượt ngưỡng 10 req/s cho auth)
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 50,
      maxVUs: 100,
      exec: "rateLimitTest",
      startTime: "10s", // Bắt đầu sau 10s để hệ thống warm-up
      tags: { scenario: "rate_limit_test" },
    },

    // Scenario 3: Kiểm tra Circuit Breaker (Gọi endpoint phụ thuộc Wallet)
    circuit_breaker_probe: {
      executor: "constant-vus",
      vus: 5,
      duration: "100s",
      exec: "circuitBreakerProbe",
      tags: { scenario: "circuit_breaker" },
    },
  },

  thresholds: {
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
    errors: ["rate<0.10"], // Cho phép 10% lỗi (vì rate limit sẽ trả 429)
    rate_limited: ["rate>0"], // Phải có ít nhất 1 request bị rate limit (chứng tỏ nó hoạt động)
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// =====================================================================
// SCENARIO 1: Normal User Flow (Login + Browse Flashcards)
// =====================================================================
export function normalUserFlow() {
  group("Normal User Flow", function () {
    // Login
    const loginPayload = JSON.stringify({
      email: "test@test.com",
      password: "wrong_password_for_test",
    });

    const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
      headers: { "Content-Type": "application/json" },
      tags: { name: "POST /api/auth/login" },
    });

    loginLatency.add(loginRes.timings.duration);

    const loginOk = check(loginRes, {
      "Login: Response time < 1000ms": (r) => r.timings.duration < 1000,
      "Login: Valid HTTP status (non-5xx)": (r) => r.status < 500,
    });

    errorRate.add(!loginOk);
    sleep(1);

    // Browse Flashcards
    const flashcardRes = http.get(`${BASE_URL}/api/flashcards`, {
      tags: { name: "GET /api/flashcards" },
    });

    flashcardLatency.add(flashcardRes.timings.duration);

    const flashcardOk = check(flashcardRes, {
      "Flashcard: Response time < 500ms": (r) => r.timings.duration < 500,
      "Flashcard: No Gateway Error (502/503/504)": (r) =>
        r.status !== 502 && r.status !== 503 && r.status !== 504,
    });

    errorRate.add(!flashcardOk);
    sleep(2);
  });
}

// =====================================================================
// SCENARIO 2: Rate Limit Test (Bắn nhanh vào /api/auth/login)
// =====================================================================
export function rateLimitTest() {
  const payload = JSON.stringify({
    email: "brute@force.com",
    password: "attempt_" + Math.random(),
  });

  const res = http.post(`${BASE_URL}/api/auth/login`, payload, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "RateLimit POST /api/auth/login" },
  });

  const isRateLimited = res.status === 429;

  if (isRateLimited) {
    rateLimitHits.add(1);
  }

  rateLimitedRate.add(isRateLimited);

  check(res, {
    "Rate Limit: Received 200/400/401 or 429": (r) =>
      [200, 400, 401, 429].includes(r.status),
  });
}

// =====================================================================
// SCENARIO 3: Circuit Breaker Probe
//
// Gọi liên tục vào endpoint Admin Dashboard (phụ thuộc Wallet + Marketplace).
// Khi chạy kèm lệnh `docker pause seika-wallet-service`, kịch bản này
// sẽ cho thấy Circuit Breaker phát huy tác dụng:
//   - Fallback trả về ngay lập tức thay vì timeout 60s
//   - Hệ thống vẫn phản hồi được các request khác
// =====================================================================
export function circuitBreakerProbe() {
  // Gọi endpoint public để kiểm tra hệ thống vẫn sống
  const healthRes = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: "GET /actuator/health" },
  });

  check(healthRes, {
    "Health: Gateway is responsive": (r) => r.status === 200,
    "Health: Response time < 2000ms": (r) => r.timings.duration < 2000,
  });

  // Gọi endpoint flashcard configs (phụ thuộc Wallet qua Feign)
  const configRes = http.get(`${BASE_URL}/api/flashcards`, {
    tags: { name: "CB GET /api/flashcards" },
  });

  const cbOk = check(configRes, {
    "Circuit Breaker: API is responsive (no timeout)": (r) =>
      r.timings.duration < 5000,
    "Circuit Breaker: No Gateway Error (502/503/504)": (r) =>
      r.status !== 502 && r.status !== 503 && r.status !== 504,
  });

  errorRate.add(!cbOk);
  sleep(1);
}
