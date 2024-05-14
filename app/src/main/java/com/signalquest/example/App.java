package com.signalquest.example;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/***
 * Sets up relationships between different components, makes the ApplicationContext available,
 * and supplies an error broadcaster.
 */
public class App extends Application {
    final static Ntrip ntrip = new Ntrip();
    final static  BleManager bleManager = new BleManager();
    public final static String ERROR_ACTION = "com.signalquest.example.ERROR_ACTION";

    /**
     * Makes {@link Ntrip#next(int)} available to {@link BleManager}.
     */
    public static byte[] getRtcmData(int maxLength) {
        return ntrip.next(maxLength);
    }

    /**
     * Makes {@link BleManager#ntripParsed()} available to {@link Ntrip}.
     */
    public static void onParsed() {
        bleManager.ntripParsed();
    }

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        App.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return App.context;
    }

    public static void displayError(String logTag, String error) {
        displayError(logTag, error, null);
    }

    public static void displayError(String logTag, String error, Exception e) {
        Intent intent = new Intent(App.ERROR_ACTION);
        intent.putExtra("error", error);
        context.sendBroadcast(intent);
        if (e == null) {
            Log.e(logTag, error);
        } else {
            Log.e(logTag, error, e);
        }
    }
}
