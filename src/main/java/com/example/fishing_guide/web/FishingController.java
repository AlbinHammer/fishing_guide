package com.example.fishing_guide.web;

import com.example.fishing_guide.rules.Conditions;
import com.example.fishing_guide.rules.Evaluation;
import com.example.fishing_guide.rules.RuleEngine;
import com.example.fishing_guide.rules.Species;
import com.example.fishing_guide.weather.WeatherDataUnavailableException;
import com.example.fishing_guide.weather.WeatherSnapshot;
import com.example.fishing_guide.weather.WeatherSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class FishingController {

    private final RuleEngine ruleEngine;
    private final WeatherSnapshotService weatherSnapshotService;

    public FishingController(RuleEngine ruleEngine, WeatherSnapshotService weatherSnapshotService) {
        this.ruleEngine = ruleEngine;
        this.weatherSnapshotService = weatherSnapshotService;
    }

    @PostMapping("/evaluate")
    public Evaluation evaluate(@RequestBody EvaluationRequest request) {
        if (request == null || request.conditions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Art och väderförhållanden krävs.");
        }

        return ruleEngine.evaluate(parseSpecies(request.species()), request.conditions());
    }

    @PostMapping("/evaluate/forecast")
    public List<Evaluation> evaluateForecast(@RequestBody ForecastEvaluationRequest request) {
        if (request == null || request.conditions() == null || request.conditions().isEmpty()
                || request.conditions().size() > 48
                || request.conditions().stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mellan 1 och 48 giltiga timförhållanden krävs.");
        }

        Species species = parseSpecies(request.species());
        return request.conditions().stream()
                .map(conditions -> ruleEngine.evaluate(species, conditions))
                .toList();
    }

    @PostMapping("/weather/snapshot")
    public WeatherSnapshot weatherSnapshot(@RequestBody WeatherSnapshotRequest request) {
        if (request == null || request.latitude() == null || !Double.isFinite(request.latitude()) || request.latitude() < -90
                || request.latitude() > 90 || request.longitude() == null || !Double.isFinite(request.longitude())
                || request.longitude() < -180 || request.longitude() > 180 || request.targetTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giltiga koordinater och en fångsttid krävs.");
        }
        Instant earliestSupportedTime = LocalDate.now(ZoneOffset.UTC).minusYears(5)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        if (request.targetTime().isBefore(earliestSupportedTime)
                || request.targetTime().isAfter(Instant.now().plus(1, ChronoUnit.DAYS))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Väderdata kan hämtas högst fem år bakåt och en dag framåt.");
        }
        try {
            return weatherSnapshotService.getSnapshot(request.latitude(), request.longitude(), request.targetTime());
        } catch (WeatherDataUnavailableException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Väderkällan svarade inte som förväntat.", error);
        }
    }

    private Species parseSpecies(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En giltig art krävs.");
        }

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "ABBORRE" -> Species.ABBORRE;
            case "GADDA" -> Species.GADDA;
            case "HAVSORING" -> Species.HAVSORING;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Okänd fiskart.");
        };
    }

    public record EvaluationRequest(String species, Conditions conditions) {
    }

    public record ForecastEvaluationRequest(String species, List<Conditions> conditions) {
    }

    public record WeatherSnapshotRequest(Double latitude, Double longitude, Instant targetTime) {
    }
}
