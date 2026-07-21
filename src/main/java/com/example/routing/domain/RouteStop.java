package com.example.routing.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteStop {

    @PlanningId
    private String id;
    private Location location;
    private String address;
    private String groupingKey;
    private List<Customer> orders = new ArrayList<>();

    public RouteStop() {
    }

    public RouteStop(String id, Location location, String address, String groupingKey, List<Customer> orders) {
        this.id = id;
        this.location = location;
        this.address = address;
        this.groupingKey = groupingKey;
        this.orders = new ArrayList<>(orders);
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getAddress() {
        return address;
    }

    public String getGroupingKey() {
        return groupingKey;
    }

    public List<Customer> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public double getTotalDemand() {
        return orders.stream().mapToDouble(Customer::getDemand).sum();
    }

    public int getOrderCount() {
        return orders.size();
    }

    public boolean hasWindow(String deliveryWindow) {
        return orders.stream().anyMatch(order -> deliveryWindow.equals(order.getDeliveryWindow()));
    }

    public long countOrdersAtOrBelow(double demandLimit) {
        return orders.stream().filter(order -> order.getDemand() <= demandLimit).count();
    }

    public long countOrdersAbove(double demandLimit) {
        return orders.stream().filter(order -> order.getDemand() > demandLimit).count();
    }

    public double getMaxOrderDemand() {
        return orders.stream().mapToDouble(Customer::getDemand).max().orElse(0.0);
    }

    @Override
    public String toString() {
        return id;
    }
}
