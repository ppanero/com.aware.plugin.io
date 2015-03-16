package com.aware.plugin.io_pablo_panero;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.aware.*;
import com.aware.providers.*;
import com.aware.plugin.io_pablo_panero.Provider.IOMeter_Data;
import com.aware.utils.Aware_Plugin;

public class Plugin extends Aware_Plugin {

    public static final String ACTION_AWARE_PLUGIN_IO_METER = "ACTION_AWARE_PLUGIN_IO_METER";
    public static final String EXTRA_OVERALL_CONFIDENCE = "overall_confidence";
    public static final String EXTRA_IO_STATUS = "io_status";
    public static final String INDOOR = "indoor";
    public static final String OUTDOOR = "outdoor";
    /*
    This decision matrix will tell if the device is indoors or outdoors according to the
    values it has:
    the rows stand for:
        1:=Magnetometer
        2:=Accelerometer
        3:=Battery
        4:=Light
        5:=Telephony
    The first columns will have either 1 (indoor) or 0 (outdoor).
    The second column will have the confidence of the sensor in that precise moment,
    therefor it will change in time.
     */
    private static final int NUMBER_OF_SENSORS = 5;
    private static double[][] decisionMatrix = new double[2][5];
    private static BatteryObserver batteryObserver;
    private static TelephonyObserver telephonyObserver;
    private LightReceiver lightReceiver = new LightReceiver();
    private MagnetReceiver magnetReceiver = new MagnetReceiver();
    private AccelerometerReceiver accelerometerReceiver = new AccelerometerReceiver();
    private static ContextProducer contextProducer;
    private static double overallConfidence = 0;
    private static String io_status = "indoor";
    private static int temp_interval = 0;
    private static boolean lock = false;
    private IOAlarm alarm = new IOAlarm();
    private static double GRAVITY = 9.81;


    @Override
    public void onCreate() {
        super.onCreate();
        TAG = "INDOOR-OUTDOOR";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_TELEPHONY, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, true);

        if( Aware.getSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER, 30);
        }
        if( Aware.getSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER, 30);
        }
        if( Aware.getSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER, 30);
        }
        if( Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_ACCELEROMETER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_ACCELEROMETER, 10);
        }
        if( Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_MAGNETOMETER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_MAGNETOMETER, 10);
        }
        if( Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_LIGHT_METER).length() == 0 ) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_LIGHT_METER, 10);
        }

        contextProducer = new ContextProducer() {
            @Override
            public void onContext() {
                Intent context_lux_meter = new Intent();
                context_lux_meter.setAction(ACTION_AWARE_PLUGIN_IO_METER);
                context_lux_meter.putExtra(EXTRA_IO_STATUS, io_status);
                context_lux_meter.putExtra(EXTRA_OVERALL_CONFIDENCE, overallConfidence);
                sendBroadcast(context_lux_meter);
            }
        };

        batteryObserver = new BatteryObserver(new Handler());
        getContentResolver().registerContentObserver(Battery_Provider.Battery_Data.CONTENT_URI, true, batteryObserver);

        telephonyObserver = new TelephonyObserver(new Handler());
        getContentResolver().registerContentObserver(Telephony_Provider.Telephony_Data.CONTENT_URI, true, telephonyObserver);

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
        int interval_min = Integer.parseInt(Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_LIGHT_METER));

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
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_TELEPHONY, false);

        getContentResolver().unregisterContentObserver(batteryObserver);
        getContentResolver().unregisterContentObserver(telephonyObserver);
        unregisterReceiver(lightReceiver);
        unregisterReceiver(magnetReceiver);
        unregisterReceiver(accelerometerReceiver);

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private void updateStatus() {
        double indoor = 0;
        double outdoor = 0;
        for (int i = 0; i < NUMBER_OF_SENSORS; i++) {
            if (decisionMatrix[0][i] == 1) {
                indoor += decisionMatrix[1][i];
            } else {
                outdoor += decisionMatrix[1][i];
            }
        }
        if (indoor > outdoor) {
            io_status = INDOOR;
            overallConfidence = indoor / NUMBER_OF_SENSORS;
        } else {
            io_status = OUTDOOR;
            overallConfidence = outdoor / NUMBER_OF_SENSORS;
        }
        ContentValues data = new ContentValues();
        data.put(IOMeter_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(IOMeter_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(IOMeter_Data.IO_STATUS, io_status);
        data.put(IOMeter_Data.IO_CONFIDENCE, overallConfidence);
        getContentResolver().insert(IOMeter_Data.CONTENT_URI, data);

    }

    protected static void lockOff(Context context) {
        lock = false;
    }

    /*
    Battery and telephony are made as observers, because changes they are not expensive in power consumption.
    Also changes are less frequent (above all in battery).
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
                        Log.d("BatteryObserver", "Battery plugged to AC");
                        decisionMatrix[0][2] = 1;
                        decisionMatrix[1][2] = 0.9;
                        break;

                    //case BatteryManager.BATTERY_PLUGGED_USB:
                    case 2:
                        Log.d("BatteryObserver", "Battery plugged to USB");
                        decisionMatrix[0][2] = 1;
                        decisionMatrix[1][2] = 0.6;
                        break;

                    //case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    case 3:
                        Log.d("BatteryObserver", "Battery unplugged, discharging");
                        decisionMatrix[0][2] = 0;
                        decisionMatrix[1][2] = 0.2;
                        break;
                }
                if (battery != null && battery.isClosed()) {
                    battery.close();
                }
                updateStatus();
            }
        }
    }

    public class TelephonyObserver extends ContentObserver {

        public TelephonyObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            //Get the latest recorded value
            Cursor telephony = getContentResolver().query(Telephony_Provider.Telephony_Data.CONTENT_URI,
                    null, null, null, Telephony_Provider.Telephony_Data.TIMESTAMP + " DESC LIMIT 1");
            if (telephony != null && telephony.moveToFirst()) {
                int telephony_status = telephony.getInt(telephony.getColumnIndex(Telephony_Provider.GSM_Data.SIGNAL_STRENGTH));

                if (telephony_status <= 2) {
                    Log.d("TelephonyObserver", "Really low, somewhere in the country side");
                    decisionMatrix[0][5] = 0;
                    decisionMatrix[1][5] = 0.3;
                } else if (telephony_status > 2 && telephony_status <= 10) {
                    Log.d("TelephonyObserver", "Quite low, big building with too much interference");
                    decisionMatrix[0][5] = 1;
                    decisionMatrix[1][5] = 0.3;
                } else if (telephony_status > 10 && telephony_status <= 25) {
                    Log.d("TelephonyObserver", "Most common values inside a building");
                    decisionMatrix[0][5] = 1;
                    decisionMatrix[1][5] = 0.6;
                } else if (telephony_status > 25 && telephony_status <= 28) {
                    Log.d("TelephonyObserver", "Quite high, probably inside a building without intereference");
                    decisionMatrix[0][5] = 1;
                    decisionMatrix[1][5] = 0.4;
                } else {
                    Log.d("TelephonyObserver", "Really high, probably outside near a cell tower");
                    decisionMatrix[0][5] = 0;
                    decisionMatrix[1][5] = 0.7;
                }
                if (telephony != null && telephony.isClosed()) {
                    telephony.close();
                }
                updateStatus();
            }
        }
    }

    /*
    Magnetometer, accelerometer and light are turn on with an alarm, due to its power consumption.
    Also, it helps getting less data to analyse more carefully.
     */

    public class LightReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_LIGHT_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_LIGHT_METER));
            int light_counter = 0;
            double current_light_val = 0;
            double avg_light_val = 0;

            if (interval_min > 0 && !lock) {

                Cursor light = getContentResolver().query(Light_Provider.Light_Data.CONTENT_URI,
                        null, null, null, Light_Provider.Light_Data.TIMESTAMP + " DESC LIMIT 1");
                if (light_counter < samples && light != null && light.moveToFirst()) {
                    current_light_val = light.getDouble(light.getColumnIndex(Light_Provider.Light_Data.LIGHT_LUX));
                    avg_light_val += current_light_val;
                    light_counter += 1;
                } else {
                    lock = true;
                    avg_light_val = (int) (avg_light_val / light_counter);

                    if (avg_light_val <= 200) {
                        Log.d("LightBroadcast", "Quite dark, inside a room or late in the day");
                        decisionMatrix[0][4] = 1;
                        decisionMatrix[1][4] = 0.4;
                    } else if (avg_light_val > 200 && avg_light_val <= 500) {
                        Log.d("LightBroadcast", "Possible indoor values, light room");
                        decisionMatrix[0][4] = 1;
                        decisionMatrix[1][4] = 0.3;
                    } else {
                        Log.d("LightBroadcast", "Really light, outside during daylight");
                        decisionMatrix[0][4] = 0;
                        decisionMatrix[1][4] = 0.5;
                    }

                    Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    contextProducer.onContext();

                    alarm.SetAlarm(context, interval_min);
                }
            }
        }
    }

    public class MagnetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_MAGNETOMETER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_MAGNETOMETER));
            int magnet_counter = 0;
            double current_magnet_val = 0;
            double avg_magnet_val = 0;
            int avg_magnet = 0;

            if (interval_min > 0 && !lock) {

                if (magnet_counter < samples) {
                    Cursor magnetometer = getContentResolver().query(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI,
                            null, null, null, Magnetometer_Provider.Magnetometer_Data.TIMESTAMP + " DESC LIMIT 1");
                    if (magnet_counter < samples && magnetometer != null && magnetometer.moveToFirst()) {
                        double value_x = magnetometer.getDouble(magnetometer.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_0));
                        double value_y = magnetometer.getDouble(magnetometer.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_1));
                        double value_z = magnetometer.getDouble(magnetometer.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_2));
                        current_magnet_val = Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                        avg_magnet_val += current_magnet_val;
                        magnet_counter += 1;
                    } else {
                        lock = true;
                        avg_magnet = (int) (avg_magnet_val / magnet_counter);

                        if (avg_magnet <= 50) {
                            Log.d("MagnetBroadcast", "Low magnetism, probably outside");
                            decisionMatrix[0][1] = 0;
                            decisionMatrix[1][1] = 0.4;
                        } else if (avg_magnet < 60) {
                            Log.d("MagnetBroadcast", "Really high, magnetism probably nearby electric devices, therefore indoor");
                            decisionMatrix[0][1] = 1;
                            decisionMatrix[1][1] = 0.3;
                        } else {
                            Log.d("MagnetBroadcast", "High magnetism, probably indoors");
                            decisionMatrix[0][1] = 1;
                            decisionMatrix[1][1] = 0.6;
                        }

                        Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
                        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                        context.sendBroadcast(apply);
                        contextProducer.onContext();

                        alarm.SetAlarm(context, interval_min);
                    }
                }
            }
        }
    }

    public class AccelerometerReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_ACCELEROMETER));
                int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_ACCELEROMETER));
                int accelerometer_counter = 0;
                double current_accelerometer_val = 0;
                double avg_accelerometer_val = 0;
                double avg_accelerometer = 0;

                if (interval_min > 0 && !lock) {
                    Cursor accelerometer = getContentResolver().query(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI,
                            null, null, null, Accelerometer_Provider.Accelerometer_Data.TIMESTAMP + " DESC LIMIT 1");
                    if (accelerometer_counter < samples && accelerometer != null && accelerometer.moveToFirst()) {
                        double value_x = accelerometer.getDouble(accelerometer.getColumnIndex(Accelerometer_Provider.Accelerometer_Data.VALUES_0));
                        double value_y = accelerometer.getDouble(accelerometer.getColumnIndex(Accelerometer_Provider.Accelerometer_Data.VALUES_1));
                        double value_z = accelerometer.getDouble(accelerometer.getColumnIndex(Accelerometer_Provider.Accelerometer_Data.VALUES_2));
                        current_accelerometer_val = Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                        avg_accelerometer_val += current_accelerometer_val;
                        accelerometer_counter += 1;
                    } else {
                        lock = true;
                        avg_accelerometer = (avg_accelerometer_val / accelerometer_counter - GRAVITY);

                        if (avg_accelerometer < 1) {
                            Log.d("AccelerometerBroadcast", "Still, different due to possible noise hence indoor");
                            decisionMatrix[0][2] = 1;
                            decisionMatrix[1][2] = 0.2;
                        } else if (avg_accelerometer > 1 && avg_accelerometer <= 20) {
                            Log.d("AccelerometerBroadcast", "Walking, biking, or running, hence outdoor");
                            decisionMatrix[0][2] = 0;
                            decisionMatrix[1][2] = 0.5;
                        } else {
                            Log.d("AcclerometerBroadcast", "High speed, meaning motor vehicle. Assumed they are indoor");
                            decisionMatrix[0][2] = 1;
                            decisionMatrix[1][2] = 0.8;
                        }

                        Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
                        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                        context.sendBroadcast(apply);
                        contextProducer.onContext();

                        alarm.SetAlarm(context, interval_min);
                    }
                }
            }
        }

}