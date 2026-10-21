package com.tcs.bancs.tr;

/**
 * TCS BaNCS Trade Order Processing Service
 * Demonstrates Banking Module Archetypes & Merger Policies
 */
public class TradeOrderService {

    private String serviceId = "TR-SRV-001";
    private String deskCode = "FX-TRADING";

    // START_MERGE
    private boolean highFrequencyTrading = false;
    // END_MERGE

    // START_MERGE
    // {MODULE}ET -> FETCH archetype. MERGER POLICY: PRESERVE_EXISTING
    public String TRETGetOrderStatus(String orderId) {
        // PRODUCTION BASELINE LOGIC - DO NOT OVERWRITE IN AUTOMATED MERGES
        if (orderId == null) return "INVALID_ID";
        return "STATUS_CONFIRMED_V1_PRODUCTION";
    }
    // END_MERGE

    // START_MERGE
    // {MODULE}BT -> MUTATE archetype. MERGER POLICY: MARKER_MERGE
    public boolean TRBTSubmitOrder(String orderId, double amount) {
        if (amount <= 0.0) return false;
        System.out.println("Processing trade order: " + orderId);
        return true;
    }
    // END_MERGE

    // POJO getter
    public String getDeskCode() {
        return deskCode;
    }
}
