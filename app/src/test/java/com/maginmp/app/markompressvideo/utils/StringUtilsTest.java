/*
 * StringUtilsTest.java
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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by MarkGintsburg on 30/06/2017.
 */
public class StringUtilsTest {
    @Test
    public void dimenToWidthAndHeight() throws Exception {
        String input = "1920x1080";
        int[] expected = {1920, 1080};
        int[] result = StringUtils.dimenToWidthAndHeight(input);
        assertArrayEquals(expected, result);
    }

    @Test
    public void widthAndHeightToDimen() throws Exception {
        int[] input = {1920, 1080};
        String expected = "1920x1080";
        String result = StringUtils.widthAndHeightToDimen(input[0], input[1]);
        assertEquals(expected, result);
    }

    @Test
    public void millisToMinAndSec() throws Exception {
        long input = 564756;
        String expected = "09:24";
        String result = StringUtils.millisToMinAndSec(input);
        assertEquals(expected, result);
    }

}