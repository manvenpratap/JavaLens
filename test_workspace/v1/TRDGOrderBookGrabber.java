package com.tcs.bancs.tr;

/**
 * BaNCS Data Grabber Component for Order Books
 */
public class TRDGOrderBookGrabber {
    private String marketVenue = "NYSE";

    public String TRDGExtractSnapshot() {
        return "SNAPSHOT_OK";
    }
}
