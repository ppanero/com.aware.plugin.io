package com.aware.plugin.io_panero;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.aware.*;
import com.aware.providers.*;
import com.aware.plugin.io_panero.Provider.IOMeter_Data;
import com.aware.utils.Aware_Plugin;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Plugin extends Aware_Plugin {

    public static final String ACTION_AWARE_LOCATION_TYPE_INDOOR = "ACTION_AWARE_LOCATION_TYPE_INDOOR";
    public static final String ACTION_AWARE_LOCATION_TYPE_OUTDOOR = "ACTION_AWARE_LOCATION_TYPE_OUTDOOR";
    public static final String EXTRA_ELAPSED_TIME = "elapsed_time";
    public static final String INDOOR = "indoor";
    public static final String OUTDOOR = "outdoor";
    public static final int MID_DAY = 12;
    public static final int LIGHT_DAY_MID_BOUND = 18;
    /*
    This decision matrix will tell if the device is indoors or outdoors according to the
    values it has:
    the rows stand for:
        1:=Magnetometer
        2:=Accelerometer
        3:=Battery
        4:=Light
        5:=GPS
    The first columns will have either 1 (indoor) or 0 (outdoor).
    The second column will have the confidence of the sensor in that precise moment,
    therefor it will change in time.
    The third column is its value.
     */
    private static final int NUMBER_OF_SENSORS = 5;
    private static double[][] decisionMatrix = new double[3][5]; //{{0,0},{0,0},{0,0},{0,0},{0,0}};
    private static BatteryObserver batteryObserver;
    private static LocationObserver locationObserver;
    private LightReceiver lightReceiver = new LightReceiver();
    private MagnetReceiver magnetReceiver = new MagnetReceiver();
    private AccelerometerReceiver accelerometerReceiver = new AccelerometerReceiver();
    private static ContextProducer contextProducer;
    private static double overallConfidence = 0;
    private static String io_status = "indoor";
    private static String previous_io_status = "";
    private static long starting_time = 0;
    private static long elapsed_time = 0;
    private static long previous_elapsed_time = 0;
    private static long last_update = 0;
    private static int temp_interval = 0;
    protected static boolean lockLight = false;
    protected static boolean lockMagnetometer = false;
    protected static boolean lockAccelerometer = false;
    private IOAlarm alarm = new IOAlarm();
    private static double GRAVITY = 9.81;
    private int magnet_counter = 0;
    private double current_magnet_val = 0;
    private double avg_magnet_val = 0;
    private int avg_magnet = 0;
    private int light_counter = 0;
    private double current_light_val = 0;
    private double avg_light_val = 0;
    private int accelerometer_counter = 0;
    private double current_accelerometer_val = 0;
    private double avg_accelerometer_val = 0;
    private double avg_accelerometer = 0;


    @Override
    public void onCreate() {
        super.onCreate();
        TAG = "INDOOR-OUTDOOR";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        if (DEBUG) Log.d(TAG, "creating IO plugin");

        starting_time = System.currentTimeMillis();

        //initialize decission matrix
        for (int i = 0; i < NUMBER_OF_SENSORS; i++) {
            decisionMatrix[0][i] = -1;
            decisionMatrix[1][i] = -1;
            decisionMatrix[2][i] = 0;
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER, 20);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER, 20);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER, 10);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_IO_METER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_IO_METER, 3);
        }

        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER, 200000);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT, 1000);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS, true);

        contextProducer = new ContextProducer() {
            @Override
            public void onContext() {
                Intent context_io_meter = new Intent();
                if(io_status.equals(INDOOR)){
                    context_io_meter.setAction(ACTION_AWARE_LOCATION_TYPE_INDOOR);
                }
                else{
                    context_io_meter.setAction(ACTION_AWARE_LOCATION_TYPE_OUTDOOR);
                }
                context_io_meter.putExtra(EXTRA_ELAPSED_TIME, elapsed_time);
                sendBroadcast(context_io_meter);
            }
        };


        batteryObserver = new BatteryObserver(new Handler());
        getContentResolver().registerContentObserver(Battery_Provider.Battery_Data.CONTENT_URI, true, batteryObserver);

        locationObserver = new LocationObserver(new Handler());
        getContentResolver().registerContentObserver(Locations_Provider.Locations_Data.CONTENT_URI, true, locationObserver);

        IntentFilter lightFilter = new IntentFilter();
        lightFilter.addAction(Light.ACTION_AWARE_LIGHT);
        registerReceiver(lightReceiver, lightFilter);

        IntentFilter magnetFilter = new IntentFilter();
        magnetFilter.addAction(Magnetometer.ACTION_AWARE_MAGNETOMETER);
        registerReceiver(magnetReceiver, magnetFilter);

        IntentFilter accelerometerFilter = new IntentFilter();
        accelerometerFilter.addAction(Accelerometer.ACTION_AWARE_ACCELEROMETER);
        registerReceiver(accelerometerReceiver, accelerometerFilter);

        if (DEBUG) Log.d(TAG, "IO plugin running");

        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.IOMeter_Data.CONTENT_URI};

        //Apply settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int interval_min = Integer.parseInt(Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_IO_METER));
        if (interval_min != temp_interval) {
            if (interval_min >= 1) {
                alarm.SetAlarm(Plugin.this, interval_min);
            } else {
                alarm.CancelAlarm(Plugin.this);
            }
            temp_interval = interval_min;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DEBUG) Log.d(TAG, "IO plugin terminating.");
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS, false);

        getContentResolver().unregisterContentObserver(batteryObserver);
        getContentResolver().unregisterContentObserver(locationObserver);
        unregisterReceiver(lightReceiver);
        unregisterReceiver(magnetReceiver);
        unregisterReceiver(accelerometerReceiver);

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private synchronized void updateStatus() {
        double indoor_counter = 0;
        double outdoor_counter = 0;
        double outdoor_confidence = 0;
        double indoor_confidence = 0;


        for (int i = 0; i < NUMBER_OF_SENSORS; i++) {
            if (decisionMatrix[0][i] == 1) {
                indoor_confidence += decisionMatrix[1][i];
                indoor_counter += 1;
            } else if (decisionMatrix[0][i] == 0) {
                outdoor_confidence += decisionMatrix[1][i];
                outdoor_counter += 1;
            }
        }
        if (indoor_confidence >= outdoor_confidence) {
            io_status = INDOOR;
            overallConfidence = indoor_confidence / indoor_counter;
        } else {
            io_status = OUTDOOR;
            overallConfidence = outdoor_confidence / outdoor_counter;
        }
        if(previous_elapsed_time == 0){
            elapsed_time = System.currentTimeMillis() - starting_time;
        }
        else {
            elapsed_time = System.currentTimeMillis() - last_update;
        }
        if (!previous_io_status.equals("") && previous_io_status.equals(io_status)) {
            elapsed_time += previous_elapsed_time;
        }
        last_update = System.currentTimeMillis();
        previous_elapsed_time = elapsed_time;
        previous_io_status = io_status;
        int aux_elapsed_time = (int) (elapsed_time/1000L);
        int aux_last_update = (int) (last_update/1000L);

        ContentValues data = new ContentValues();
        data.put(IOMeter_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(IOMeter_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(IOMeter_Data.IO_STATUS, io_status);
        data.put(IOMeter_Data.IO_ELAPSED_TIME, aux_elapsed_time);
        data.put(IOMeter_Data.IO_LAST_UPDATE, aux_last_update);
        data.put(IOMeter_Data.IO_MAGNETOMETER, decisionMatrix[2][0]);
        data.put(IOMeter_Data.IO_ACCELEROMETER, decisionMatrix[2][1]);
        data.put(IOMeter_Data.IO_BATTERY, decisionMatrix[2][2]);
        data.put(IOMeter_Data.IO_LIGHT, decisionMatrix[2][3]);
        data.put(IOMeter_Data.IO_GPS, decisionMatrix[2][4]);
        getContentResolver().insert(IOMeter_Data.CONTENT_URI, data);
        contextProducer.onContext();
        if (DEBUG)
            Log.d("Status updated", "The device is: " + io_status.toString() + " with confifende " + overallConfidence);

    }

    protected static void lockOff(Context context, boolean lock) {
        lock = false;
    }

    public static String secondsToTime(long seconds){
        long aux_seconds = seconds;
        if(aux_seconds < 0)
        {
            throw new IllegalArgumentException("Duration must be greater than zero!");
        }

        long days = TimeUnit.SECONDS.toDays(aux_seconds);
        aux_seconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(aux_seconds);
        aux_seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(aux_seconds);
        aux_seconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder sb = new StringBuilder(64);
        sb.append(hours);
        sb.append(" h ");
        sb.append(minutes);
        sb.append(" min ");
        sb.append(aux_seconds);
        sb.append(" sec");

        return(sb.toString());
    }

    public static long timezoneOffset(){
        return TimeUnit.MILLISECONDS.toSeconds(java.util.TimeZone.getDefault().getOffset(Calendar.ZONE_OFFSET));
    }
    /*
    Battery is made as an observer, because changes are not expensive in power consumption.
    Also changes are less frequent.
     */
    public class BatteryObserver extends ContentObserver {

        public BatteryObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            //Get the latest recorded value
            Cursor battery = getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI,
                    null, null, null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
            if (battery != null && battery.moveToFirst()) {
                int battery_status = battery.getInt(battery.getColumnIndex(Battery_Provider.Battery_Data.PLUG_ADAPTOR));

                switch (battery_status) {
                    //case BatteryManager.BATTERY_PLUGGED_AC:
                    case 1:
                        if (DEBUG) Log.d("BatteryObserver", "Battery plugged to AC");
                        decisionMatrix[0][2] = 1;
                        decisionMatrix[1][2] = 0.9;
                        break;

                    //case BatteryManager.BATTERY_PLUGGED_USB:
                    case 2:
                        if (DEBUG) Log.d("BatteryObserver", "Battery plugged to USB");
                        decisionMatrix[0][2] = 1;
                        decisionMatrix[1][2] = 0.8;
                        break;
                }
                decisionMatrix[2][2] = (double) battery_status;
                updateStatus();
            }

            if (battery != null && battery.isClosed()) battery.close();
        }
    }

    public class LocationObserver extends ContentObserver {

        public LocationObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            //Get the latest recorded value
            Cursor location = getContentResolver().query(Locations_Provider.Locations_Data.CONTENT_URI,
                    null, null, null, Locations_Provider.Locations_Data.TIMESTAMP + " DESC LIMIT 1");
            if (location != null && location.moveToFirst()) {
                int accuracy = location.getInt(location.getColumnIndex(Locations_Provider.Locations_Data.ACCURACY));

                if (accuracy < 50) {
                    if (DEBUG) Log.d("LocationObserver", "low accuracy");
                    decisionMatrix[0][1] = 1;
                    decisionMatrix[1][1] = 0.6;
                }
                else if(accuracy >= 50 && accuracy < 100){
                    if (DEBUG) Log.d("LocationObserver", "low accuracy");
                    decisionMatrix[0][1] = 1;
                    decisionMatrix[1][1] = 0.4;
                }
                else if(accuracy >= 100 && accuracy < 600){
                    if (DEBUG) Log.d("LocationObserver", "low accuracy");
                    decisionMatrix[0][1] = 0;
                    decisionMatrix[1][1] = 0.4;
                }
                else {
                    if (DEBUG) Log.d("LocationObserver", "high accuracy");
                    decisionMatrix[0][1] = 0;
                    decisionMatrix[1][1] = 0.7;
                }
                decisionMatrix[2][4] = accuracy;
                updateStatus();
            }

            if (location != null && location.isClosed()) location.close();
        }
    }
    /*
    Magnetometer, accelerometer and light are turn on with an alarm, due to its power consumption.
    Also, it helps getting less data to analyse more carefully.
     */

    public class LightReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_LIGHT_METER));

            if (interval_min > 0 && !lockLight) {
                ContentValues values = (ContentValues) intent.getExtras().get(Light.EXTRA_DATA);
                current_light_val = Double.parseDouble(values.get(Light_Provider.Light_Data.LIGHT_LUX).toString());
                if (light_counter < samples) {
                    avg_light_val += current_light_val;
                    light_counter += 1;
                } else {
                    double hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    double hour_weight = 1;
                    int high_value = -1;
                    int low_value = -1;
                    if (hour >= MID_DAY) {
                        if (hour < LIGHT_DAY_MID_BOUND) {
                            hour_weight = hour / LIGHT_DAY_MID_BOUND;
                        } else {
                            hour_weight = LIGHT_DAY_MID_BOUND / hour;
                        }
                        high_value = 0;
                        low_value = 1;
                    } else {
                        if (hour == 0) hour_weight = 1;
                        else {
                            hour_weight = -(hour - MID_DAY) / MID_DAY;
                            high_value = 1;
                            low_value = 0;
                        }
                    }
                    lockLight = true;
                    if (light_counter > 0) {
                        avg_light_val = (int) (avg_light_val / light_counter);
                    }

                    if (avg_light_val <= 200) {
                        if (DEBUG) Log.d("LightBroadcast", "lower than 200");
                        decisionMatrix[0][3] = low_value;
                        decisionMatrix[1][3] = 0.8 * hour_weight;
                    } else if (avg_light_val > 200 && avg_light_val <= 500) {
                        if (DEBUG) Log.d("LightBroadcast", "between 200 and 500");
                        decisionMatrix[0][3] = low_value;
                        decisionMatrix[1][3] = 0.6 * hour_weight;
                    } else if (avg_light_val > 500 && avg_light_val <= 800) {
                        if (DEBUG) Log.d("LightBroadcast", "between 200 and 500");
                        decisionMatrix[0][3] = low_value;
                        decisionMatrix[1][3] = 0.7 * hour_weight;
                    } else {
                        if (DEBUG) Log.d("LightBroadcast", "higher than 500");
                        decisionMatrix[0][3] = high_value;
                        decisionMatrix[1][3] = 0.8 * hour_weight;
                    }
                    decisionMatrix[2][3] = avg_light_val;
                    Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    updateStatus();
                    alarm.SetAlarm(context, interval_min);
                }
            }
        }
    }

    public class MagnetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_MAGNETOMETER));

            if (interval_min > 0 && !lockMagnetometer) {

                ContentValues values = (ContentValues) intent.getExtras().get(Magnetometer.EXTRA_DATA);
                if (magnet_counter < samples) {
                    double value_x = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_0).toString());
                    double value_y = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_1).toString());
                    double value_z = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_2).toString());
                    current_magnet_val = Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                    avg_magnet_val += current_magnet_val;
                    magnet_counter += 1;
                    if (DEBUG) Log.d("MagnetBroadcast", "current: " + avg_magnet_val + magnet_counter);
                } else {
                    lockMagnetometer = true;
                    if (magnet_counter > 0) {
                        avg_magnet = (int) (avg_magnet_val / magnet_counter);
                    }
                    if (avg_magnet <= 47) {
                        if (DEBUG) Log.d("MagnetBroadcast", "Low magnetism, probably inside");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.7;
                    } else if (avg_magnet > 47 && avg_magnet < 52) {
                        if (DEBUG) Log.d("MagnetBroadcast", "Semindoor values, therefore indoor");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.5;
                    } else if (avg_magnet >= 52 && avg_magnet < 60) {
                        if (DEBUG) Log.d("MagnetBroadcast", "High magnetism, therefore outdoors");
                        decisionMatrix[0][0] = 0;
                        decisionMatrix[1][0] = 0.7;
                    } else {
                        if (DEBUG)
                            Log.d("MagnetBroadcast", "Really high magnetism, nearby electronic device therefore indoors");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.5;
                    }
                    decisionMatrix[2][0] = avg_magnet;
                    Aware.setSetting(context, Aware_Preferences.STATUS_MAGNETOMETER, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    updateStatus();
                }
            }
        }
    }

    public class AccelerometerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_ACCELEROMETER));

            if (interval_min > 0 && !lockAccelerometer) {

                ContentValues values = (ContentValues) intent.getExtras().get(Accelerometer.EXTRA_DATA);
                if (accelerometer_counter < samples) {
                    double value_x = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_0).toString());
                    double value_y = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_1).toString());
                    double value_z = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_2).toString());
                    current_accelerometer_val = Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                    avg_accelerometer_val += current_accelerometer_val;
                    accelerometer_counter += 1;
                } else {
                    lockAccelerometer = true;
                    if (accelerometer_counter > 0) {
                        avg_accelerometer = (avg_accelerometer_val / accelerometer_counter - GRAVITY);
                        avg_accelerometer = (double)Math.round(avg_accelerometer * 100) / 100;
                    }

                    if (avg_accelerometer <= 2) {
                        if (DEBUG)
                            Log.d("AccelerometerBroadcast", "Still, different due to possible noise hence indoor");
                        decisionMatrix[0][1] = 1;
                        decisionMatrix[1][1] = 0.2;
                    } else if (avg_accelerometer > 2 && avg_accelerometer <= 20) {
                        if (DEBUG) Log.d("AccelerometerBroadcast", "Walking, biking, or running, hence outdoor");
                        decisionMatrix[0][1] = 0;
                        decisionMatrix[1][1] = 0.5;
                    }
                    decisionMatrix[2][1] = avg_accelerometer;
                    Aware.setSetting(context, Aware_Preferences.STATUS_ACCELEROMETER, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    updateStatus();
                }
            }
        }
    }
}
