package com.example.routing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routing")
public class RoutingRulesProperties {

    private int solverSeconds = 15;
    private long maxRouteDistanceMeters = 40_000L;
    private long maxLegDistanceMeters = 5_000L;
    private long maxClusterRadiusMeters = 10_000L;
    private long maxInterStopDistanceMeters = 10_000L;
    private long urbanClusterRadiusMeters = 6_000L;
    private long outOfCityClusterRadiusMeters = 18_000L;
    private long outOfCityThresholdMeters = 15_000L;
    private long openRouteAverageStopDistanceMeters = 2_000L;

    private double carryCapacityKg = 750.0;
    private double nhrCapacityKg = 3_000.0;
    private double nprCapacityKg = 5_000.0;
    private double hypotheticalCapacityKg = 10_000.0;

    private double carryHeavyOrderThresholdKg = 500.0;
    private double nprHeavyOrderThresholdKg = 3_000.0;

    private int nhrMaxOrders = 10;
    private int nprMaxOrders = 5;

    private long carryFixedCost = 0L;
    private long carrySecondTripFixedCost = 80L;
    private long nhrFixedCost = 320L;
    private long nprFixedCost = 450L;
    private long hypotheticalFixedCost = 100_000_000L;

    private long distanceWeight = 30L;
    private long clusterOverflowWeight = 200L;
    private long utilizationWeight = 80L;
    private long smallOrderInLargeVehicleWeight = 1_000L;
    private long hypotheticalOrderWeight = 5_000_000L;
    private long nprWithoutHeavyOrderWeight = 40_000L;

    public int getSolverSeconds() {
        return solverSeconds;
    }

    public void setSolverSeconds(int solverSeconds) {
        this.solverSeconds = solverSeconds;
    }

    public long getMaxRouteDistanceMeters() {
        return maxRouteDistanceMeters;
    }

    public void setMaxRouteDistanceMeters(long maxRouteDistanceMeters) {
        this.maxRouteDistanceMeters = maxRouteDistanceMeters;
    }

    public long getMaxLegDistanceMeters() {
        return maxLegDistanceMeters;
    }

    public void setMaxLegDistanceMeters(long maxLegDistanceMeters) {
        this.maxLegDistanceMeters = maxLegDistanceMeters;
    }

    public long getMaxClusterRadiusMeters() {
        return maxClusterRadiusMeters;
    }

    public void setMaxClusterRadiusMeters(long maxClusterRadiusMeters) {
        this.maxClusterRadiusMeters = maxClusterRadiusMeters;
    }

    public long getMaxInterStopDistanceMeters() {
        return maxInterStopDistanceMeters;
    }

    public void setMaxInterStopDistanceMeters(long maxInterStopDistanceMeters) {
        this.maxInterStopDistanceMeters = maxInterStopDistanceMeters;
    }

    public long getUrbanClusterRadiusMeters() {
        return urbanClusterRadiusMeters;
    }

    public void setUrbanClusterRadiusMeters(long urbanClusterRadiusMeters) {
        this.urbanClusterRadiusMeters = urbanClusterRadiusMeters;
    }

    public long getOutOfCityClusterRadiusMeters() {
        return outOfCityClusterRadiusMeters;
    }

    public void setOutOfCityClusterRadiusMeters(long outOfCityClusterRadiusMeters) {
        this.outOfCityClusterRadiusMeters = outOfCityClusterRadiusMeters;
    }

    public long getOutOfCityThresholdMeters() {
        return outOfCityThresholdMeters;
    }

    public void setOutOfCityThresholdMeters(long outOfCityThresholdMeters) {
        this.outOfCityThresholdMeters = outOfCityThresholdMeters;
    }

    public long getOpenRouteAverageStopDistanceMeters() {
        return openRouteAverageStopDistanceMeters;
    }

    public void setOpenRouteAverageStopDistanceMeters(long openRouteAverageStopDistanceMeters) {
        this.openRouteAverageStopDistanceMeters = openRouteAverageStopDistanceMeters;
    }

    public double getCarryCapacityKg() {
        return carryCapacityKg;
    }

    public void setCarryCapacityKg(double carryCapacityKg) {
        this.carryCapacityKg = carryCapacityKg;
    }

    public double getNhrCapacityKg() {
        return nhrCapacityKg;
    }

    public void setNhrCapacityKg(double nhrCapacityKg) {
        this.nhrCapacityKg = nhrCapacityKg;
    }

    public double getNprCapacityKg() {
        return nprCapacityKg;
    }

    public void setNprCapacityKg(double nprCapacityKg) {
        this.nprCapacityKg = nprCapacityKg;
    }

    public double getHypotheticalCapacityKg() {
        return hypotheticalCapacityKg;
    }

    public void setHypotheticalCapacityKg(double hypotheticalCapacityKg) {
        this.hypotheticalCapacityKg = hypotheticalCapacityKg;
    }

    public double getCarryHeavyOrderThresholdKg() {
        return carryHeavyOrderThresholdKg;
    }

    public void setCarryHeavyOrderThresholdKg(double carryHeavyOrderThresholdKg) {
        this.carryHeavyOrderThresholdKg = carryHeavyOrderThresholdKg;
    }

    public double getNprHeavyOrderThresholdKg() {
        return nprHeavyOrderThresholdKg;
    }

    public void setNprHeavyOrderThresholdKg(double nprHeavyOrderThresholdKg) {
        this.nprHeavyOrderThresholdKg = nprHeavyOrderThresholdKg;
    }

    public int getNhrMaxOrders() {
        return nhrMaxOrders;
    }

    public void setNhrMaxOrders(int nhrMaxOrders) {
        this.nhrMaxOrders = nhrMaxOrders;
    }

    public int getNprMaxOrders() {
        return nprMaxOrders;
    }

    public void setNprMaxOrders(int nprMaxOrders) {
        this.nprMaxOrders = nprMaxOrders;
    }

    public long getCarryFixedCost() {
        return carryFixedCost;
    }

    public void setCarryFixedCost(long carryFixedCost) {
        this.carryFixedCost = carryFixedCost;
    }

    public long getCarrySecondTripFixedCost() {
        return carrySecondTripFixedCost;
    }

    public void setCarrySecondTripFixedCost(long carrySecondTripFixedCost) {
        this.carrySecondTripFixedCost = carrySecondTripFixedCost;
    }

    public long getNhrFixedCost() {
        return nhrFixedCost;
    }

    public void setNhrFixedCost(long nhrFixedCost) {
        this.nhrFixedCost = nhrFixedCost;
    }

    public long getNprFixedCost() {
        return nprFixedCost;
    }

    public void setNprFixedCost(long nprFixedCost) {
        this.nprFixedCost = nprFixedCost;
    }

    public long getHypotheticalFixedCost() {
        return hypotheticalFixedCost;
    }

    public void setHypotheticalFixedCost(long hypotheticalFixedCost) {
        this.hypotheticalFixedCost = hypotheticalFixedCost;
    }

    public long getDistanceWeight() {
        return distanceWeight;
    }

    public void setDistanceWeight(long distanceWeight) {
        this.distanceWeight = distanceWeight;
    }

    public long getClusterOverflowWeight() {
        return clusterOverflowWeight;
    }

    public void setClusterOverflowWeight(long clusterOverflowWeight) {
        this.clusterOverflowWeight = clusterOverflowWeight;
    }

    public long getUtilizationWeight() {
        return utilizationWeight;
    }

    public void setUtilizationWeight(long utilizationWeight) {
        this.utilizationWeight = utilizationWeight;
    }

    public long getSmallOrderInLargeVehicleWeight() {
        return smallOrderInLargeVehicleWeight;
    }

    public void setSmallOrderInLargeVehicleWeight(long smallOrderInLargeVehicleWeight) {
        this.smallOrderInLargeVehicleWeight = smallOrderInLargeVehicleWeight;
    }

    public long getHypotheticalOrderWeight() {
        return hypotheticalOrderWeight;
    }

    public void setHypotheticalOrderWeight(long hypotheticalOrderWeight) {
        this.hypotheticalOrderWeight = hypotheticalOrderWeight;
    }

    public long getNprWithoutHeavyOrderWeight() {
        return nprWithoutHeavyOrderWeight;
    }

    public void setNprWithoutHeavyOrderWeight(long nprWithoutHeavyOrderWeight) {
        this.nprWithoutHeavyOrderWeight = nprWithoutHeavyOrderWeight;
    }
}
