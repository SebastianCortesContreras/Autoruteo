package com.example.routing.service;

import com.example.routing.domain.Customer;
import com.example.routing.domain.Location;
import com.example.routing.domain.RouteStop;
import com.example.routing.domain.Vehicle;
import com.example.routing.domain.VehicleRoutingSolution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutePlanResponseBuilder {

    public Map<String, Object> buildResponse(List<Customer> originalOrders, VehicleRoutingSolution solution) {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> routes = new ArrayList<>();

        for (Vehicle vehicle : solution.getVehicleList()) {
            if (!vehicle.isUsed()) {
                continue;
            }

            Map<String, Object> route = new LinkedHashMap<>();
            route.put("vehicleId", vehicle.getId());
            route.put("vehicleType", vehicle.getType());
            route.put("capacity", vehicle.getCapacity());
            route.put("totalLoad", vehicle.getTotalDemand());
            route.put("totalDistance", vehicle.getTotalDistanceMeters());
            route.put("totalDistanceKm", String.format("%.2f km", vehicle.getTotalDistanceMeters() / 1000.0));
            route.put("comments", vehicle.getComments());
            route.put("routeStatus", vehicle.getRouteStatus());

            List<Map<String, Object>> orders = new ArrayList<>();
            Location previousLocation = vehicle.getDepot().getLocation();
            long accumulatedDistance = 0L;

            for (RouteStop stop : vehicle.getStops()) {
                accumulatedDistance += previousLocation.getDistanceTo(stop.getLocation());

                for (Customer order : stop.getOrders()) {
                    Map<String, Object> orderPayload = new LinkedHashMap<>();
                    orderPayload.put("id", order.getId());
                    orderPayload.put("name", order.getName());
                    orderPayload.put("address", order.getAddress());
                    orderPayload.put("demand", order.getDemand());
                    orderPayload.put("deliveryWindow", order.getDeliveryWindow());
                    orderPayload.put("location", order.getLocation());
                    orderPayload.put("accumulatedDistanceKm", String.format("%.2f km", accumulatedDistance / 1000.0));
                    orders.add(orderPayload);
                }

                previousLocation = stop.getLocation();
            }

            route.put("orders", orders);
            routes.add(route);
        }

        int assignedOrders = solution.getVehicleList().stream().mapToInt(Vehicle::getTotalOrderCount).sum();
        response.put("totalVehiclesUsed", routes.size());
        response.put("routes", routes);
        response.put("unassigned", Math.max(0, originalOrders.size() - assignedOrders));
        return response;
    }
}
