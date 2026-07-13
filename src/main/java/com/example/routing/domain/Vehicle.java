package com.example.routing.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@PlanningEntity
public class Vehicle {

    @PlanningId
    private String id;
    private double capacity;
    private String type; // "Carry" or "NHR"
    private Depot depot;
    private long fixedCost; // Costo fijo por usar este vehículo
    private String comments;
    private String routeStatus;

    @PlanningListVariable
    private List<Customer> customers = new ArrayList<>();

    public Vehicle() {}

    public Vehicle(String id, double capacity, String type, Depot depot, long fixedCost) {
        this.id = id;
        this.capacity = capacity;
        this.type = type;
        this.depot = depot;
        this.fixedCost = fixedCost;
    }

    // Constructor simplificado para compatibilidad
    public Vehicle(String id, double capacity, String type, Depot depot) {
        this(id, capacity, type, depot, 0);
    }

    public String getId() { return id; }
    public double getCapacity() { return capacity; }
    public String getType() { return type; }
    public Depot getDepot() { return depot; }
    public long getFixedCost() { return fixedCost; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    
    public String getRouteStatus() { return routeStatus; }
    public void setRouteStatus(String routeStatus) { this.routeStatus = routeStatus; }

    public List<Customer> getCustomers() { return customers; }
    public void setCustomers(List<Customer> customers) { this.customers = customers; }

    public long getTotalDistanceMeters() {
        if (customers.isEmpty()) return 0;
        
        long totalDistance = 0;
        Location previousLocation = depot.getLocation();
        
        for (Customer customer : customers) {
            totalDistance += previousLocation.getDistanceTo(customer.getLocation());
            previousLocation = customer.getLocation();
        }
        
        // Return to depot excluded based on new requirement
        // totalDistance += previousLocation.getDistanceTo(depot.getLocation());
        
        return totalDistance;
    }

    public long getCompactnessPenalty() {
        if (customers.isEmpty()) {
            return 0;
        }

        long penalty = 0;
        Location previousLocation = depot.getLocation();

        for (Customer customer : customers) {
            long legDistance = previousLocation.getDistanceTo(customer.getLocation());
            // Penaliza mucho más los saltos largos para favorecer secuencias de pedidos cercanos.
            penalty += (legDistance * legDistance) / 100L;
            previousLocation = customer.getLocation();
        }

        return penalty;
    }

    // Verifica si algún tramo (Depot->C1, C1->C2... o Cn->Depot) supera el límite en metros
    public boolean hasLegLongerThan(long limitMeters) {
        if (customers.size() <= 1) return false;

        Location previousLocation = customers.get(0).getLocation();
        for (int i = 1; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            if (previousLocation.getDistanceTo(customer.getLocation()) > limitMeters) {
                return true;
            }
            previousLocation = customer.getLocation();
        }
        return false;
    }

    public long getExceededLegDistanceMeters(long limitMeters) {
        if (customers.size() <= 1) {
            return 0;
        }

        long totalExceededDistance = 0;
        Location previousLocation = customers.get(0).getLocation();

        for (int i = 1; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            long legDistance = previousLocation.getDistanceTo(customer.getLocation());
            if (legDistance > limitMeters) {
                totalExceededDistance += (legDistance - limitMeters);
            }
            previousLocation = customer.getLocation();
        }

        return totalExceededDistance;
    }

    public long getExceededZoneDistanceMeters(long limitMeters) {
        if (customers.size() <= 1) {
            return 0;
        }

        Location anchorLocation = customers.get(0).getLocation();
        long totalExceededDistance = 0;

        for (int i = 1; i < customers.size(); i++) {
            long distanceFromAnchor = anchorLocation.getDistanceTo(customers.get(i).getLocation());
            if (distanceFromAnchor > limitMeters) {
                totalExceededDistance += (distanceFromAnchor - limitMeters);
            }
        }

        return totalExceededDistance;
    }

    public double getTotalDemand() {
        return customers.stream().mapToDouble(Customer::getDemand).sum();
    }

    public long getSharedLocationKeyCount(Vehicle other) {
        if (other == null || this.customers.isEmpty() || other.customers.isEmpty()) {
            return 0;
        }

        Set<String> ownLocationKeys = new HashSet<>();
        for (Customer customer : this.customers) {
            ownLocationKeys.add(customer.getLocationGroupingKey());
        }

        long sharedCount = 0;
        Set<String> alreadyCounted = new HashSet<>();
        for (Customer customer : other.customers) {
            String key = customer.getLocationGroupingKey();
            if (ownLocationKeys.contains(key) && alreadyCounted.add(key)) {
                sharedCount++;
            }
        }

        return sharedCount;
    }
}
