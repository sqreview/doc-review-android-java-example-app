package com.signalquest.example;

import android.annotation.SuppressLint;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.SystemClock;
import android.util.SparseArray;

import com.signalquest.api.ScanStatus;

import java.util.UUID;

/**
 * Convenience wrapper around bluetooth scan information, and holder of SitePoint-specific Bluetooth IDs.
 */
public class SitePoint {

    private final static int SIGNALQUEST_ID = 3127;

    public static UUID SITEPOINT_SERVICE = UUID.fromString("00000100-34ed-12ef-63f4-317792041d17");
    public static UUID RTCM_CHARACTERISTIC = UUID.fromString("00000102-34ed-12ef-63f4-317792041d17");
    public static UUID MESSAGE_CHARACTERISTIC = UUID.fromString("00000105-34ed-12ef-63f4-317792041d17");
    public static UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static short SCAN_OLD_SECONDS = 10;
    final ScanResult scanResult;
    private final ScanRecord scanRecord;
    public ScanStatus scanStatus;

    public String getName() {
        return scanRecord.getDeviceName();
    }
    public String getAddress() {
        return scanResult.getDevice().getAddress();
    }
    public int getRssi() {
        return scanResult.getRssi();
    }

    public boolean addressMatches(SitePoint another) {
        if (another == null) {
            return false;
        }
        return getAddress().equals(another.getAddress());
    }

    /**
     * @return whether the scan result is old, so the UI can stop showing it.
     */
    public boolean expired() {
        long androidSeconds = (long) (SystemClock.elapsedRealtime() / Math.pow(10, 3));
        long scanSeconds = (long) (scanResult.getTimestampNanos() / Math.pow(10, 9));
        return SCAN_OLD_SECONDS < androidSeconds - scanSeconds;
    }

    @SuppressLint("MissingPermission")
    public SitePoint(ScanResult scanResult, ScanRecord scanRecord) {
        this.scanResult = scanResult;
        this.scanRecord = scanRecord;
        byte[] manufacturerData = getManufacturerData();
        this.scanStatus = new ScanStatus(manufacturerData);
    }

    private byte[] getManufacturerData() {
        byte[] signalQuestData = null;
        SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
        for (int i = 0; i < manufacturerData.size(); i++) {
            int manufacturerId = manufacturerData.keyAt(i);
            if (SIGNALQUEST_ID == manufacturerId) {
                signalQuestData = manufacturerData.valueAt(i);
            }
        }
        assert (signalQuestData != null);
        return signalQuestData;
    }

    /**
     * Connected to any Bluetooth Central, not necessarily this Android.
     */
    public boolean connectedFromScan() {
        // Please note: this app does not scan while connecting/connected, so this will
        // return false when connected to this Android.
        return scanStatus.isConnected();
    }
}
