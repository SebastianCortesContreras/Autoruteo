package com.example.routing.service;

import com.example.routing.config.RoutingRulesProperties;
import com.example.routing.domain.Customer;
import com.example.routing.domain.Depot;
import com.example.routing.domain.Location;
import com.example.routing.domain.RouteStop;
import com.example.routing.domain.Vehicle;
import com.example.routing.domain.VehicleRoutingSolution;
import com.example.routing.solver.VehicleRoutingConstraintProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RouteOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(RouteOptimizationService.class);

    private final RoutingRulesProperties rules;

    public RouteOptimizationService(RoutingRulesProperties rules) {
        this.rules = rules;
        VehicleRoutingConstraintProvider.configure(rules);
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers, Location depotLocation, Integer carryCount, Integer nhrCount, Integer nprCount) {
        log.debug("Iniciando optimizacion. pedidos={}, depositoDefinido={}, carryCount={}, nhrCount={}, nprCount={}",
                customers.size(), depotLocation != null, carryCount, nhrCount, nprCount);

        if (customers.isEmpty()) {
            log.warn("Se solicito optimizacion sin pedidos.");
            return new VehicleRoutingSolution(new ArrayList<>(), new ArrayList<>());
        }

        Location resolvedDepot = depotLocation != null ? depotLocation : calculateCentroid(customers);
        Depot depot = new Depot(resolvedDepot);

        List<RouteStop> stops = buildStops(customers);
        List<Vehicle> plannedFleet = buildProximityFirstPlan(stops, depot, carryCount, nhrCount, nprCount);
        VehicleRoutingSolution solution = new VehicleRoutingSolution(plannedFleet, stops);

        postProcessRoutes(solution);
        log.debug("Solver finalizado. vehiculosResueltos={}, pedidosAsignados={}",
                solution.getVehicleList().size(),
                solution.getVehicleList().stream().mapToInt(Vehicle::getTotalOrderCount).sum());
        return solution;
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers, Location depotLocation) {
        return optimizeRoutes(customers, depotLocation, 10, 10, 5);
    }

    public VehicleRoutingSolution optimizeRoutes(List<Customer> customers) {
        return optimizeRoutes(customers, null, 10, 10, 5);
    }

    private void postProcessRoutes(VehicleRoutingSolution solution) {
        for (Vehicle vehicle : solution.getVehicleList()) {
            if (!vehicle.isUsed()) {
                continue;
            }

            long distanceMeters = vehicle.getTotalDistanceMeters();
            int stopCount = vehicle.getStopCount();
            int orderCount = vehicle.getTotalOrderCount();
            double totalWeight = vehicle.getTotalDemand();
            double fillRatio = vehicle.getFillRatio();
            long exceededZoneMeters = vehicle.getExceededZoneDistanceMeters(rules.getMaxClusterRadiusMeters());
            double averageInterStopDistance = vehicle.getAverageInterStopDistanceMeters();

            String status = "Ruta Optimizada";
            String comments = String.format("Carga %.0f%% de capacidad, %d pedidos y %d paradas.",
                    fillRatio * 100.0, orderCount, stopCount);

            if (vehicle.isHypothetical()) {
                status = "Ruta Contingencia";
                comments = "Se utilizó vehículo de contingencia porque la flota real no absorbió toda la demanda.";
            } else if (stopCount == 1 && (distanceMeters > rules.getMaxRouteDistanceMeters()
                    || totalWeight > rules.getCarryHeavyOrderThresholdKg())) {
                status = "Ruta Dedicada";
                comments = "Ruta dedicada por peso o distancia para no deteriorar la compactación del resto.";
            } else if (exceededZoneMeters > 0) {
                status = "Ruta Abierta";
                comments = "Se abrió la ruta porque juntar más pedidos en el área empeoraba la solución global.";
            } else if (stopCount >= 3 && averageInterStopDistance > rules.getOpenRouteAverageStopDistanceMeters()) {
                status = "Ruta Abierta";
                comments = "Las paradas quedaron demasiado separadas entre sí para considerarse una ruta sectorizada.";
            } else if (fillRatio >= 0.85) {
                status = "Ruta Consolidada";
                comments = "Ruta compacta y con alto aprovechamiento de capacidad.";
            } else if (stopCount >= 2) {
                comments = "Ruta sectorizada por cercanía geográfica con ocupación operativamente saludable.";
            }

            vehicle.setRouteStatus(status);
            vehicle.setComments(comments);
        }
    }

    private Location calculateCentroid(List<Customer> customers) {
        double avgLat = customers.stream().mapToDouble(customer -> customer.getLocation().getLatitude()).average().orElse(0.0);
        double avgLon = customers.stream().mapToDouble(customer -> customer.getLocation().getLongitude()).average().orElse(0.0);
        return new Location(avgLat, avgLon);
    }

    private List<RouteStop> buildStops(List<Customer> customers) {
        Map<String, List<Customer>> groupedOrders = new LinkedHashMap<>();
        for (Customer customer : customers) {
            groupedOrders.computeIfAbsent(customer.getLocationGroupingKey(), key -> new ArrayList<>()).add(customer);
        }

        List<RouteStop> stops = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, List<Customer>> entry : groupedOrders.entrySet()) {
            List<Customer> stopOrders = new ArrayList<>(entry.getValue());
            stopOrders.sort(Comparator.comparing(Customer::getId));
            Customer referenceOrder = stopOrders.get(0);
            String address = referenceOrder.getAddress() != null && !referenceOrder.getAddress().isBlank()
                    ? referenceOrder.getAddress()
                    : referenceOrder.getName();
            stops.add(new RouteStop(
                    "STOP-" + index++,
                    referenceOrder.getLocation(),
                    address,
                    entry.getKey(),
                    stopOrders));
        }

        log.debug("Pedidos agrupados en {} paradas.", stops.size());
        return stops;
    }

    private List<Vehicle> buildProximityFirstPlan(List<RouteStop> stops, Depot depot, Integer carryCount, Integer nhrCount, Integer nprCount) {
        List<Vehicle> fleet = generateVehicleFleet(depot, carryCount, nhrCount, nprCount);
        List<RouteStop> urbanStops = new ArrayList<>();
        List<RouteStop> outOfCityStops = new ArrayList<>();

        for (RouteStop stop : stops) {
            if (isOutOfCity(stop, depot)) {
                outOfCityStops.add(stop);
            } else {
                urbanStops.add(stop);
            }
        }

        assignUrbanStops(urbanStops, fleet);
        assignOutOfCityStops(outOfCityStops, fleet);

        List<RouteStop> assignedStops = fleet.stream()
                .flatMap(vehicle -> vehicle.getStops().stream())
                .distinct()
                .toList();
        List<RouteStop> unassigned = new ArrayList<>(stops);
        unassigned.removeIf(assignedStops::contains);

        assignStopsToVehicleGroup(unassigned, fleet, Vehicle::isHypothetical, stop -> true,
                rules.getOutOfCityClusterRadiusMeters(), depot, true);

        assignedStops = fleet.stream()
                .flatMap(vehicle -> vehicle.getStops().stream())
                .distinct()
                .toList();
        unassigned = new ArrayList<>(stops);
        unassigned.removeIf(assignedStops::contains);

        if (!unassigned.isEmpty()) {
            throw new IllegalStateException("Quedaron paradas sin asignar aun usando toda la flota disponible.");
        }

        for (Vehicle vehicle : fleet) {
            if (vehicle.isUsed()) {
                vehicle.setStops(orderRouteByNearestNeighbor(vehicle));
            }
        }

        String infeasibilityReason = explainInfeasibility(new VehicleRoutingSolution(fleet, stops));
        if (infeasibilityReason != null) {
            throw new IllegalStateException("La planeacion proximity-first genero una solucion invalida: " + infeasibilityReason);
        }

        return fleet;
    }

    private void assignUrbanStops(List<RouteStop> urbanStops, List<Vehicle> fleet) {
        assignStopsToVehicleGroup(urbanStops, fleet, vehicle -> "Carry".equals(vehicle.getType()), this::isCarryEligibleStop,
                rules.getUrbanClusterRadiusMeters(), null, false);
        assignStopsToVehicleGroup(remainingStops(urbanStops, fleet), fleet,
                vehicle -> "Carry (Trip 2)".equals(vehicle.getType()),
                stop -> isCarryEligibleStop(stop) && !stop.hasWindow("WINDOW_1"),
                rules.getUrbanClusterRadiusMeters(), null, false);
        assignStopsToVehicleGroup(remainingStops(urbanStops, fleet), fleet,
                vehicle -> "NHR".equals(vehicle.getType()),
                stop -> stop.getTotalDemand() <= rules.getNhrCapacityKg() && stop.getMaxOrderDemand() <= rules.getNprHeavyOrderThresholdKg(),
                rules.getUrbanClusterRadiusMeters(), null, false);
        assignStopsToVehicleGroup(remainingStops(urbanStops, fleet), fleet,
                vehicle -> "NPR".equals(vehicle.getType()), stop -> true,
                rules.getUrbanClusterRadiusMeters(), null, false);
    }

    private void assignOutOfCityStops(List<RouteStop> outOfCityStops, List<Vehicle> fleet) {
        assignStopsToVehicleGroup(outOfCityStops, fleet,
                vehicle -> "NHR".equals(vehicle.getType()),
                stop -> stop.getTotalDemand() <= rules.getNhrCapacityKg() && stop.getMaxOrderDemand() <= rules.getNprHeavyOrderThresholdKg(),
                rules.getOutOfCityClusterRadiusMeters(), null, true);
        assignStopsToVehicleGroup(remainingStops(outOfCityStops, fleet), fleet,
                vehicle -> "NPR".equals(vehicle.getType()), stop -> true,
                rules.getOutOfCityClusterRadiusMeters(), null, true);
        assignStopsToVehicleGroup(remainingStops(outOfCityStops, fleet), fleet,
                vehicle -> "Carry".equals(vehicle.getType()), this::isCarryEligibleStop,
                rules.getOutOfCityClusterRadiusMeters(), null, true);
        assignStopsToVehicleGroup(remainingStops(outOfCityStops, fleet), fleet,
                vehicle -> "Carry (Trip 2)".equals(vehicle.getType()),
                stop -> isCarryEligibleStop(stop) && !stop.hasWindow("WINDOW_1"),
                rules.getOutOfCityClusterRadiusMeters(), null, true);
    }

    private List<RouteStop> remainingStops(List<RouteStop> sourceStops, List<Vehicle> fleet) {
        List<RouteStop> assignedStops = fleet.stream()
                .flatMap(vehicle -> vehicle.getStops().stream())
                .distinct()
                .toList();

        List<RouteStop> remaining = new ArrayList<>(sourceStops);
        remaining.removeIf(assignedStops::contains);
        return remaining;
    }

    private void assignStopsToVehicleGroup(List<RouteStop> candidates,
                                           List<Vehicle> fleet,
                                           java.util.function.Predicate<Vehicle> vehicleFilter,
                                           java.util.function.Predicate<RouteStop> stopFilter,
                                           long clusterRadiusMeters,
                                           Depot depot,
                                           boolean preferFartherSeeds) {
        List<Vehicle> vehicles = fleet.stream().filter(vehicleFilter).toList();
        if (vehicles.isEmpty() || candidates.isEmpty()) {
            return;
        }

        List<RouteStop> availableStops = new ArrayList<>(candidates);

        for (Vehicle vehicle : vehicles) {
            if (!vehicle.isUsed()) {
                RouteStop seed = selectSeedStop(vehicle, availableStops, stopFilter, clusterRadiusMeters, depot, preferFartherSeeds);
                if (seed == null) {
                    continue;
                }

                vehicle.getStops().add(seed);
                availableStops.remove(seed);
            }

            boolean added;
            do {
                added = false;
                RouteStop nextStop = selectNearestFittingStop(vehicle, availableStops, stopFilter, clusterRadiusMeters, depot);
                if (nextStop != null) {
                    vehicle.getStops().add(nextStop);
                    availableStops.remove(nextStop);
                    added = true;
                }
            } while (added);
        }
    }

    private RouteStop selectSeedStop(Vehicle vehicle,
                                     List<RouteStop> candidates,
                                     java.util.function.Predicate<RouteStop> stopFilter,
                                     long clusterRadiusMeters,
                                     Depot depot,
                                     boolean preferFartherSeeds) {
        Comparator<RouteStop> comparator;
        if (depot != null && preferFartherSeeds) {
            comparator = Comparator
                    .comparingLong((RouteStop stop) -> depot.getLocation().getDistanceTo(stop.getLocation()))
                    .thenComparingLong(stop -> countNeighborsWithinRadius(stop, candidates, clusterRadiusMeters))
                    .thenComparingDouble(RouteStop::getTotalDemand)
                    .thenComparingDouble(RouteStop::getMaxOrderDemand);
        } else {
            comparator = Comparator
                    .comparingLong((RouteStop stop) -> countNeighborsWithinRadius(stop, candidates, clusterRadiusMeters))
                    .thenComparingDouble(RouteStop::getTotalDemand)
                    .thenComparingDouble(RouteStop::getMaxOrderDemand);
        }

        return candidates.stream()
                .filter(stopFilter)
                .filter(stop -> canAssignStop(vehicle, stop))
                .max(comparator)
                .orElse(null);
    }

    private long countNeighborsWithinRadius(RouteStop reference, List<RouteStop> candidates, long clusterRadiusMeters) {
        return candidates.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), reference.getId()))
                .filter(candidate -> reference.getLocation().getDistanceTo(candidate.getLocation()) <= clusterRadiusMeters)
                .count();
    }

    private RouteStop selectNearestFittingStop(Vehicle vehicle,
                                               List<RouteStop> candidates,
                                               java.util.function.Predicate<RouteStop> stopFilter,
                                               long clusterRadiusMeters,
                                               Depot depot) {
        if (!vehicle.isUsed()) {
            return null;
        }

        RouteStop anchor = vehicle.getStops().get(0);
        RouteStop lastStop = vehicle.getStops().get(vehicle.getStops().size() - 1);

        return candidates.stream()
                .filter(stopFilter)
                .filter(stop -> canAssignStop(vehicle, stop))
                .filter(stop -> anchor.getLocation().getDistanceTo(stop.getLocation()) <= clusterRadiusMeters)
                .filter(stop -> isCompactWithCurrentRoute(vehicle, stop, clusterRadiusMeters))
                .min(Comparator
                        .comparingDouble((RouteStop stop) -> getAverageDistanceToRoute(vehicle, stop))
                        .thenComparingLong(stop -> getMaxDistanceToRoute(vehicle, stop))
                        .thenComparingLong(stop -> getDepotDistanceDifference(vehicle, stop, depot))
                        .thenComparingLong(stop -> lastStop.getLocation().getDistanceTo(stop.getLocation()))
                        .thenComparingLong(stop -> anchor.getLocation().getDistanceTo(stop.getLocation()))
                        .thenComparing(Comparator.comparingDouble(RouteStop::getTotalDemand).reversed()))
                .orElse(null);
    }

    private boolean isCompactWithCurrentRoute(Vehicle vehicle, RouteStop candidate, long clusterRadiusMeters) {
        if (vehicle.getStops().isEmpty()) {
            return true;
        }

        long maxInterStopDistanceMeters = rules.getMaxInterStopDistanceMeters();
        return vehicle.getStops().stream()
                .allMatch(existingStop -> {
                    long distanceToExistingStop = existingStop.getLocation().getDistanceTo(candidate.getLocation());
                    return distanceToExistingStop <= clusterRadiusMeters
                            && distanceToExistingStop <= maxInterStopDistanceMeters;
                });
    }

    private double getAverageDistanceToRoute(Vehicle vehicle, RouteStop candidate) {
        return vehicle.getStops().stream()
                .mapToLong(existingStop -> existingStop.getLocation().getDistanceTo(candidate.getLocation()))
                .average()
                .orElse(0.0);
    }

    private long getMaxDistanceToRoute(Vehicle vehicle, RouteStop candidate) {
        return vehicle.getStops().stream()
                .mapToLong(existingStop -> existingStop.getLocation().getDistanceTo(candidate.getLocation()))
                .max()
                .orElse(0L);
    }

    private long getDepotDistanceDifference(Vehicle vehicle, RouteStop candidate, Depot depot) {
        if (depot == null || !vehicle.isUsed()) {
            return 0L;
        }

        double averageRouteDepotDistance = vehicle.getStops().stream()
                .mapToLong(stop -> depot.getLocation().getDistanceTo(stop.getLocation()))
                .average()
                .orElse(0.0);
        long candidateDepotDistance = depot.getLocation().getDistanceTo(candidate.getLocation());
        return Math.round(Math.abs(candidateDepotDistance - averageRouteDepotDistance));
    }

    private boolean isOutOfCity(RouteStop stop, Depot depot) {
        return depot.getLocation().getDistanceTo(stop.getLocation()) >= rules.getOutOfCityThresholdMeters();
    }

    private boolean isCarryEligibleStop(RouteStop stop) {
        return stop.getTotalDemand() <= rules.getCarryCapacityKg()
                && stop.getMaxOrderDemand() <= rules.getCarryHeavyOrderThresholdKg();
    }

    private List<RouteStop> orderRouteByNearestNeighbor(Vehicle vehicle) {
        List<RouteStop> remaining = new ArrayList<>(vehicle.getStops());
        List<RouteStop> ordered = new ArrayList<>();
        Location current = vehicle.getDepot().getLocation();

        while (!remaining.isEmpty()) {
            Location currentLocation = current;
            RouteStop next = remaining.stream()
                    .min(Comparator.comparingLong(stop -> currentLocation.getDistanceTo(stop.getLocation())))
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            current = next.getLocation();
        }

        return ordered;
    }

    private boolean canAssignStop(Vehicle vehicle, RouteStop stop) {
        double newDemand = vehicle.getTotalDemand() + stop.getTotalDemand();
        int newOrderCount = vehicle.getTotalOrderCount() + stop.getOrderCount();

        if (newDemand > vehicle.getCapacity()) {
            return false;
        }

        if (vehicle.isSecondTrip() && stop.hasWindow("WINDOW_1")) {
            return false;
        }

        if ("Carry".equals(vehicle.getType()) || "Carry (Trip 2)".equals(vehicle.getType())) {
            if (stop.getMaxOrderDemand() > rules.getCarryHeavyOrderThresholdKg()) {
                return false;
            }
            if (stop.getTotalDemand() > rules.getCarryCapacityKg()) {
                return false;
            }
        }

        if ("NHR".equals(vehicle.getType()) && newOrderCount > rules.getNhrMaxOrders()) {
            return false;
        }

        if ("NPR".equals(vehicle.getType()) && newOrderCount > rules.getNprMaxOrders()) {
            return false;
        }

        return true;
    }

    private boolean isFeasible(VehicleRoutingSolution solution) {
        return explainInfeasibility(solution) == null;
    }

    private String explainInfeasibility(VehicleRoutingSolution solution) {
        for (Vehicle vehicle : solution.getVehicleList()) {
            if (vehicle.getTotalDemand() > vehicle.getCapacity()) {
                return "el vehiculo " + vehicle.getId() + " excede capacidad";
            }
            if (vehicle.isSecondTrip() && vehicle.getStops().stream().anyMatch(stop -> stop.hasWindow("WINDOW_1"))) {
                return "el vehiculo " + vehicle.getId() + " recibio pedidos de WINDOW_1 en segundo viaje";
            }
            if (("Carry".equals(vehicle.getType()) || "Carry (Trip 2)".equals(vehicle.getType()))
                    && vehicle.getStops().stream().anyMatch(stop ->
                    stop.getMaxOrderDemand() > rules.getCarryHeavyOrderThresholdKg()
                            || stop.getTotalDemand() > rules.getCarryCapacityKg())) {
                return "la carry " + vehicle.getId() + " recibio una parada no elegible";
            }
            if ("NHR".equals(vehicle.getType()) && vehicle.getTotalOrderCount() > rules.getNhrMaxOrders()) {
                return "el vehiculo " + vehicle.getId() + " supera el maximo de pedidos NHR";
            }
            if ("NPR".equals(vehicle.getType()) && vehicle.getTotalOrderCount() > rules.getNprMaxOrders()) {
                return "el vehiculo " + vehicle.getId() + " supera el maximo de pedidos NPR";
            }
            if (vehicle.hasInterStopDistanceLongerThan(rules.getMaxInterStopDistanceMeters())) {
                return "el vehiculo " + vehicle.getId() + " tiene puntos de entrega separados por mas de "
                        + (rules.getMaxInterStopDistanceMeters() / 1000) + " km";
            }
        }
        return null;
    }

    private List<Vehicle> generateVehicleFleet(Depot depot, Integer carryCount, Integer nhrCount, Integer nprCount) {
        int numCarrys = carryCount != null ? carryCount : 10;
        int numNhrs = nhrCount != null ? nhrCount : 10;
        int numNprs = nprCount != null ? nprCount : 5;

        List<Vehicle> vehicles = new ArrayList<>();

        for (int i = 0; i < numCarrys; i++) {
            vehicles.add(new Vehicle("Carry-" + (i + 1), rules.getCarryCapacityKg(), "Carry", depot, rules.getCarryFixedCost()));
        }

        for (int i = 0; i < numCarrys; i++) {
            vehicles.add(new Vehicle("Carry-" + (i + 1) + "-Trip2", rules.getCarryCapacityKg(), "Carry (Trip 2)", depot, rules.getCarrySecondTripFixedCost()));
        }

        for (int i = 0; i < numNhrs; i++) {
            vehicles.add(new Vehicle("NHR-" + (i + 1), rules.getNhrCapacityKg(), "NHR", depot, rules.getNhrFixedCost()));
        }

        for (int i = 0; i < numNprs; i++) {
            vehicles.add(new Vehicle("NPR-" + (i + 1), rules.getNprCapacityKg(), "NPR", depot, rules.getNprFixedCost()));
        }

        for (int i = 0; i < 5; i++) {
            vehicles.add(new Vehicle("Hypothetical-" + (i + 1), rules.getHypotheticalCapacityKg(), "Hipotético", depot, rules.getHypotheticalFixedCost()));
        }

        return vehicles;
    }
}
