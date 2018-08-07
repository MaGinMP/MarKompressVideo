/*
 * InfoFragment.java
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

package com.maginmp.app.markompressvideo.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.maginmp.app.markompressvideo.BuildConfig;
import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.utils.StringUtils;
import com.snilius.aboutit.AboutIt;
import com.snilius.aboutit.L;

/**
 * Created by MarkGintsburg on 11/11/2016.
 */
public class InfoFragment extends Fragment {

    private static final String TAG = InfoFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_info, container, false);

    }

    @Override
    public void onStart() {
        super.onStart();
        new AboutIt(getActivity()).app(StringUtils.getApplicationName(getActivity()))
                .buildInfo(BuildConfig.DEBUG, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
                .copyright("Mark Gintsburg\nmaginmp@gmail.com\nUnder GPL v3.0 license")
                .description(R.string.info_text_description)
                .year(2017)
                .libLicense("ExpandableLayout", "Daniel Cachapa", L.AP2, "https://github.com/cachapa/ExpandableLayout")
                .libLicense("Welcome", "Stephen Tuso", L.AP2, "https://github.com/stephentuso/welcome-android")
                .libLicense("AboutIt", "Victor Häggqvist", L.AP2, "https://github.com/victorhaggqvist/aboutit")
                .libLicense("FFmpeg-Android", "Bravobit", L.MIT, "https://github.com/bravobit/FFmpeg-Android")
                .libLicense("BottomBar", "Iiro Krankka", L.AP2, "https://github.com/roughike/BottomBar")
                .toTextView(R.id.info_txt_about);
    }

    // forceShowSplash from XML onclick is handled in MainActivity.
}
