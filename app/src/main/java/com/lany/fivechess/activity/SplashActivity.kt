package com.lany.fivechess.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler

import com.lany.fivechess.R


class SplashActivity : BaseActivity() {
    override fun hasActionBar(): Boolean {
        return false
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_welcome
    }

    override fun init(savedInstanceState: Bundle?) {
        Handler().postDelayed({
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }, 2000)
    }
}
