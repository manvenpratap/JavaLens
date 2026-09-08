package com.example;

public class PaymentGateway {
    public static final int DEFAULT_TIMEOUT_MS = 5000;
    @Id
    private String merchantKey;
    @NotNull
    private int retryAttempts;

    // START_MERGE
    private int transactionTimeout;
    // END_MERGE

    public PaymentGateway(String merchantKey) {
        this.merchantKey = merchantKey;
        this.retryAttempts = 3;
    }

    // START_MERGE
    public boolean charge(String customerId, double amount) {
        System.out.println("Executing baseline charge: $" + amount + " for customer " + customerId);
        return amount > 0;
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
