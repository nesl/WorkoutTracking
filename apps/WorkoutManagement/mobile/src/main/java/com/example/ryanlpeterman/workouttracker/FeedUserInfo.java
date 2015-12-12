package com.example.ryanlpeterman.workouttracker;

import android.provider.BaseColumns;

/**
 * Created by tahiyasalam on 8/7/15.
 */

//defines format for SQLite database
public class FeedUserInfo {
    public FeedUserInfo() {

    }
    public static abstract class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "userinfo";
        //provides columns for database
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_ACTIVITY = "activity";
        public static final String COLUMN_TOTAL_TIME = "total_time";
        public static final String COLUMN_START_TIME = "start_time";
        public static final String COLUMN_STOP_TIME = "stop_time";

    }

    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + FeedEntry.TABLE_NAME + " (" +
                    FeedEntry._ID + " INTEGER PRIMARY KEY," +
                    FeedEntry.COLUMN_DATE + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_ACTIVITY + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_TOTAL_TIME + TEXT_TYPE + " )";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;
}


