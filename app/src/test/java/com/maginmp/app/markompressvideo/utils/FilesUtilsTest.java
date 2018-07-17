/*
 * FilesUtilsTest.java
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

import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.external.MD5;
import com.maginmp.app.markompressvideo.objects.VideoObject;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by MarkGintsburg on 30/06/2017.
 */
public class FilesUtilsTest {

    public static final String MKV_TEST_CONTENT = "mkv_test_content";

    @Test
    public void listAllDirectories() throws Exception {
        CharSequence[] result = FilesUtils.listAllDirectories(new File(MKV_TEST_CONTENT + "/folders"));
        Arrays.sort(result);
        CharSequence[] expected  = {"Camera", "Foo", "Screenshots"};
        Arrays.sort(expected);
        CharSequence[] expectedMac  = {".DS_Store", "Camera", "Foo", "Screenshots"};
        Arrays.sort(expectedMac);
        if (expected.length == result.length)
            assertArrayEquals(expected, result);
        else
            assertArrayEquals(expectedMac, result);
    }

    @Test
    public void listAllFiles() throws Exception {
        File rootDir = new File(MKV_TEST_CONTENT + "/folders");
        CharSequence[] dirs = FilesUtils.listAllDirectories(rootDir);
        String[] ext = {".mp4",".mov"};
        ArrayList<File> dirsToObserveList = new ArrayList<>();
        for (CharSequence dir : dirs)
            if (!dir.toString().contains(".DS_Store"))
                dirsToObserveList.add(new File(rootDir, dir.toString()));
        ArrayList<File> resultList = FilesUtils.listAllFiles(dirsToObserveList, ext);
        String[] result = new String[resultList.size()];
        int i=0;
        for (File f : resultList)
        {
            result[i] = f.getName();
            i++;
        }

        String[] expected = {"SampleVideo_1280x720_1mb.mp4","small.mp4","SampleVideo_1280x720_2mb.mp4"};
        Arrays.sort(expected);
        Arrays.sort(result);
        assertArrayEquals(expected, result);
        assertTrue((new File(rootDir + "/Screenshots/pnggrad16rgb.png")).isFile());
    }

    @Test
    public void swapFiles() throws Exception {
        //Should be instrumental test
//        File f1 = new File(MKV_TEST_CONTENT + "/folders/Camera/SampleVideo_1280x720_1mb.mp4");
//        File f2 = new File(MKV_TEST_CONTENT + "/folders/Camera/small.mp4");
//
//        String md5_f1 = MD5.calculateMD5(f1);
//        String md5_f2 = MD5.calculateMD5(f2);
//
//        assertTrue(!md5_f1.equals(md5_f2));
//
//        VideoObject video = new VideoObject(-1, f1, f2, null, 0, 0, 0, 0, 0, 0, 0, 0);
//
//        FilesUtils.swapFiles(video);
//
//        String md5_f1_postswap = MD5.calculateMD5(f1);
//        String md5_f2_postswap = MD5.calculateMD5(f2);
//
//        assertTrue(!md5_f1_postswap.equals(md5_f2_postswap));
//        assertTrue(md5_f1.equals(md5_f2_postswap));
//        assertTrue(md5_f2.equals(md5_f1_postswap));
//
//        FilesUtils.swapFiles(video);
//
//        String md5_f1_postswap2 = MD5.calculateMD5(f1);
//        String md5_f2_postswap2 = MD5.calculateMD5(f2);
//
//        assertTrue(!md5_f1_postswap2.equals(md5_f2_postswap2));
//        assertTrue(md5_f1.equals(md5_f1_postswap2));
//        assertTrue(md5_f2.equals(md5_f2_postswap2));
    }

    @Test
    public void readTextFile() throws Exception {
        String txt = FilesUtils.readTextFile(new File(MKV_TEST_CONTENT + "/textfilehello.txt"));
        assertTrue(txt.contains("hello world!"));
    }

}