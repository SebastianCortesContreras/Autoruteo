package com.example.routing.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

import java.util.List;

@PlanningSolution
public class VehicleRoutingSolution {

    @PlanningEntityCollectionProperty
    private List<Vehicle> vehicleList;

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Customer> customerList;

    @PlanningScore
    private HardSoftLongScore score;

    public VehicleRoutingSolution() {}

    public VehicleRoutingSolution(List<Vehicle> vehicleList, List<Customer> customerList) {
        this.vehicleList = vehicleList;
        this.customerList = customerList;
    }

    public List<Vehicle> getVehicleList() { return vehicleList; }
    public void setVehicleList(List<Vehicle> vehicleList) { this.vehicleList = vehicleList; }

    public List<Customer> getCustomerList() { return customerList; }
    public void setCustomerList(List<Customer> customerList) { this.customerList = customerList; }

    public HardSoftLongScore getScore() { return score; }
    public void setScore(HardSoftLongScore score) { this.score = score; }
}
