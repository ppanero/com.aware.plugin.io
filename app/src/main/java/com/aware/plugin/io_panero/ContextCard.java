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

import java.util.Set;

public class ContextCard implements IContextCard {
    //Empty constructor used to instantiate this card
    public ContextCard(){};

    //You may use sContext on uiChanger to do queries to databases, etc.
    private Context sContext;

    //Set how often your card needs to refresh if the stream is visible (in milliseconds)
    private int alarm_frecuency = Integer.parseInt(Aware.getSetting(sContext.getApplicationContext(), Settings.FREQUENCY_IO_METER));
    private int time_offset = 30;
    private int refresh_interval =  (alarm_frecuency * 60 * 1000) + time_offset; //1 second = 1000 milliseconds

    //Declare here all the UI elements you'll be accessing
    private View card;
    private TextView counter_txt;

    //Used to load your context card
    private LayoutInflater sInflater;

    private Handler uiRefresher = new Handler(Looper.getMainLooper());
    private Runnable uiChanger = new Runnable() {
        @Override
        public void run() {

            TextView io_status_text = (TextView) card.findViewById(R.id.io_status);
            TextView io_confidence_text = (TextView) card.findViewById(R.id.io_confidence);
            TextView io_magnetometer_text = (TextView) card.findViewById(R.id.io_magnetometer);
            TextView io_accelerometer_text = (TextView) card.findViewById(R.id.io_accelerometer);
            TextView io_battery_text = (TextView) card.findViewById(R.id.io_battery);
            TextView io_light_text = (TextView) card.findViewById(R.id.io_light);
            TextView io_telephony_text = (TextView) card.findViewById(R.id.io_gsm_strength);
            //Modify card's content here once it's initialized
            if( card != null ) {
                //Modify card's content
                Cursor ioMeter = sContext.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                        null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
                if (ioMeter != null && ioMeter.moveToFirst()) {
                    double io_confidence = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_CONFIDENCE));
                    String io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
                    double io_magnetometer = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_MAGNETOMETER));
                    double io_acceletometer = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_ACCELEROMETER));
                    int io_battery = ioMeter.getInt(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_BATTERY));
                    double io_light = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_LIGHT));
                    double io_telephony = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_TELEPHONY));
                    io_status_text.setText("IO Status: " + io_status);
                    io_confidence_text.setText("IO Confidence: " + io_confidence);
                    io_magnetometer_text.setText("Magnetometer: " + io_magnetometer);
                    io_accelerometer_text.setText("Acceletometer: " + io_acceletometer);
                    io_battery_text.setText("Battery: " + io_battery);
                    io_light_text.setText("Light: " + io_light);
                    io_telephony_text.setText("GSM Strength: " + io_telephony);

                }
                if( ioMeter != null && !ioMeter.isClosed()) ioMeter.close();
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
        TextView io_status_text = (TextView) card.findViewById(R.id.io_status);
        TextView io_confidence_text = (TextView) card.findViewById(R.id.io_confidence);

        //Modify card's content
        Cursor ioMeter = sContext.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
        if (ioMeter != null && ioMeter.moveToFirst()) {
            double io_confidence = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_CONFIDENCE));
            String io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
            io_status_text.setText("IO Status: " + io_status);
            io_confidence_text.setText("IO Confidence: " + io_confidence);
        }
        if( ioMeter != null && !ioMeter.isClosed()) ioMeter.close();

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
                TextView io_status_text = (TextView) card.findViewById(R.id.io_status);
                TextView io_confidence_text = (TextView) card.findViewById(R.id.io_confidence);

                //Modify card's content
                Cursor ioMeter = sContext.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                        null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
                if (ioMeter != null && ioMeter.moveToFirst()) {
                    double io_confidence = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_CONFIDENCE));
                    String io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
                    io_status_text.setText("IO Status: " + io_status);
                    io_confidence_text.setText("IO Confidence: " + io_confidence);
                }
                if( ioMeter != null && !ioMeter.isClosed()) ioMeter.close();
            }
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_CLOSED) ) {
                //stop refreshing when user leaves the stream
                uiRefresher.removeCallbacks(uiChanger);
                uiRefresher.removeCallbacksAndMessages(null);
            }
        }
    }
}