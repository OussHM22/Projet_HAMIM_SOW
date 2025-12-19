import javax.json.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class JsonFlightFiller {
    private final World world;

    public JsonFlightFiller(World world) {
        this.world = world;
    }

    /* ---------- Parsing générique d'une réponse JSON aviationstack ---------- */

    private List<Flight> parseFlights(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) return List.of();
        List<Flight> flights = new ArrayList<>();

        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            JsonObject root = reader.readObject();
            JsonArray data = root.getJsonArray("data");
            if (data == null) return List.of();

            for (JsonValue v : data) {
                if (!(v instanceof JsonObject)) continue;
                JsonObject obj = (JsonObject) v;

                JsonObject dep = obj.getJsonObject("departure");
                JsonObject arr = obj.getJsonObject("arrival");
                JsonObject airline = obj.getJsonObject("airline");
                JsonObject flight = obj.getJsonObject("flight");

                String depIata = stringOf(dep, "iata");
                String arrIata = stringOf(arr, "iata");
                if (depIata == null || arrIata == null) continue;

                String airlineName = stringOf(airline, "name");
                String flightNumber = stringOf(flight, "number");

                Aeroport origin = world.findByCode(depIata);
                Aeroport dest   = world.findByCode(arrIata);
                if (origin != null && dest != null) {
                    flights.add(new Flight(origin, dest,
                            airlineName != null ? airlineName : "",
                            flightNumber != null ? flightNumber : ""));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flights;
    }

    private static String stringOf(JsonObject o, String key) {
        if (o == null || !o.containsKey(key) || o.isNull(key)) return null;
        JsonValue val = o.get(key);
        return (val.getValueType() == JsonValue.ValueType.STRING)
                ? ((JsonString) val).getString() : null;
    }

    /* ---------- Lecture depuis une ressource locale (déjà utilisée en partie 6) ---------- */

    public Set<Aeroport> distinctOriginsArrivingToFromResource(String resourcePath, String iataArrival) {
        String json = readResourceAsString(resourcePath);
        return distinctOriginsArrivingToFromJson(json, iataArrival);
    }

    public Set<Aeroport> distinctOriginsArrivingToFromJson(String json, String iataArrival) {
        List<Flight> flights = parseFlights(json);
        Set<Aeroport> origins = new HashSet<>();
        for (Flight f : flights) {
            if (f.getDestination().getIata().equalsIgnoreCase(iataArrival)) {
                origins.add(f.getOrigin());
            }
        }
        return origins;
    }

    private static String readResourceAsString(String resourcePath) {
        try (InputStream is = JsonFlightFiller.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    /* ---------- ACCÈS LIVE via HttpClient (Partie 3 du PDF) ---------- */

    public Set<Aeroport> fetchLiveOriginsArrivingTo(String iataArrival, String apiKey) throws Exception {
        String url = "http://api.aviationstack.com/v1/flights?access_key="
                + apiKey + "&arr_iata=" + iataArrival; // URL indiquée dans l'énoncé. :contentReference[oaicite:3]{index=3}

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " : " + resp.body());
        }

        return distinctOriginsArrivingToFromJson(resp.body(), iataArrival);
    }
}
