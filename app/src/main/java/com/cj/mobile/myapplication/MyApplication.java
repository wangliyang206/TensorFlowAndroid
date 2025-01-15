package com.cj.mobile.myapplication;

import android.app.Application;

import com.wayz.location.MapsInitializer;

/**
 * @ProjectName: MyApplication
 * @Package: com.cj.mobile.myapplication
 * @ClassName: MyApplication
 * @Description:
 * @Author: WLY
 * @CreateDate: 2024/12/27 14:57
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        MapsInitializer.updatePrivacyShow(this, true, false);
        MapsInitializer.updatePrivacyAgree(this, true);
    }
}
