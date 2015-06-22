package com.lany.fivechess.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.LayoutParams;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

public abstract class BaseActivity extends AppCompatActivity implements
        OnClickListener {
    protected String TAG = this.getClass().getSimpleName();
    private ActionBar mActionBar;
    protected LayoutInflater mInflater;

    protected abstract int getLayoutId();


    protected abstract void init(Bundle savedInstanceState);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        onBeforeSetContentView();
        if (getLayoutId() != 0) {
            setContentView(getLayoutId());
        }
        mInflater = getLayoutInflater();// 要在initActionBar之前

        if (hasActionBar()) {
            initActionBar();
        }
        init(savedInstanceState);
    }


    private void initActionBar() {
        mActionBar = getSupportActionBar();
        if(mActionBar!=null) {
//		mActionBar.setBackgroundDrawable(getResources().getDrawable(
//				R.color.actionbar_bg));
            mActionBar.setDisplayShowTitleEnabled(true);
            mActionBar.setDisplayUseLogoEnabled(true);
            if (hasBackButton()) {
                mActionBar.setDisplayShowHomeEnabled(false);
                mActionBar.setDisplayHomeAsUpEnabled(true);
                // mActionBar.setHomeAsUpIndicator(R.drawable.ic_launcher);
            }
            if (hasActionBarCustomView()) {
                mActionBar.setDisplayShowCustomEnabled(true);
                int layoutRes = getActionBarCustomViewLayoutRescId();
                View actionBarView = inflateView(layoutRes);
                LayoutParams params;
                if (isAllActionBarCustom()) {
                    // XLog.i(TAG, "全部自定义");
                    params = new LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT);
                } else {
                    // XLog.i(TAG, "右边自定义");
                    params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.MATCH_PARENT);
                    params.gravity = Gravity.RIGHT;
                }
                mActionBar.setCustomView(actionBarView, params);
                handlerActionBarCustomViewAction(actionBarView);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackAction();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            onBackAction();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    protected void onBackAction() {
        onBackPressed();
    }

    protected void setActionBarTitle(int resId) {
        if (resId != 0) {
            setActionBarTitle(getString(resId));
        }
    }

    protected void setActionBarTitle(String title) {
        if (hasActionBar()) {
            mActionBar.setTitle(title);
        }
    }

    protected void handlerActionBarCustomViewAction(View actionBarView) {

    }

    protected boolean isAllActionBarCustom() {
        return false;
    }

    protected View inflateView(int resId) {
        return mInflater.inflate(resId, null);
    }

    protected int getActionBarCustomViewLayoutRescId() {
        return 0;
    }

    protected boolean hasActionBarCustomView() {
        return false;
    }

    protected boolean hasBackButton() {
        return true;
    }

    protected void onBeforeSetContentView() {

    }

    protected boolean hasActionBar() {
        return true;
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

}
