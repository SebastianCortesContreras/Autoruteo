package com.example.routing.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import java.util.ArrayList;
import java.util.List;

@PlanningEntity
public class Vehicle {

    @PlanningId
    private String id;
    private double capacity;
    private String type;
    private Depot depot;
    private long fixedCost;
    private String comments;
    private String routeStatus;

    @PlanningListVariable
    private List<RouteStop> stops = new ArrayList<>();

    public Vehicle() {
    }

    public Vehicle(String id, double capacity, String type, Depot depot, long fixedCost) {
        this.id = id;
        this.capacity = capacity;
        this.type = type;
        this.depot = depot;
        this.fixedCost = fixedCost;
    }

    public String getId() {
        return id;
    }

    public double getCapacity() {
        return capacity;
    }

    public String getType() {
        return type;
    }

    public Depot getDepot() {
        return depot;
    }

    public long getFixedCost() {
        return fixedCost;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getRouteStatus() {
        return routeStatus;
    }

    public void setRouteStatus(String routeStatus) {
        this.routeStatus = routeStatus;
    }

    public List<RouteStop> getStops() {
        return stops;
    }

    public void setStops(List<RouteStop> stops) {
        this.stops = stops;
    }

    public List<Customer> getOrders() {
        List<Customer> orders = new ArrayList<>();
        for (RouteStop stop : stops) {
            orders.addAll(stop.getOrders());
        }
        return orders;
    }

    public int getStopCount() {
        return stops.size();
    }

    public int getTotalOrderCount() {
        return stops.stream().mapToInt(RouteStop::getOrderCount).sum();
    }

    public boolean isUsed() {
        return !stops.isEmpty();
    }

    public boolean isSecondTrip() {
        return id != null && id.contains("Trip2");
    }

    public boolean isHypothetical() {
        return type != null && type.toLowerCase().contains("hipot");
    }

    public long getTotalDistanceMeters() {
        if (stops.isEmpty()) {
            return 0L;
        }

        long totalDistance = 0L;
        Location previousLocation = depot.getLocation();

        for (RouteStop stop : stops) {
            totalDistance += previousLocation.getDistanceTo(stop.getLocation());
            previousLocation = stop.getLocation();
        }

        totalDistance += previousLocation.getDistanceTo(depot.getLocation());
        return totalDistance;
    }

    public long getCompactnessPenalty() {
        if (stops.isEmpty()) {
            return 0L;
        }

        long penalty = 0L;
        Location previousLocation = depot.getLocation();

        for (RouteStop stop : stops) {
            long legDistance = previousLocation.getDistanceTo(stop.getLocation());
            penalty += (legDistance * legDistance) / 200L;
            previousLocation = stop.getLocation();
        }

        penalty += (long) Math.pow(previousLocation.getDistanceTo(depot.getLocation()), 2) / 250L;
        return penalty;
    }

    public double getAverageInterStopDistanceMeters() {
        if (stops.size() <= 1) {
            return 0.0;
        }

        long totalInterStopDistance = 0L;
        for (int i = 1; i < stops.size(); i++) {
            totalInterStopDistance += stops.get(i - 1).getLocation().getDistanceTo(stops.get(i).getLocation());
        }

        return totalInterStopDistance / (double) (stops.size() - 1);
    }

    public boolean hasLegLongerThan(long limitMeters) {
        return getExceededLegDistanceMeters(limitMeters) > 0L;
    }

    public long getExceededLegDistanceMeters(long limitMeters) {
        if (stops.isEmpty()) {
            return 0L;
        }

        long exceededDistance = 0L;
        Location previousLocation = depot.getLocation();

        for (RouteStop stop : stops) {
            long legDistance = previousLocation.getDistanceTo(stop.getLocation());
            if (legDistance > limitMeters) {
                exceededDistance += legDistance - limitMeters;
            }
            previousLocation = stop.getLocation();
        }

        long returnDistance = previousLocation.getDistanceTo(depot.getLocation());
        if (returnDistance > limitMeters) {
            exceededDistance += returnDistance - limitMeters;
        }

        return exceededDistance;
    }

    public long getExceededZoneDistanceMeters(long limitMeters) {
        if (stops.size() <= 1) {
            return 0L;
        }

        Location anchorLocation = stops.get(0).getLocation();
        long exceededDistance = 0L;

        for (int i = 1; i < stops.size(); i++) {
            long distanceFromAnchor = anchorLocation.getDistanceTo(stops.get(i).getLocation());
            if (distanceFromAnchor > limitMeters) {
                exceededDistance += distanceFromAnchor - limitMeters;
            }
        }

        return exceededDistance;
    }

    public double getTotalDemand() {
        return stops.stream().mapToDouble(RouteStop::getTotalDemand).sum();
    }

    public double getUnusedCapacity() {
        return Math.max(0.0, capacity - getTotalDemand());
    }

    public double getFillRatio() {
        if (!isUsed() || capacity <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, getTotalDemand() / capacity);
    }

    public long getUtilizationPenalty() {
        if (!isUsed()) {
            return 0L;
        }

        double fillRatio = getFillRatio();
        if (getStopCount() == 1 && fillRatio >= 0.60) {
            return 0L;
        }

        return Math.round(getUnusedCapacity());
    }

    public long countOrdersAtOrBelow(double demandLimit) {
        return stops.stream().mapToLong(stop -> stop.countOrdersAtOrBelow(demandLimit)).sum();
    }

    public long countOrdersAbove(double demandLimit) {
        return stops.stream().mapToLong(stop -> stop.countOrdersAbove(demandLimit)).sum();
    }

    public boolean hasOrderAbove(double demandLimit) {
        return countOrdersAbove(demandLimit) > 0L;
    }
}
