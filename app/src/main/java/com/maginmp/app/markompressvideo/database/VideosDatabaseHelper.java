/*
 * VideosDatabaseHelper.java
 *
 * MarKompressVideo
 * Copyright (c) 2017. Mark Gintsburg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.maginmp.app.markompressvideo.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by MarkGintsburg on 13/11/2016.
 */

public class VideosDatabaseHelper extends SQLiteOpenHelper {

    public static final String TABLE_NAME = "videos_table";
    // Auto generated ID
    public static final String COL_ID = "_id";
    // Video file name with extension
    public static final String COL_VIDEO_NAME = "VIDEO_NAME";
    // Path to video file directory
    public static final String COL_VIDEO_PATH = "VIDEO_PATH";
    // Thumbnail as BLOB
    public static final String COL_THUMB_BLOB = "THUMB_BLOB";
    // Full path to backup file
    public static final String COL_VIDEO_BU_PATH = "VIDEO_BU_PATH";
    // Should the file be shown in the list of files
    public static final String COL_IS_SHOW_IN_LIST = "IS_SHOW_IN_LIST";
    // Shouldn't this file be converted
    public static final String COL_IS_BYPASS_PROC = "IS_BYPASS_PROC";
    // Does the backup copy still exists
    public static final String COL_IS_REVERTABLE = "IS_REVERTABLE";
    // The FFMPEG command that was applied on the file
    public static final String COL_FFMPEG_CMD = "FFMPEG_CMD";
    // The date when the file was added to queue list
    public static final String COL_ADDED_TO_QUEUE_DATE = "ADDED_TO_QUEUE_DATE";
    // The date when the file was processed
    public static final String COL_PROC_DATE = "PROC_DATE";
    // Original file size
    public static final String COL_ORIG_FILE_SIZE = "ORIG_FILE_SIZE";
    // Converted file size
    public static final String COL_PROC_FILE_SIZE = "PROC_FILE_SIZE";
    // The mkv app version when the file was processed
    public static final String COL_APP_VERSION = "APP_VERSION";
    // Duration of the video
    public static final String COL_VIDEO_DURATION = "VIDEO_DURATION";
    // The time it took to process the video
    public static final String COL_ENCODE_TIME = "ENCODE_TIME";
    // WxH of the original video
    public static final String COL_ORIG_VIDEO_DIMEN = "ORIG_VIDEO_DIMEN";
    // WxH of the converted video
    public static final String COL_PROC_VIDEO_DIMEN = "PROC_VIDEO_DIMEN";
    // Converting status of the video. See STATUS_* for options
    public static final String COL_VIDEO_STATUS = "VIDEO_STATUS";
    public static final int STATUS_QUEUE = 0x01;
    public static final int STATUS_BYPASS = 0x02;
    public static final int STATUS_DONE = 0x04;
    public static final int STATUS_ERROR = 0x08;
    public static final int STATUS_REVERTED = 0x10;
    public static final int STATUS_DELETED = 0x20;
    public static final int STATUS_RUNNING = 0x40;
    public static final int STATUS_OTHER = 0x80;
    public static final List<String> ALL_COLS_BY_ORDER = Arrays.asList(
            COL_ID, COL_VIDEO_NAME, COL_VIDEO_PATH, COL_THUMB_BLOB,
            COL_VIDEO_BU_PATH, COL_IS_SHOW_IN_LIST, COL_IS_BYPASS_PROC,
            COL_IS_REVERTABLE, COL_FFMPEG_CMD, COL_ADDED_TO_QUEUE_DATE,
            COL_PROC_DATE, COL_ORIG_FILE_SIZE, COL_PROC_FILE_SIZE, COL_APP_VERSION,
            COL_VIDEO_DURATION, COL_ENCODE_TIME, COL_ORIG_VIDEO_DIMEN, COL_PROC_VIDEO_DIMEN,
            COL_VIDEO_STATUS);
    private static final String TAG = VideosDatabaseHelper.class.getSimpleName();
    private static final String DATABASE_NAME = "videos.db";

    /*

    CREATE TABLE `TABLE_NAME` (
	`COL_ID`	INTEGER PRIMARY KEY AUTOINCREMENT,
	`COL_VIDEO_NAME`	TEXT NOT NULL,
	`COL_VIDEO_PATH`	TEXT NOT NULL,
	`COL_THUMB_BLOB`	BLOB NOT NULL,
	`COL_VIDEO_BU_PATH`	TEXT,
	`COL_IS_SHOW_IN_LIST`	INTEGER NOT NULL DEFAULT 1,
	`COL_IS_BYPASS_PROC`	INTEGER NOT NULL DEFAULT 0,
	`COL_IS_REVERTABLE`	INTEGER NOT NULL DEFAULT 0,
	`COL_FFMPEG_CMD`	TEXT,
	`COL_ADDED_TO_QUEUE_DATE`	INTEGER NOT NULL DEFAULT 0,
	`COL_PROC_DATE`	INTEGER NOT NULL DEFAULT 0,
	`COL_ORIG_FILE_SIZE`	INTEGER NOT NULL DEFAULT 0,
	`COL_PROC_FILE_SIZE`	INTEGER,
	`COL_APP_VERSION`	INTEGER NOT NULL DEFAULT 0,
	`COL_VIDEO_DURATION`	INTEGER NOT NULL DEFAULT 0,
	`COL_ENCODE_TIME`	INTEGER,
	`COL_ORIG_VIDEO_DIMEN`	TEXT NOT NULL DEFAULT '0x0',
	`COL_PROC_VIDEO_DIMEN`	TEXT,
	`COL_VIDEO_STATUS`	INTEGER NOT NULL DEFAULT 0
    );

     */
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_CREATE_CMD =
            String.format("CREATE TABLE `" + TABLE_NAME + "` (\n" +
                    "\t`%s`\tINTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "\t`%s`\tTEXT NOT NULL,\n" +
                    "\t`%s`\tTEXT NOT NULL,\n" +
                    "\t`%s`\tBLOB NOT NULL,\n" +
                    "\t`%s`\tTEXT,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 1,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tTEXT,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0,\n" +
                    "\t`%s`\tINTEGER,\n" +
                    "\t`%s`\tTEXT NOT NULL DEFAULT '0x0',\n" +
                    "\t`%s`\tTEXT,\n" +
                    "\t`%s`\tINTEGER NOT NULL DEFAULT 0\n" +
                    ");", ALL_COLS_BY_ORDER.toArray()
            );


    public VideosDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        //test__dumpDB(context);
    }

    /**
     * Test function for dumping db file on devices w/o root
     *
     * @param context
     */
    private static void test__dumpDB(Context context) {
        File f = context.getDatabasePath("videos.db");
        File[] ff = f.getParentFile().listFiles();
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(f);
            fos = new FileOutputStream("/mnt/sdcard/db_dump.db");
            while (true) {
                int i = fis.read();
                if (i != -1) {
                    fos.write(i);
                } else {
                    break;
                }
            }
            fos.flush();
            Toast.makeText(context, "DB dump OK", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "DB dump ERROR", Toast.LENGTH_LONG).show();
        } finally {
            try {
                fos.close();
                fis.close();
            } catch (IOException ioe) {
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_CREATE_CMD);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        Log.v(TAG, "Upgrading database from version " + i + " to " + i1 + ", which will destroy all old data");
        //TODO [HIGH PRIORITY] for db version 2 should implement table value to value copy instead of dropping!
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
