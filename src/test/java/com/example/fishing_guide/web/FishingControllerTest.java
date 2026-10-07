package com.example.fishing_guide.web;

import com.example.fishing_guide.rules.Conditions;
import com.example.fishing_guide.rules.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingControllerTest {

    private final FishingController controller = new FishingController(new RuleEngine());

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
}
