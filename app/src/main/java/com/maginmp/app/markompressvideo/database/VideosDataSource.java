/*
 * VideosDataSource.java
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.maginmp.app.markompressvideo.objects.VideoObject;
import com.maginmp.app.markompressvideo.system.ErrorCollector;
import com.maginmp.app.markompressvideo.system.Startup;

import java.util.Arrays;
import java.util.List;

/**
 * Created by MarkGintsburg on 21/05/2017.
 */

public class VideosDataSource {
    private static final String TAG = VideosDataSource.class.getSimpleName();
    private SQLiteDatabase mDatabase;
    private VideosDatabaseHelper mDbHelper;
    private Context mContext;

    public VideosDataSource(Context context) {
        mDbHelper = VideosDatabaseHelper.getInstance(context);
        mContext = context;
    }

    public void open() throws SQLException {
        mDatabase = mDbHelper.getWritableDatabase();
    }

    public void close() {
        ErrorCollector.debugLog(TAG, "close() was called, but connection will not be killed");
        //mDbHelper.close();
    }

    /**
     * Add video object to DB
     *
     * @param video the video object to add
     */
    public void addVideo(VideoObject video) {
        ContentValues values = video.toContentValues();
        long insertId = mDatabase.insert(VideosDatabaseHelper.TABLE_NAME, null, values);

        if (insertId < 1) {
            String err = TAG + " Add video to DB failed: " + video.getmFile().getName();
            Log.e(TAG, err);
            Startup.ERROR_COLLECTOR.addError(err, mContext);
        }

    }

    /**
     * Remove a single video from DB. If possible to gather several videos at once, use @removeVideoByIds
     *
     * @param video video to remove
     */
    public void removeVideo(VideoObject video) {
        long id = video.getmId();
        long deletedCount = mDatabase.delete(VideosDatabaseHelper.TABLE_NAME, VideosDatabaseHelper.COL_ID + " = " + id, null);
        if (deletedCount <= 0) {
            String err = TAG + " Remove video from DB failed: " + video.getmFile().getName();
            Log.e(TAG, err);
            Startup.ERROR_COLLECTOR.addError(err, mContext);
        }
    }

    /**
     * Remove the videos having these ids
     *
     * @param ids video object ids array
     */
    public void removeVideoByIds(Long[] ids) {
        // _id IN (?,?,...,?)
        String qm = "(" + new String(new char[ids.length-1]).replace("\0", "?,") + "?)";
        long deletedCount = mDatabase.delete(VideosDatabaseHelper.TABLE_NAME, VideosDatabaseHelper.COL_ID + " IN " + qm, Arrays.toString(ids).split("[\\[\\]]")[1].split(", "));
        if (deletedCount != ids.length) {
            String err = TAG + " Remove video by ids from DB failed, ids.len=" + ids.length;
            Log.e(TAG, err);
            Startup.ERROR_COLLECTOR.addError(err, mContext);
        }
    }

    /**
     * Read all videos from DB. see @getAllVideos(boolean isGetOnlyPathCols, String rowsSelection, String DescAsc) for more info.
     *
     * @param isGetOnlyPathCols
     * @param rowsSelection
     * @return
     */
    public Cursor getAllVideos(boolean isGetOnlyPathCols, String rowsSelection) {
        return getAllVideos(isGetOnlyPathCols, rowsSelection, "DESC");
    }

    /**
     * Reads all video from db by the following criteria
     *
     * @param isGetOnlyPathCols read only id, path and backup path. Column indexing changes to [0..2] correspondingly
     * @param rowsSelection     sql criteria for row selection
     * @param DescAsc           DESC or ASC ordering
     * @return the corresponding cursor
     */
    public Cursor getAllVideos(boolean isGetOnlyPathCols, String rowsSelection, String DescAsc) {
        String[] cols;
        List<String> colsList;
        if (isGetOnlyPathCols)
            colsList = VideosDatabaseHelper.COMPACT_COLS_LIST;
        else
            colsList = VideosDatabaseHelper.ALL_COLS_BY_ORDER;
        cols = colsList.toArray(new String[colsList.size()]);

        Cursor videos = mDatabase.query(VideosDatabaseHelper.TABLE_NAME,
                cols, rowsSelection, null, null, null, VideosDatabaseHelper.COL_ID + " " + DescAsc.toUpperCase());

        videos.moveToFirst();
        ErrorCollector.debugLog(TAG, "Read " + videos.getCount() + " videos from database");
        return videos;
    }

    /**
     * Update video status in DB
     *
     * @param status status const
     * @param id     video id
     */
    public void updateVideoStatus(int status, long id) {
        ContentValues values = new ContentValues();
        values.put(VideosDatabaseHelper.COL_VIDEO_STATUS, status);

        mDatabase.update(VideosDatabaseHelper.TABLE_NAME, values, VideosDatabaseHelper.COL_ID + " = " + id, null);
    }

    /**
     * Update the row in DB according to video object
     *
     * @param video            video
     * @param videosDataSource not sure if needed...
     */
    public void updateRow(VideoObject video, VideosDataSource videosDataSource) {
        mDatabase.update(VideosDatabaseHelper.TABLE_NAME, video.toContentValues(), VideosDatabaseHelper.COL_ID + " = " + video.getmId(), null);
    }


    /**
     * Update video's revertable state in DB
     *
     * @param b  is revertable
     * @param id video id
     */
    public void updateRevertable(boolean b, long id) {
        ContentValues values = new ContentValues();
        values.put(VideosDatabaseHelper.COL_IS_REVERTABLE, b ? 1 : 0);

        mDatabase.update(VideosDatabaseHelper.TABLE_NAME, values, VideosDatabaseHelper.COL_ID + " = " + id, null);
    }

    /**
     * Get videos count by status const
     *
     * @param status
     * @return
     */
    public int getCount(int status) {
        Cursor c = getAllVideos(true, "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + status + ")=" + status + "");
        int count = c.getCount();
        c.close();
        return count;
    }
}
