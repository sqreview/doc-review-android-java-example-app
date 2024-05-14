package com.signalquest.example;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Uses {@link LocationManager} to get NMEA GGA sentences for seeding NTRIP services.
 * <p>
 * This class doesn't use any SignalQuest services; it is only included to make using this example,
 * with NTRIP services that require location seeding, easier.
 * <p>
 * To use, instantiate this class and call {@link #toString()} to get the latest GGA sentence.
 * <p>
 * Requires ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION permissions.
 */
public class NtripGga {

    LocationService locationService;

    NtripGga() { locationService = new LocationService(); }

    /**
     * @return An NMEA GGA sentence, using this Android's current location, for passing to an NTRIP service.
     */
    @NonNull
    @Override
    public String toString() {
        String gga = "";
        // could also use a SitePoint location
        if (locationService.location != null) {
            @SuppressLint("SimpleDateFormat") DateFormat timestampFormatter = new SimpleDateFormat("HHmmss");
            timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

            String nmea0183GPGGA;
            double latitude = toNmea(locationService.location.getLatitude());
            double longitude = toNmea(locationService.location.getLongitude());

            nmea0183GPGGA = "GPGGA," + timestampFormatter.format(new Date(locationService.location.getTime()));
            nmea0183GPGGA += String.format(Locale.US, ",%07.2f,", Math.abs(latitude));
            nmea0183GPGGA += latitude > 0.0 ? "N" : "S";
            nmea0183GPGGA += String.format(Locale.US, ",%08.2f,", Math.abs(longitude));
            nmea0183GPGGA += longitude > 0.0 ? "E" : "W";
            nmea0183GPGGA += ",1,10,1,";
            nmea0183GPGGA += String.format(Locale.US, "%1.1f,M,%1.1f,M,5,", locationService.location.getAltitude(), locationService.location.getAltitude());
            nmea0183GPGGA += String.format("*%02X", nmeaSentenceChecksum(nmea0183GPGGA));
            nmea0183GPGGA = "$" + nmea0183GPGGA;
            gga = nmea0183GPGGA;
        }
        return gga;
    }

    private double toNmea(double degrees) {
        double degreeSign = degrees < 0.0 ? -1.0 : 1.0;
        double degree = Math.abs(degrees);
        double degreeDecimal = Math.floor(degree);
        double degreeFraction = degree - degreeDecimal;
        double minutes = degreeFraction * 60.0;
        return degreeSign * ((degreeDecimal * 100) + minutes);
    }

    private long nmeaSentenceChecksum(String sentence) {
        int checksum = 0;
        for (int i = 0; i < sentence.length(); i++) {
            checksum ^= sentence.charAt(i);
        }
        checksum &= 0x0ff;
        return checksum;
    }

    private static class LocationService implements LocationListener {
        int REFRESH_MILLISECONDS = 5000;

        int REFRESH_METERS = 500;

        Location location;

        LocationService() {
            Context context = App.getAppContext();
            LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
            boolean fineGranted = PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarseGranted = PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
            // should be granted on startup; not handling user-revocation
            assert(fineGranted && coarseGranted);
            String provider = (Build.VERSION.SDK_INT >= 31) ? LocationManager.FUSED_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestLocationUpdates(provider, REFRESH_MILLISECONDS, REFRESH_METERS, this);
        }

        @Override
        public void onLocationChanged(@NonNull Location location) {
            this.location = location;
        }
    }
}

