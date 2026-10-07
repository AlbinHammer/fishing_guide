package com.example.fishing_guide.rules;

import java.util.List;

/** Resultatet av en bedömning. Spring serialiserar den automatiskt till JSON. */
public record Evaluation(
        String species,
        int score,
        String rating,
        List<String> reasons,
        List<String> methods,
        List<String> warnings) {
}