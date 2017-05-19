package com.lany.fivechess.activity

import android.os.Bundle
import android.support.v7.app.ActionBar
import android.support.v7.app.ActionBar.LayoutParams
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import butterknife.ButterKnife

abstract class BaseActivity : AppCompatActivity(), OnClickListener {
    protected var TAG = this.javaClass.simpleName
    private var mActionBar: ActionBar? = null
    protected var mInflater: LayoutInflater? = null

    protected abstract fun getLayoutId(): Int

    protected abstract fun init(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        onBeforeSetContentView()
        if (getLayoutId() != 0) {
            setContentView(getLayoutId())
            ButterKnife.bind(this)
        }
        mInflater = layoutInflater// 要在initActionBar之前

        if (hasActionBar()) {
            initActionBar()
        }
        init(savedInstanceState)
    }


    private fun initActionBar() {
        mActionBar = supportActionBar
        if (mActionBar != null) {
            //		mActionBar.setBackgroundDrawable(getResources().getDrawable(
            //				R.color.actionbar_bg));
            mActionBar!!.setDisplayShowTitleEnabled(true)
            mActionBar!!.setDisplayUseLogoEnabled(true)
            if (hasBackButton()) {
                mActionBar!!.setDisplayShowHomeEnabled(false)
                mActionBar!!.setDisplayHomeAsUpEnabled(true)
                // mActionBar.setHomeAsUpIndicator(R.drawable.ic_launcher);
            }
            if (hasActionBarCustomView()) {
                mActionBar!!.setDisplayShowCustomEnabled(true)
                val layoutRes = actionBarCustomViewLayoutRescId
                val actionBarView = inflateView(layoutRes)
                val params: LayoutParams
                if (isAllActionBarCustom) {
                    // XLog.i(TAG, "全部自定义");
                    params = LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT)
                } else {
                    // XLog.i(TAG, "右边自定义");
                    params = LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.MATCH_PARENT)
                    params.gravity = Gravity.RIGHT
                }
                mActionBar!!.setCustomView(actionBarView, params)
                handlerActionBarCustomViewAction(actionBarView)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackAction()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            onBackAction()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    protected fun onBackAction() {
        onBackPressed()
    }

    protected fun setActionBarTitle(resId: Int) {
        if (resId != 0) {
            setActionBarTitle(getString(resId))
        }
    }

    protected fun setActionBarTitle(title: String) {
        if (hasActionBar()) {
            mActionBar!!.title = title
        }
    }

    protected fun handlerActionBarCustomViewAction(actionBarView: View) {

    }

    protected val isAllActionBarCustom: Boolean
        get() = false

    protected fun inflateView(resId: Int): View {
        return mInflater!!.inflate(resId, null)
    }

    protected val actionBarCustomViewLayoutRescId: Int
        get() = 0

    protected fun hasActionBarCustomView(): Boolean {
        return false
    }

    protected open fun hasBackButton(): Boolean {
        return true
    }

    protected fun onBeforeSetContentView() {

    }

    protected open fun hasActionBar(): Boolean {
        return true
    }

    override fun onClick(v: View) {

    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

}
