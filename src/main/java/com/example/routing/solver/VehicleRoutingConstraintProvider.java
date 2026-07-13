package com.example.routing.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import com.example.routing.domain.Vehicle;

public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            vehicleCapacity(factory),
            totalDistance(factory),
            vehicleFixedCost(factory),
            noFirstWindowOnSecondTrip(factory),
            heavyOrdersInNhrOnly(factory),
            smallOrdersPreferCarry(factory),
            minimizeHypotheticalUsage(factory),
            nprMaxOrders(factory),
            nprUsageJustification(factory),
            maxDistanceBetweenStops(factory),
            maxRouteDistance(factory)
        };
    }

    // Hard Constraint: Max total route distance 40km (unless it's a single dedicated order)
    protected Constraint maxRouteDistance(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getTotalDistanceMeters() > 40000)
                .filter(vehicle -> vehicle.getCustomers().size() > 1) // Allow if it's a dedicated trip (1 customer)
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> (vehicle.getTotalDistanceMeters() - 40000L))
                .asConstraint("Max route distance 40km");
    }

    // Hard Constraint: Max distance between stops (5km)
    protected Constraint maxDistanceBetweenStops(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.hasLegLongerThan(5000)) // 5km limit
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> 1L)
                .asConstraint("Max distance between stops 5km");
    }

    // Hard Constraint: Vehicle capacity must not be exceeded
    protected Constraint vehicleCapacity(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getTotalDemand() > vehicle.getCapacity())
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> (long) (vehicle.getTotalDemand() - vehicle.getCapacity()))
                .asConstraint("Vehicle capacity");
    }

    // Soft Constraint: Minimize total distance
    // Multiplicamos por 100 para dar prioridad MUY ALTA a la sectorización (rutas compactas)
    protected Constraint totalDistance(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getTotalDistanceMeters() * 100L)
                .asConstraint("Minimize Distance");
    }

    // Soft Constraint: Minimize vehicle fixed costs (prioritize specific vehicles)
    protected Constraint vehicleFixedCost(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> !vehicle.getCustomers().isEmpty())
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        Vehicle::getFixedCost)
                .asConstraint("Vehicle fixed cost");
    }

    // Hard Constraint: Second Load (Trip 2) vehicles cannot have Window 1 orders
    protected Constraint noFirstWindowOnSecondTrip(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getId().contains("Trip2"))
                .filter(vehicle -> vehicle.getCustomers().stream()
                        .anyMatch(c -> "WINDOW_1".equals(c.getDeliveryWindow())))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getCustomers().stream()
                                .filter(c -> "WINDOW_1".equals(c.getDeliveryWindow()))
                                .count())
                .asConstraint("No Window 1 on Second Trip");
    }

    // Hard Constraint: Heavy orders (> 500kg) must be in NHR, not Carry
    protected Constraint heavyOrdersInNhrOnly(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getType() != null && vehicle.getType().contains("Carry"))
                .filter(vehicle -> vehicle.getCustomers().stream()
                        .anyMatch(c -> c.getDemand() > 500.0))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getCustomers().stream()
                                .filter(c -> c.getDemand() > 500.0)
                                .count())
                .asConstraint("Heavy orders in NHR only");
    }

    // Soft Constraint: Small orders (<= 500kg) prefer Carry (penalize if in NHR)
    protected Constraint smallOrdersPreferCarry(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getType() != null && vehicle.getType().contains("NHR"))
                .filter(vehicle -> vehicle.getCustomers().stream()
                        .anyMatch(c -> c.getDemand() <= 500.0))
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getCustomers().stream()
                                .filter(c -> c.getDemand() <= 500.0)
                                .count() * 1500) // Penalización más alta para priorizar Carry primero
                .asConstraint("Small orders prefer Carry");
    }

    // Soft Constraint: Penalize each order assigned to a Hypothetical vehicle
    protected Constraint minimizeHypotheticalUsage(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "Hipotético".equals(vehicle.getType()))
                .filter(vehicle -> !vehicle.getCustomers().isEmpty())
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getCustomers().size() * 1_000_000L)
                .asConstraint("Minimize Hypothetical Usage");
    }

    // Hard Constraint: NPR vehicles max 5 orders
    protected Constraint nprMaxOrders(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "NPR".equals(vehicle.getType()))
                .filter(vehicle -> vehicle.getCustomers().size() > 5)
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getCustomers().size() - 5L)
                .asConstraint("NPR max 5 orders");
    }

    // Hard Constraint: NPR should only be used if there is at least one order > 3000kg
    // Penalize if an NPR is used (has customers) but NONE of them are > 3000kg
    protected Constraint nprUsageJustification(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "NPR".equals(vehicle.getType()))
                .filter(vehicle -> !vehicle.getCustomers().isEmpty())
                .filter(vehicle -> vehicle.getCustomers().stream()
                        .allMatch(c -> c.getDemand() <= 3000.0))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> 1L)
                .asConstraint("NPR used without heavy order");
    }
}
