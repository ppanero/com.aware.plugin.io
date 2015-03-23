package com.aware.plugin.io_panero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.aware.Aware;
import com.aware.ui.Stream_UI;
import com.aware.utils.IContextCard;

import java.util.Calendar;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ContextCard implements IContextCard {
    //Empty constructor used to instantiate this card
    public ContextCard(){};

    //You may use sContext on uiChanger to do queries to databases, etc.
    private Context sContext;

    //Set how often your card needs to refresh if the stream is visible (in milliseconds)
    private int refresh_interval =  3 * 60 * 1000; //1 second = 1000 milliseconds

    //Declare here all the UI elements you'll be accessing
    private View card;
    private TextView io_status_text;
    private TextView io_elapsed_time_text;
    private TextView io_last_update_text;
    private TextView io_magnetometer_text;
    private TextView io_accelerometer_text;
    private TextView io_battery_text;
    private TextView io_light_text;
    private TextView io_gps_text;

    private int io_elapsed_time = 0;
    private int io_last_update = 0;
    private String io_status = "Initializing";
    private double io_accelerometer = 0;
    private int io_magnetometer = 0;
    private int io_battery = 0;
    private int io_light = 0;
    private int io_gps = 0;

    //Used to load your context card
    private LayoutInflater sInflater;

    private Handler uiRefresher = new Handler(Looper.getMainLooper());
    private Runnable uiChanger = new Runnable() {
        @Override
        public void run() {
            //Modify card's content here once it's initialized
            if( card != null ) {
                //Modify card's content
               Cursor ioMeter = sContext.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                        null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
                if (ioMeter != null && ioMeter.moveToFirst()) {
                    io_elapsed_time = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_ELAPSED_TIME));
                    io_last_update = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_LAST_UPDATE));
                    io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
                    io_magnetometer = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_MAGNETOMETER));
                    io_accelerometer = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_ACCELEROMETER));
                    io_battery = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_BATTERY));
                    io_light = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_LIGHT));
                    io_gps = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_GPS));
                }
                if( ioMeter != null && !ioMeter.isClosed()) ioMeter.close();
                io_status_text.setText("IO Status: " + io_status);
                io_elapsed_time_text.setText("Elapsed time: " + Plugin.secondsToTime(io_elapsed_time));
                io_last_update_text.setText("Last update: " + Plugin.secondsToTime(io_last_update + Plugin.timezoneOffset()));
                io_magnetometer_text.setText("Magnetometer: " + io_magnetometer);
                io_accelerometer_text.setText("Accelerometer: " + io_accelerometer);
                io_battery_text.setText("Battery: " + io_battery);
                io_light_text.setText("Light: " + io_light);
                io_gps_text.setText("GPS accuracy: " + io_gps);
            }

            //Reset timer and schedule the next card refresh
            uiRefresher.postDelayed(uiChanger, refresh_interval);
        }
    };

    @Override
    public View getContextCard(Context context) {
        sContext = context;

        //Tell Android that you'll monitor the stream statuses
        IntentFilter filter = new IntentFilter();
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_OPEN);
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_CLOSED);
        context.registerReceiver(streamObs, filter);

        //Load card information to memory
        sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        card = sInflater.inflate(R.layout.card, null);
        io_status_text = (TextView) card.findViewById(R.id.io_status);
        io_elapsed_time_text = (TextView) card.findViewById(R.id.io_elapsed_time);
        io_last_update_text = (TextView) card.findViewById(R.id.io_last_update);
        io_magnetometer_text = (TextView) card.findViewById(R.id.io_magnetometer);
        io_accelerometer_text = (TextView) card.findViewById(R.id.io_accelerometer);
        io_battery_text = (TextView) card.findViewById(R.id.io_battery);
        io_light_text = (TextView) card.findViewById(R.id.io_light);
        io_gps_text = (TextView) card.findViewById(R.id.io_gps_accuracy);
        //Modify card's content
        Cursor ioMeter = sContext.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
        if (ioMeter != null && ioMeter.moveToFirst()) {
            io_elapsed_time = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_ELAPSED_TIME));
            io_last_update = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_LAST_UPDATE));
            io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
            io_magnetometer = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_MAGNETOMETER));
            io_accelerometer = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_ACCELEROMETER));
            io_battery = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_BATTERY));
            io_light = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_LIGHT));
            io_gps = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_GPS));
        }
        if (ioMeter != null && !ioMeter.isClosed()) ioMeter.close();
        io_status_text.setText("IO Status: " + io_status);
        io_elapsed_time_text.setText("Elapsed time: " + Plugin.secondsToTime(io_elapsed_time));
        io_last_update_text.setText("Last update: " + Plugin.secondsToTime(io_last_update + Plugin.timezoneOffset()));
        io_magnetometer_text.setText("Magnetometer: " + io_magnetometer);
        io_accelerometer_text.setText("Accelerometer: " + io_accelerometer);
        io_battery_text.setText("Battery: " + io_battery);
        io_light_text.setText("Light: " + io_light);
        io_gps_text.setText("GPS accuracy: " + io_gps);

        //Begin refresh cycle
        uiRefresher.postDelayed(uiChanger, refresh_interval);

        //Return the card to AWARE/apps
        return card;
    }

    //This is a BroadcastReceiver that keeps track of stream status. Used to stop the refresh when user leaves the stream and restart again otherwise
    private StreamObs streamObs = new StreamObs();
    public class StreamObs extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_OPEN) ) {
                //start refreshing when user enters the stream
                uiRefresher.postDelayed(uiChanger, refresh_interval);
            }
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_CLOSED) ) {
                //stop refreshing when user leaves the stream
                uiRefresher.removeCallbacks(uiChanger);
                uiRefresher.removeCallbacksAndMessages(null);
            }
        }
    }
}
