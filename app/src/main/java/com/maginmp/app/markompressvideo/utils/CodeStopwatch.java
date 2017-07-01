/*
 * CodeStopwatch.java
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

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class CodeStopwatch {

    private static final String TAG = CodeStopwatch.class.getSimpleName();


    private long mStartTime;

    public CodeStopwatch() {
        reset();
    }

    public void reset() {
        mStartTime = System.currentTimeMillis();
    }

    public long getTimeElapsed() {
        long stopTime = System.currentTimeMillis();
        return stopTime - mStartTime;
    }
}
