package com.tests.api.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps eventhub's unauthenticated {@code /health} and {@code /config} endpoints. Unlike
 * {@link EventService}/{@link BookingService}, neither call requires a bearer token
 * (verified live: both return 200 with no {@code Authorization} header at all).
 */
public final class SystemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemService.class);

    public ApiResponse getHealth() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/health"));
        LOGGER.info("Health check (status {})", response.statusCode());
        return response;
    }

    public ApiResponse getConfig() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/config"));
        LOGGER.info("Fetched public config (status {})", response.statusCode());
        return response;
    }
}
