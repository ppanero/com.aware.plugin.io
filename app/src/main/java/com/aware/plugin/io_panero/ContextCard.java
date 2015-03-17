package com.aware.plugin.io_panero;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.aware.utils.IContextCard;

public class ContextCard implements IContextCard {

    //Empty constructor used to instantiate this card
    public ContextCard(){};

    @Override
    public View getContextCard(Context context) {
        //Inflate and return your card's layout. See LayoutInflater documentation.
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = (View) inflater.inflate(R.layout.card, null);
        TextView io_status_text = (TextView) card.findViewById(R.id.io_status);
        TextView io_confidence_text = (TextView) card.findViewById(R.id.io_confidence);

        //Modify card's content
        Cursor ioMeter = context.getContentResolver().query(Provider.IOMeter_Data.CONTENT_URI,
                null, null, null, Provider.IOMeter_Data.TIMESTAMP + " DESC LIMIT 1");
        if (ioMeter != null && ioMeter.moveToFirst()) {
            double io_confidence = ioMeter.getDouble(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_CONFIDENCE));
            String io_status = ioMeter.getString(ioMeter.getColumnIndex(Provider.IOMeter_Data.IO_STATUS));
            io_status_text.setText("IO Status: " + io_status);
            io_confidence_text.setText("IO Confidence: " + io_confidence);
        }
        if( ioMeter != null && !ioMeter.isClosed()) ioMeter.close();
        return card;
    }
}
