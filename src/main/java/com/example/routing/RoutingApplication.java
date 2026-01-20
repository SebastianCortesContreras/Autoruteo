package com.example.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tablesaw.api.Table;

import java.util.Map;

@SpringBootApplication
@RestController
public class RoutingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("message", "Route Automation API is running",
                      "status", "active");
    }

    @GetMapping("/demo-panda")
    public String demoPanda() {
        // Tablesaw demo (Java's alternative to Pandas)
        Table table = Table.create("Rutas")
            .addColumns(
                tech.tablesaw.api.StringColumn.create("Vehiculo", "Camion A", "Camion B"),
                tech.tablesaw.api.DoubleColumn.create("Distancia", 120.5, 95.2)
            );
        
        return table.print();
    }
}
