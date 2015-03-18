package com.aware.plugin.io_panero;

import com.aware.Aware;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    public static final String STATUS_PLUGIN_IO = "status_plugin_io";
    public static final String FREQUENCY_IO_METER = "frecuenty_io_meter";
    public static final String SAMPLES_LIGHT_METER = "light_samples";
    public static final String SAMPLES_MAGNETOMETER = "magnet_samples";
    public static final String SAMPLES_ACCELEROMETER = "accelerometer_samples";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        syncSettings();
    }

    private void syncSettings() {
        //Make sure to load the latest values
        CheckBoxPreference status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_IO);
        status.setChecked(Aware.getSetting(this, STATUS_PLUGIN_IO).equals("true"));

        //...
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = (Preference) findPreference(key);

        if( setting.getKey().equals(STATUS_PLUGIN_IO) ) {
            boolean is_active = sharedPreferences.getBoolean(key, false);
            Aware.setSetting(this, key, is_active);
            if( is_active ) {
                Aware.startPlugin(this, getPackageName());
            } else {
                Aware.stopPlugin(this, getPackageName());
            }
        }

        //Apply the new settings
        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        sendBroadcast(apply);
    }
}


