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

    // Verifica si algún tramo (Depot->C1, C1->C2... o Cn->Depot) supera el límite en metros
    public boolean hasLegLongerThan(long limitMeters) {
        if (customers.isEmpty()) return false;

        Location previousLocation = depot.getLocation();
        for (Customer customer : customers) {
            if (previousLocation.getDistanceTo(customer.getLocation()) > limitMeters) {
                return true;
            }
            previousLocation = customer.getLocation();
        }
        // Check return to depot logic? 
        // El usuario dijo "pedidos de mas de 5 km", usualmente se refiere a inter-paradas.
        // Incluir el retorno al depósito podría ser muy estricto si el depósito está lejos.
        // Asumiremos tramos de ida y entre clientes.
        // Si el cliente está a > 5km del depósito inicial, también cuenta.
        return false;
    }

    public double getTotalDemand() {
        return customers.stream().mapToDouble(Customer::getDemand).sum();
    }
}
