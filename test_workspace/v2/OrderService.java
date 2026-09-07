package com.example;

public class OrderService {
    private String serviceId;
    private boolean active;
    private double taxRate;

    // START_MERGE
    private boolean fraudCheckEnabled; // ADDED
    private String currencyCode; // ADDED
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
        if (this.fraudCheckEnabled && !verifyFraudScore(customerId, amount)) {
            System.err.println("Transaction declined: Fraud risk detected for " + customerId);
            return;
        }
        System.out.println("Processing order " + orderId + " [" + currencyCode + "] for customer " + customerId + ": $" + amount);
    }

    public boolean verifyFraudScore(String customerId, double amount) {
        return customerId != null && amount < 10000.0;
    }
    // END_MERGE

    public String getServiceStatus() {
        return active ? "ONLINE" : "OFFLINE";
    }
}
