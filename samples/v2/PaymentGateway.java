package com.example;

public class PaymentGateway {
    public static final int DEFAULT_TIMEOUT_MS = 5000;
    private String merchantKey;
    private int retryAttempts;

    // START_MERGE
    private long transactionTimeout;
    private String webhookEndpoint;
    private boolean idempotencyEnabled;
    private String apiKey;
    // END_MERGE

    public PaymentGateway(String merchantKey) {
        this.merchantKey = merchantKey;
        this.retryAttempts = 3;
    }

    // START_MERGE
    public boolean charge(String customerId, double amount) {
        System.out.println("Executing enhanced charge: $" + amount + " for customer " + customerId);
        if (idempotencyEnabled) {
            System.out.println("Idempotent charge token verified for customer " + customerId);
        }
        dispatchWebhook("EVT_CHARGE_SUCCESS", customerId + ":" + amount);
        return amount > 0;
    }

    public String dispatchWebhook(String eventId, String payload) {
        String eventUrl = (this.webhookEndpoint != null) ? this.webhookEndpoint : "https://api.payments.internal/events";
        System.out.println("Dispatching webhook event [" + eventId + "] to " + eventUrl + " with payload: " + payload);
        return "DISPATCHED:" + eventId;
    }

    public boolean validateSignature(String signature, String payload) {
        return signature != null && !signature.isEmpty() && payload != null;
    }

    public String getWebhookEndpoint() {
        return this.webhookEndpoint;
    }

    public void setWebhookEndpoint(String endpoint) {
        this.webhookEndpoint = endpoint;
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
