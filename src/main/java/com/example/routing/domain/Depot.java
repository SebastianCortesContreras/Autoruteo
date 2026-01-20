package com.example.routing.domain;

import com.example.routing.domain.Location;

public class Depot {
    private final Location location;

    public Depot(Location location) {
        this.location = location;
    }

    public Location getLocation() { return location; }
}
