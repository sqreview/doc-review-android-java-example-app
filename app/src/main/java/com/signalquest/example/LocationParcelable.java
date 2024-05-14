package com.signalquest.example;

import android.os.Parcel;
import android.os.Parcelable;

import com.signalquest.api.Location;

/**
 * Wraps a {@link com.signalquest.api.Location} in Android's {@link Parcelable} interface, for broadcasting/receiving.
 */
public class LocationParcelable implements Parcelable {
    private final Location location;
    public LocationParcelable(Location location) {
        this.location = location;
    }
    protected LocationParcelable(Parcel in) {
        long iTow = in.readLong();
        double latitude = in.readDouble();
        double longitude = in.readDouble();
        double height = in.readDouble();
        double horizontalAccuracy = in.readDouble();
        double verticalAccuracy = in.readDouble();
        location = new Location(iTow, latitude, longitude, height, horizontalAccuracy, verticalAccuracy);
    }

    public static final Creator<LocationParcelable> CREATOR = new Creator<LocationParcelable>() {
        @Override
        public LocationParcelable createFromParcel(Parcel in) {
            return new LocationParcelable(in);
        }

        @Override
        public LocationParcelable[] newArray(int size) {
            return new LocationParcelable[size];
        }
    };

    public Location getLocation() {
        return location;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(location.getITow());
        dest.writeDouble(location.getLatitude());
        dest.writeDouble(location.getLongitude());
        dest.writeDouble(location.getHeight());
        dest.writeDouble(location.getHorizontalAccuracy());
        dest.writeDouble(location.getVerticalAccuracy());
    }
}