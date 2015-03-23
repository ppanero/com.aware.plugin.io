package com.aware.plugin.io_panero;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.util.Log;
import com.aware.Aware;
import com.aware.Aware_Preferences;


public class IOAlarm extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("Alarm", "Alarm received, sensors on");
        //Turn on  the sensors
        Aware.setSetting(context, Aware_Preferences.STATUS_ACCELEROMETER, true);
        Aware.setSetting(context, Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, true);
        Aware.setSetting(context, Aware_Preferences.STATUS_LOCATION_GPS, true);
        //unlock sensors
        Plugin.lockOffAccelerometer();
        Plugin.lockOffMagnetometer();
        Plugin.lockOffLocation();
        Plugin.lockOffLight();
        //Apply settings
        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        context.sendBroadcast(apply);


    }

    public void SetAlarm(Context context, int interval) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, IOAlarm.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        //interval must come in minutes
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (interval*60*1000), pi);
    }

    public void CancelAlarm(Context context) {
        Intent intent = new Intent(context, IOAlarm.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}
