package com.signalquest.example;

import android.os.Parcel;
import android.os.Parcelable;

import com.signalquest.api.Status;

/**
 * Wraps a {@link com.signalquest.api.Status} in Android's {@link Parcelable} interface, for broadcasting/receiving.
 */
public class StatusParcelable implements Parcelable {
    private final Status status;

    public StatusParcelable(Status status) {
        this.status = status;
    }

    protected StatusParcelable(Parcel in) {
        long iTow = in.readLong();
        long time = in.readLong();
        int mode = in.readInt();
        int satellites = in.readInt();
        int battery = in.readInt();
        boolean charging = in.readByte() != 0;
        boolean[] aidingQuality = new boolean[8];
        in.readBooleanArray(aidingQuality);
        status = new Status(iTow, time, mode, satellites, battery, charging, aidingQuality);
    }

    public static final Creator<StatusParcelable> CREATOR = new Creator<StatusParcelable>() {
        @Override
        public StatusParcelable createFromParcel(Parcel in) {
            return new StatusParcelable(in);
        }

        @Override
        public StatusParcelable[] newArray(int size) {
            return new StatusParcelable[size];
        }
    };

    public Status getStatus() {
        return status;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(status.getITow());
        dest.writeLong(status.getTime());
        dest.writeInt(status.getMode());
        dest.writeInt(status.getSatellites());
        dest.writeInt(status.getBattery());
        dest.writeByte((byte) (status.isCharging() ? 1 : 0));
        dest.writeBooleanArray(status.getAidingQuality());
    }
}
