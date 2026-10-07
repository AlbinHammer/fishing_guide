package com.example.fishing_guide.web;

import com.example.fishing_guide.rules.Conditions;
import com.example.fishing_guide.rules.Evaluation;
import com.example.fishing_guide.rules.RuleEngine;
import com.example.fishing_guide.rules.Species;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class FishingController {

    private final RuleEngine ruleEngine;

    public FishingController(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @PostMapping("/evaluate")
    public Evaluation evaluate(@RequestBody EvaluationRequest request) {
        if (request == null || request.conditions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Art och väderförhållanden krävs.");
        }

        return ruleEngine.evaluate(parseSpecies(request.species()), request.conditions());
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
}
