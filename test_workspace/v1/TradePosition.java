package com.tcs.bancs.tr;

/**
 * BaNCS Persistent Lifecycle Entity
 * Must never be classified as simple POJO due to Get/Create/Modify lifecycle methods.
 */
public class TradePosition {
    private String positionId;
    private double quantity;

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
}
