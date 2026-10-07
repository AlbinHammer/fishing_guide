package com.example.fishing_guide.rules;

import java.util.List;
import java.util.Set;

public enum Species {

    HAVSORING("Havsöring",
            Set.of(3, 4, 5, 9, 10, 11), Set.of(6, 7, 8),
            3, 8, 12,
            true, true,
            List.of("Kolla minimimått, fettfinneklippt-regler och fiskeförbud vid åmynningar.")),

    ABBORRE("Abborre",
            Set.of(5, 6, 8, 9, 10), Set.of(4, 7, 11),
            2, 7, 10,
            false, true,
            List.of("Abborre står ofta i stim: hittar du en, stanna och fiska av området.")),

    GADDA("Gädda",
            Set.of(4, 5, 9, 10, 11), Set.of(3, 6, 7, 8),
            2, 8, 11,
            true, true,
            List.of("Fredningstider och regler skiljer sig mellan områden: kontrollera lokalt."));

    private final String displayName;
    private final Set<Integer> bestMonths;
    private final Set<Integer> okMonths;
    private final double windIdealMin;   // m/s
    private final double windIdealMax;   // m/s
    private final double windLimit;      // m/s, över detta avråds fiske
    private final boolean prefersFallingPressure;
    private final boolean prefersOvercast;
    private final List<String> notes;

    Species(String displayName, Set<Integer> bestMonths, Set<Integer> okMonths,
            double windIdealMin, double windIdealMax, double windLimit,
            boolean prefersFallingPressure, boolean prefersOvercast, List<String> notes) {
        this.displayName = displayName;
        this.bestMonths = bestMonths;
        this.okMonths = okMonths;
        this.windIdealMin = windIdealMin;
        this.windIdealMax = windIdealMax;
        this.windLimit = windLimit;
        this.prefersFallingPressure = prefersFallingPressure;
        this.prefersOvercast = prefersOvercast;
        this.notes = notes;
    }

    public String displayName() { return displayName; }
    public Set<Integer> bestMonths() { return bestMonths; }
    public Set<Integer> okMonths() { return okMonths; }
    public double windIdealMin() { return windIdealMin; }
    public double windIdealMax() { return windIdealMax; }
    public double windLimit() { return windLimit; }
    public boolean prefersFallingPressure() { return prefersFallingPressure; }
    public boolean prefersOvercast() { return prefersOvercast; }
    public List<String> notes() { return notes; }
}