package com.example.calvinmap;

import android.app.Application;
import android.content.Context;

public class App extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

}