package com.hien.marketplace.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint for the API.
 *
 * Why a custom health endpoint instead of just using Actuator?
 * - /api/health is more intuitive for API consumers (REST convention)
 * - /actuator/health is meant for ops/monitoring, not application users
 * - We keep both: this one is simple, Actuator shows db/redis details
 *
 * @RestController = @Controller + @ResponseBody (returns JSON, not HTML views)
 * @GetMapping = shorthand for @RequestMapping(method = GET)
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "service-marketplace");
    }
}
