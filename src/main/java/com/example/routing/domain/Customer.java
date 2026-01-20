package com.example.routing.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

public class Customer {

    @PlanningId
    private String id;
    private Location location;
    private double demand; // Weight
    private String name;
    private String address;
    private String deliveryWindow; // "WINDOW_1", "WINDOW_2", "WINDOW_3"

    public Customer() {}

    public Customer(String id, Location location, double demand, String name, String address, String deliveryWindow) {
        this.id = id;
        this.location = location;
        this.demand = demand;
        this.name = name;
        this.address = address;
        this.deliveryWindow = deliveryWindow;
    }

    public Customer(String id, Location location, double demand, String name, String address) {
        this(id, location, demand, name, address, "WINDOW_2"); // Default to standard window
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }
    public double getDemand() { return demand; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDeliveryWindow() { return deliveryWindow; }
    
    @Override
    public String toString() {
        return id;
    }
}
