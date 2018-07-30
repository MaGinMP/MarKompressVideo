/*
 * VideoObject.java
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

package com.maginmp.app.markompressvideo.objects;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.maginmp.app.markompressvideo.BuildConfig;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;
import com.maginmp.app.markompressvideo.utils.FilesUtils;
import com.maginmp.app.markompressvideo.utils.ImageVideoUtils;
import com.maginmp.app.markompressvideo.utils.StringUtils;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * Created by MarkGintsburg on 08/05/2017.
 */

public class VideoObject {

    private long mId;
    private File mFile;
    private File mBackupFile;
    private byte[] mThumbnail;

    private int mSourceWidth;
    private int mSourceHeight;
    private int mTargetWidth;
    private int mTargetHeight;

    private long mSourceFilesize;
    private long mTargetFilesize;
    private long mDurationMilisec;

    private String mFfmpegCmd;
    private int mAppVersion;
    private boolean mIsRevetable;
    private long mEncodeTime;
    private boolean mIsBypassProc;
    private boolean mIsShowInList;

    private Date mAddedToQueueDate;
    private Date mProcessedDate;
    private int mStatus; // According to STATUS_* from VideoDatabaseHelper

    // Keys that will be embed in mp4 comment
    private static final String[] KEYS_TO_CONVERT = {VideosDatabaseHelper.COL_VIDEO_BU_PATH, VideosDatabaseHelper.COL_ORIG_VIDEO_DIMEN,
            VideosDatabaseHelper.COL_ORIG_FILE_SIZE, VideosDatabaseHelper.COL_ENCODE_TIME,
            VideosDatabaseHelper.COL_FFMPEG_CMD, VideosDatabaseHelper.COL_APP_VERSION,
            VideosDatabaseHelper.COL_PROC_DATE};

    public VideoObject(long mId, File mFile, File mBackupFile, byte[] mThumbnail, int mSourceWidth, int mSourceHeight, int mTargetWidth, int mTargetHeight, long mSourceFilesize, long mTargetFilesize, long mDurationMilisec, int mStatus) {
        this.mId = mId;
        this.mFile = mFile;
        this.mBackupFile = mBackupFile;
        this.mThumbnail = mThumbnail;
        this.mSourceWidth = mSourceWidth;
        this.mSourceHeight = mSourceHeight;
        this.mTargetWidth = mTargetWidth;
        this.mTargetHeight = mTargetHeight;
        this.mSourceFilesize = mSourceFilesize;
        this.mTargetFilesize = mTargetFilesize;
        this.mDurationMilisec = mDurationMilisec;
        this.mStatus = mStatus;
    }

    public VideoObject(File mFile, String metadata, Context context) throws Exception {

        ContentValues cvMetadata = null;

        // metadata extract
        if (metadata != null) {
            String commentStr = "comment=";
            //substring from the end of 'comment=' till the last ;; after 'comment='
            if (metadata.toLowerCase().contains(commentStr.toLowerCase())) {
                String commentSection = metadata.substring(
                        metadata.toLowerCase().indexOf(commentStr.toLowerCase()) + commentStr.length(),
                        metadata.toLowerCase().lastIndexOf(
                                MainActivity.MKV_METADATA_DELIMITER_READ));
                cvMetadata = StringUtils.StringToContentValues(commentSection, VideoObject.KEYS_TO_CONVERT);
            }
        }

        this.mId = -1; // temp vid file

        if (!mFile.exists())
            throw new Exception("Cannot create video from File. Reason: File does not exist");
        this.mFile = mFile;

        this.mThumbnail = ImageVideoUtils.getJpegThumbFromVideo(mFile);

        this.mBackupFile = FilesUtils.constructBackupFile(mFile);


        this.mIsRevetable = mBackupFile.exists();


        int[] wxhxlen = ImageVideoUtils.getWidthHeightFromVideo(mFile);
        this.mDurationMilisec = wxhxlen[2];
        this.mSourceWidth = wxhxlen[0];
        this.mSourceHeight = wxhxlen[1];
        this.mTargetWidth = wxhxlen[0];
        this.mTargetHeight = wxhxlen[1];

        this.mSourceFilesize = mFile.length();
        this.mTargetFilesize = mFile.length();


        this.mStatus = VideosDatabaseHelper.STATUS_QUEUE;

        this.mEncodeTime = 0;
        this.mFfmpegCmd = "NULL";

        this.mAppVersion = BuildConfig.VERSION_CODE;

        Long now = System.currentTimeMillis();
        this.mAddedToQueueDate = new Date(now);
        this.mProcessedDate = new Date(now - 1); // for sanity check later

        if (cvMetadata != null) {
            // If the video has MKV stamp, it means it was already precessed in the past
            // and maybe db was erased or the video was removed and brought back later.
            // Mark as "done"


            // check if metadata points to different backup file
            // done for back compatibility
            if (!this.mIsRevetable) {
                String bu = cvMetadata.getAsString(VideosDatabaseHelper.COL_VIDEO_BU_PATH);
                if (bu != null) {
                    File buF = new File(bu);
                    this.mIsRevetable = buF.exists();
                    mBackupFile = buF;
                }
            }

            if (!this.mIsRevetable) {
                this.mSourceWidth = 0;
                this.mSourceHeight = 0;
                this.mSourceFilesize = -1;
            } else {
                wxhxlen = ImageVideoUtils.getWidthHeightFromVideo(mBackupFile);
                this.mSourceWidth = wxhxlen[0];
                this.mSourceHeight = wxhxlen[1];
                this.mSourceFilesize = mBackupFile.length();
            }


            if (!this.mIsRevetable) {
                String dimen = cvMetadata.getAsString(VideosDatabaseHelper.COL_ORIG_VIDEO_DIMEN);
                if (dimen != null) {
                    int[] wxh = StringUtils.dimenToWidthAndHeight(dimen);
                    this.mSourceWidth = wxh[0];
                    this.mSourceHeight = wxh[1];
                }
                String fSize = cvMetadata.getAsString(VideosDatabaseHelper.COL_ORIG_FILE_SIZE);
                if (fSize != null) {
                    this.mSourceFilesize = Long.parseLong(fSize);
                }
            }

            String tmpStr = cvMetadata.getAsString(VideosDatabaseHelper.COL_ENCODE_TIME);
            if (tmpStr != null) {
                this.mEncodeTime = Long.parseLong(tmpStr);
            }

            tmpStr = cvMetadata.getAsString(VideosDatabaseHelper.COL_FFMPEG_CMD);
            if (tmpStr != null) {
                this.mFfmpegCmd = tmpStr;
            }

            tmpStr = cvMetadata.getAsString(VideosDatabaseHelper.COL_APP_VERSION);
            if (tmpStr != null) {
                this.mAppVersion = Integer.parseInt(tmpStr);
            }

            tmpStr = cvMetadata.getAsString(VideosDatabaseHelper.COL_PROC_DATE);
            if (tmpStr != null) {
                this.mProcessedDate = new Date(Long.parseLong(tmpStr));
            }

            this.mStatus = VideosDatabaseHelper.STATUS_DONE;

        }

        this.mIsBypassProc = (mStatus & (VideosDatabaseHelper.STATUS_BYPASS | VideosDatabaseHelper.STATUS_DELETED | VideosDatabaseHelper.STATUS_DONE | VideosDatabaseHelper.STATUS_REVERTED | VideosDatabaseHelper.STATUS_ERROR)) != 0;
        this.mIsShowInList = (mStatus & VideosDatabaseHelper.STATUS_DELETED) != 0;

        if ((this.mStatus & VideosDatabaseHelper.STATUS_DONE) == VideosDatabaseHelper.STATUS_DONE) {
            Date dateTmp = new Date(mFile.lastModified());
            if (dateTmp.before(this.mProcessedDate))
                this.mProcessedDate = dateTmp;
        }
    }


    public VideoObject(Cursor cursor) {
        List<String> cols = VideosDatabaseHelper.ALL_COLS_BY_ORDER;
        this.mId = cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_ID));
        this.mFile = new File(cursor.getString(cols.indexOf(VideosDatabaseHelper.COL_VIDEO_PATH)));
        this.mThumbnail = cursor.getBlob(cols.indexOf(VideosDatabaseHelper.COL_THUMB_BLOB));
        this.mBackupFile = new File(cursor.getString(cols.indexOf(VideosDatabaseHelper.COL_VIDEO_BU_PATH)));
        int[] wxh = StringUtils.dimenToWidthAndHeight(cursor.getString(cols.indexOf(VideosDatabaseHelper.COL_ORIG_VIDEO_DIMEN)));
        this.mSourceWidth = wxh[0];
        this.mSourceHeight = wxh[1];
        wxh = StringUtils.dimenToWidthAndHeight(cursor.getString(cols.indexOf(VideosDatabaseHelper.COL_PROC_VIDEO_DIMEN)));
        this.mTargetWidth = wxh[0];
        this.mTargetHeight = wxh[1];
        this.mSourceFilesize = cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_ORIG_FILE_SIZE));
        this.mTargetFilesize = cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_PROC_FILE_SIZE));
        this.mDurationMilisec = cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_VIDEO_DURATION));
        this.mStatus = cursor.getInt(cols.indexOf(VideosDatabaseHelper.COL_VIDEO_STATUS));
        this.mEncodeTime = cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_ENCODE_TIME));
        this.mFfmpegCmd = cursor.getString(cols.indexOf(VideosDatabaseHelper.COL_FFMPEG_CMD));
        this.mAppVersion = cursor.getInt(cols.indexOf(VideosDatabaseHelper.COL_APP_VERSION));
        this.mIsRevetable = cursor.getInt(cols.indexOf(VideosDatabaseHelper.COL_IS_REVERTABLE)) == 1 && this.mBackupFile.exists();
        this.mIsBypassProc = cursor.getInt(cols.indexOf(VideosDatabaseHelper.COL_IS_BYPASS_PROC)) == 1;
        this.mIsShowInList = cursor.getInt(cols.indexOf(VideosDatabaseHelper.COL_IS_SHOW_IN_LIST)) == 1;
        this.mAddedToQueueDate = new Date(cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_ADDED_TO_QUEUE_DATE)));
        this.mProcessedDate = new Date(cursor.getLong(cols.indexOf(VideosDatabaseHelper.COL_PROC_DATE)));
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(VideosDatabaseHelper.COL_VIDEO_PATH, mFile.getAbsolutePath());
        values.put(VideosDatabaseHelper.COL_VIDEO_NAME, mFile.getName());
        values.put(VideosDatabaseHelper.COL_THUMB_BLOB, mThumbnail);
        values.put(VideosDatabaseHelper.COL_VIDEO_BU_PATH, mBackupFile.getAbsolutePath());
        values.put(VideosDatabaseHelper.COL_ORIG_VIDEO_DIMEN, StringUtils.widthAndHeightToDimen(mSourceWidth, mSourceHeight));
        values.put(VideosDatabaseHelper.COL_PROC_VIDEO_DIMEN, StringUtils.widthAndHeightToDimen(mTargetWidth, mTargetHeight));
        values.put(VideosDatabaseHelper.COL_ORIG_FILE_SIZE, mSourceFilesize);
        values.put(VideosDatabaseHelper.COL_PROC_FILE_SIZE, mTargetFilesize);
        values.put(VideosDatabaseHelper.COL_VIDEO_DURATION, mDurationMilisec);
        values.put(VideosDatabaseHelper.COL_VIDEO_STATUS, mStatus);
        values.put(VideosDatabaseHelper.COL_ENCODE_TIME, mEncodeTime);
        values.put(VideosDatabaseHelper.COL_FFMPEG_CMD, mFfmpegCmd);
        values.put(VideosDatabaseHelper.COL_APP_VERSION, mAppVersion);
        values.put(VideosDatabaseHelper.COL_IS_REVERTABLE, mIsRevetable ? 1 : 0);
        values.put(VideosDatabaseHelper.COL_IS_BYPASS_PROC, mIsBypassProc ? 1 : 0);
        values.put(VideosDatabaseHelper.COL_IS_SHOW_IN_LIST, mIsShowInList ? 1 : 0);
        values.put(VideosDatabaseHelper.COL_ADDED_TO_QUEUE_DATE, mAddedToQueueDate.getTime());
        values.put(VideosDatabaseHelper.COL_PROC_DATE, mProcessedDate.getTime());
        return values;
    }

    @Override
    public String toString() {

        return StringUtils.contentValuesToString(this.toContentValues(), VideoObject.KEYS_TO_CONVERT);

    }

    public File getmFile() {
        return mFile;
    }

    public void setmFile(File mFile) {
        this.mFile = mFile;
    }

    public File getmBackupFile() {
        return mBackupFile;
    }

    public void setmBackupFile(File mBackupFile) {
        this.mBackupFile = mBackupFile;
    }

    public byte[] getmThumbnail() {
        return mThumbnail;
    }

    public void setmThumbnail(byte[] mThumbnailPath) {
        this.mThumbnail = mThumbnailPath;
    }

    public int getmSourceWidth() {
        return mSourceWidth;
    }

    public void setmSourceWidth(int mSourceWidth) {
        this.mSourceWidth = mSourceWidth;
    }

    public int getmSourceHeight() {
        return mSourceHeight;
    }

    public void setmSourceHeight(int mSourceHeight) {
        this.mSourceHeight = mSourceHeight;
    }

    public int getmTargetWidth() {
        return mTargetWidth;
    }

    public void setmTargetWidth(int mTargetWidth) {
        this.mTargetWidth = mTargetWidth;
    }

    public int getmTargetHeight() {
        return mTargetHeight;
    }

    public void setmTargetHeight(int mTargetHeight) {
        this.mTargetHeight = mTargetHeight;
    }

    public long getmSourceFilesize() {
        return mSourceFilesize;
    }

    public void setmSourceFilesize(long mSourceFilesize) {
        this.mSourceFilesize = mSourceFilesize;
    }

    public long getmTargetFilesize() {
        return mTargetFilesize;
    }

    public void setmTargetFilesize(long mTargetFilesize) {
        this.mTargetFilesize = mTargetFilesize;
    }

    public long getmDurationMilisec() {
        return mDurationMilisec;
    }

    public void setmDurationMilisec(long mDurationMilisec) {
        this.mDurationMilisec = mDurationMilisec;
    }

    public int getmStatus() {
        return mStatus;
    }

    public void setmStatus(int mStatus) {
        this.mStatus = mStatus;
    }

    public long getmId() {
        return mId;
    }

    public void setmId(long mId) {
        this.mId = mId;
    }


    public int getmAppVersion() {
        return mAppVersion;
    }

    public void setmAppVersion(int mAppVersion) {
        this.mAppVersion = mAppVersion;
    }

    public boolean ismIsRevetable() {
        return mIsRevetable && getmBackupFile().exists();
    }

    public void setmIsRevetable(boolean mIsRevetable) {
        this.mIsRevetable = mIsRevetable;
    }

    public long getmEncodeTime() {
        return mEncodeTime;
    }

    public void setmEncodeTime(long mEncodeTime) {
        this.mEncodeTime = mEncodeTime;
    }

    public Date getmAddedToQueueDate() {
        return mAddedToQueueDate;
    }

    public void setmAddedToQueueDate(Date mAddedToQueueDate) {
        this.mAddedToQueueDate = mAddedToQueueDate;
    }

    public Date getmProcessedDate() {
        return mProcessedDate;
    }

    public void setmProcessedDate(Date mProcessedDate) {
        this.mProcessedDate = mProcessedDate;
    }

    public String getmFfmpegCmd() {
        return mFfmpegCmd;
    }

    public void setmFfmpegCmd(String mFfmpegCmd) {
        this.mFfmpegCmd = mFfmpegCmd;
    }
}
