package com.example.fishing_guide.weather;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeatherSnapshotServiceTest {

    private final WeatherSnapshotService.ObservationParameter temperature =
            new WeatherSnapshotService.ObservationParameter(1, "temperature_2m", "Lufttemperatur");
    private final WeatherSnapshotService.Station station = new WeatherSnapshotService.Station(
            "1", "Teststation", 59.0, 18.0,
            Instant.parse("2020-01-01T00:00:00Z"),
            Instant.parse("2030-01-01T00:00:00Z"), 4.0);

    @Test
    void parsesNearestSmhiUtcObservationWithinOneHour() {
        String csv = """
                Datum;Tid (UTC);Lufttemperatur;Kvalitet
                2026-10-11;18:00:00;8.4;G
                2026-10-11;17:00:00;7.9;G
                """;

        var result = WeatherSnapshotService.parseObservation(
                csv, temperature, station, LocalDateTime.of(2026, 10, 11, 18, 40));

        assertEquals(8.4, result.value());
        assertEquals(Instant.parse("2026-10-11T18:00:00Z"), result.time());
    }

    @Test
    void ignoresObservationMoreThanOneHourAway() {
        String csv = """
                Datum;Tid (UTC);Lufttemperatur;Kvalitet
                2026-10-11;16:00:00;8.4;G
                """;

        var result = WeatherSnapshotService.parseObservation(
                csv, temperature, station, LocalDateTime.of(2026, 10, 11, 18, 0));

        assertNull(result);
    }

    @Test
    void prefersApprovedSmhiValuesOverCloserUnreviewedReadings() {
        String csv = """
                Datum;Tid (UTC);Lufttemperatur;Kvalitet
                2026-10-11;18:20:00;8.4;Y
                2026-10-11;18:00:00;7.9;G
                """;

        var result = WeatherSnapshotService.parseObservation(
                csv, temperature, station, LocalDateTime.of(2026, 10, 11, 18, 30));

        assertEquals(7.9, result.value());
        assertEquals("G", result.quality());
    }
}
