package com.example.fishing_guide.weather;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherSnapshotService {
    private static final String SMHI_API = "https://opendata-download-metobs.smhi.se/api/version/1.0";
    private static final String STOCKHOLM_ZONE = "Europe/Stockholm";
    private static final Duration STATION_CACHE_TTL = Duration.ofHours(6);
    private static final Duration HISTORIC_DATA_CACHE_TTL = Duration.ofHours(24);
    private static final Duration RECENT_DATA_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration MAX_TIME_DIFFERENCE = Duration.ofHours(1);
    private static final double MAX_STATION_DISTANCE_KM = 50;
    private static final DateTimeFormatter SMHI_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ObservationParameter> PARAMETERS = List.of(
            new ObservationParameter(1, "temperature_2m", "Lufttemperatur"),
            new ObservationParameter(4, "wind_speed_10m", "Vindhastighet"),
            new ObservationParameter(3, "wind_direction_10m", "Vindriktning"),
            new ObservationParameter(16, "cloud_cover", "Total molnmängd"));

    private final RestClient restClient;
    private final Map<Integer, CachedValue<List<Station>>> stationCache = new ConcurrentHashMap<>();
    private final Map<String, CachedValue<String>> observationCache = new ConcurrentHashMap<>();

    public WeatherSnapshotService() {
        this.restClient = RestClient.builder().build();
    }

    public WeatherSnapshot getSnapshot(double latitude, double longitude, Instant targetTime) {
        Map<String, Double> values = new HashMap<>();
        List<Observation> observations = new ArrayList<>();
        List<String> stations = new ArrayList<>();

        for (ObservationParameter parameter : PARAMETERS) {
            Observation observation = findSmhiObservation(parameter, latitude, longitude, targetTime);
            if (observation == null) {
                continue;
            }
            values.put(parameter.field(), observation.value());
            observations.add(observation);
            stations.add(parameter.name() + ": " + observation.stationName()
                    + " (" + String.format(java.util.Locale.ROOT, "%.1f", observation.distanceKm())
                    + " km, kvalitet " + (observation.quality().isBlank() ? "okänd" : observation.quality()) + ")");
        }

        String source = observations.isEmpty() ? "" : "SMHI";
        Instant capturedAt = observations.stream()
                .min(Comparator.comparingLong(observation ->
                        Math.abs(Duration.between(targetTime, observation.time()).toMillis())))
                .map(Observation::time)
                .orElse(null);

        if (values.get("temperature_2m") == null
                || values.get("wind_speed_10m") == null
                || values.get("wind_direction_10m") == null) {
            CerraObservation cerra = getCerraObservation(latitude, longitude, targetTime);
            for (Map.Entry<String, Double> entry : cerra.values().entrySet()) {
                if (values.get(entry.getKey()) == null && entry.getValue() != null) {
                    values.put(entry.getKey(), entry.getValue());
                    source = source.isEmpty() ? "CERRA via Open-Meteo" : "SMHI + CERRA via Open-Meteo";
                }
            }
            if (capturedAt == null) {
                capturedAt = cerra.capturedAt();
            }
        }

        if (values.get("temperature_2m") == null || values.get("wind_speed_10m") == null
                || values.get("wind_direction_10m") == null) {
            throw new WeatherDataUnavailableException(
                    "Varken SMHI eller CERRA har kompletta vädervärden inom en timme från den valda tidpunkten.");
        }

        return new WeatherSnapshot(
                values.get("temperature_2m"),
                values.get("wind_speed_10m"),
                values.get("wind_direction_10m"),
                values.get("cloud_cover"),
                capturedAt,
                source,
                String.join(", ", stations));
    }

    private Observation findSmhiObservation(
            ObservationParameter parameter,
            double latitude,
            double longitude,
            Instant targetTime) {
        Station station = getStations(parameter.id()).stream()
                .filter(candidate -> candidate.from().isBefore(targetTime.plus(MAX_TIME_DIFFERENCE))
                        && candidate.to().isAfter(targetTime.minus(MAX_TIME_DIFFERENCE)))
                .map(candidate -> candidate.withDistance(distanceKm(
                        latitude, longitude, candidate.latitude(), candidate.longitude())))
                .filter(candidate -> candidate.distanceKm() <= MAX_STATION_DISTANCE_KM)
                .min(Comparator.comparingDouble(Station::distanceKm))
                .orElse(null);
        if (station == null) {
            return null;
        }

        LocalDateTime targetUtc = LocalDateTime.ofInstant(targetTime, ZoneOffset.UTC);
        String period = targetTime.isAfter(Instant.now().atZone(ZoneOffset.UTC).minusMonths(4).toInstant())
                ? "latest-months"
                : "corrected-archive";
        String cacheKey = parameter.id() + ":" + station.id() + ":" + period;
        Duration cacheTtl = period.equals("latest-months") ? RECENT_DATA_CACHE_TTL : HISTORIC_DATA_CACHE_TTL;
        String csv = getCached(observationCache, cacheKey, cacheTtl, () -> restClient.get()
                .uri(SMHI_API + "/parameter/{parameter}/station/{station}/period/{period}/data.csv",
                        parameter.id(), station.id(), period)
                .retrieve()
                .body(String.class));

        return parseObservation(csv, parameter, station, targetUtc);
    }

    private List<Station> getStations(int parameterId) {
        return getCached(stationCache, parameterId, STATION_CACHE_TTL, () -> {
            JsonNode root = restClient.get()
                    .uri(SMHI_API + "/parameter/{parameter}.json", parameterId)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || !root.path("station").isArray()) {
                throw new IllegalStateException("SMHI returnerade ingen stationslista för parameter " + parameterId + ".");
            }

            List<Station> stations = new ArrayList<>();
            for (JsonNode node : root.path("station")) {
                if (!node.path("latitude").isNumber() || !node.path("longitude").isNumber()) {
                    continue;
                }
                stations.add(new Station(
                        node.path("key").asText(),
                        node.path("name").asText(),
                        node.path("latitude").asDouble(),
                        node.path("longitude").asDouble(),
                        Instant.ofEpochMilli(node.path("from").asLong(Long.MIN_VALUE)),
                        Instant.ofEpochMilli(node.path("to").asLong(Long.MAX_VALUE)),
                        0));
            }
            return List.copyOf(stations);
        });
    }

    private CerraObservation getCerraObservation(double latitude, double longitude, Instant targetTime) {
        LocalDate date = targetTime.atZone(ZoneOffset.UTC).toLocalDate();
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("archive-api.open-meteo.com")
                        .path("/v1/archive")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("start_date", date.minusDays(1))
                        .queryParam("end_date", date.plusDays(1))
                        .queryParam("hourly", "temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover")
                        .queryParam("timezone", "auto")
                        .queryParam("wind_speed_unit", "ms")
                        .queryParam("models", "cerra")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        JsonNode hourly = root == null ? null : root.path("hourly");
        if (hourly == null || !hourly.path("time").isArray()) {
            return new CerraObservation(Map.of(), null);
        }
        java.time.ZoneId timezone = java.time.ZoneId.of(
                root.path("timezone").asString(STOCKHOLM_ZONE));

        int closestIndex = -1;
        long closestDifference = Long.MAX_VALUE;
        for (int index = 0; index < hourly.path("time").size(); index++) {
            LocalDateTime localDateTime = LocalDateTime.parse(hourly.path("time").get(index).asText());
            long difference = Math.abs(Duration.between(
                    targetTime,
                    localDateTime.atZone(timezone).toInstant()).toMillis());
            if (difference < closestDifference) {
                closestDifference = difference;
                closestIndex = index;
            }
        }
        if (closestIndex < 0 || closestDifference > MAX_TIME_DIFFERENCE.toMillis()) {
            return new CerraObservation(Map.of(), null);
        }

        Map<String, Double> values = new HashMap<>();
        for (String field : List.of("temperature_2m", "wind_speed_10m", "wind_direction_10m", "cloud_cover")) {
            JsonNode series = hourly.path(field);
            if (series.isArray() && series.get(closestIndex).isNumber()) {
                values.put(field, series.get(closestIndex).asDouble());
            }
        }
        Instant capturedAt = LocalDateTime.parse(hourly.path("time").get(closestIndex).asText())
                .atZone(timezone).toInstant();
        return new CerraObservation(values, capturedAt);
    }

    static Observation parseObservation(
            String csv,
            ObservationParameter parameter,
            Station station,
            LocalDateTime targetUtc) {
        if (csv == null) {
            return null;
        }
        String[] lines = csv.split("\\R");
        int headerIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].startsWith("Datum;Tid (UTC);")) {
                headerIndex = index;
                break;
            }
        }
        if (headerIndex < 0) {
            return null;
        }

        Observation closest = null;
        long closestDifference = Long.MAX_VALUE;
        int bestQuality = -1;
        for (int index = headerIndex + 1; index < lines.length; index++) {
            String[] columns = lines[index].split(";", -1);
            if (columns.length < 3) {
                continue;
            }
            try {
                LocalDateTime time = LocalDateTime.parse(columns[0] + " " + columns[1], SMHI_TIME_FORMAT);
                long difference = Math.abs(Duration.between(targetUtc, time).toMillis());
                String quality = columns.length > 3 ? columns[3].trim() : "";
                int qualityRank = switch (quality) {
                    case "G" -> 2;
                    case "Y" -> 1;
                    case "" -> 0;
                    default -> -1;
                };
                if (difference > MAX_TIME_DIFFERENCE.toMillis() || qualityRank < 0
                        || qualityRank < bestQuality
                        || qualityRank == bestQuality && difference >= closestDifference) {
                    continue;
                }
                double value = Double.parseDouble(columns[2].replace(',', '.'));
                closest = new Observation(parameter.field(), value, time.toInstant(ZoneOffset.UTC),
                        station.name(), station.distanceKm(), quality);
                closestDifference = difference;
                bestQuality = qualityRank;
            } catch (java.time.DateTimeException | NumberFormatException ignored) {
                // Skip CSV metadata, missing values, and malformed rows.
            }
        }
        return closest;
    }

    private static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDifference = Math.toRadians(latitude2 - latitude1);
        double longitudeDifference = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private <K, V> V getCached(
            Map<K, CachedValue<V>> cache,
            K key,
            Duration ttl,
            java.util.function.Supplier<V> loader) {
        CachedValue<V> cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        V value = loader.get();
        cache.put(key, new CachedValue<>(value, Instant.now().plus(ttl)));
        return value;
    }

    record ObservationParameter(int id, String field, String name) {
    }

    record Station(
            String id,
            String name,
            double latitude,
            double longitude,
            Instant from,
            Instant to,
            double distanceKm) {
        private Station withDistance(double distance) {
            return new Station(id, name, latitude, longitude, from, to, distance);
        }
    }

    record Observation(
            String field,
            double value,
            Instant time,
            String stationName,
            double distanceKm,
            String quality) {
    }

    private record CachedValue<V>(V value, Instant expiresAt) {
    }

    private record CerraObservation(Map<String, Double> values, Instant capturedAt) {
    }
}
