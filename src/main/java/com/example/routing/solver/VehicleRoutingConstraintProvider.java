package com.example.routing.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import com.example.routing.config.RoutingRulesProperties;
import com.example.routing.domain.Vehicle;

public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    private static volatile long maxRouteDistanceMeters = 40_000L;
    private static volatile long maxLegDistanceMeters = 5_000L;
    private static volatile long maxClusterRadiusMeters = 10_000L;
    private static volatile long maxInterStopDistanceMeters = 10_000L;
    private static volatile double carryHeavyOrderThresholdKg = 500.0;
    private static volatile double nprHeavyOrderThresholdKg = 3_000.0;
    private static volatile int nhrMaxOrders = 10;
    private static volatile int nprMaxOrders = 5;
    private static volatile long distanceWeight = 30L;
    private static volatile long clusterOverflowWeight = 200L;
    private static volatile long utilizationWeight = 80L;
    private static volatile long smallOrderInLargeVehicleWeight = 1_000L;
    private static volatile long hypotheticalOrderWeight = 5_000_000L;
    private static volatile long nprWithoutHeavyOrderWeight = 40_000L;

    public static void configure(RoutingRulesProperties rules) {
        if (rules == null) {
            return;
        }

        maxRouteDistanceMeters = rules.getMaxRouteDistanceMeters();
        maxLegDistanceMeters = rules.getMaxLegDistanceMeters();
        maxClusterRadiusMeters = rules.getMaxClusterRadiusMeters();
        maxInterStopDistanceMeters = rules.getMaxInterStopDistanceMeters();
        carryHeavyOrderThresholdKg = rules.getCarryHeavyOrderThresholdKg();
        nprHeavyOrderThresholdKg = rules.getNprHeavyOrderThresholdKg();
        nhrMaxOrders = rules.getNhrMaxOrders();
        nprMaxOrders = rules.getNprMaxOrders();
        distanceWeight = rules.getDistanceWeight();
        clusterOverflowWeight = rules.getClusterOverflowWeight();
        utilizationWeight = rules.getUtilizationWeight();
        smallOrderInLargeVehicleWeight = rules.getSmallOrderInLargeVehicleWeight();
        hypotheticalOrderWeight = rules.getHypotheticalOrderWeight();
        nprWithoutHeavyOrderWeight = rules.getNprWithoutHeavyOrderWeight();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            vehicleCapacity(factory),
            maxRouteDistance(factory),
            maxDistanceBetweenStops(factory),
            maxDistanceBetweenAnyDeliveryPoints(factory),
            noFirstWindowOnSecondTrip(factory),
            heavyOrdersInCarry(factory),
            nhrMaxOrders(factory),
            nprMaxOrders(factory),
            totalDistance(factory),
            compactRoute(factory),
            clusterByFirstStop(factory),
            vehicleFixedCost(factory),
            capacityUtilization(factory),
            smallOrdersPreferCarry(factory),
            discourageUnjustifiedNpr(factory),
            minimizeHypotheticalUsage(factory)
        };
    }

    protected Constraint vehicleCapacity(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getTotalDemand() > vehicle.getCapacity())
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> Math.round(vehicle.getTotalDemand() - vehicle.getCapacity()))
                .asConstraint("Vehicle capacity");
    }

    protected Constraint maxRouteDistance(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getStopCount() > 1)
                .filter(vehicle -> vehicle.getTotalDistanceMeters() > maxRouteDistanceMeters)
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getTotalDistanceMeters() - maxRouteDistanceMeters)
                .asConstraint("Max route distance");
    }

    protected Constraint maxDistanceBetweenStops(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.hasLegLongerThan(maxLegDistanceMeters))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getExceededLegDistanceMeters(maxLegDistanceMeters))
                .asConstraint("Max distance between stops");
    }

    protected Constraint maxDistanceBetweenAnyDeliveryPoints(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.hasInterStopDistanceLongerThan(maxInterStopDistanceMeters))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getExceededInterStopDistanceMeters(maxInterStopDistanceMeters))
                .asConstraint("Max distance between any delivery points");
    }

    protected Constraint noFirstWindowOnSecondTrip(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(Vehicle::isSecondTrip)
                .filter(vehicle -> vehicle.getStops().stream().anyMatch(stop -> stop.hasWindow("WINDOW_1")))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getStops().stream().filter(stop -> stop.hasWindow("WINDOW_1")).count())
                .asConstraint("No window 1 on second trip");
    }

    protected Constraint heavyOrdersInCarry(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getType() != null && vehicle.getType().contains("Carry"))
                .filter(vehicle -> vehicle.hasOrderAbove(carryHeavyOrderThresholdKg))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.countOrdersAbove(carryHeavyOrderThresholdKg))
                .asConstraint("Heavy orders in carry");
    }

    protected Constraint nhrMaxOrders(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "NHR".equals(vehicle.getType()))
                .filter(vehicle -> vehicle.getTotalOrderCount() > nhrMaxOrders)
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getTotalOrderCount() - (long) nhrMaxOrders)
                .asConstraint("NHR max orders");
    }

    protected Constraint nprMaxOrders(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "NPR".equals(vehicle.getType()))
                .filter(vehicle -> vehicle.getTotalOrderCount() > nprMaxOrders)
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        vehicle -> vehicle.getTotalOrderCount() - (long) nprMaxOrders)
                .asConstraint("NPR max orders");
    }

    protected Constraint totalDistance(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(Vehicle::isUsed)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getTotalDistanceMeters() * distanceWeight)
                .asConstraint("Minimize total distance");
    }

    protected Constraint compactRoute(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getStopCount() > 1)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, Vehicle::getCompactnessPenalty)
                .asConstraint("Compact route");
    }

    protected Constraint clusterByFirstStop(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getStopCount() > 1)
                .filter(vehicle -> vehicle.getExceededZoneDistanceMeters(maxClusterRadiusMeters) > 0)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getExceededZoneDistanceMeters(maxClusterRadiusMeters) * clusterOverflowWeight)
                .asConstraint("Cluster around first stop");
    }

    protected Constraint vehicleFixedCost(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(Vehicle::isUsed)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, Vehicle::getFixedCost)
                .asConstraint("Vehicle fixed cost");
    }

    protected Constraint capacityUtilization(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(Vehicle::isUsed)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getUtilizationPenalty() * utilizationWeight)
                .asConstraint("Fill vehicles");
    }

    protected Constraint smallOrdersPreferCarry(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> vehicle.getType() != null
                        && ("NHR".equals(vehicle.getType()) || "NPR".equals(vehicle.getType())))
                .filter(vehicle -> vehicle.countOrdersAtOrBelow(carryHeavyOrderThresholdKg) > 0)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.countOrdersAtOrBelow(carryHeavyOrderThresholdKg) * smallOrderInLargeVehicleWeight)
                .asConstraint("Small orders prefer carry");
    }

    protected Constraint discourageUnjustifiedNpr(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(vehicle -> "NPR".equals(vehicle.getType()))
                .filter(Vehicle::isUsed)
                .filter(vehicle -> !vehicle.hasOrderAbove(nprHeavyOrderThresholdKg))
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> nprWithoutHeavyOrderWeight)
                .asConstraint("NPR without heavy orders");
    }

    protected Constraint minimizeHypotheticalUsage(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(Vehicle::isHypothetical)
                .filter(Vehicle::isUsed)
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        vehicle -> vehicle.getTotalOrderCount() * hypotheticalOrderWeight)
                .asConstraint("Minimize hypothetical usage");
    }
}
