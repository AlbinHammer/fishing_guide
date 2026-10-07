package com.example.fishing_guide.rules;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleEngine {

    private static final String[] COMPASS = {"N", "NO", "O", "SO", "S", "SV", "V", "NV"};

    public Evaluation evaluate(Species species, Conditions c) {
        int score = 50;
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>(species.notes());

        // Säsong
        if (species.bestMonths().contains(c.month())) {
            score += 20;
            reasons.add("Bra säsong för arten");
        } else if (species.okMonths().contains(c.month())) {
            score += 5;
            reasons.add("Okej säsong");
        } else {
            score -= 20;
            reasons.add("Svag säsong");
        }

        // Vind
        if (c.windSpeed() >= species.windIdealMin() && c.windSpeed() <= species.windIdealMax()) {
            score += 15;
            reasons.add("Lagom vind (%.0f m/s, %s)".formatted(c.windSpeed(), compass(c.windDir())));
        } else if (c.windSpeed() < species.windIdealMin()) {
            score -= 5;
            reasons.add("Nästan vindstilla");
        } else if (c.windSpeed() <= species.windLimit()) {
            score -= 10;
            reasons.add("Ganska mycket vind");
        } else {
            score -= 40;
            warnings.add("Kraftig vind (%.0f m/s): avråds, särskilt med liten båt".formatted(c.windSpeed()));
        }

        // Lufttryck
        if (c.pressureTrend() <= -1) {
            if (species.prefersFallingPressure()) {
                score += 10;
                reasons.add("Fallande lufttryck, ofta bra bett");
            } else {
                score += 3;
                reasons.add("Svagt fallande lufttryck");
            }
        } else if (c.pressureTrend() >= 2) {
            score -= 8;
            reasons.add("Snabbt stigande lufttryck, ofta trögare bett");
        }

        // Moln och ljus
        if (species.prefersOvercast() && c.cloudCover() >= 60) {
            score += 8;
            reasons.add("Mulet, bra ljus för fisket");
        }
        if (c.dawnOrDusk()) {
            score += 8;
            reasons.add("Gryning/skymning");
        }

        score = Math.max(0, Math.min(100, score));
        String rating = score >= 75 ? "Mycket bra"
                : score >= 55 ? "Bra"
                : score >= 40 ? "Okej"
                : "Dåligt";

        return new Evaluation(species.displayName(), score, rating, reasons, methodsFor(species, c), warnings);
    }

    private List<String> methodsFor(Species species, Conditions c) {
        List<String> methods = new ArrayList<>();
        switch (species) {
            case HAVSORING -> {
                if (c.windSpeed() >= 4) {
                    methods.add("Spinnfiske från båt längs uddar och grund (1–3 m), driv med vinden mot land");
                } else {
                    methods.add("Lugnt: fiska långt och smalt, små skeddrag/wobblers, eller flugfiske i gryning/skymning");
                }
                if (c.windSpeed() >= 6) {
                    methods.add("Alternativ: trolling med båt längs kanter och sluttningar");
                }
            }
            case ABBORRE -> {
                if (c.month() >= 8 || c.month() <= 5) {
                    methods.add("Jigg/skakhuvud vid kanter, bryggor och sjunkande bottnar");
                }
                if (c.windSpeed() < 5) {
                    methods.add("Lugnt: småspinnare, drop-shot eller wobbler vid vass och strukturer");
                } else {
                    methods.add("Vindigt: fiska på läsidan eller djupare med tyngre jiggar");
                }
            }
            case GADDA -> {
                if (c.month() >= 4 && c.month() <= 6) {
                    methods.add("Grunda vikar och vass: wobbler, spinnare eller jerkbait");
                } else if (c.month() >= 7 && c.month() <= 8) {
                    methods.add("Sommar: fiska kanter och djupare vass med gummibete eller större wobbler");
                } else if (c.month() >= 9) {
                    methods.add("Höst: större bete (jerk, stora gummibeten) vid kanter och utanför vass");
                }
            }
        }
        return methods;
    }

    /** Vindriktning i grader (varifrån vinden blåser) till kompassstreck. */
    public static String compass(double deg) {
        double normalized = ((deg % 360) + 360) % 360;
        return COMPASS[(int) Math.round(normalized / 45.0) % 8];
    }
}