/*
 * WelcomeScreenActivity.java
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

package com.maginmp.app.markompressvideo.activities;

import com.maginmp.app.markompressvideo.R;
import com.stephentuso.welcome.BasicPage;
import com.stephentuso.welcome.WelcomeActivity;
import com.stephentuso.welcome.WelcomeConfiguration;

/**
 * Created by MarkGintsburg on 25/06/2017.
 */

public class WelcomeScreenActivity extends WelcomeActivity {
    // On a major change in welcome screen, the welcome key should be changed to pop up next
    // time the app is opened
    private static final String WELCOME_KEY = "WELCOME 0.1.0";

    public static String welcomeKey() {
        return WELCOME_KEY;
    }

    @Override
    protected WelcomeConfiguration configuration() {
        return new WelcomeConfiguration.Builder(this)

                .page(new BasicPage(R.drawable.welcome_mkv_logo,
                        getString(R.string.welcome_screen_1_title),
                        getString(R.string.welcome_screen_1_body))
                        .background(R.color.welcome_screen_1)
                )

                .page(new BasicPage(R.drawable.welcome_screen_2,
                        getString(R.string.welcome_screen_2_title),
                        getString(R.string.welcome_screen_2_body))
                        .background(R.color.welcome_screen_2)
                )

                .page(new BasicPage(R.drawable.welcome_screen_3,
                        getString(R.string.welcome_screen_3_title),
                        getString(R.string.welcome_screen_3_body))
                        .background(R.color.welcome_screen_3)
                )

                .page(new BasicPage(R.drawable.welcome_screen_4,
                        getString(R.string.welcome_screen_4_title),
                        getString(R.string.welcome_screen_4_body))
                        .background(R.color.welcome_screen_4)
                )

                .page(new BasicPage(R.drawable.welcome_screen_5,
                        getString(R.string.welcome_screen_5_title),
                        getString(R.string.welcome_screen_5_body))
                        .background(R.color.welcome_screen_5)
                )

                .page(new BasicPage(R.drawable.welcome_screen_6,
                        getString(R.string.welcome_screen_6_title),
                        getString(R.string.welcome_screen_6_body))
                        .background(R.color.welcome_screen_6)
                )

                .page(new BasicPage(R.drawable.welcome_gpl_v3_logo,
                        getString(R.string.welcome_screen_7_title),
                        getString(R.string.welcome_screen_7_body))
                        .background(R.color.welcome_screen_1)
                )

                .swipeToDismiss(true)
                .exitAnimation(android.R.anim.fade_out)
                .build();
    }
}
