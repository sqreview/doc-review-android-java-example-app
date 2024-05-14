package com.signalquest.example;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

/**
 * Splash screen and permissions requester.
 */
public class PermissionsActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        // in onCreate because onResume gets called after a system dialog is dismissed
        // 3s pause to let app finish starting (plus gives time for the splash screen to show)
        new Handler(Looper.getMainLooper()).postDelayed(this::requestAppPermissions, 3000);
    }

    /**
     * Requests permissions, one Android system dialog at a time.
     * <p>
     * Will be called until all required permissions are accepted, then forwards to the {@link MainActivity}.
     * <p>
     * The FINE_LOCATION permission is needed for Bluetooth before Android 12, but this app uses it
     * for NMEA GGA sentences, for NTRIP, so it's requesting it for all Androids here.
     */
    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                (denied(Manifest.permission.BLUETOOTH_SCAN) ||
                        denied(Manifest.permission.BLUETOOTH_CONNECT))) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT },
                    PERMISSION_REQUEST_CODE
            );
        } else if (denied(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            launchMainScreen();
        }
    }

    private boolean denied(String permission) {
        return PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission);
    }

    /**
     * Handles denied permissions by either:
     * <ol>
     *     <li>Can {@link ActivityCompat#shouldShowRequestPermissionRationale(Activity, String) re-ask}?
     *         Offer a "Grant" button, which calls {@link #requestAppPermissions()}.
     *     <li>Can't re-ask: direct user to their Settings, and only offer the "Close App" button.
     * </ol>
     * <p>
     * No denied permissions? Calls {@link #requestAppPermissions()}, to request the next or to go
     * to the {@link MainActivity}.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> deniedPermissions = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (PackageManager.PERMISSION_DENIED == grantResults[i]) {
                deniedPermissions.add(permissions[i]);
            }
        }

        if (!deniedPermissions.isEmpty()) {
            boolean canAsk = true;
            for (String p : deniedPermissions) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, p)) {
                    canAsk = false;
                    break;
                }
            }
            final String ask = getAsk(deniedPermissions, canAsk);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permissions Required").setMessage(ask);
            if (canAsk) {
                builder.setPositiveButton("Grant", (dialog, which) -> requestAppPermissions());
            }
            builder.setNegativeButton("Close App", (dialog, which) -> finish());
            builder.setCancelable(false).create().show();
        } else {
            requestAppPermissions();
        }
    }

    @NonNull
    private String getAsk(ArrayList<String> deniedPermissions, boolean canAskAgain) {
        String permissionLabel = null;
        if (deniedPermissions.get(0).contains("BLUETOOTH")) {
            permissionLabel = "Bluetooth permissions";
        } else if (deniedPermissions.get(0).contains("LOCATION")) {
            permissionLabel = "Location permissions";
        }
        assert(permissionLabel != null);

        String ask;
        if (canAskAgain) {
            ask = "Please choose Grant for " + permissionLabel + " so app can function.";
        } else {
            ask = "Please grant " + permissionLabel + " in your Settings so app can function";
        }
        return ask;
    }

    private void launchMainScreen() {
        startActivity(new Intent(this, MainActivity.class));
    }
}