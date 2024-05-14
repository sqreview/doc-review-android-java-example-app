package com.signalquest.example;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static com.signalquest.example.SitePoint.CCCD;
import static com.signalquest.example.SitePoint.RTCM_CHARACTERISTIC;
import static com.signalquest.example.SitePoint.SITEPOINT_SERVICE;
import static com.signalquest.example.SitePoint.MESSAGE_CHARACTERISTIC;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import com.signalquest.api.Location;
import com.signalquest.api.MessageHandler;
import com.signalquest.api.ApiException;
import com.signalquest.api.Status;

import java.util.Collections;
import java.util.List;

/**
 * Interacts with Android's Bluetooth implementation.
 * <p>
 * Scans for SitePoints, connects to them, reads SitePoint messages and NTRIP data,
 * disconnects from SitePoints, and reports on some Bluetooth status.
 */
@SuppressLint("MissingPermission")
public class BleManager {
    private final static String LOG_TAG = "BleManager";
    static final String BT_CONNECT_ACTION = "com.signalquest.example.BT_CONNECT_ACTION";
    static final String BT_DISCONNECT_ACTION = "com.signalquest.example.BT_DISCONNECT_ACTION";
    static final String LOCATION_MESSAGE_RECEIVED = "com.signalquest.example.LOCATION_MESSAGE_RECEIVED";
    static final String STATUS_MESSAGE_RECEIVED = "com.signalquest.example.STATUS_MESSAGE_RECEIVED";
    SitePoint sitePoint = null;
    private BluetoothGatt gatt = null;
    private BluetoothGattCharacteristic messageCharacteristic = null;
    private BluetoothGattCharacteristic rtcmCharacteristic = null;
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothManager bluetoothManager = null;
    private BluetoothLeScanner bleScanner = null;
    private ScanCallback scanCallback = null;
    private boolean writingRtcm = false;

    /**
     * Scans and creates SitePoints from scan results.
     *
     * @param sitePointHandler listens for SitePoint scan results
     */
    public void startScanning(SitePointHandler sitePointHandler) {
        if (disabled()) {
            return;
        }
        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();
        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(SITEPOINT_SERVICE))
                        .build();
        List<ScanFilter> filters = Collections.singletonList(scanFilter);
        assignBluetoothVariables();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                final ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord == null) {
                    Log.w(LOG_TAG, "No scan record");
                    return;
                }
                sitePointHandler.onScanResult(new SitePoint(result, scanRecord));
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(LOG_TAG, "Scan failed with error code " + errorCode);
            }
        };
        bleScanner.startScan(filters, scanSettings, scanCallback);
    }

    public void stopScanning() {
        bleScanner.stopScan(scanCallback);
    }

    /**
     * Connect GATT for the passed in SitePoint.
     */
    public void connect(SitePoint sitePoint) throws IllegalStateException {
        this.sitePoint = sitePoint;
        gatt = sitePoint.scanResult.getDevice().connectGatt(App.getAppContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    /**
     * Disconnect from the connected SitePoint.
     * <p>
     * Disconnects GATT, but closing GATT is handled in the {@link BluetoothGattCallback}
     */
    public void disconnect() {
        if (sitePoint != null) {
            if (gatt != null) {
                try {
                    Log.i(LOG_TAG, "Disconnecting");
                    gatt.disconnect();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Disconnect exception", e);
                    App.displayError(LOG_TAG, "Disconnecting failed.");
                }
            } else {
                sitePoint = null;
                messageCharacteristic = null;
                rtcmCharacteristic = null;
            }
        }
    }

    /**
     * Turn on notifications for the passed-in characteristic.
     * <p>
     * Failure to enable the notification is unexpected, so we cleanup by disconnecting.
     */
    private void enableNotifications(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            Log.i(LOG_TAG, "enableNotifications, characteristic null");
            return;
        } else if ( (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.i(LOG_TAG,"enableNotifications, notify disabled");
            return;
        }
        Log.d(LOG_TAG,"enableNotifications characteristic " + characteristic.getUuid());
        boolean enabled = gatt.setCharacteristicNotification(characteristic, true);
        if (!enabled) {
            Log.w(LOG_TAG,"enableNotifications, setCharacteristicNotification failed");
            disconnect();
            return;
        }
        BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD);
        cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        // Calls onDescriptorWrite()
        if (!gatt.writeDescriptor(cccd)) {
            Log.w(LOG_TAG, "enableNotifications, writeDescriptor failed");
            disconnect();
        }
    }

    /**
     * Handle an NTRIP parse by writing the resulting RTCM data to a SitePoint.
     */
    void ntripParsed() {
        writeRtcm();
    }

    /**
     * Writes RTCM data to the {#rtcmCharacteristic}.
     * <p>
     * There may be more data then can be written at once, so this gets re-called in
     * {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)},
     * after the current data has been sent to the SitePoint (writing with
     * {@link BluetoothGattCharacteristic#WRITE_TYPE_NO_RESPONSE} for fast turnarounds).
     */
    private synchronized void writeRtcm() {
        if (writingRtcm || rtcmCharacteristic == null) { return; }
        // see https://developer.android.com/about/versions/14/behavior-changes-all#mtu-set-to-517
        byte[] message = App.getRtcmData(Math.min(mtu, 512) - 5);
        if (message.length == 0) {
            Log.d(LOG_TAG, "RTCM timing no data available");
            return;
        }
        writingRtcm = true;
        StringBuilder sb = new StringBuilder();
        for (byte b : message) { sb.append(String.format("%02X ", b)); }
        Log.d(LOG_TAG, "RTCM timing to write , " + sb);

        if (Build.VERSION.SDK_INT < 33) {
            rtcmCharacteristic.setValue(message);
            gatt.writeCharacteristic(rtcmCharacteristic);
        } else {
            gatt.writeCharacteristic(rtcmCharacteristic, message, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        }
    }

    private void assignBluetoothVariables() {
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) App.getAppContext().getSystemService(Context.BLUETOOTH_SERVICE);
        }
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (bluetoothAdapter != null && bleScanner == null) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    public boolean disabled() {
        assignBluetoothVariables();
        return bluetoothAdapter == null || !bluetoothAdapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public boolean connectedToThisAndroid(SitePoint sitePoint) {
        ScanResult scanResult = sitePoint.scanResult;
        BluetoothManager bluetoothManager = (BluetoothManager) App.getAppContext().getSystemService(Context.BLUETOOTH_SERVICE);
        return BluetoothProfile.STATE_CONNECTED == bluetoothManager.getConnectionState(scanResult.getDevice(), BluetoothProfile.GATT);
    }

    // default starting value, probably much lower than negotiated MTU
    private int mtu = 23;

    private void setMtu(int mtu) {
        this.mtu = mtu;
    }

    /**
     * Passes scan results back as SitePoints
     */
    public interface SitePointHandler {
        void onScanResult(SitePoint sitePoint);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        /**
         * Connected?
         * <ol>
         * <li>Request a higher MTU, which gets handled in {@link #onMtuChanged(BluetoothGatt, int, int)}.</li>
         * <li>Let the UI know by calling {@link #broadcastConnect()}.</li>
         * </ol>
         *
         * Disconnected?
         * <ol>
         * <li>Close GATT.</li>
         * <li>Let the UI know by calling {@link #broadcastDisconnect()}.</li>
         * </ol>
         *
         * Failed? Disconnect GATT (calls back to here with BluetoothProfile.STATE_DISCONNECTED).
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(LOG_TAG, "GATT connected");
                    BleManager.this.gatt = gatt;
                    // request Android max of 517
                    gatt.requestMtu(517);
                    broadcastConnect();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(LOG_TAG, "GATT disconnected");
                    gatt.close();
                    sitePoint = null;
                    messageCharacteristic = null;
                    rtcmCharacteristic = null;
                    broadcastDisconnect();
                } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                    Log.d(LOG_TAG, "GATT connecting");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                    Log.d(LOG_TAG, "GATT disconnecting");
                } else {
                    Log.w(LOG_TAG, "GATT unhandled state " + newState);
                }
            } else {
                Log.i(LOG_TAG,"GATT failed with status " + status);
                gatt.disconnect();
                sitePoint = null;
                messageCharacteristic = null;
                rtcmCharacteristic = null;
                broadcastDisconnect();
            }
        }

        /**
         * Call {@link BluetoothGatt#discoverServices()}, triggered from the {@link BluetoothGatt#requestMtu(int)}
         * call from {@link #onConnectionStateChange(BluetoothGatt, int, int)}.
         */
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(LOG_TAG, "mtu changed to " + mtu + " (success? " + (status == BluetoothGatt.GATT_SUCCESS) + ")");
            BleManager.this.setMtu(mtu);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices();
            }
        }

        /**
         * Hook up characteristics and enable notifications on the message characteristic.
         * triggered from the {@link BluetoothGatt#discoverServices()} call in {@link #onMtuChanged(BluetoothGatt, int, int)}.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(LOG_TAG,"Discovered service " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(LOG_TAG, "Discovered characteristic " + characteristic.getUuid());
                        if (MESSAGE_CHARACTERISTIC.equals(characteristic.getUuid())) {
                            messageCharacteristic = characteristic;
                        } else if (RTCM_CHARACTERISTIC.equals(characteristic.getUuid())) {
                            rtcmCharacteristic = characteristic;
                            // write "with response"
                            rtcmCharacteristic.setWriteType(WRITE_TYPE_NO_RESPONSE);
                        }
                    }
                }
                if (messageCharacteristic != null) {
                    Log.d(LOG_TAG,"enabling notifications for messaging characteristic");
                    enableNotifications(messageCharacteristic);
                }
            } else {
                Log.w(LOG_TAG, "Discovering services failed");
                disconnect();
            }
        }

        /**
         * Handle descriptor write failures (e.g. enabling/disabling notifications).
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor written");
            } else {
                Log.i(LOG_TAG, "Descriptor write failed");
                disconnect();
            }
        }

        /**
         * Send SignalQuest message data to {@link #readMessage(byte[])}.
         */
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] data) {
            super.onCharacteristicChanged(gatt, characteristic, data);
            Log.v(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid() + ", data " + toHex(data));
            if (characteristic.getUuid().equals(MESSAGE_CHARACTERISTIC)) {
                readMessage(data);
            }
        }

        /**
         * Same as {@link #onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, byte[])},
         * but for older Androids (pre Android 13, pre SDK level 33).
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Log.v(LOG_TAG, "Deprecated onCharacteristicChanged ignored");
                return;
            }
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(LOG_TAG, "onCharacteristicChanged (deprecated) " + characteristic.getUuid() + ", data " + toHex(characteristic.getValue()));
            if (characteristic.getUuid().equals(MESSAGE_CHARACTERISTIC)) {
                readMessage(characteristic.getValue());
            }
        }

        /**
         * Logging writes and write failures, and possibly writing the next batch of RTCM data.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic write successful");
                if (characteristic.getUuid().equals(RTCM_CHARACTERISTIC)) {
                    Log.d(LOG_TAG, "RTCM timing written");
                    writingRtcm = false;
                    writeRtcm();
                }
            } else {
                Log.e(LOG_TAG, "Characteristic write unsuccessful: " + status);
            }
        }

        /**
         * This {@link MessageHandler} parses SitePoint data, in {@link #readMessage(byte[])}, and broadcasts the results for the UI.
         */
        private final MessageHandler messageHandler = new MessageHandler(new MessageHandler.MessageReceiver() {
            @Override
            public void receive(Status status) {
                Intent intent = new Intent(STATUS_MESSAGE_RECEIVED);
                intent.putExtra("status", new StatusParcelable(status));
                App.getAppContext().sendBroadcast(intent);
                StringBuilder sb = new StringBuilder();
                for (boolean b : status.getAidingQuality()) { sb.append(b ? "1" : "0"); }
                Log.d(LOG_TAG, "RTCM timing aiding bins , " + sb);
            }

            @Override
            public void receive(Location location) {
                Intent intent = new Intent(LOCATION_MESSAGE_RECEIVED);
                intent.putExtra("location", new LocationParcelable(location));
                App.getAppContext().sendBroadcast(intent);
            }
        });

        // TODO turn on private Javadoc? Leaning towards YES for example, not SDK.
        /**
         * Parse the SignalQuest messages using the {@link MessageHandler}, which sends the results
         * to its {@link MessageHandler.MessageReceiver}.
         */
        private void readMessage(byte[] data) {
            if (data == null || data.length == 0 || allZero(data)) {
                Log.w(LOG_TAG, "No messages");
                return;
            }
            try {
                messageHandler.parse(data);
            } catch (ApiException e) {
                App.displayError(LOG_TAG, e.getMessage());
            } catch (Exception e) {
                App.displayError(LOG_TAG, "readMessage exception: " + e);
                Log.e(LOG_TAG, "readMessage failure", e);
            }
        }

        private boolean allZero(byte[] bytes) {
            for (byte b : bytes) { if (b != 0) { return false; }}
            return true;
        }

        private String toHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) { sb.append(String.format("%02X ", b)); }
            return sb.toString();
        }

        private void broadcastDisconnect() {
            App.getAppContext().sendBroadcast(new Intent(BT_DISCONNECT_ACTION));
        }

        private void broadcastConnect() {
            App.getAppContext().sendBroadcast(new Intent(BT_CONNECT_ACTION));
        }
    };
}
