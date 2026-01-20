package com.example.routing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.Row;
import tech.tablesaw.io.xlsx.XlsxReadOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

import com.example.routing.domain.Customer;
import com.example.routing.domain.Location;
import com.example.routing.domain.Vehicle;
import com.example.routing.domain.VehicleRoutingSolution;
import com.example.routing.service.RouteOptimizationService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final GeocodingService geocodingService;
    private final RouteOptimizationService routeOptimizationService;

    public RouteController(GeocodingService geocodingService, RouteOptimizationService routeOptimizationService) {
        this.geocodingService = geocodingService;
        this.routeOptimizationService = routeOptimizationService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadRoutes(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "depotAddress", required = false) String depotAddress,
            @RequestParam(value = "carryCount", required = false, defaultValue = "10") Integer carryCount,
            @RequestParam(value = "nhrCount", required = false, defaultValue = "10") Integer nhrCount,
            @RequestParam(value = "nprCount", required = false, defaultValue = "5") Integer nprCount) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Por favor selecciona un archivo.");
        }

        try {
            // Procesar dirección del depósito si existe
            Location depotLocation = null;
            if (depotAddress != null && !depotAddress.trim().isEmpty()) {
                double[] depotCoords = geocodingService.geocodeAddress(depotAddress);
                if (depotCoords != null) {
                    depotLocation = new Location(depotCoords[0], depotCoords[1]);
                } else {
                    // Si falla geocoding, podríamos retornar error o advertencia, 
                    // por ahora logueamos y dejamos que use el centroide o lógica por defecto si se prefiere
                    System.out.println("No se pudo geocodificar el depósito: " + depotAddress);
                    // Opcional: Retornar error al usuario
                    // return ResponseEntity.badRequest().body("No se pudo encontrar la ubicación del depósito: " + depotAddress);
                }
            }

            // Guardar archivo temporalmente
            Path tempDir = Files.createTempDirectory("routes_upload");
            File tempFile = tempDir.resolve(file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);

            // Leer Excel usando Tablesaw
            XlsxReadOptions options = XlsxReadOptions.builder(tempFile).build();
            Table table = Table.read().usingOptions(options);
            
            // Limpiar archivo temporal
            tempFile.delete();

            // Mapeo flexible de columnas
            System.out.println("Columnas detectadas en el archivo: " + table.columnNames());
            
            List<Customer> customers = new ArrayList<>();
            
            String colId = findColumn(table, "Pedido", "Order", "ID", "Id Pedido", "Codigo", "Código");
            String colClient = findColumn(table, "Cliente", "Client", "Customer", "Nombre", "Razon Social", "Razón Social");
            String colAddress = findColumn(table, "Direccion", "Dirección", "Address", "Ubicacion", "Ubicación", "Domicilio", "Calle", "Dir");
            String colLat = findColumn(table, "Latitud", "Lat", "Latitude", "Y", "Lat.");
            String colLon = findColumn(table, "Longitud", "Lon", "Lng", "Longitude", "X", "Long", "Long.");
            String colWeight = findColumn(table, "Peso", "Weight", "Kg", "Kg.", "Kgs", "Carga", "Masa", "Volumen", "Peso Total (KG)", "zubale");
            String colWindow = findColumn(table, "Franja Entrega", "Franja", "Horario", "Window", "Time Window", "Ventana");

            System.out.println("Mapeo de columnas: ");
            System.out.println("Dirección -> " + colAddress);
            System.out.println("Peso -> " + colWeight);
            System.out.println("Franja -> " + colWindow);

            for (Row row : table) {
                String id = (colId != null) ? getRowString(row, colId) : UUID.randomUUID().toString();
                String client = (colClient != null) ? getRowString(row, colClient) : "Desconocido";
                String address = (colAddress != null) ? getRowString(row, colAddress) : "";
                Double weight = (colWeight != null) ? getRowDouble(row, colWeight) : 10.0;
                double roundedWeight = Math.round(weight != null ? weight : 10.0);
                
                // Parse Window
                String rawWindow = (colWindow != null) ? getRowString(row, colWindow) : "";
                String deliveryWindow = normalizeWindow(rawWindow);

                // Obtener Lat/Lon
                Double lat = (colLat != null) ? getRowDouble(row, colLat) : 0.0;
                Double lon = (colLon != null) ? getRowDouble(row, colLon) : 0.0;

                // Validación y Geocoding
                if (!isValidCoordinate(lat, lon) && !address.isEmpty()) {
                    double[] coords = geocodingService.geocodeAddress(address);
                    if (coords != null) {
                        lat = coords[0];
                        lon = coords[1];
                    }
                }

                if (isValidCoordinate(lat, lon)) {
                    customers.add(new Customer(id, new Location(lat, lon), roundedWeight, client, address, deliveryWindow));
                }
            }

            if (customers.isEmpty()) {
                return ResponseEntity.badRequest().body("No se encontraron pedidos válidos con coordenadas.");
            }

            // Optimizar Rutas (Pasando la ubicación del depósito)
            VehicleRoutingSolution solution = routeOptimizationService.optimizeRoutes(customers, depotLocation, carryCount, nhrCount, nprCount);

            // Preparar respuesta simplificada
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> routes = new ArrayList<>();

            for (Vehicle vehicle : solution.getVehicleList()) {
                if (!vehicle.getCustomers().isEmpty()) {
                    Map<String, Object> route = new HashMap<>();
                    route.put("vehicleId", vehicle.getId());
                    route.put("vehicleType", vehicle.getType());
                    route.put("capacity", vehicle.getCapacity());
                    route.put("totalLoad", vehicle.getTotalDemand());
                    route.put("totalDistance", vehicle.getTotalDistanceMeters());
                    route.put("totalDistanceKm", String.format("%.2f km", vehicle.getTotalDistanceMeters() / 1000.0));
                    route.put("comments", vehicle.getComments()); // Incluir comentarios
                    route.put("routeStatus", vehicle.getRouteStatus()); // Usar campo dedicado
                    // Calcular distancia acumulada por pedido
                    List<Map<String, Object>> ordersWithInfo = new ArrayList<>();
                    Location previousLocation = vehicle.getDepot().getLocation();
                    long accumulatedDistance = 0;

                    for (Customer customer : vehicle.getCustomers()) {
                        long legDistance = previousLocation.getDistanceTo(customer.getLocation());
                        accumulatedDistance += legDistance;
                        
                        Map<String, Object> orderInfo = new HashMap<>();
                        orderInfo.put("id", customer.getId());
                        orderInfo.put("name", customer.getName());
                        orderInfo.put("address", customer.getAddress());
                        orderInfo.put("demand", customer.getDemand());
                        orderInfo.put("deliveryWindow", customer.getDeliveryWindow());
                        orderInfo.put("location", customer.getLocation());
                        orderInfo.put("accumulatedDistanceKm", String.format("%.2f km", accumulatedDistance / 1000.0));
                        
                        ordersWithInfo.add(orderInfo);
                        previousLocation = customer.getLocation();
                    }
                    route.put("orders", ordersWithInfo);

                    routes.add(route);
                }
            }
            
            response.put("totalVehiclesUsed", routes.size());
            response.put("routes", routes);
            response.put("unassigned", customers.size() - solution.getVehicleList().stream().mapToInt(v -> v.getCustomers().size()).sum());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al procesar el archivo: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error en el formato de datos o en la optimización: " + e.getMessage());
        }
    }

    private boolean isValidCoordinate(Double lat, Double lon) {
        return lat != null && lon != null && lat != 0.0 && lon != 0.0 && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private String normalizeWindow(String raw) {
        if (raw == null) return "WINDOW_2";
        String normalized = raw.trim().toLowerCase();
        
        // Log para depuración
        // System.out.println("Normalizando franja: '" + raw + "' -> '" + normalized + "'");

        // Window 1: "08:00 - 11:59"
        // Buscar patrones que indiquen mañana temprano hasta mediodía
        if (normalized.contains("08:00") && normalized.contains("11:59")) {
            return "WINDOW_1";
        }
        
        // Window 3: "12:30 - 16:59" or "12:00 - 16:59"
        // Buscar patrones de tarde
        if ((normalized.contains("12:00") || normalized.contains("12:30")) && normalized.contains("16:59")) {
             return "WINDOW_3";
        }
        
        // Si no coincide exactamente, intentar inferir
        // Si termina antes de las 12:00 -> Window 1
        // Si empieza después de las 12:00 -> Window 3
        
        // Default (incluye 08:01 - 17:00)
        return "WINDOW_2";
    }

    // Método auxiliar para buscar nombres de columnas (case-insensitive)
    private String findColumn(Table table, String... aliases) {
        List<String> columnNames = table.columnNames();
        for (String alias : aliases) {
            for (String colName : columnNames) {
                if (colName.trim().equalsIgnoreCase(alias.trim())) {
                    return colName;
                }
            }
        }
        return null;
    }

    // Métodos seguros para obtener datos
    private String getRowString(Row row, String colName) {
        try {
            return row.getString(colName);
        } catch (Exception e) {
            // Si falla como string, intenta como objeto y convierte
            Object val = row.getObject(colName);
            return val != null ? val.toString() : "";
        }
    }

    private Double getRowDouble(Row row, String colName) {
        try {
            return row.getDouble(colName);
        } catch (Exception e) {
            try {
                // Intentar parsear string a double si la columna no es numérica
                String val = row.getString(colName);
                if (val != null && !val.trim().isEmpty()) {
                    return Double.parseDouble(val.replace(",", ".")); // Manejar decimales con coma
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }
}
