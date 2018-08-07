/*
 * VideosManagementService.java
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

package com.maginmp.app.markompressvideo.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.maginmp.app.markompressvideo.BuildConfig;
import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.database.VideosDataSource;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;
import com.maginmp.app.markompressvideo.external.MD5;
import com.maginmp.app.markompressvideo.objects.VideoObject;
import com.maginmp.app.markompressvideo.system.ErrorCollector;
import com.maginmp.app.markompressvideo.system.Startup;
import com.maginmp.app.markompressvideo.utils.CodeStopwatch;
import com.maginmp.app.markompressvideo.utils.FfmpegUtils;
import com.maginmp.app.markompressvideo.utils.FilesUtils;
import com.maginmp.app.markompressvideo.utils.ImageVideoUtils;
import com.maginmp.app.markompressvideo.utils.PermissionsUtils;
import com.maginmp.app.markompressvideo.utils.ResourcesUtils;
import com.maginmp.app.markompressvideo.utils.StringUtils;
import com.maginmp.app.markompressvideo.utils.ThreadsUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler;
import nl.bravobit.ffmpeg.FFmpeg;
import nl.bravobit.ffmpeg.FFtask;

/**
 * Created by MarkGintsburg on 11/12/2016.
 */
public class VideosManagementService extends JobService {

    public static final String TEMP_FILE_NAME = "temp.tmp";
    public static final String BROADCAST_RESULT = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_RESULT";
    public static final String BROADCAST_MESSAGE_QUEUE_COUNT = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_MESSAGE_QUEUE_COUNT";
    public static final String BROADCAST_MESSAGE_CURSOR_POS = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_MESSAGE_CURSOR_POS";
    private static final String[] BATTERY_ACTION = {"android.intent.action.ACTION_POWER_CONNECTED","android.intent.action.ACTION_POWER_DISCONNECTED","android.intent.action.BATTERY_LOW","android.intent.action.BATTERY_OKAY","android.intent.action.BATTERY_CHANGED"};
    public static final String VIDEO_FILE_EXTENSION = ".mp4";
    public static final String BACKUP_FILE_EXTENSION = ".bak";
    private static final String TAG = VideosManagementService.class.getSimpleName();
    private static final long FFMPEG_METADATA_TIMEOUT_MILLIS = 30 * 1000; //30 sec
    private static final int SRC_FILESIZE_MULT = 2;
    private static final HashMap<String, String> X264_CRF_MAP = new HashMap<>();
    private static final HashMap<String, String> AUDIO_QUALITY_MAP = new HashMap<>();
    private static final long FFMPEG_ENCODE_TIMEOUT_MILLIS = ResourcesUtils.hoursToMilis(8); // 8 hours
    public static final int SLEEP_TIME_INTERVALS_SERVICE_END  = 5 * 60; // 5 minutes
    public static final int SLEEP_TIME_INTERVALS_SERVICE_START  = 1 * 60; // 1 minute
    private static final long SLEEP_TIME_CANCELED_WAIT_TO_CLOSE = 2 * 1000; //2 sec.
    private static final double MINIMUM_CR_TO_REPLACE = 1.1; // if the created file shows an improvement lower than this CR, don't replace
    private static final int MESSAGE_REFRESH_DATABASE = 0;
    private static final int MESSAGE_START_ENCODING = 1;
    private static boolean IS_DEVICE_STATE_ENCODING_READY = false;
    private static boolean IS_FFMPEG_CANCELED = false;
    private static final String JOB_TAG = "service_job_tag";

    static {
        AUDIO_QUALITY_MAP.put("10", "96kb");
        AUDIO_QUALITY_MAP.put("100", "160kb");

        X264_CRF_MAP.put("10", "24");
        X264_CRF_MAP.put("100", "21");
        X264_CRF_MAP.put("1000", "19");
    }

    private static final class StatusUpdater {
        private VideoObject video;
        private int idx;
        private int totalOf;
    }

    private static final class ResultUpdater {
        private int countSuc = 0;
        private int countFail = 0;
    }

    private LocalBroadcastManager mBroadcaster;
    private AsyncTask<Integer, StatusUpdater, ResultUpdater> mTask;

    @Override
    public void onCreate() {
        super.onCreate();

        mBroadcaster = LocalBroadcastManager.getInstance(this);
    }

    public static void schedule(Context context)
    {
        FirebaseJobDispatcher jobDispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        Job job = jobDispatcher.newJobBuilder().
                setService(VideosManagementService.class).
                setLifetime(Lifetime.FOREVER).
                setRecurring(true).
                setTag(JOB_TAG).
                setTrigger(Trigger.executionWindow(SLEEP_TIME_INTERVALS_SERVICE_START,SLEEP_TIME_INTERVALS_SERVICE_END)).
                setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL).
                setReplaceCurrent(false).build();
        jobDispatcher.mustSchedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters job) {
        ErrorCollector.debugToast("entered job", this);
        boolean isPermissionsGranted = PermissionsUtils.CheckPermissionsGrantedSimple(VideosManagementService.this);
        if (!isPermissionsGranted) {
            ErrorCollector.debugToast("no permissions", this);
            return false;
        }
        if (!FFmpeg.getInstance(this).isSupported()) {
            ErrorCollector.debugToast("no ff support", this);
            ffmpegUnsupported();
            return false;
        }

        ErrorCollector.debugToast("starting job", this);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        ResourcesUtils.deviceEncoderStateUpdater(sharedPreferences,null, this);
        IS_DEVICE_STATE_ENCODING_READY = sharedPreferences.getBoolean(getString(R.string.keysetting_is_device_state_encoding_ready), false);
        int msg = IS_DEVICE_STATE_ENCODING_READY ? MESSAGE_START_ENCODING : MESSAGE_REFRESH_DATABASE;
        mTask = new VideoManagementAsyncTask(VideosManagementService.this, job);
        mTask.execute(msg);


        return true;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        ErrorCollector.debugToast("Job finished", this);
        ResourcesUtils.cancelNotification(this, MainActivity.NOTIFICATION_ID_FG_SERVICE);
        return false;
    }

    @Override
    public void onDestroy()
    {
        ErrorCollector.debugToast("Job destroyed", this);
        ResourcesUtils.cancelNotification(this, MainActivity.NOTIFICATION_ID_FG_SERVICE);
        super.onDestroy();
    }

    private NotificationCompat.Builder setFgNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        return
                 new NotificationCompat.Builder(VideosManagementService.this, MainActivity.NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_notification)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_notification))
                        .setContentTitle(StringUtils.getApplicationName(VideosManagementService.this) + " | " + getString(R.string.notification_fg_service_on_title))
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(text));
    }

    private void ffmpegUnsupported() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getString(R.string.keysetting_is_ffmpeg_supported), false);
        editor.commit();

        FfmpegUtils.showFfmpegUnsupportedNotification(VideosManagementService.this);
    }

    public void broadcastResult(int inQueueCount, int updatedCursorPos) {
        ResourcesUtils.broadcastResult(inQueueCount, updatedCursorPos, mBroadcaster);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////



    private static class VideoManagementAsyncTask extends AsyncTask<Integer, StatusUpdater, ResultUpdater> {
        private final WeakReference<VideosManagementService> mServiceReference;
        private int mOperation;
        private final JobParameters mJob;
        final Semaphore mSemaphore = new Semaphore(0, true);

        VideoManagementAsyncTask(VideosManagementService videosManagementService, JobParameters job)
        {
            mServiceReference = new WeakReference<>(videosManagementService);
            mJob = job;
        }

        @Override
        protected ResultUpdater doInBackground(Integer... integers) {

            try {

                fileCleanuper();

                ResultUpdater result = new ResultUpdater();
                mOperation = integers[0];
                if (mOperation == MESSAGE_REFRESH_DATABASE || mOperation == MESSAGE_START_ENCODING) {
                    refreshDb();
                }
                if (mOperation == MESSAGE_START_ENCODING) {
                    result = startEncoding();
                }
                return result;
            } finally {
                VideosManagementService serviceWeakRef = mServiceReference.get();
                if (serviceWeakRef != null)
                    serviceWeakRef.jobFinished(mJob, false);
            }
        }

        @Override
        protected void onProgressUpdate(StatusUpdater... progress) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return;
            Startup.ERROR_COLLECTOR.showNotification(serviceWeakRef.getApplicationContext());
            StatusUpdater progress0 = progress[0];
            String status = serviceWeakRef.getString(R.string.notification_fg_service_on_message_clips_status, progress0.video.getmFile().getName(), progress0.totalOf - progress0.idx);

            NotificationCompat.Builder notification = serviceWeakRef.setFgNotification(status);
            ResourcesUtils.showNotification(serviceWeakRef, MainActivity.NOTIFICATION_ID_FG_SERVICE, notification);
        }

        @Override
        protected void onPostExecute(ResultUpdater result) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return;

            ResourcesUtils.cancelNotification(serviceWeakRef, MainActivity.NOTIFICATION_ID_FG_SERVICE);
            Startup.ERROR_COLLECTOR.showNotification(serviceWeakRef.getApplicationContext());
            serviceWeakRef.jobFinished(mJob,false);
        }

        private void fileCleanuper() {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return;

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(serviceWeakRef);
            // Find all encoded files that are older than time to keep file and delete orig
            int hoursTokeepFile = Integer.parseInt(sharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_file_keep_time), serviceWeakRef.getString(R.string.settings_file_keep_time_def)));
            if (hoursTokeepFile >= 0) {
                VideosDataSource videosDataSource = new VideosDataSource(serviceWeakRef);
                videosDataSource.open();
                Cursor dbFindVideo = videosDataSource.getAllVideos(false, "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_DONE + ")=" + VideosDatabaseHelper.STATUS_DONE + "");
                List<String> cols = VideosDatabaseHelper.ALL_COLS_BY_ORDER;
                long now = System.currentTimeMillis();
                long newest = ResourcesUtils.hoursToMilis(hoursTokeepFile) + 1;

                while (!dbFindVideo.isAfterLast()) {
                    synchronized (ThreadsUtils.SYNC_FILE_OPERATION) {
                        long addedToQueDate = dbFindVideo.getLong(cols.indexOf(VideosDatabaseHelper.COL_ADDED_TO_QUEUE_DATE));
                        long backupCreationDate = dbFindVideo.getLong(cols.indexOf(VideosDatabaseHelper.COL_PROC_DATE));
                        if (addedToQueDate < backupCreationDate && backupCreationDate > newest)
                            newest = backupCreationDate;
                        if (addedToQueDate < backupCreationDate && now - backupCreationDate > ResourcesUtils.hoursToMilis(hoursTokeepFile)) {
                            File buFile = new File(dbFindVideo.getString(cols.indexOf(VideosDatabaseHelper.COL_VIDEO_BU_PATH)));
                            if (buFile.exists()) {
                                FilesUtils.delFile(buFile);
                            }
                            videosDataSource.updateRevertable(false, dbFindVideo.getLong(cols.indexOf(VideosDatabaseHelper.COL_ID)));
                        }
                        dbFindVideo.moveToNext();
                    }
                }

                // If the newest encoded file is more than the keep time it means that
                // the MKV directory shouldn't include bak and mp4 files - so cleanup
                // If app data was clean, then all BU files will be cleaned up
                if (now - newest > ResourcesUtils.hoursToMilis(hoursTokeepFile)) {
                    File fList[] = MainActivity.MKV_DIRECTORY.listFiles();
                    for (int i = 0; i < fList.length; i++) {
                        if (fList[i].getName().endsWith(VIDEO_FILE_EXTENSION) || fList[i].getName().endsWith(BACKUP_FILE_EXTENSION)) {
                            FilesUtils.delFile(fList[i]);
                        }
                    }
                }

                dbFindVideo.close();

                String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
                dbFindVideo = videosDataSource.getAllVideos(true, q);
                //int cursorCount = dbFindVideo.getCount();
                if (serviceWeakRef != null)
                    ResourcesUtils.broadcastResult(-1, dbFindVideo.getPosition(), serviceWeakRef.mBroadcaster);
                dbFindVideo.close();

                videosDataSource.close();
            }

        }

        private void refreshDb() {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return;

            CodeStopwatch timer = new CodeStopwatch();

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(serviceWeakRef.getApplicationContext());
            SharedPreferences.Editor editor;

            ArrayList<File> dirsScanAllVideoFiles = FilesUtils.getAllVideoFilesInDirs(serviceWeakRef, sharedPreferences);
            VideosDataSource videosDataSource = new VideosDataSource(serviceWeakRef);
            videosDataSource.open();


            // Go over all db rows and remove rows if file doesn't exist
            Cursor dbScanAllVideoFiles = videosDataSource.getAllVideos(true, null);
            ArrayList<Long> rowsToRemove = new ArrayList<>();
            while (!dbScanAllVideoFiles.isAfterLast()) {
                // force queue if status is running.
                // If crashes during encoding, not always returns to queue.
                // Should be safe because refresh is sync to encoding
                if (dbScanAllVideoFiles.getInt(VideosDatabaseHelper.COMPACT_COLS_LIST.indexOf(VideosDatabaseHelper.COL_VIDEO_STATUS)) == VideosDatabaseHelper.STATUS_RUNNING) {
                    videosDataSource.updateVideoStatus(VideosDatabaseHelper.STATUS_QUEUE, dbScanAllVideoFiles.getLong(0));
                    if (serviceWeakRef != null) {
                        editor = sharedPreferences.edit();
                        editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing), true);
                        editor.commit();
                    }
                }
                String videoPath = dbScanAllVideoFiles.getString(VideosDatabaseHelper.COMPACT_COLS_LIST.indexOf(VideosDatabaseHelper.COL_VIDEO_PATH));
                if (!dirsScanAllVideoFiles.contains(new File(videoPath))) {

                    if (serviceWeakRef != null) {
                        editor = sharedPreferences.edit();
                        editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing), true);
                        editor.commit();
                    }

                    // remove backup file
                    File backupFile = new File(dbScanAllVideoFiles.getString(2));
                    if (backupFile.exists())
                        FilesUtils.delFile(backupFile);
                    // add to remove from db list
                    rowsToRemove.add(dbScanAllVideoFiles.getLong(0));
                } else // Video is in db so remove from file list to lower iterations in next step
                    dirsScanAllVideoFiles.remove(new File(videoPath));

                dbScanAllVideoFiles.moveToNext();
            }
            if (rowsToRemove.size() > 0)
                videosDataSource.removeVideoByIds(rowsToRemove.toArray(new Long[rowsToRemove.size()]));
            dbScanAllVideoFiles.close();

            // Go over physical files and add to db accordingly
            for (File physicalFile : dirsScanAllVideoFiles) {
                Cursor dbFindVideo = videosDataSource.getAllVideos(false, VideosDatabaseHelper.COL_VIDEO_PATH + "='" + physicalFile.getAbsolutePath() + "'");
                long count = dbFindVideo.getCount();
                if (count == 0) //video does not exist in db: add and check
                {
                    String metadata = null;
                    try {
                        metadata = getVideoFileMetadata(physicalFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (metadata == null) {
                        metadata = "";
                        String err = TAG + " Cannot load metadata: " + physicalFile.getName();
                        Log.e(TAG, err);
                        Startup.ERROR_COLLECTOR.addError(err, null);
                    }

                    try {
                        if (serviceWeakRef != null) {
                            VideoObject video = new VideoObject(physicalFile, metadata, serviceWeakRef);
                            videosDataSource.addVideo(video);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (serviceWeakRef != null) {
                        boolean toggle = !sharedPreferences.getBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing_update), false);
                        editor = sharedPreferences.edit();
                        editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing_update), toggle);
                        editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing), true);
                        editor.commit();
                    }
                } else if (count == 1) //video exists in db
                {
                    // place holder. for now, no action required.
                } else {
                    String err = TAG + " Duplicates are not possible";
                    Log.e(TAG, err);
                    Startup.ERROR_COLLECTOR.addError(err, null);
                }
                dbFindVideo.close();
            }

            // Send broadcast about change in video db to UI
            String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
            Cursor dbFindVideo = videosDataSource.getAllVideos(true, q, "ASC");
            int cursorCount = dbFindVideo.getCount();
            if (serviceWeakRef != null)
                serviceWeakRef.broadcastResult(cursorCount, -1);
            dbFindVideo.close();

            videosDataSource.close();

            Log.i(TAG, "Refresh operation took " + timer.getTimeElapsed() + "millis");

            if (serviceWeakRef != null) {
                editor = sharedPreferences.edit();
                editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing), false);
                editor.putInt(serviceWeakRef.getString(R.string.keysetting_last_refresh_on_num), cursorCount);
                editor.putLong(serviceWeakRef.getString(R.string.keysetting_last_refresh_on_date), System.currentTimeMillis());
                editor.commit();
            }
        }

        private ResultUpdater startEncoding() {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return null;

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(serviceWeakRef);

            ResultUpdater result = new ResultUpdater();

            VideosDataSource videosDataSource = new VideosDataSource(serviceWeakRef);
            videosDataSource.open();

            // Find all video with 'queue' status
            // Also find videos with 'running' status - it indicates that the process was interrupted in prev encodings
            String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
            Cursor dbFindVideo = videosDataSource.getAllVideos(false, q, "ASC");
            Log.v(TAG, q);

            int cursorCount = dbFindVideo.getCount();
            int count = 0;
            boolean encSuc = true;
            IS_FFMPEG_CANCELED = false;
            while (IS_DEVICE_STATE_ENCODING_READY && !isCancelled() && !dbFindVideo.isAfterLast()) {
                StatusUpdater status = new StatusUpdater();
                status.totalOf = cursorCount;

                VideoObject video = new VideoObject(dbFindVideo);
                status.video = video;
                status.idx = ++count;

                publishProgress(status);
                video.setmStatus(VideosDatabaseHelper.STATUS_RUNNING);
                videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());

                if (serviceWeakRef != null)
                    serviceWeakRef.broadcastResult(-1, dbFindVideo.getPosition());

                // Encode
                video = encode(video);

                if (IS_FFMPEG_CANCELED && video != null) {

                    video.setmStatus(VideosDatabaseHelper.STATUS_QUEUE);
                    videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());

                    // Send broadcast about change in video db to UI
                    serviceWeakRef.broadcastResult(cursorCount - result.countSuc - result.countFail, dbFindVideo.getPosition());

                    break;
                }
                if (video == null || video.getmStatus() == VideosDatabaseHelper.STATUS_ERROR || serviceWeakRef == null) {
                    encSuc = false;
                } else
                    video.setmStatus(VideosDatabaseHelper.STATUS_DONE);

                // If the source file to encoded file compression ratio lower than MINIMUM_CR_TO_REPLACE: bypass
                if (video != null && (double)video.getmSourceFilesize()/video.getmTargetFilesize() < MINIMUM_CR_TO_REPLACE)
                {
                    video.setmStatus(VideosDatabaseHelper.STATUS_BYPASS);
                    videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());
                    FilesUtils.delFile(video.getmBackupFile());
                    encSuc = false;
                }


                if (encSuc) {
                    // get MD5
                    String file1Md5 = MD5.calculateMD5(video.getmFile());
                    String file2Md5 = MD5.calculateMD5(video.getmBackupFile());
                    if (file1Md5 == null || file2Md5 == null)
                        encSuc = false;
                    // Swap swap encoded with orig
                    if (!FilesUtils.swapFiles(video)) {
                        encSuc = false;
                        String err = TAG + " File swap failed: " + video.getmFile().getName() + " <--> " + video.getmBackupFile().getName();
                        Log.e(TAG, err);
                        Startup.ERROR_COLLECTOR.addError(err, null);
                    }
                    if (encSuc) {
                        // Check if swap is bit exact
                        if (!file1Md5.equals(MD5.calculateMD5(video.getmBackupFile())) || !file2Md5.equals(MD5.calculateMD5(video.getmFile()))) {
                            String err = TAG + " File swap not bit exact: " + video.getmFile().getName() + " <--> " + video.getmBackupFile().getName();
                            Log.e(TAG, err);
                            Startup.ERROR_COLLECTOR.addError(err, null);
                            // TODO Future task: try to at least revert source.
                            // TODO MD5 of file1 is calculated in encode() think about better design --> no need, too deep (inside FileUtils.constructBackupFile)
                        }
                        //Update DB
                        if (!postEncodeUpdateDb(video, videosDataSource)) {
                            encSuc = false;
                            String err = TAG + " post encode update DB failed: " + video.getmFile().getName();
                            Log.e(TAG, err);
                            Startup.ERROR_COLLECTOR.addError(err, null);
                        }

                        if (encSuc) {
                            videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());
                            // serviceWeakRef !=n because encSuc = true
                            if (Integer.parseInt(sharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_file_keep_time), serviceWeakRef.getString(R.string.settings_file_keep_time_def))) == 0) {
                                // If no keep time -> delete
                                video.setmIsRevetable(false);
                                FilesUtils.delFile(video.getmBackupFile());
                            } else
                                video.setmIsRevetable(true);

                            videosDataSource.updateRevertable(video.ismIsRevetable(), video.getmId());
                        }
                    }
                }

                if (encSuc)
                    result.countSuc++;
                else
                    result.countFail++;

                // Send broadcast about change in video db to UI
                if (serviceWeakRef != null)
                    serviceWeakRef.broadcastResult(cursorCount - result.countSuc - result.countFail, dbFindVideo.getPosition());

                dbFindVideo.moveToNext();
            }


            dbFindVideo.close();
            videosDataSource.close();
            //serviceWeakRef.stopForeground(true);

            return result;
        }

        private VideoObject encode(VideoObject video) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return null;

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(serviceWeakRef);

            boolean failed = false;

            if (MainActivity.MKV_DIRECTORY.getFreeSpace() > SRC_FILESIZE_MULT * video.getmSourceFilesize()) {
                ArrayList<String> encCmd = new ArrayList<>();

                encCmd.add("-noautorotate"); //Disable physical rotation caused by metadata matrix

                //source file
                encCmd.add("-i");
                try {
                    encCmd.add(video.getmFile().getCanonicalPath());
                } catch (IOException e) {
                    e.printStackTrace();
                    String err = TAG + " cannot get canonical path: " + video.getmFile().getAbsolutePath();
                    Log.e(TAG, err);
                    Startup.ERROR_COLLECTOR.addError(err, null);
                    failed = true;
                }

                if (!failed) {
                    // video codec x264
                    encCmd.add("-c:v");
                    encCmd.add("libx264");

                    // slow preset
                    encCmd.add("-preset");
                    encCmd.add("slow");

                    // Video quality
                    String vqStr = sharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_video_q), serviceWeakRef.getString(R.string.settings_video_q_def));
                    encCmd.add("-crf");
                    encCmd.add(X264_CRF_MAP.get(vqStr));

                    // Video scale
                    String scaleConditionStr = sharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_video_dimen_limit), serviceWeakRef.getString(R.string.settings_video_dimen_limit));
                    int scaleCondition = Integer.parseInt(scaleConditionStr);
                    if (video.getmSourceHeight() > scaleCondition) {
                        encCmd.add("-vf");
                        encCmd.add("scale=-1:" + scaleCondition); //keep AR
                        encCmd.add("-sws_flags");
                        encCmd.add("lanczos");

                        // If scaling down, use lower crf -> higher quality
                        int crf_idx = encCmd.indexOf(X264_CRF_MAP.get(vqStr));
                        encCmd.set(crf_idx, Integer.toString(Integer.parseInt(X264_CRF_MAP.get(vqStr)) - 1));
                    }

                    // Audio stream copy / process
                    String audioProcStr = sharedPreferences.getString(serviceWeakRef.getString(R.string.keysettings_audio_q), serviceWeakRef.getString(R.string.settings_audio_q_def));
                    int audioProc = Integer.parseInt(audioProcStr);
                    if (audioProc < 0) {
                        encCmd.add("-c:a");
                        encCmd.add("copy");
                    } else {
                        encCmd.add("-c:a");
                        encCmd.add("aac");
                        encCmd.add("-b:a");
                        encCmd.add(AUDIO_QUALITY_MAP.get(audioProcStr));
                    }

                    encCmd.add("-y"); //silently overwrite

                    encCmd.add("-vsync");
                    encCmd.add("0");

                    encCmd.add("-async");
                    encCmd.add("1");

                    //Embed MKV metadata to video
                    video.setmFfmpegCmd(TextUtils.join(" ", encCmd));
                    encCmd.add("-metadata");
                    encCmd.add("comment=" + video.toString());

                    //output file path
                    File out = new File(MainActivity.MKV_DIRECTORY, video.getmFile().getName());
                    try {
                        encCmd.add(out.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                        failed = true;
                    }

                    if (!out.getParentFile().canWrite())
                        failed = true;

                    if (!failed) {
                        String[] encCmdArr = encCmd.toArray(new String[encCmd.size()]);

                        CodeStopwatch stopWatch = new CodeStopwatch();

                        FFmpeg ff = FFmpeg.getInstance(serviceWeakRef);
                        ff.setTimeout(FFMPEG_ENCODE_TIMEOUT_MILLIS);
                        FfmpegEncResponseHandler handler = new FfmpegEncResponseHandler();
                        handler.setContext(serviceWeakRef);
                        FFtask task = ff.execute(encCmdArr,handler);
                        handler.setTask(task);
                        Log.v(TAG, "enc fun Lock acquiring. Available permits: " + mSemaphore.availablePermits());
                        try {
                            mSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mSemaphore.drainPermits(); // just for safety
                        Log.v(TAG, "enc fun Lock released. Available permits: " + mSemaphore.availablePermits());

                        if (IS_FFMPEG_CANCELED) {
                            try {
                                Thread.sleep(SLEEP_TIME_CANCELED_WAIT_TO_CLOSE);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                e.printStackTrace();
                            }
                            FilesUtils.delFile(out);
                            video.setmStatus(VideosDatabaseHelper.STATUS_QUEUE);
                            return video;
                        }

                        if (!failed) {
                            File outWithBakExt = FilesUtils.constructBackupFile(out);
                            out.renameTo(outWithBakExt);
                            video.setmFfmpegCmd(TextUtils.join(" ", encCmd));
                            video.setmProcessedDate(new Date(System.currentTimeMillis()));
                            video.setmAppVersion(BuildConfig.VERSION_CODE);
                            video.setmBackupFile(outWithBakExt);
                            video.setmEncodeTime(stopWatch.getTimeElapsed());
                            video.setmTargetFilesize(out.length());
                            int[] wxhxlen = ImageVideoUtils.getWidthHeightFromVideo(outWithBakExt);
                            video.setmTargetWidth(wxhxlen[0]);
                            video.setmTargetHeight(wxhxlen[1]);
                            video.setmTargetFilesize(outWithBakExt.length());
                        }
                    }
                }
                if (failed)
                    video.setmStatus(VideosDatabaseHelper.STATUS_ERROR);

                return video;
            }

            return null;
        }

        private boolean postEncodeUpdateDb(VideoObject video, VideosDataSource videosDataSource) {
            videosDataSource.updateRow(video, videosDataSource);
            return true; //TODO check? if yes, proper approach?
        }

        /**
         * Read metadata from video file using ffmpeg
         *
         * @param videoFile mp4/ mov file
         * @return metadata
         * @throws IOException
         */
        private String getVideoFileMetadata(File videoFile) throws IOException {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null) return null;

            FilesUtils.mkDirIfNotExist(MainActivity.MKV_DIRECTORY);
            File MetadataDestfile = new File(MainActivity.MKV_DIRECTORY, "metadata.txt");

            ArrayList<String> metadataCmd = new ArrayList<>();
            metadataCmd.add("-i");
            metadataCmd.add(videoFile.getCanonicalPath());
            metadataCmd.add("-f");
            metadataCmd.add("ffmetadata");
            metadataCmd.add("-y");
            metadataCmd.add(MetadataDestfile.getCanonicalPath());
            String[] metadataCmdArr = metadataCmd.toArray(new String[metadataCmd.size()]);

            FFmpeg ff = FFmpeg.getInstance(serviceWeakRef);
            ff.setTimeout(FFMPEG_METADATA_TIMEOUT_MILLIS);
            FFtask task = ff.execute(metadataCmdArr,new FfmpegReadMetadataResponseHandler());
            Log.v(TAG, "metadata fun Lock acquiring. Available permits: " + mSemaphore.availablePermits());
            try {
                mSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mSemaphore.drainPermits(); // just for safety
            Log.v(TAG, "metadata fun Lock released. Available permits: " + mSemaphore.availablePermits());

            return FilesUtils.readTextFile(MetadataDestfile);
        }

        public class FfmpegReadMetadataResponseHandler extends ExecuteBinaryResponseHandler {

            @Override
            public void onFailure(String s) {
                String err = TAG + " FFMPEG get metadata failed: " + s;
                Log.e(TAG, err);
                Startup.ERROR_COLLECTOR.addError(err, null);
            }

            @Override
            public void onStart() {
                Log.v(TAG, "FFMPEG get metadata Started");
            }

            @Override
            public void onProgress(String s) {
                Log.v(TAG, "FFMPEG get metadata progress: " + s);
            }

            @Override
            public void onSuccess(String s) {
                Log.v(TAG, "FFMPEG get metadata success: " + s);
            }

            @Override
            public void onFinish() {
                Log.v(TAG, "FFMPEG get metadata finished");
                mSemaphore.release();
                Log.v(TAG, "ff meta Lock released. Available permits: " + mSemaphore.availablePermits());
            }
        }

        private class FfmpegEncResponseHandler extends ExecuteBinaryResponseHandler {


            private FFtask mTask;
            private Context mContext;

            public void setTask(final FFtask task)
            {
                mTask = task;
            }


            @Override
            public void onFailure(String s) {
                if (!IS_FFMPEG_CANCELED) {
                    String err = TAG + " FFMPEG encode failed: " + s;
                    Log.e(TAG, err);
                    Startup.ERROR_COLLECTOR.addError(err, null);
                }
            }

            @Override
            public void onStart() {
                Log.v(TAG, "FFMPEG encode Started");
            }

            @Override
            public void onProgress(String s) {
                Log.v(TAG, "FFMPEG encode progress: " + s);
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                ResourcesUtils.deviceEncoderStateUpdater(sharedPreferences,null, mContext);
                IS_DEVICE_STATE_ENCODING_READY = sharedPreferences.getBoolean(mContext.getString(R.string.keysetting_is_device_state_encoding_ready), false);

                if (!IS_DEVICE_STATE_ENCODING_READY && mTask != null)
                {
                    IS_FFMPEG_CANCELED = true;
                    mTask.sendQuitSignal();
                }
            }

            @Override
            public void onSuccess(String s) {
                Log.v(TAG, "FFMPEG encode success: " + s);
            }

            @Override
            public void onFinish() {
                Log.v(TAG, "FFMPEG encode finished");
                mSemaphore.release();
                Log.v(TAG, "ff enc Lock released. Available permits: " + mSemaphore.availablePermits());
            }

            public void setContext(Context context)
            {
                mContext = context;
            }
        }
    }
}