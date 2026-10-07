package com.example.fishing_guide.rules;

/**
 * Förhållanden för en plats och en tidpunkt.
 *
 * @param month          1-12
 * @param windSpeed      m/s
 * @param windDir        grader, varifrån vinden blåser (0-360)
 * @param pressureTrend  hPa-förändring senaste 3 h (negativ = fallande)
 * @param cloudCover     0-100 (%)
 * @param dawnOrDusk     true vid gryning/skymning
 */
public record Conditions(
        int month,
        double windSpeed,
        double windDir,
        double pressureTrend,
        int cloudCover,
        boolean dawnOrDusk) {

    // Compact constructor: validera indata så att felaktiga värden aldrig når regelmotorn
    public Conditions {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month måste vara 1-12, fick " + month);
        }
        if (windSpeed < 0) {
            throw new IllegalArgumentException("windSpeed kan inte vara negativ");
        }
        if (cloudCover < 0 || cloudCover > 100) {
            throw new IllegalArgumentException("cloudCover måste vara 0-100, fick " + cloudCover);
        }
    }
}