package com.example.fishing_guide.web;

import com.example.fishing_guide.rules.Conditions;
import com.example.fishing_guide.rules.RuleEngine;
import com.example.fishing_guide.weather.WeatherSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FishingControllerTest {

    private final FishingController controller = new FishingController(new RuleEngine(), mock(WeatherSnapshotService.class));

    @Test
    void evaluatesSwedishSpeciesName() {
        var request = new FishingController.EvaluationRequest(
                "havsöring",
                new Conditions(10, 6, 225, -2, 80, false));

        var result = controller.evaluate(request);

        assertEquals("Havsöring", result.species());
        assertTrue(result.score() >= 55);
    }

    @Test
    void keepsStrongWindWarningInApiResponse() {
        var request = new FishingController.EvaluationRequest(
                "gädda",
                new Conditions(10, 14, 225, 0, 50, false));

        var result = controller.evaluate(request);

        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Kraftig vind")));
    }

    @Test
    void rejectsUnknownSpecies() {
        var request = new FishingController.EvaluationRequest(
                "lax",
                new Conditions(10, 5, 0, 0, 50, false));

        assertThrows(ResponseStatusException.class, () -> controller.evaluate(request));
    }

    @Test
    void evaluatesForecastHoursWithTheSameRuleEngine() {
        var conditions = List.of(
                new Conditions(10, 6, 225, -2, 80, false),
                new Conditions(10, 14, 225, 0, 50, true));

        var results = controller.evaluateForecast(
                new FishingController.ForecastEvaluationRequest("gädda", conditions));

        assertEquals(2, results.size());
        assertEquals(controller.evaluate(
                new FishingController.EvaluationRequest("gädda", conditions.get(0))).score(),
                results.get(0).score());
        assertTrue(results.get(1).warnings().stream()
                .anyMatch(warning -> warning.contains("Kraftig vind")));
    }

    @Test
    void rejectsEmptyForecast() {
        assertThrows(ResponseStatusException.class, () -> controller.evaluateForecast(
                new FishingController.ForecastEvaluationRequest("abborre", List.of())));
    }

    @Test
    void rejectsWeatherCoordinatesOutsideValidRange() {
        assertThrows(ResponseStatusException.class, () -> controller.weatherSnapshot(
                new FishingController.WeatherSnapshotRequest(91.0, 18.0, Instant.now())));
    }

    @Test
    void rejectsWeatherRequestsOlderThanFiveYears() {
        assertThrows(ResponseStatusException.class, () -> controller.weatherSnapshot(
                new FishingController.WeatherSnapshotRequest(59.0, 18.0, Instant.now().minusSeconds(6L * 365 * 24 * 60 * 60))));
    }
}
