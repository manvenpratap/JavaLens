package com.tcs.bancs.tr;

/**
 * BaNCS Persistent Lifecycle Entity - Enhanced
 */
public class TradePosition {
    private String positionId;
    private double quantity;
    private String counterparty;

    public TradePosition Get(String id) {
        return this;
    }

    public void Create() {
        System.out.println("Persisting position to datastore");
    }

    public void Modify() {
        System.out.println("Updating position in datastore");
    }

    public String getPositionId() {
        return positionId;
    }

    public String getCounterparty() {
        return counterparty;
    }
}
