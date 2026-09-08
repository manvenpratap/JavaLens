package com.example;

public class OrderService {
    private String serviceId;
    private boolean active;
    private double taxRate;

    // START_MERGE
    private boolean fraudCheckEnabled;
    private String currencyCode;
    private double discountRate;
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
            System.err.println("Transaction declined: High fraud risk for " + customerId);
            return;
        }
        double finalAmount = applyDiscount(amount);
        System.out.println("Processing order " + orderId + " [" + (currencyCode != null ? currencyCode : "USD") + "] for customer " + customerId + ": $" + finalAmount);
    }

    public boolean verifyFraudScore(String customerId, double amount) {
        return customerId != null && amount < 10000.0;
    }

    public double applyDiscount(double amount) {
        return amount - (amount * this.discountRate);
    }

    public String getCurrencyCode() {
        return this.currencyCode;
    }

    public void setDiscountRate(double discountRate) {
        this.discountRate = discountRate;
    }
    // END_MERGE

    public String getServiceStatus() {
        return active ? "ONLINE" : "OFFLINE";
    }
}
