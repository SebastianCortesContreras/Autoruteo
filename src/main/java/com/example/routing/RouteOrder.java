package com.example.routing;

public class RouteOrder {
    private String id;
    private String client;
    private String address;
    private Double latitude;
    private Double longitude;
    private String deliveryWindow;
    private Double weight;

    public RouteOrder() {}

    public RouteOrder(String id, String client, String address, Double latitude, Double longitude, String deliveryWindow, Double weight) {
        this.id = id;
        this.client = client;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deliveryWindow = deliveryWindow;
        this.weight = weight;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getDeliveryWindow() { return deliveryWindow; }
    public void setDeliveryWindow(String deliveryWindow) { this.deliveryWindow = deliveryWindow; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    @Override
    public String toString() {
        return "RouteOrder{" +
                "id='" + id + '\'' +
                ", client='" + client + '\'' +
                ", address='" + address + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", deliveryWindow='" + deliveryWindow + '\'' +
                ", weight=" + weight +
                '}';
    }
}
