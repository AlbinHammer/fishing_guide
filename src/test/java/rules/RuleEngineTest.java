package rules;

import com.example.fishing_guide.rules.Conditions;
import com.example.fishing_guide.rules.Evaluation;
import com.example.fishing_guide.rules.RuleEngine;
import com.example.fishing_guide.rules.Species;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void havsoringIOktoberMedFallandeTryckGerMinstBra() {
        var c = new Conditions(10, 6, 225, -2, 80, false);
        Evaluation result = engine.evaluate(Species.HAVSORING, c);
        assertTrue(result.score() >= 55, "Förväntade Bra eller bättre, fick " + result.rating());
    }

    @Test
    void kraftigVindGerVarning() {
        var c = new Conditions(10, 14, 225, 0, 50, false);
        Evaluation result = engine.evaluate(Species.HAVSORING, c);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Kraftig vind")));
    }

    @Test
    void ogiltigManadKastarException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conditions(13, 5, 0, 0, 50, false));
    }

    @Test
    void kompassstreckBlirRatt() {
        assertEquals("SV", RuleEngine.compass(225));
        assertEquals("N", RuleEngine.compass(359));
    }
}