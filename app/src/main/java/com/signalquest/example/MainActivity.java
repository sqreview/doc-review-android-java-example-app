package com.signalquest.example;

import android.annotation.SuppressLint;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.signalquest.api.Location;
import com.signalquest.api.Status;
import com.signalquest.example.Ntrip.NtripService;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

/**
 * Scan for and connect to SitePoints, display their data, and relay NTRIP to them.
 */
public class MainActivity extends AppCompatActivity {
    final private static String LOG_TAG = "MainActivity";
    private boolean scanning = false;
    private final ArrayList<SitePoint> sitePoints = new ArrayList<>();
    private SitePointView sitePointView = null;
    private final Handler expiryHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @SuppressLint("MissingSuperCall")
            @Override
            public void handleOnBackPressed() {
                // prevent back press
            }
        });
    }

    @SuppressLint("MissingSuperCall")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // prevent back press
    }

    /**
     * <ol>
     *     <li>Sets up a {@link BroadcastReceiver}, for the actions listed below in 'dataFilter'.</li>
     *     <li>Hooks up section toggling and NTRIP button click handlers.</li>
     *     <li>Attaches SitePoint section, for receiving scan updates.</li>
     *     <li>Ensures Bluetooth is enabled.</li>
     *     <li>Starts scanning.</li>
     * </ol>
     */
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter dataFilter = new IntentFilter();
        for (String action : new String [] {
                App.ERROR_ACTION,
                BleManager.BT_CONNECT_ACTION, BleManager.BT_DISCONNECT_ACTION,
                Ntrip.NTRIP_DISCONNECT_ACTION,
                BleManager.LOCATION_MESSAGE_RECEIVED, BleManager.STATUS_MESSAGE_RECEIVED,
        }) {
            dataFilter.addAction(action);
        }
        ContextCompat.registerReceiver(this, receiver, dataFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        setupToggling(R.id.sitepoint_tab_header_toggle, R.id.sitepoint_layout);
        setupToggling(R.id.ntrip_tab_header_toggle, R.id.ntrip_layout);
        setupToggling(R.id.status_tab_header_toggle, R.id.status_layout);
        setupToggling(R.id.location_tab_header_toggle, R.id.location_layout);

        Button ntripConnectButton = findViewById(R.id.ntrip_connect_button);
        ntripConnectButton.setOnClickListener(view -> {
            if (App.ntrip.getState() == Ntrip.State.IDLE) {
                connectNtrip();
            } else {
                disconnectNtrip();
            }
        });

        RecyclerView sitePointRow = findViewById(R.id.sitepoint_row);
        sitePointRow.setItemAnimator(null);
        sitePointRow.setLayoutManager(new LinearLayoutManager(this));
        sitePointView = new SitePointView();
        sitePointRow.setAdapter(sitePointView);

        if (App.bleManager.disabled()) {
            enableBluetooth();
        } else if (App.bleManager.sitePoint == null) {
            sitePoints.clear();
            startScanning();
        }
    }

    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(ACTION_REQUEST_ENABLE);
        bluetoothEnabler.launch(enableBtIntent);
    }

    private final ActivityResultLauncher<Intent> bluetoothEnabler = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.i(LOG_TAG, "Bluetooth enabled");
                    startScanning();
                } else {
                    Toast.makeText(this,"Bluetooth is required for this example app.",Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
    );

    /**
     * Sets up section-toggling controls.
     */
    private void setupToggling(int imageViewId, int layoutId) {
        ImageView view = findViewById(imageViewId);
        view.setOnClickListener(v -> {
            LinearLayout layout = findViewById(layoutId);
            if (layout.getVisibility() == LinearLayout.VISIBLE) {
                layout.setVisibility(LinearLayout.GONE);
                view.setImageResource(R.drawable.ic_arrow_up);
            } else {
                layout.setVisibility(LinearLayout.VISIBLE);
                view.setImageResource(R.drawable.ic_arrow_down);
            }
        });
    }

    private void connectNtrip() {
        if (Ntrip.State.IDLE != App.ntrip.getState()) {
            showErrorDialog("NTRIP is active");
            return;
        }

        try {
            String server = getNtripValue(R.id.ntrip_server);
            String portString = getNtripValue(R.id.ntrip_port);
            String username = getNtripValue(R.id.ntrip_username);
            String password = getNtripValue(R.id.ntrip_password);
            String mountpoint = getNtripValue(R.id.ntrip_mountpoint);

            int port;
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                showErrorDialog("Invalid NTRIP port");
                return;
            }

            SwitchMaterial sm = findViewById(R.id.ntrip_send_position);
            boolean sendPosition = sm.isChecked();
            App.ntrip.connect(new NtripService(server, port, username, password, mountpoint, sendPosition));

            Button button = findViewById(R.id.ntrip_connect_button);
            button.setText(R.string.ntrip_disconnect);
        } catch (MissingValueException e) {
            showErrorDialog(e.getMessage());
        }
    }

    private static class MissingValueException extends Exception {
        public MissingValueException(String s) {
            super(s);
        }
    }

    private String getNtripValue(int fieldId) throws MissingValueException {
        EditText field = findViewById(fieldId);
        String value = field.getText().toString().trim();
        if (value.isEmpty()) {
            throw new MissingValueException("Missing NTRIP " + field.getHint());
        }
        return value;
    }

    private void disconnectNtrip() {
        App.ntrip.disconnect();
    }

    private void handleNtripDisconnected() {
        Button button = findViewById(R.id.ntrip_connect_button);
        button.setText(R.string.ntrip_connect);
    }

    public void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ntrip Error")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.bleManager.disconnect();
    }

    /**
     * Removes old scan results; turned on in {@link #startScanning()}, turns off when {@link #scanning} is false.
     */
    Runnable expiryChecker = new Runnable() {
        @Override
        public void run() {
            if (!scanning) {
                return;
            }
            // reversed for deleting by index
            for (int i = sitePoints.size() - 1; 0 <= i; i--) {
                SitePoint sitePoint = sitePoints.get(i);
                if (sitePoint.expired()) {
                    sitePointView.remove(i);
                    sitePoints.remove(i);
                }
            }
            expiryHandler.postDelayed(expiryChecker, 10 * 1000);
        }
    };

    /**
     * Start scanning, handle scan results, and start the {@link #expiryChecker}.
     */
    private void startScanning() {
        if (App.bleManager.disabled()) {
            Log.w(LOG_TAG, "Bluetooth not enabled");
            return;
        }
        sitePoints.clear();
        sitePointView.rebuild();
        App.bleManager.startScanning(sitePoint -> {
            if (!scanning) {
                Log.d(LOG_TAG, "Ignoring late scan result while not scanning.");
                return;
            }
            boolean exists = false;
            for (int i = 0; i < sitePoints.size(); i++) {
                SitePoint sp = sitePoints.get(i);
                if (sp.addressMatches(sitePoint)) {
                    sitePoints.set(i, sitePoint);
                    sitePointView.update(i);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                sitePoints.add(sitePoint);
                sitePointView.add(sitePoints.size() - 1);
            }
        });
        scanning = true;
        expiryHandler.post(expiryChecker);
    }

    private void stopScanning() {
        if (scanning) {
            Log.i(LOG_TAG, "Stop scanning for SitePoint devices");
            App.bleManager.stopScanning();
            scanning = false;
        }
    }

    /**
     * Stop scanning then connect.
     * <p>
     * This app stops scanning when connected to simplify the UI; stopping the scan is not necessary.
     */
    public void connect(SitePoint sitePoint) {
        Log.i(LOG_TAG,"Connecting to " + sitePoint.getName());
        stopScanning();
        try {
            App.bleManager.connect(sitePoint);
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "Error connecting: " + e.getMessage());
        }
        sitePoints.clear();
        sitePoints.add(App.bleManager.sitePoint);
        sitePointView.rebuild();
    }

    public void disconnectSitePoint() {
        Log.i(LOG_TAG,"Disconnect from SitePoint device");
        App.bleManager.disconnect();
        // Status and Location values are no longer relevant
        clearMessageValues();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BleManager.BT_CONNECT_ACTION.equals(action)) {
                // updates the connected button
                sitePointView.rebuild();
            } else if (BleManager.BT_DISCONNECT_ACTION.equals(action)) {
                Log.d(LOG_TAG, "Handling disconnect");
                sitePointView.rebuild();
                startScanning();
            } else if (Ntrip.NTRIP_DISCONNECT_ACTION.equals(action)) {
                handleNtripDisconnected();
            } else if (App.ERROR_ACTION.equals(action)) {
                String error = intent.getStringExtra("error");
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            } else if (BleManager.LOCATION_MESSAGE_RECEIVED.equals(action)) {
                LocationParcelable parcelable = intent.getParcelableExtra("location");
                assert parcelable != null;
                Location location = parcelable.getLocation();
                setValue(R.id.location_itow, location.getITow() + "");
                setValue(R.id.location_latitude, String.format("%.7f", location.getLatitude()) + "°");
                setValue(R.id.location_longitude, String.format("%.7f", location.getLongitude()) + "°");
                setValue(R.id.location_height, String.format("%,.4f", location.getHeight()) + "m");
                setValue(R.id.location_horizontal_accuracy, String.format("%,.4f", location.getHorizontalAccuracy()) + "m");
                setValue(R.id.location_vertical_accuracy, String.format("%,.4f", location.getVerticalAccuracy()) + "m");
            } else if (BleManager.STATUS_MESSAGE_RECEIVED.equals(action)) {
                StatusParcelable parcelable = intent.getParcelableExtra("status");
                assert parcelable != null;
                Status status = parcelable.getStatus();
                setValue(R.id.status_battery, status.getBattery() + "%");
                setValue(R.id.status_charging, status.isCharging() + "");
                setValue(R.id.status_itow, status.getITow() + "");
                setValue(R.id.status_time, status.getTime() + "");
                DateFormat timeFormat = SimpleDateFormat.getTimeInstance(DateFormat.MEDIUM);
                timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                setValue(R.id.status_time_formatted, timeFormat.format(new Date(status.getTime() * 1000)));
                setValue(R.id.status_satellites, status.getSatellites() + "");
                setValue(R.id.status_mode, status.getMode() + "");
                setValue(R.id.status_mode_label, status.getModeLabel());
                setValue(R.id.status_aiding_quality, Arrays.toString(status.getAidingQuality())
                        .replace("true", "1").replace("false", "0")
                        .replaceAll("[, \\[\\]]", ""));
            }
        }
    };

    private void clearMessageValues() {
        clearValue(R.id.status_battery, R.string.status_battery);
        clearValue(R.id.status_charging, R.string.status_charging);
        clearValue(R.id.status_itow, R.string.status_itow);
        clearValue(R.id.status_time, R.string.status_time);
        clearValue(R.id.status_time_formatted, R.string.status_time_formatted);
        clearValue(R.id.status_satellites, R.string.status_satellites);
        clearValue(R.id.status_mode, R.string.status_mode);
        clearValue(R.id.status_mode_label, R.string.status_mode_label);
        clearValue(R.id.status_aiding_quality, R.string.status_aiding_quality);

        clearValue(R.id.location_itow, R.string.location_itow);
        clearValue(R.id.location_latitude, R.string.location_latitude);
        clearValue(R.id.location_longitude, R.string.location_longitude);
        clearValue(R.id.location_height, R.string.location_height);
        clearValue(R.id.location_horizontal_accuracy, R.string.location_horizontal_accuracy);
        clearValue(R.id.location_vertical_accuracy, R.string.location_vertical_accuracy);
    }

    private void clearValue(int fieldId, int stringId) {
        TextView field = findViewById(fieldId);
        field.setText(stringId);
    }

    private void setValue(int fieldId, String value) {
        TextView field = findViewById(fieldId);
        field.setText(value);
    }

    public static class SitePointRow extends RecyclerView.ViewHolder {
        public SitePointRow(View itemView) {
            super(itemView);
        }
    }

    class SitePointView extends RecyclerView.Adapter<SitePointRow> {
        final private static String LOG_TAG = "SitePointRows";

        @NonNull
        @Override
        public SitePointRow onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SitePointRow(LayoutInflater.from(MainActivity.this).inflate(R.layout.site_point_row_layout, parent, false));
        }

        public void rebuild() {
            MainActivity.this.runOnUiThread(this::notifyDataSetChanged);
        }

        public void update(int index) {
            MainActivity.this.runOnUiThread(() -> notifyItemChanged(index));
        }

        public void add(int index) {
            MainActivity.this.runOnUiThread(() -> notifyItemInserted(index));
        }

        public void remove(int index) {
            MainActivity.this.runOnUiThread(() -> notifyItemRemoved(index));
        }

        /**
         * Displays a SitePoint row, including querying connection status for updating buttons.
         */
        @Override
        public void onBindViewHolder(@NonNull SitePointRow holder, int position) {
            SitePoint sitePoint = sitePoints.get(position);
            TextView nameView = holder.itemView.findViewById(R.id.sitepoint_name);
            TextView rssiView = holder.itemView.findViewById(R.id.sitepoint_rssi_value);
            nameView.setText(sitePoint.getName());
            rssiView.setText(String.valueOf(sitePoint.getRssi()));

            Button connectButton = holder.itemView.findViewById(R.id.connect_button);

            boolean connectedHere = App.bleManager.connectedToThisAndroid(sitePoint);
            // this app isn't scanning after we're connected, so this will be out of date while connecting and connected
            boolean connectedFromScan = sitePoint.connectedFromScan();
            boolean selected = sitePoint.addressMatches(App.bleManager.sitePoint);
            if (connectedHere) {
                updateButton(connectButton, "Disconnect", true);
            } else if (selected) {
                updateButton(connectButton, "Connecting", false);
            } else if (connectedFromScan) {
                updateButton(connectButton, "In use", false);
            } else {
                updateButton(connectButton, "Connect", true);
            }

            // set buttons to Disconnecting/Connecting; updated when the BroadcastReceiver receives a connection state change.
            connectButton.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                Button connButton = v.findViewById(R.id.connect_button);
                if (connectedHere) {
                    Log.d(LOG_TAG,"Disconnect button clicked for device " + sitePoints.get(pos).getName());
                    updateButton(connButton, "Disconnecting", false);
                    MainActivity.this.disconnectSitePoint();
                } else if (!selected) {
                    Log.d(LOG_TAG,"Connect button clicked for device " + sitePoints.get(pos).getName());
                    updateButton(connButton, "Connecting", false);
                    MainActivity.this.connect(sitePoints.get(pos));
                }
            });
        }

        private void updateButton(Button button, String label, boolean enabled) {
            button.setText(label);
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f: .8f);
        }

        @Override
        public int getItemCount() {
            return sitePoints.size();
        }
    }
}
