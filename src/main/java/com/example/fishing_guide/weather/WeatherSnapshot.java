package com.example.fishing_guide.weather;

import java.time.Instant;

public record WeatherSnapshot(
        Double temperature_2m,
        Double wind_speed_10m,
        Double wind_direction_10m,
        Double cloud_cover,
        Instant capturedAt,
        String source,
        String station) {
}
