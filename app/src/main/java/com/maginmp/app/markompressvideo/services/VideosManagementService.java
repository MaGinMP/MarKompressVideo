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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;
import com.maginmp.app.markompressvideo.BuildConfig;
import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.database.VideosDataSource;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;
import com.maginmp.app.markompressvideo.external.MD5;
import com.maginmp.app.markompressvideo.objects.VideoObject;
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

/**
 * Created by MarkGintsburg on 11/12/2016.
 */
public class VideosManagementService extends Service {

    public static final String TEMP_FILE_NAME = "temp.tmp";
    public static final String BROADCAST_RESULT = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_RESULT";
    public static final String BROADCAST_MESSAGE_QUEUE_COUNT = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_MESSAGE_QUEUE_COUNT";
    public static final String BROADCAST_MESSAGE_CURSOR_POS = "com.maginmp.app.markompressvideo.services.VideosManagementService.BROADCAST_MESSAGE_CURSOR_POS";
    public static final String VIDEO_FILE_EXTENSION = ".mp4";
    public static final String BACKUP_FILE_EXTENSION = ".bak";
    private static final String TAG = VideosManagementService.class.getSimpleName();
    private static final int SLEEP_TIME_INTERVALS_GET_METADATA = 100; // 0.1 sec
    private static final long FFMPEG_METADATA_TIMEOUT_MILLIS = 30 * 1000; //30 sec
    private static final int SRC_FILESIZE_MULT = 2;
    private static final HashMap<String, String> X264_CRF_MAP = new HashMap<>();
    private static final HashMap<String, String> AUDIO_QUALITY_MAP = new HashMap<>();
    private static final long FFMPEG_ENCODE_TIMEOUT_MILLIS = ResourcesUtils.hoursToMilis(8); // 8 hours
    private static final int SLEEP_TIME_INTERVALS_ENCODE = 3 * 1000; // 3 sec
    private static final long SLEEP_TIME_INTERVALS_FILE_CLEANUPER = ResourcesUtils.hoursToMilis(0.05f); //3 minutes
    private static final long SLEEP_TIME_INTERVALS_WAIT_FOR_CLEANER_FINISH = 10 * 1000; //10 seconds
    private static final long SLEEP_TIME_CANCELED_WAIT_TO_CLOSE = 2 * 1000; //2 sec.
    private static final int MESSAGE_REFRESH_DATABASE = 0;
    private static final int MESSAGE_START_ENCODING = 1;
    public static boolean IS_SERVICE_RUNNING = false;
    // IS_*_FFMPEG_RUNNING : additional indication is needed in
    // case ffmpeg paused. isFFmpegCommandRunning() doesn't cover this scenario
    public static boolean IS_METADATA_FFMPEG_RUNNING = false;
    public static boolean IS_ENC_FFMPEG_RUNNING = false;
    private static boolean IS_FILECLEANUP_RUNNING = false;
    private static boolean IS_ENCODING = false;
    private static boolean IS_DEVICE_STATE_ENCODING_READY = false;
    private static boolean IS_FFMPEG_CANCELED = false;

    static {
        AUDIO_QUALITY_MAP.put("10", "96kb");
        AUDIO_QUALITY_MAP.put("100", "160kb");

        X264_CRF_MAP.put("10", "24");
        X264_CRF_MAP.put("100", "21");
        X264_CRF_MAP.put("1000", "19");
    }

    public FFmpeg FFMPEG;
    public SharedPreferences mSharedPreferences;
    public OnSharedPreferenceChangeListener mPrefListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(TAG, key + " Preference was changed");

            boolean isPermissionsGranted = PermissionsUtils.CheckPermissionsGrantedSimple(VideosManagementService.this);

            if (key.equals(getString(R.string.keysetting_videos_is_refreshing))) {
                boolean isRefreshing = mSharedPreferences.getBoolean(key, false);
                if (isRefreshing && isPermissionsGranted) {
                    (new VideoManagementAsyncTask(VideosManagementService.this)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, MESSAGE_REFRESH_DATABASE);
                }
            } else if (key.equals(getString(R.string.keysetting_enable_service))) // service was enabled in settings
            {
                ResourcesUtils.deviceEncoderStateUpdater(mSharedPreferences, null, VideosManagementService.this);
            } else if (key.equals(getString(R.string.keysetting_is_device_state_encoding_ready))) {
                IS_DEVICE_STATE_ENCODING_READY = mSharedPreferences.getBoolean(key, false);
                if (IS_DEVICE_STATE_ENCODING_READY && !IS_ENCODING && isPermissionsGranted) // Start encoding
                {
                    NotificationCompat.Builder notification = setFgNotification(getString(R.string.notification_fg_service_on_message));
                    startForeground(MainActivity.NOTIFICATION_ID_FG_SERVICE, notification.build());
                    (new VideoManagementAsyncTask(VideosManagementService.this)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, MESSAGE_START_ENCODING);
                } else if (!IS_DEVICE_STATE_ENCODING_READY && IS_ENCODING) // Cancel encoding
                {
                    IS_FFMPEG_CANCELED = true;
                    FFMPEG.killRunningProcesses();
                    IS_ENC_FFMPEG_RUNNING = false;
                    NotificationCompat.Builder notification = setFgNotification(getString(R.string.notification_fg_service_on_message_cancelling));
                    NotificationManager mNotificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (mNotificationManager != null) {
                        mNotificationManager.notify(MainActivity.NOTIFICATION_ID_FG_SERVICE, notification.build());
                    }
                }
            }
        }
    };
    private int mStartId;
    private FfmpegReadMetadataResponseHandler mFfMetaResHandler = new FfmpegReadMetadataResponseHandler();
    private FfmpegEncResponseHandler mFfEncodeHandler = new FfmpegEncResponseHandler();
    private LocalBroadcastManager mBroadcaster;

    @Override
    public void onCreate() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // in case prev refreshing was interrupted
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (mSharedPreferences.getBoolean(getString(R.string.keysetting_videos_is_refreshing), true)) {
            editor.putBoolean(getString(R.string.keysetting_videos_is_refreshing), false);
        }
        editor.putBoolean(getString(R.string.keysetting_is_device_state_encoding_ready), false);
        editor.commit();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);

        loadFfmpegBinary();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "service starting");
        IS_SERVICE_RUNNING = true;
        ResourcesUtils.deviceEncoderStateUpdater(mSharedPreferences, null, this);
        mStartId = startId;
        (new Thread(new FileCleanuper())).start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        IS_SERVICE_RUNNING = false;
        Log.i(TAG, "service done");
    }

    private NotificationCompat.Builder setFgNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        return
                 new NotificationCompat.Builder(VideosManagementService.this, "M_CH_ID")
                        .setSmallIcon(R.mipmap.icon_mkv)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.icon_mkv))
                        .setContentTitle(StringUtils.getApplicationName(VideosManagementService.this) + " | " + getString(R.string.notification_fg_service_on_title))
                        .setContentIntent(pendingIntent)
                        .setContentText(text);
    }

    private void loadFfmpegBinary() {
        FFMPEG = FFmpeg.getInstance(this);
        try {
            FFMPEG.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    ffmpegUnsupported();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            ffmpegUnsupported();
        }
    }

    private void ffmpegUnsupported() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(getString(R.string.keysetting_is_ffmpeg_supported), false);
        editor.commit();

        FfmpegUtils.showFfmpegUnsupportedNotification(VideosManagementService.this);

        VideosManagementService.this.stopSelf(VideosManagementService.this.mStartId);
    }

    public void broadcastResult(int inQueueCount, int updatedCursorPos) {
        ResourcesUtils.broadcastResult(inQueueCount, updatedCursorPos, mBroadcaster);
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

    private static class VideoManagementAsyncTask extends AsyncTask<Integer, StatusUpdater, ResultUpdater> {
        private WeakReference<VideosManagementService> mServiceReference;
        private int mOperation;

        VideoManagementAsyncTask(VideosManagementService context)
        {
            mServiceReference = new WeakReference<>(context);
        }

        @Override
        protected ResultUpdater doInBackground(Integer... integers) {

            // If file cleanuper is working, wait to finish
            while (IS_FILECLEANUP_RUNNING)
                try {
                    Thread.sleep(SLEEP_TIME_INTERVALS_WAIT_FOR_CLEANER_FINISH);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            ResultUpdater result = new ResultUpdater();
            mOperation = integers[0];
            if (mOperation == MESSAGE_REFRESH_DATABASE || mOperation == MESSAGE_START_ENCODING) {
                refreshDb();
            }
            if (mOperation == MESSAGE_START_ENCODING) {
                result = startEncoding();
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(StatusUpdater... progress) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return;
            StatusUpdater progress0 = progress[0];
            String status = String.format(serviceWeakRef.getString(R.string.notification_fg_service_on_message_clips_status), progress0.idx, progress0.totalOf, progress0.video.getmFile().getName());
            NotificationCompat.Builder notification = serviceWeakRef.setFgNotification(status);
            NotificationManager mNotificationManager =
                    (NotificationManager) serviceWeakRef.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager != null) {
                mNotificationManager.notify(MainActivity.NOTIFICATION_ID_FG_SERVICE, notification.build());
            }
        }

        @Override
        protected void onPostExecute(ResultUpdater result) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return;
            NotificationManager mNotificationManager;
            mNotificationManager = (NotificationManager) serviceWeakRef.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager != null) {
                mNotificationManager.cancel(MainActivity.NOTIFICATION_ID_FG_SERVICE);
            }
        }

        private void refreshDb() {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return;

            CodeStopwatch timer = new CodeStopwatch();

            ArrayList<File> dirsScanAllVideoFiles = FilesUtils.getAllVideoFilesInDirs(serviceWeakRef, serviceWeakRef.mSharedPreferences);
            VideosDataSource videosDataSource = new VideosDataSource(serviceWeakRef);
            videosDataSource.open();


            // Go over all db rows and remove rows if file doesn't exist
            Cursor dbScanAllVideoFiles = videosDataSource.getAllVideos(true, null);
            ArrayList<Long> rowsToRemove = new ArrayList<>();
            while (!dbScanAllVideoFiles.isAfterLast()) {
                String videoPath = dbScanAllVideoFiles.getString(1); //TODO improve design so no explicit dbScanAllVideoFiles.getString(number)
                if (!dirsScanAllVideoFiles.contains(new File(videoPath))) {
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
                        Startup.ERROR_COLLECTOR.addError(err);
                    }

                    try {
                        VideoObject video = new VideoObject(physicalFile, metadata, serviceWeakRef);
                        videosDataSource.addVideo(video);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (count == 1) //video exists in db
                {
                    // place holder. for now, no action required.
                } else {
                    String err = TAG + " Duplicates are not possible";
                    Log.e(TAG, err);
                    Startup.ERROR_COLLECTOR.addError(err);
                }
                dbFindVideo.close();
            }

            // Send broadcast about change in video db to UI
            String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
            Cursor dbFindVideo = videosDataSource.getAllVideos(true, q, "ASC");
            int cursorCount = dbFindVideo.getCount();
            serviceWeakRef.broadcastResult(cursorCount, -1);
            dbFindVideo.close();

            videosDataSource.close();

            Log.i(TAG, "Refresh operation took " + timer.getTimeElapsed() + "millis");

            SharedPreferences.Editor editor = serviceWeakRef.mSharedPreferences.edit();
            editor.putBoolean(serviceWeakRef.getString(R.string.keysetting_videos_is_refreshing), false);
            editor.commit();
        }

        private ResultUpdater startEncoding() {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return null;

            IS_ENCODING = true;

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
                if (video == null || video.getmStatus() == VideosDatabaseHelper.STATUS_ERROR) {
                    encSuc = false;
                } else
                    video.setmStatus(VideosDatabaseHelper.STATUS_DONE);

                // If the encoded file size larger than source: bypass
                if (video != null && video.getmTargetFilesize() > video.getmSourceFilesize())
                {
                    video.setmStatus(VideosDatabaseHelper.STATUS_BYPASS);
                    videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());
                    FilesUtils.delFile(video.getmBackupFile());
                    encSuc = false;
                }

                // If the charger is weak, the device may get discharged even though it is connected
                // to USB and shows charging state. Thus after each ffmpeg run check if the device
                // is in 'encoding state'
                // Update: No need as cleanup thread patch takes care of it.
                // ResourcesUtils.deviceEncoderStateUpdater(mSharedPreferences, null, VideosManagementService.this);


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
                        Startup.ERROR_COLLECTOR.addError(err);
                    }
                    if (encSuc) {
                        // Check if swap is bit exact
                        if (!file1Md5.equals(MD5.calculateMD5(video.getmBackupFile())) || !file2Md5.equals(MD5.calculateMD5(video.getmFile()))) {
                            String err = TAG + " File swap not bit exact: " + video.getmFile().getName() + " <--> " + video.getmBackupFile().getName();
                            Log.e(TAG, err);
                            Startup.ERROR_COLLECTOR.addError(err);
                            // TODO Future task: try to at least revert source.
                            // TODO MD5 of file1 is calculated in encode() think about better design --> no need, too deep (inside FileUtils.constructBackupFile)
                        }
                        //Update DB
                        if (!postEncodeUpdateDb(video, videosDataSource)) {
                            encSuc = false;
                            String err = TAG + " post encode update DB failed: " + video.getmFile().getName();
                            Log.e(TAG, err);
                            Startup.ERROR_COLLECTOR.addError(err);
                        }

                        if (encSuc) {
                            videosDataSource.updateVideoStatus(video.getmStatus(), video.getmId());
                            if (Integer.parseInt(serviceWeakRef.mSharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_file_keep_time), serviceWeakRef.getString(R.string.settings_file_keep_time_def))) == 0) {
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
                serviceWeakRef.broadcastResult(cursorCount - result.countSuc - result.countFail, dbFindVideo.getPosition());

                dbFindVideo.moveToNext();
            }


            dbFindVideo.close();
            videosDataSource.close();
            serviceWeakRef.stopForeground(true);
            IS_ENCODING = false;

            return result;
        }

        private boolean postEncodeUpdateDb(VideoObject video, VideosDataSource videosDataSource) {
            videosDataSource.updateRow(video, videosDataSource);
            return true; //TODO check? if yes, proper approach?
        }


        private VideoObject encode(VideoObject video) {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return null;

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
                    Startup.ERROR_COLLECTOR.addError(err);
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
                    String vqStr = serviceWeakRef.mSharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_video_q), serviceWeakRef.getString(R.string.settings_video_q_def));
                    encCmd.add("-crf");
                    encCmd.add(X264_CRF_MAP.get(vqStr));

                    // Video scale
                    String scaleConditionStr = serviceWeakRef.mSharedPreferences.getString(serviceWeakRef.getString(R.string.keysetting_video_dimen_limit), serviceWeakRef.getString(R.string.settings_video_dimen_limit));
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
                    String audioProcStr = serviceWeakRef.mSharedPreferences.getString(serviceWeakRef.getString(R.string.keysettings_audio_q), serviceWeakRef.getString(R.string.settings_audio_q_def));
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
                        if (!FfmpegUtils.executeFfmpegSafely(serviceWeakRef.FFMPEG, encCmdArr, FFMPEG_ENCODE_TIMEOUT_MILLIS, SLEEP_TIME_INTERVALS_ENCODE, serviceWeakRef.mFfEncodeHandler))
                            failed = true;

                        if (IS_FFMPEG_CANCELED) {
                            try {
                                Thread.sleep(SLEEP_TIME_CANCELED_WAIT_TO_CLOSE);
                            } catch (InterruptedException e) {
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

        /**
         * Read metadata from video file using ffmpeg
         *
         * @param videoFile mp4/ mov file
         * @return metadata
         * @throws IOException
         */
        private String getVideoFileMetadata(File videoFile) throws IOException {
            VideosManagementService serviceWeakRef = mServiceReference.get();
            if (serviceWeakRef == null || !IS_SERVICE_RUNNING) return null;

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
            if (!FfmpegUtils.executeFfmpegSafely(serviceWeakRef.FFMPEG, metadataCmdArr, FFMPEG_METADATA_TIMEOUT_MILLIS, SLEEP_TIME_INTERVALS_GET_METADATA, serviceWeakRef.mFfMetaResHandler))
                return null;
            return FilesUtils.readTextFile(MetadataDestfile);
        }
    }

    public class FfmpegReadMetadataResponseHandler extends ExecuteBinaryResponseHandler {

        @Override
        public void onFailure(String s) {
            String err = TAG + " FFMPEG get metadata failed: " + s;
            Log.e(TAG, err);
            Startup.ERROR_COLLECTOR.addError(err);
            IS_METADATA_FFMPEG_RUNNING = false;
        }

        @Override
        public void onStart() {
            Log.v(TAG, "FFMPEG get metadata Started");
            IS_METADATA_FFMPEG_RUNNING = true;
        }

        @Override
        public void onProgress(String s) {
            Log.v(TAG, "FFMPEG get metadata progress: " + s);
        }

        @Override
        public void onSuccess(String s) {
            Log.v(TAG, "FFMPEG get metadata success: " + s);
            IS_METADATA_FFMPEG_RUNNING = false;
        }

        @Override
        public void onFinish() {
            Log.v(TAG, "FFMPEG get metadata finished");
            IS_METADATA_FFMPEG_RUNNING = false;
        }
    }

    private class FfmpegEncResponseHandler extends ExecuteBinaryResponseHandler {

        @Override
        public void onFailure(String s) {
            String err = TAG + " FFMPEG encode failed: " + s;
            Log.e(TAG, err);
            Startup.ERROR_COLLECTOR.addError(err);
            IS_ENC_FFMPEG_RUNNING = false;
        }

        @Override
        public void onStart() {
            Log.v(TAG, "FFMPEG encode Started");
            IS_ENC_FFMPEG_RUNNING = true;
        }

        @Override
        public void onProgress(String s) {
            Log.v(TAG, "FFMPEG encode progress: " + s);
        }

        @Override
        public void onSuccess(String s) {
            Log.v(TAG, "FFMPEG encode success: " + s);
            IS_ENC_FFMPEG_RUNNING = false;
        }

        @Override
        public void onFinish() {
            Log.v(TAG, "FFMPEG encode finished");
            IS_ENC_FFMPEG_RUNNING = false;
        }

    }


    private class FileCleanuper implements Runnable {
        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {

                //patch
                //Unfortunately not always full battery fires a broadcast, thus the encoding readiness
                //should be checked from time to time.
                ResourcesUtils.deviceEncoderStateUpdater(mSharedPreferences, null, VideosManagementService.this);

                try {
                    Thread.sleep(SLEEP_TIME_INTERVALS_FILE_CLEANUPER);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Don't enter if encoding
                if (IS_ENCODING || mSharedPreferences.getBoolean(getString(R.string.keysetting_videos_is_refreshing), false) || !PermissionsUtils.CheckPermissionsGrantedSimple(VideosManagementService.this))
                    continue;

                IS_FILECLEANUP_RUNNING = true;

                // Find all encoded files that are older than time to keep file and delete orig
                int hoursTokeepFile = Integer.parseInt(mSharedPreferences.getString(getString(R.string.keysetting_file_keep_time), getString(R.string.settings_file_keep_time_def)));
                if (hoursTokeepFile >= 0) {
                    VideosDataSource videosDataSource = new VideosDataSource(VideosManagementService.this);
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
                    ResourcesUtils.broadcastResult(-1, dbFindVideo.getPosition(), mBroadcaster);
                    dbFindVideo.close();

                    videosDataSource.close();
                }

                IS_FILECLEANUP_RUNNING = false;
            }
        }
    }
}