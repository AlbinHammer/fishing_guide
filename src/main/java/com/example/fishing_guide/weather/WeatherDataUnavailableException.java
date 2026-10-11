package com.example.fishing_guide.weather;

public class WeatherDataUnavailableException extends RuntimeException {
    public WeatherDataUnavailableException(String message) {
        super(message);
    }
}
