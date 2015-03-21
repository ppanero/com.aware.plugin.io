package com.aware.plugin.io_panero;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

public class Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 9;

    /**
     * Provider authority: com.aware.plugin.io_panero.provider.io
     */

    public static String AUTHORITY = "com.aware.plugin.io_panero.provider.io";

    private static final int IO_METER = 1;
    private static final int IO_METER_ID = 2;

    public static final String DATABASE_NAME = Environment.getExternalStorageDirectory() + "/AWARE/plugin_io.db";

    public static final String[] DATABASE_TABLES = {
            "plugin_io"
    };

    public static final String[] TABLES_FIELDS = {
            IOMeter_Data._ID + " integer primary key autoincrement," +
                    IOMeter_Data.TIMESTAMP + " real default 0," +
                    IOMeter_Data.DEVICE_ID + " text default ''," +
                    IOMeter_Data.IO_STATUS + " text default 'indoor'," +
                    IOMeter_Data.IO_ELAPSED_TIME + " int default 0," +
                    IOMeter_Data.IO_LAST_UPDATE + " int default 0," +
                    IOMeter_Data.IO_MAGNETOMETER + " real default 0," +
                    IOMeter_Data.IO_ACCELEROMETER + " real default 0," +
                    IOMeter_Data.IO_BATTERY + " integer default 0," +
                    IOMeter_Data.IO_LIGHT + " real default 0," +
                    IOMeter_Data.IO_GPS + " real default 0," +
                    "UNIQUE("+IOMeter_Data.TIMESTAMP+","+IOMeter_Data.DEVICE_ID+")"
    };

    public static final class IOMeter_Data implements BaseColumns {
        private IOMeter_Data(){};

        public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/plugin_io");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.io";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.io";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String IO_STATUS = "io_status";
        public static final String IO_ELAPSED_TIME = "elapsed_time";
        public static final String IO_LAST_UPDATE = "last_update";
        public static final String IO_MAGNETOMETER = "double_magnetometer";
        public static final String IO_ACCELEROMETER = "double_accelerometer";
        public static final String IO_BATTERY = "battery";
        public static final String IO_LIGHT = "double_light";
        public static final String IO_GPS = "double_gps";
    }

    private static UriMatcher URIMatcher;
    private static HashMap<String, String> databaseMap;
    private static DatabaseHelper databaseHelper;
    private static SQLiteDatabase database;

    @Override
    public boolean onCreate() {

        AUTHORITY = getContext().getPackageName() + ".provider.io";

        URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], IO_METER);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0]+"/#", IO_METER_ID);

        databaseMap = new HashMap<String, String>();
        databaseMap.put(IOMeter_Data._ID, IOMeter_Data._ID);
        databaseMap.put(IOMeter_Data.TIMESTAMP, IOMeter_Data.TIMESTAMP);
        databaseMap.put(IOMeter_Data.DEVICE_ID, IOMeter_Data.DEVICE_ID);
        databaseMap.put(IOMeter_Data.IO_STATUS, IOMeter_Data.IO_STATUS);
        databaseMap.put(IOMeter_Data.IO_ELAPSED_TIME, IOMeter_Data.IO_ELAPSED_TIME);
        databaseMap.put(IOMeter_Data.IO_LAST_UPDATE, IOMeter_Data.IO_LAST_UPDATE);
        databaseMap.put(IOMeter_Data.IO_MAGNETOMETER, IOMeter_Data.IO_MAGNETOMETER);
        databaseMap.put(IOMeter_Data.IO_ACCELEROMETER, IOMeter_Data.IO_ACCELEROMETER);
        databaseMap.put(IOMeter_Data.IO_BATTERY, IOMeter_Data.IO_BATTERY);
        databaseMap.put(IOMeter_Data.IO_LIGHT, IOMeter_Data.IO_LIGHT);
        databaseMap.put(IOMeter_Data.IO_GPS, IOMeter_Data.IO_GPS);

        return true;
    }

    private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper( getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS );
        }
        if( databaseHelper != null && ( database == null || ! database.isOpen() )) {
            database = databaseHelper.getWritableDatabase();
        }
        return( database != null && databaseHelper != null);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case IO_METER:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (URIMatcher.match(uri)) {
            case IO_METER:
                return IOMeter_Data.CONTENT_TYPE;
            case IO_METER_ID:
                return IOMeter_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();

        switch (URIMatcher.match(uri)) {
            case IO_METER:
                long io_id = database.insert(DATABASE_TABLES[0], IOMeter_Data.DEVICE_ID, values);

                if (io_id > 0) {
                    Uri new_uri = ContentUris.withAppendedId(
                            IOMeter_Data.CONTENT_URI,
                            io_id);
                    getContext().getContentResolver().notifyChange(new_uri,
                            null);
                    return new_uri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URIMatcher.match(uri)) {
            case IO_METER:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(databaseMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());

            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case IO_METER:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:

                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}

