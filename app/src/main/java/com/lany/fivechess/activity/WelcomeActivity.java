package com.lany.fivechess.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.lany.fivechess.R;


public class WelcomeActivity extends BaseActivity {
    @Override
    protected boolean hasActionBar() {
        return false;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_welcome;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            }
        }, 2000);
    }



}
