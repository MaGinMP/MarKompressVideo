/*
 * ImageVideoUtils.java
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

package com.maginmp.app.markompressvideo.utils;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class ImageVideoUtils {

    private static final String TAG = ImageVideoUtils.class.getSimpleName();

    /**
     * Generates a thumbnail of a video file
     *
     * @param video video file
     * @return byte array of jpg
     */
    public static byte[] getJpegThumbFromVideo(File video) {
        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(video.getAbsolutePath(),
                MediaStore.Images.Thumbnails.MINI_KIND);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.JPEG, 60, stream);
        return stream.toByteArray();
    }

    /**
     * Extract the width height and video duration of video file
     *
     * @param video video file
     * @return [w, h, duration milis]
     */
    public static int[] getWidthHeightFromVideo(File video) {
        int[] res = new int[3];
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(video.getAbsolutePath());
        res[0] = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        res[1] = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        res[2] = Integer.parseInt(time); // int is good for 25 days of video
        retriever.release();

        return res;
    }
}
