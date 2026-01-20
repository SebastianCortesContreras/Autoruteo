package com.example.routing;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search?format=json&q=";
    private static final java.util.Map<String, double[]> HARDCODED_LOCATIONS = new java.util.HashMap<>();

    static {
        // Cumara
        HARDCODED_LOCATIONS.put("Cra. 45 #192 - 18, Bogotá", new double[]{4.7715, -74.0445});
        // Boyaca
        HARDCODED_LOCATIONS.put("Cl. 81b #72-32, Bogotá", new double[]{4.6965, -74.0845});
        // Villa del Rio
        HARDCODED_LOCATIONS.put("Carrera 63 # 57G Calle 47 Sur, Bogotá", new double[]{4.5935, -74.1555});
        // Villa Santos / Barranquilla
        HARDCODED_LOCATIONS.put("Cra. 51B #106-200, Riomar, Barranquilla, Atlántico", new double[]{11.0155, -74.8305});
    }

    public double[] geocodeAddress(String address) {
        try {
            if (address == null || address.trim().isEmpty()) {
                return null;
            }

            // Check hardcoded locations first
            if (HARDCODED_LOCATIONS.containsKey(address)) {
                System.out.println("Using hardcoded coordinates for: " + address);
                return HARDCODED_LOCATIONS.get(address);
            }

            // Nominatim requires a User-Agent
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "RoutingAutomationApp/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = NOMINATIM_API + address.replace(" ", "+");
            
            // Respect API usage policy (1 request per second max recommended)
            Thread.sleep(1000); 

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.isArray() && root.size() > 0) {
                    JsonNode firstResult = root.get(0);
                    double lat = firstResult.get("lat").asDouble();
                    double lon = firstResult.get("lon").asDouble();
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception e) {
            System.err.println("Error geocoding address '" + address + "': " + e.getMessage());
        }
        return null;
    }
}
