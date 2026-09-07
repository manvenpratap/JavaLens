package com.example;

public class PaymentGateway {
    public static final int DEFAULT_TIMEOUT_MS = 5000;
    private String merchantKey;
    private int retryAttempts;

    // START_MERGE
    private String webhookEndpoint; // ADDED
    private boolean idempotencyEnabled; // ADDED
    // END_MERGE

    public PaymentGateway(String merchantKey) {
        this.merchantKey = merchantKey;
        this.retryAttempts = 3;
    }

    // START_MERGE
    public boolean charge(String customerId, double amount) {
        System.out.println("Executing enhanced charge: $" + amount + " for customer " + customerId);
        if (idempotencyEnabled) {
            System.out.println("Idempotent token verified for customer " + customerId);
        }
        dispatchWebhook("EVT_CHARGE", customerId + ":" + amount);
        return amount > 0;
    }

    public String dispatchWebhook(String eventId, String payload) {
        return "DISPATCHED:" + eventId + " -> " + this.webhookEndpoint;
    }
    // END_MERGE

    public boolean refund(String transactionId, double amount) {
        System.out.println("Refunding transaction: " + transactionId + ", amount: $" + amount);
        return true;
    }

    public int getRetryAttempts() {
        return this.retryAttempts;
    }

    public void setRetryAttempts(int retries) {
        this.retryAttempts = retries;
    }
}
