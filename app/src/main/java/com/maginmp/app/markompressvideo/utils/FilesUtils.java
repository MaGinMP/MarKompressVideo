/*
 * FilesUtils.java
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.external.MD5;
import com.maginmp.app.markompressvideo.objects.VideoObject;
import com.maginmp.app.markompressvideo.services.VideosManagementService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class FilesUtils {

    private static final String TAG = FilesUtils.class.getSimpleName();

    /**
     * List of all level one subdirs in a given dir
     *
     * @param dir Directory file
     * @return an array of level one subdirs names (not full path)
     */
    public static CharSequence[] listAllDirectories(File dir) {
        File[] subdirs = dir.listFiles();

        if (subdirs == null)
            return null;

        CharSequence[] names = new CharSequence[subdirs.length];
        for (int i = 0; i < subdirs.length; i++) {
            names[i] = subdirs[i].getName();
        }

        return names;
    }

    /**
     * List all files in given dirs by extension
     *
     * @param dirs list of dirs to scan
     * @param ext  array of allowed file extensions
     * @return
     */
    public static ArrayList<File> listAllFiles(ArrayList<File> dirs, final String[] ext) {

        ArrayList<File> retList = new ArrayList<>();
        for (File dir : dirs) {
            File[] files = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    boolean retVal = false;
                    for (int i1 = 0; i1 < ext.length; i1++) {
                        retVal = retVal || name.toLowerCase().endsWith(ext[i1]);
                    }
                    return retVal;
                }
            });
            retList.addAll(Arrays.asList(files));
        }
        return retList;
    }

    /**
     * Get all the video files in all the selected dirs in preferences
     *
     * @param context     context
     * @param preferences shared preferences
     * @return list of the video files
     */
    public static ArrayList<File> getAllVideoFilesInDirs(Context context, SharedPreferences preferences) {
        ArrayList<File> dirsToObserveList = new ArrayList<>();
        Set<String> dirsToObserve = preferences.getStringSet(
                context.getString(R.string.keysetting_file_directories),
                new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.settings_file_directories_def_val)))
        );

        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        for (String dir : dirsToObserve)
            dirsToObserveList.add(new File(dcimDir, dir));

        return FilesUtils.listAllFiles(dirsToObserveList, MainActivity.VIDEO_FILE_EXTENSIONS);
    }

    /**
     * Swap video's file and backup file
     *
     * @param video video
     * @return true on success false otherwise
     */
    public static boolean swapFiles(VideoObject video) {
        File resultVideoTmpFile = new File(MainActivity.MKV_DIRECTORY, VideosManagementService.TEMP_FILE_NAME);
        delFile(resultVideoTmpFile);
        boolean a = moveFile(video.getmFile(), resultVideoTmpFile);
        boolean b = moveFile(video.getmBackupFile(), video.getmFile());
        boolean c = moveFile(resultVideoTmpFile, video.getmBackupFile());
        video.getmFile().setLastModified(video.getmProcessedDate().getTime());
        return a && b && c;
    }

    /**
     * same as ctrl+x ctrl+v
     *
     * @param inputFile
     * @param outputFile
     * @return true on success false otherwise
     */
    public static boolean moveFile(File inputFile, File outputFile) {

        return inputFile.renameTo(outputFile);

        /* does not work correctly (SO ticket 4178168)
        InputStream in = null;
        OutputStream out = null;
        try {

            //create output directory if it doesn't exist
            File dir = new File (outputPath);
            mkDirIfNotExist(dir);


            in = new FileInputStream(inputPath);
            out = new FileOutputStream(outputPath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;

            // write the output file (You have now copied the file)
            out.flush();
            out.close();
            out = null;

        }  catch (FileNotFoundException fnfe1) {
            Log.e(TAG, fnfe1.getMessage());
            return false;
        }
        catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }

        return true;
        */
    }

    /**
     * Delete file
     *
     * @param inputFile
     * @return true on success false otherwise
     */
    public static boolean delFile(File inputFile) {
        boolean res = false;
        try {
            // delete the original file
            res = inputFile.delete();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return res;
    }

    /**
     * Reads the contents of a text file
     *
     * @param file the text file
     * @return contents of the text files, separated by lines
     */
    public static String readTextFile(File file) {
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            return null;
        }

        return text.toString();
    }

    public static void mkDirIfNotExist(File dir) {
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    /**
     * Generate a backup file according to file
     *
     * @param file
     * @return backup file
     */
    public static File constructBackupFile(File file) {
        return constructBackupFile(file, MD5.calculateMD5(file));
    }

    /**
     * Generate a backup file according to file and md5 of file
     *
     * @param file
     * @param md5  file md5 digest
     * @return backup file
     */
    public static File constructBackupFile(File file, String md5) {
        int lenThresh = 0; //defines how many chars from file name
        String basename = file.getName().length() > lenThresh ? file.getName().substring(0, lenThresh) : file.getName();
        return new File(MainActivity.MKV_DIRECTORY, basename.replace('.', '_') + md5 + VideosManagementService.BACKUP_FILE_EXTENSION);
    }

    /* file.getFreeSpace is accurate enough
    public static float megabytesAvailable(File f) {
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        else
            bytesAvailable = (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        return bytesAvailable / (1024.f * 1024.f);
    }
    */
}
