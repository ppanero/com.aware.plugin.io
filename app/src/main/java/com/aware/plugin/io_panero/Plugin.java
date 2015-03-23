package com.aware.plugin.io_panero;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
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
    private static final int MID_DAY = 12;
    private static final int NUMBER_OF_SENSORS = 5;
    /*
    This decision matrix will tell if the device is indoors or outdoors according to the
    values it has:
    the rows stand for:
        1:=Magnetometer
        2:=Accelerometer
        3:=Battery
        4:=Light
        5:=GPS
    The first column will have either 1 (indoor) or 0 (outdoor).
    The second column will have the weight of the sensor according to its measured value-
    The third column is its value.
     */
    private static ContextProducer contextProducer;
    //IO status computation variables
    private static IOAlarm alarm = new IOAlarm();
    private static double[][] decisionMatrix = new double[3][5]; //{{0,0},{0,0},{0,0},{0,0},{0,0}};
    private static double overallWeight = 0;
    private static String io_status = "indoor";
    private static String previous_io_status = "";
    private static long starting_time = 0;
    private static long elapsed_time = 0;
    private static long previous_elapsed_time = 0;
    private static long last_update = 0;
    private static int temp_interval = 0;
    //Magnetometer variables
    private static MagnetReceiver magnetReceiver = new MagnetReceiver();
    private static int magnet_counter = 0;
    private static double magnet_val = 0;
    private static int avg_magnet = 0;
    private static boolean lockMagnetometer = false;
    //Light variables
    private static LightReceiver lightReceiver = new LightReceiver();
    private static double light_val = 0;
    private static int avg_light = 0;
    private static int light_counter = 0;
    private static boolean lockLight = false;
    //Accelerometer variables
    private static AccelerometerReceiver accelerometerReceiver = new AccelerometerReceiver();
    private static double GRAVITY = 9.81;
    private static int accelerometer_counter = 0;
    private static double accelerometer_val = 0;
    private static double avg_accelerometer = 0;
    private static boolean lockAccelerometer = false;
    //Location variables
    private static LocationReceiver locationReceiver = new LocationReceiver();
    private static int gps_accuracy = 0;
    private static int avg_gps_accuracy = 0;
    private static int location_counter = 0;
    private static boolean lockLocation = false;
    //Battery variables
    private static BatteryReceiver batteryReceiver = new BatteryReceiver();
    private static boolean lockBattery = false;

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

        //Set settings (amount of samples for each sensor, and IO plugin awaking frequency)
        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_ACCELEROMETER, 10);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_MAGNETOMETER, 10);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_LIGHT_METER, 5);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.SAMPLES_LOCATION_METER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.SAMPLES_LOCATION_METER, 1);
        }

        if (Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_IO_METER).length() == 0) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_IO_METER, 3);
        }

        //Turn on the sensors
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS, true);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, true);

        //Context producer
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

        //Register the receivers
        IntentFilter magnetFilter = new IntentFilter();
        magnetFilter.addAction(Magnetometer.ACTION_AWARE_MAGNETOMETER);
        registerReceiver(magnetReceiver, magnetFilter);

        IntentFilter accelerometerFilter = new IntentFilter();
        accelerometerFilter.addAction(Accelerometer.ACTION_AWARE_ACCELEROMETER);
        registerReceiver(accelerometerReceiver, accelerometerFilter);

        IntentFilter batteryFilter = new IntentFilter();
        batteryFilter.addAction(Battery.ACTION_AWARE_BATTERY_CHARGING_USB);
        batteryFilter.addAction(Battery.ACTION_AWARE_BATTERY_CHARGING_AC);
        batteryFilter.addAction(Battery.ACTION_AWARE_BATTERY_DISCHARGING);
        registerReceiver(batteryReceiver, batteryFilter);

        IntentFilter lightFilter = new IntentFilter();
        lightFilter.addAction(Light.ACTION_AWARE_LIGHT);
        registerReceiver(lightReceiver, lightFilter);

        IntentFilter locationFilter = new IntentFilter();
        locationFilter.addAction(Locations.ACTION_AWARE_LOCATIONS);
        registerReceiver(locationReceiver, locationFilter);

        //Apply settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));

        if (DEBUG) Log.d(TAG, "IO plugin running");

        //Database
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.IOMeter_Data.CONTENT_URI};
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Set the alarm again or cancel it
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
        //Turn off the sensors
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS, false);

        //Unregister receivers
        unregisterReceiver(magnetReceiver);
        unregisterReceiver(accelerometerReceiver);
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(lightReceiver);
        unregisterReceiver(locationReceiver);

        //Apply context
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private static void updateStatus(Context context) {
        double indoor_counter = 0;
        double outdoor_counter = 0;
        double outdoor_weight = 0;
        double indoor_weight = 0;

        //Go through the decision matrix
        synchronized(decisionMatrix) {
            for (int i = 0; i < NUMBER_OF_SENSORS; i++) {
                if (decisionMatrix[0][i] == 1) {
                    indoor_weight += decisionMatrix[1][i];
                    indoor_counter += 1;
                } else if (decisionMatrix[0][i] == 0) {
                    outdoor_weight += decisionMatrix[1][i];
                    outdoor_counter += 1;
                }
            }
        }

        //Check which status has more weight
        if (indoor_weight >= outdoor_weight) {
            io_status = INDOOR;
            overallWeight = indoor_weight / indoor_counter;
        } else {
            io_status = OUTDOOR;
            overallWeight = outdoor_weight / outdoor_counter;
        }

        //Calculate elapsed time
        if (previous_elapsed_time == 0) {
            elapsed_time = System.currentTimeMillis() - starting_time;
        } else {
            elapsed_time = System.currentTimeMillis() - last_update;
        }
        if (!previous_io_status.equals("") && previous_io_status.equals(io_status)) {
            elapsed_time += previous_elapsed_time;
        }
        last_update = System.currentTimeMillis();
        previous_elapsed_time = elapsed_time;
        previous_io_status = io_status;
        int aux_elapsed_time = (int) (elapsed_time / 1000L);
        int aux_last_update = (int) (last_update / 1000L);

        //Insert in the database
        ContentValues data = new ContentValues();
        data.put(IOMeter_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
        data.put(IOMeter_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(IOMeter_Data.IO_STATUS, io_status);
        data.put(IOMeter_Data.IO_ELAPSED_TIME, aux_elapsed_time);
        data.put(IOMeter_Data.IO_LAST_UPDATE, aux_last_update);
        synchronized (decisionMatrix) {
            data.put(IOMeter_Data.IO_MAGNETOMETER, (int) decisionMatrix[2][0]);
            data.put(IOMeter_Data.IO_ACCELEROMETER, decisionMatrix[2][1]);
            data.put(IOMeter_Data.IO_BATTERY, (int) decisionMatrix[2][2]);
            data.put(IOMeter_Data.IO_LIGHT, (int) decisionMatrix[2][3]);
            data.put(IOMeter_Data.IO_GPS, (int) decisionMatrix[2][4]);
        }
        context.getContentResolver().insert(IOMeter_Data.CONTENT_URI, data);
        contextProducer.onContext();
        alarm.SetAlarm(context, 2);
        if (DEBUG) Log.d("Status updated", "The device is: " + io_status.toString() + " weight: " + overallWeight);
    }

    protected static void lockOffMagnetometer() {
        lockMagnetometer = false;
    }

    protected static void lockOffAccelerometer() {
        lockMagnetometer = false;
    }

    protected static void lockOffLight() {
        lockLight = false;
    }

    protected static void lockOffLocation() {
        lockLocation = false;
    }

    protected static void lockOffBattery() {
        lockBattery = false;
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

    public static double getHourWeight(int hour){
        return ((-0.5 / 11) * (hour % 12)) + 1;
    }

    public static class MagnetReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_MAGNETOMETER));

            if (interval_min > 0 && !lockMagnetometer) {
                if (magnet_counter < samples) {
                    ContentValues values = (ContentValues) intent.getExtras().get(Magnetometer.EXTRA_DATA);
                    double value_x = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_0).toString());
                    double value_y = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_1).toString());
                    double value_z = Double.parseDouble(values.get(Magnetometer_Provider.Magnetometer_Data.VALUES_2).toString());
                    magnet_val += Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                    magnet_counter++;
                } else {
                    //Turn off the sensor for computation
                    Aware.setSetting(context, Aware_Preferences.STATUS_MAGNETOMETER, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    if (DEBUG) Log.d("MagnetSensor", "Magnetometer turned off");
                    lockMagnetometer = true;
                    if (magnet_counter > 0) {
                        avg_magnet = (int) (magnet_val / magnet_counter);
                    }
                    if (avg_magnet <= 47) {
                        if (DEBUG) Log.d("MagnetReceiver", "Low magnetism, probably inside");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.7;
                    } else if (avg_magnet > 47 && avg_magnet < 52) {
                        if (DEBUG) Log.d("MagnetReceiver", "Semindoor values, therefore indoor");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.5;
                    } else if (avg_magnet >= 52 && avg_magnet < 60) {
                        if (DEBUG) Log.d("MagnetReceiver", "High magnetism, therefore outdoors");
                        decisionMatrix[0][0] = 0;
                        decisionMatrix[1][0] = 0.7;
                    } else {
                        if (DEBUG) Log.d("MagnetReceiver", "Really high magnetism, nearby electronic device therefore indoors");
                        decisionMatrix[0][0] = 1;
                        decisionMatrix[1][0] = 0.5;
                    }
                    decisionMatrix[2][0] = avg_magnet;
                    avg_magnet = 0;
                    magnet_val = 0;
                    magnet_counter = 0;
                    updateStatus(context);
                }
            }
        }
    }

    public static class AccelerometerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_ACCELEROMETER));

            if (interval_min > 0 && !lockAccelerometer) {
                if (accelerometer_counter < samples) {
                    ContentValues values = (ContentValues) intent.getExtras().get(Accelerometer.EXTRA_DATA);
                    double value_x = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_0).toString());
                    double value_y = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_1).toString());
                    double value_z = Double.parseDouble(values.get(Accelerometer_Provider.Accelerometer_Data.VALUES_2).toString());
                    accelerometer_val += Math.sqrt(Math.pow(value_x, 2) + Math.pow(value_y, 2) + Math.pow(value_z, 2));
                    accelerometer_counter++;
                } else {
                    //Turn off the sensor for computation
                    Aware.setSetting(context, Aware_Preferences.STATUS_ACCELEROMETER, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    if (DEBUG) Log.d("AccelerometerSensor", "Accelerometer turned off");
                    lockAccelerometer = true;
                    if (accelerometer_counter > 0) {
                        avg_accelerometer = (accelerometer_val / accelerometer_counter - GRAVITY);
                        avg_accelerometer = Math.abs((double)(Math.round(avg_accelerometer * 100)) / 100);
                    }

                    if (avg_accelerometer <= 2) {
                        if (DEBUG)
                            Log.d("AccelerometerReceiver", "Still, different due to possible noise hence indoor");
                        decisionMatrix[0][1] = 1;
                        decisionMatrix[1][1] = 0.2;
                    }
                    else {
                        if (DEBUG) Log.d("AccelerometerReceiver", "Walking, biking, or running, hence outdoor");
                        decisionMatrix[0][1] = 0;
                        decisionMatrix[1][1] = 0.2;
                    }
                    decisionMatrix[2][1] = avg_accelerometer;
                    accelerometer_counter = 0;
                    avg_accelerometer = 0;
                    accelerometer_val = 0;
                    updateStatus(context);
                }
            }
        }
    }

    public static class BatteryReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));

            //Get the latest recorded value
            if (interval_min > 0 && !lockBattery) {
                Cursor battery = context.getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI,
                        null, null, null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
                if (battery != null && battery.moveToFirst()) {
                    int battery_status = battery.getInt(battery.getColumnIndex(Battery_Provider.Battery_Data.PLUG_ADAPTOR));
                    //Turn off the sensor for computation
                    Aware.setSetting(context, Aware_Preferences.STATUS_BATTERY, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    if (DEBUG) Log.d("BatterySensor", "Battery turned off");
                    lockBattery = true;
                    switch (battery_status) {
                        //case BatteryManager.BATTERY_PLUGGED_AC:
                        case 1:
                            if (DEBUG) Log.d("BatteryReceiver", "Battery plugged to AC");
                            decisionMatrix[0][2] = 1;
                            decisionMatrix[1][2] = 0.9;
                            break;

                        //case BatteryManager.BATTERY_PLUGGED_USB:
                        case 2:
                            if (DEBUG) Log.d("BatteryReceiver", "Battery plugged to USB");
                            decisionMatrix[0][2] = 1;
                            decisionMatrix[1][2] = 0.8;
                            break;
                        //case BatteryManager.DISCHARGING:
                        default:
                            if (DEBUG) Log.d("BatteryReceiver", "Battery discharging");
                            decisionMatrix[0][2] = -1;
                            decisionMatrix[1][2] = -1;
                            break;
                    }
                    decisionMatrix[2][2] = battery_status;
                    updateStatus(context);
                }
                if (battery != null && !battery.isClosed()) battery.close();
            }
        }
    }

    public static class LightReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_LIGHT_METER));

            if (interval_min > 0 && !lockLight) {
                if (light_counter < samples) {
                    ContentValues values = (ContentValues) intent.getExtras().get(Light.EXTRA_DATA);
                    light_val += Double.parseDouble(values.get(Light_Provider.Light_Data.LIGHT_LUX).toString());
                    light_counter++;
                } else {
                    //Turn off the sensor for computation
                    Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    if (DEBUG) Log.d("LightSensor", "Light turned off");
                    lockLight = true;
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    double hour_weight = getHourWeight(hour);

                    if (light_counter > 0) {
                        avg_light = (int)(light_val / light_counter);
                    }
                    if (hour >= MID_DAY) { //Daylight
                        if (avg_light <= 200) {
                            if (DEBUG) Log.d("LightReceiver", "lower than 200");
                            decisionMatrix[0][3] = 1;
                            decisionMatrix[1][3] = 0.8 * hour_weight;
                        } else if (avg_light > 200 && avg_light <= 500) {
                            if (DEBUG) Log.d("LightReceiver", "between 200 and 500");
                            decisionMatrix[0][3] = 1;
                            decisionMatrix[1][3] = 0.6 * hour_weight;
                        } else if (avg_light > 500 && avg_light <= 800) {
                            if (DEBUG) Log.d("LightReceiver", "between 200 and 500");
                            decisionMatrix[0][3] = 1;
                            decisionMatrix[1][3] = 0.7 * hour_weight;
                        } else {
                            if (DEBUG) Log.d("LightReceiver", "higher than 500");
                            decisionMatrix[0][3] = 0;
                            decisionMatrix[1][3] = 0.8 * hour_weight;
                        }
                    }
                    else {//Night
                        if (avg_light < 2) {
                            if (DEBUG) Log.d("LightReceiver", "lower than 2");
                            decisionMatrix[0][3] = 0;
                            decisionMatrix[1][3] = 0.8 * hour_weight;
                        } else if (avg_light >=2 && avg_light < 20) {
                            if (DEBUG) Log.d("LightReceiver", "between 2 and 20");
                            decisionMatrix[0][3] = 1;
                            decisionMatrix[1][3] = 0.6 * hour_weight;
                        } else {
                            if (DEBUG) Log.d("LightReceiver", "higher than 20");
                            decisionMatrix[0][3] = 1;
                            decisionMatrix[1][3] = 0.8 * hour_weight;
                        }
                    }
                    decisionMatrix[2][3] = avg_light;
                    light_val = 0;
                    avg_light = 0;
                    light_counter = 0;
                    updateStatus(context);
                    alarm.SetAlarm(context, interval_min);
                }
            }
        }
    }

    public static class LocationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int interval_min = Integer.parseInt(Aware.getSetting(context, Settings.FREQUENCY_IO_METER));
            int samples = Integer.parseInt(Aware.getSetting(context, Settings.SAMPLES_LOCATION_METER));

            if (interval_min > 0 && !lockLocation) {
                if (location_counter < samples) {
                    //Get the latest recorded value
                    Cursor location = context.getContentResolver().query(Locations_Provider.Locations_Data.CONTENT_URI,
                            null, null, null, Locations_Provider.Locations_Data.TIMESTAMP + " DESC LIMIT 1");
                    if(location != null && location.moveToFirst()){
                        gps_accuracy += location.getInt(location.getColumnIndex(Locations_Provider.Locations_Data.ACCURACY));
                        location_counter++;
                    }
                    if (location != null && !location.isClosed()) {
                        location.close();
                    }
                }
                else{
                    //Turn the sensor off for computation
                    Aware.setSetting(context, Aware_Preferences.STATUS_LOCATION_GPS, false);
                    Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
                    context.sendBroadcast(apply);
                    if (DEBUG) Log.d("LocationSensor", "Location turned off");
                    lockLocation = true;
                    avg_gps_accuracy = gps_accuracy / samples;
                    if (avg_gps_accuracy < 50) {
                        if (DEBUG) Log.d("LocationReceiver", "low accuracy");
                        decisionMatrix[0][1] = 1;
                        decisionMatrix[1][1] = 0.6;
                    } else if (avg_gps_accuracy >= 50 && avg_gps_accuracy < 100) {
                        if (DEBUG) Log.d("LocationReceiver", "low accuracy");
                        decisionMatrix[0][1] = 1;
                        decisionMatrix[1][1] = 0.4;
                    } else if (avg_gps_accuracy >= 100 && avg_gps_accuracy < 600) {
                        if (DEBUG) Log.d("LocationReceiver", "low accuracy");
                        decisionMatrix[0][1] = 0;
                        decisionMatrix[1][1] = 0.4;
                    } else {
                        if (DEBUG) Log.d("LocationReceiver", "high accuracy");
                        decisionMatrix[0][1] = 0;
                        decisionMatrix[1][1] = 0.7;
                    }
                    decisionMatrix[2][4] = avg_gps_accuracy;
                    gps_accuracy = 0;
                    avg_gps_accuracy = 0;
                    location_counter = 0;
                    updateStatus(context);
                }
            }
        }
    }
}
