package com.example.routing.service;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.example.routing.domain.*;
import com.example.routing.solver.VehicleRoutingConstraintProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RouteOptimizationService {

    private final SolverFactory<VehicleRoutingSolution> solverFactory;

    public RouteOptimizationService() {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VehicleRoutingSolution.class)
                .withEntityClasses(Vehicle.class)
                .withConstraintProviderClass(VehicleRoutingConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(10)); // Optimizar por 10 segundos

        this.solverFactory = SolverFactory.create(solverConfig);
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers, Location depotLocation, Integer carryCount, Integer nhrCount, Integer nprCount) {
        if (customers.isEmpty()) return new VehicleRoutingSolution(new ArrayList<>(), customers);

        // 1. Determinar ubicación del depósito (Si es null, usar Centroide)
        if (depotLocation == null) {
            depotLocation = calculateCentroid(customers);
        }
        Depot depot = new Depot(depotLocation);

        // 2. Generar flota de vehículos
        List<Vehicle> vehicles = generateVehicleFleet(customers, depot, carryCount, nhrCount, nprCount);

        // 3. Crear problema
        VehicleRoutingSolution problem = new VehicleRoutingSolution(vehicles, customers);

        // 4. Crear nuevo Solver y resolver
        Solver<VehicleRoutingSolution> solver = solverFactory.buildSolver();
        VehicleRoutingSolution solution = solver.solve(problem);
        
        // 5. Post-procesamiento (Etiquetado de rutas)
        postProcessRoutes(solution);
        
        return solution;
    }

    private void postProcessRoutes(VehicleRoutingSolution solution) {
        for (Vehicle vehicle : solution.getVehicleList()) {
            if (vehicle.getCustomers().isEmpty()) continue;

            long distanceMeters = vehicle.getTotalDistanceMeters();
            int stopCount = vehicle.getCustomers().size();
            double totalWeight = vehicle.getTotalDemand();

            // Default status
            String status = "Ruta Optimizada";

            // 1. Ruta Dedicada: 1 pedido y (> 40km O > 500kg)
            if (stopCount == 1 && (distanceMeters > 40000 || totalWeight > 500)) {
                status = "Ruta Dedicada";
            }
            // 2. Ruta Abierta: Promedio < 2km por parada (ajustable)
            else if (stopCount > 0 && (distanceMeters / (double) stopCount) < 2000) {
                status = "Ruta Abierta";
            }

            vehicle.setRouteStatus(status);
            vehicle.setComments(status); // Mantener compatibilidad si se usa 'comments' en otro lado
        }
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers, Location depotLocation) {
        return optimizeRoutes(customers, depotLocation, 10, 10, 5); // Default values
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers) {
        return optimizeRoutes(customers, null, 10, 10, 5);
    }

    private Location calculateCentroid(List<Customer> customers) {
        double avgLat = customers.stream().mapToDouble(c -> c.getLocation().getLatitude()).average().orElse(0.0);
        double avgLon = customers.stream().mapToDouble(c -> c.getLocation().getLongitude()).average().orElse(0.0);
        return new Location(avgLat, avgLon);
    }

    private List<Vehicle> generateVehicleFleet(List<Customer> customers, Depot depot, Integer carryCount, Integer nhrCount, Integer nprCount) {
        List<Vehicle> vehicles = new ArrayList<>();
        
        // Defaults if null
        int numCarrys = (carryCount != null) ? carryCount : 10;
        int numNHRs = (nhrCount != null) ? nhrCount : 10;
        int numNPRs = (nprCount != null) ? nprCount : 5;

        // 1. Prioridad: Carrys (Trip 1)
        // Costo 0: Prioridad máxima
        for (int i = 0; i < numCarrys; i++) {
            vehicles.add(new Vehicle("Carry-" + (i + 1), 750.0, "Carry", depot, 0));
        }

        // 2. Prioridad: NHRs (Trip 1)
        // Costo 300: Preferir Carrys (Trip 1 y Trip 2) si es posible
        // Pero NHR sigue siendo un vehículo real, así que el costo es flexible.
        // ACTUALIZACIÓN: Capacidad aumentada a 3000kg (3 toneladas)
        for (int i = 0; i < numNHRs; i++) {
            vehicles.add(new Vehicle("NHR-" + (i + 1), 3000.0, "NHR", depot, 300));
        }

        // 3. Prioridad: NPRs (Trip 1)
        // Costo 400: Vehículo más grande (5 toneladas), preferir NHR/Carry si es posible.
        // Se usará principalmente para pedidos > 3 toneladas o cuando se llenen los otros.
        for (int i = 0; i < numNPRs; i++) {
            vehicles.add(new Vehicle("NPR-" + (i + 1), 5000.0, "NPR", depot, 400));
        }

        // 4. Segundo Cargue: Solo Carrys (Trip 2)
        // Costo 50: Usar después de los primeros viajes de Carrys, pero PREFERIBLE a usar NHRs (Costo 300)
        // Esto permite que los Carrys hagan hasta 2 viajes antes de requerir vehículos más grandes.
        for (int i = 0; i < numCarrys; i++) {
            vehicles.add(new Vehicle("Carry-" + (i + 1) + "-Trip2", 750.0, "Carry (Trip 2)", depot, 50));
        }
        
        // 5. Vehículos Hipotéticos (Última opción)
        // Costo fijo extremadamente alto para evitar su uso a menos que sea imposible rutear de otra forma
        for (int i = 0; i < 5; i++) {
            // Capacidad muy alta, costo fijo astronómico (100 millones)
            vehicles.add(new Vehicle("Hypothetical-" + (i + 1), 10000.0, "Hipotético", depot, 100_000_000));
        }

        return vehicles;
    }
}
