package com.kherphy.bchat

import android.app.Application

class BChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeHelper.apply(this)
    }
}