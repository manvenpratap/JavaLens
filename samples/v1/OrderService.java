package com.example;

public class OrderService {
    private String serviceId;
    private boolean active;
    private double taxRate;

    // START_MERGE
    // END_MERGE

    public OrderService(String serviceId, double taxRate) {
        this.serviceId = serviceId;
        this.taxRate = taxRate;
        this.active = true;
    }

    public boolean isServiceAvailable() {
        return this.active;
    }

    public double calculateTotal(double baseAmount, int quantity) {
        double subtotal = baseAmount * quantity;
        return subtotal + (subtotal * this.taxRate);
    }

    // START_MERGE
    public void processOrder(String orderId, String customerId, double amount) {
        System.out.println("Processing order " + orderId + " for customer " + customerId + ": $" + amount);
    }
    // END_MERGE

    public String getServiceStatus() {
        return active ? "ONLINE" : "OFFLINE";
    }
}
