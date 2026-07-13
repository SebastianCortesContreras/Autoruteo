package com.example.routing;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.Row;

import java.io.File;
import java.io.FileInputStream;
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

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

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
        log.info("Solicitud de carga recibida. archivo={}, size={}, depotAddress={}, carryCount={}, nhrCount={}, nprCount={}",
                file.getOriginalFilename(), file.getSize(), depotAddress, carryCount, nhrCount, nprCount);
        if (file.isEmpty()) {
            log.warn("Se recibio un archivo vacio en /api/routes/upload");
            return ResponseEntity.badRequest().body("Por favor selecciona un archivo.");
        }

        try {
            // Procesar dirección del depósito si existe
            Location depotLocation = null;
            if (depotAddress != null && !depotAddress.trim().isEmpty()) {
                log.info("Intentando geocodificar deposito: {}", depotAddress);
                double[] depotCoords = geocodingService.geocodeAddress(depotAddress);
                if (depotCoords != null) {
                    depotLocation = new Location(depotCoords[0], depotCoords[1]);
                    log.info("Deposito geocodificado correctamente: lat={}, lon={}", depotCoords[0], depotCoords[1]);
                } else {
                    // Si falla geocoding, podríamos retornar error o advertencia, 
                    // por ahora logueamos y dejamos que use el centroide o lógica por defecto si se prefiere
                    log.warn("No se pudo geocodificar el deposito: {}. Se usara la logica de respaldo.", depotAddress);
                    // Opcional: Retornar error al usuario
                    // return ResponseEntity.badRequest().body("No se pudo encontrar la ubicación del depósito: " + depotAddress);
                }
            }

            // Guardar archivo temporalmente
            Path tempDir = Files.createTempDirectory("routes_upload");
            File tempFile = tempDir.resolve(file.getOriginalFilename()).toFile();
            log.info("Guardando archivo temporal en: {}", tempFile.getAbsolutePath());
            file.transferTo(tempFile);

            // Leer Excel usando Apache POI directamente para evitar errores de parseo de tipos
            Table table = readExcelAsStrings(tempFile);
            log.info("Archivo leido correctamente. Filas={}, columnas={}", table.rowCount(), table.columnCount());
            
            // Limpiar archivo temporal
            boolean deleted = tempFile.delete();
            log.info("Archivo temporal eliminado={} path={}", deleted, tempFile.getAbsolutePath());

            // Mapeo flexible de columnas
            log.info("Columnas detectadas en el archivo: {}", table.columnNames());
            
            List<Customer> customers = new ArrayList<>();
            
            String colId = findColumn(table, "Pedido", "Order", "ID", "Id Pedido", "Codigo", "Código");
            String colClient = findColumn(table, "Cliente", "Client", "Customer", "Nombre", "Razon Social", "Razón Social");
            String colAddress = findColumn(table, "Direccion", "Dirección", "Address", "Ubicacion", "Ubicación", "Domicilio", "Calle", "Dir");
            String colLat = findColumn(table, "Latitud", "Lat", "Latitude", "Y", "Lat.");
            String colLon = findColumn(table, "Longitud", "Lon", "Lng", "Longitude", "X", "Long", "Long.");
            String colWeight = findColumn(table, "Peso", "Weight", "Kg", "Kg.", "Kgs", "Carga", "Masa", "Volumen", "Peso Total (KG)", "Peso Total", "zubale");
            String colWindow = findColumn(table, "Franja Entrega", "Franja", "Horario", "Window", "Time Window", "Ventana");

            log.info("Mapeo de columnas detectado. direccion={}, peso={}, franja={}, latitud={}, longitud={}, id={}, cliente={}",
                    colAddress, colWeight, colWindow, colLat, colLon, colId, colClient);

            int geocodedCustomers = 0;
            int invalidRows = 0;
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
                    log.info("Coordenadas invalidas para pedido {}. Se intentara geocodificar direccion: {}", id, address);
                    double[] coords = geocodingService.geocodeAddress(address);
                    if (coords != null) {
                        lat = coords[0];
                        lon = coords[1];
                        geocodedCustomers++;
                    } else {
                        log.warn("No se pudo geocodificar el pedido {} con direccion: {}", id, address);
                    }
                }

                if (isValidCoordinate(lat, lon)) {
                    customers.add(new Customer(id, new Location(lat, lon), roundedWeight, client, address, deliveryWindow));
                } else {
                    invalidRows++;
                    log.warn("Fila descartada por coordenadas invalidas. pedido={}, cliente={}, direccion={}", id, client, address);
                }
            }

            log.info("Procesamiento de filas completado. clientesValidos={}, geocodificados={}, filasDescartadas={}",
                    customers.size(), geocodedCustomers, invalidRows);

            if (customers.isEmpty()) {
                log.warn("No se encontraron pedidos validos con coordenadas tras procesar el archivo {}", file.getOriginalFilename());
                return ResponseEntity.badRequest().body("No se encontraron pedidos válidos con coordenadas.");
            }

            // Optimizar Rutas (Pasando la ubicación del depósito)
            log.info("Iniciando optimizacion de rutas para {} clientes.", customers.size());
            VehicleRoutingSolution solution = routeOptimizationService.optimizeRoutes(customers, depotLocation, carryCount, nhrCount, nprCount);
            log.info("Optimizacion finalizada. Vehiculos generados={}, clientesAsignados={}",
                    solution.getVehicleList().size(),
                    solution.getVehicleList().stream().mapToInt(v -> v.getCustomers().size()).sum());

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
            log.info("Respuesta lista. rutasMostradas={}, pedidosNoAsignados={}", routes.size(), response.get("unassigned"));

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error procesando el archivo Excel: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body("Error al procesar el archivo: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error general durante la carga y optimizacion del archivo: {}", file.getOriginalFilename(), e);
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
            log.debug("No se pudo leer la columna {} como double directamente. Se intentara parsear como texto.", colName);
            try {
                // Intentar parsear string a double si la columna no es numérica
                String val = row.getString(colName);
                if (val != null && !val.trim().isEmpty()) {
                    return Double.parseDouble(val.replace(",", ".")); // Manejar decimales con coma
                }
            } catch (Exception parseException) {
                log.debug("No se pudo parsear la columna {} con valor textual a double.", colName, parseException);
            }
        }
        return 0.0;
    }

    /**
     * Lee un archivo Excel usando Apache POI y devuelve una Tabla de Tablesaw con todas las columnas como String
     */
    private Table readExcelAsStrings(File file) throws IOException {
        log.info("Iniciando lectura del archivo Excel como texto: {}", file.getAbsolutePath());
        List<String> columnNames = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                log.warn("El archivo Excel no contiene hojas utilizables: {}", file.getAbsolutePath());
                return Table.create();
            }

            // Leer encabezados y manejar duplicados
            XSSFRow headerRow = sheet.getRow(0);
            Map<String, Integer> nameCount = new HashMap<>();
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String originalName = getCellValueAsString(cell);
                    if (originalName == null || originalName.trim().isEmpty()) {
                        originalName = "Columna " + (columnNames.size() + 1);
                    }
                    String finalName = originalName;
                    int count = nameCount.getOrDefault(originalName, 0);
                    if (count > 0) {
                        finalName = originalName + "_" + count;
                    }
                    nameCount.put(originalName, count + 1);
                    columnNames.add(finalName);
                }
            }

            // Leer filas de datos
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) continue;
                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < columnNames.size(); j++) {
                    Cell cell = row.getCell(j, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    rowData.add(getCellValueAsString(cell));
                }
                rows.add(rowData);
            }
        }

        // Construir la Tabla de Tablesaw
        Table table = Table.create();
        for (int colIdx = 0; colIdx < columnNames.size(); colIdx++) {
            String colName = columnNames.get(colIdx);
            if (colName == null || colName.trim().isEmpty()) {
                colName = "Columna " + (colIdx + 1);
            }
            tech.tablesaw.api.StringColumn col = tech.tablesaw.api.StringColumn.create(colName);
            for (List<String> row : rows) {
                col.append(colIdx < row.size() ? row.get(colIdx) : "");
            }
            table.addColumns(col);
        }

        log.info("Lectura de Excel completada. columnas={}, filas={}", columnNames.size(), rows.size());
        return table;
    }

    /**
     * Obtiene el valor de una celda como String, sin importar su tipo
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant().toString();
                } else {
                    double numVal = cell.getNumericCellValue();
                    if (numVal == (long) numVal) {
                        yield String.valueOf((long) numVal);
                    } else {
                        yield String.valueOf(numVal);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        double numVal = cell.getNumericCellValue();
                        if (numVal == (long) numVal) {
                            yield String.valueOf((long) numVal);
                        } else {
                            yield String.valueOf(numVal);
                        }
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }
}
