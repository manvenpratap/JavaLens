package com.tcs.bancs.tr;

/**
 * BaNCS Data Grabber Component for Order Books - Enhanced
 */
public class TRDGOrderBookGrabber {
    private String marketVenue = "NYSE";
    private int bufferDepth = 500;

    public String TRDGExtractSnapshot() {
        return "SNAPSHOT_OK";
    }

    public int getBufferDepth() {
        return bufferDepth;
    }
}
