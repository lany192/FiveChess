package com.lany.fivechess.activity

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.lany.fivechess.R
import com.lany.fivechess.game.Constants
import com.lany.fivechess.game.Game
import com.lany.fivechess.game.GameView
import com.lany.fivechess.game.Player
import com.lany.fivechess.net.ConnectConstants.*
import com.lany.fivechess.net.ConnectedService

class WifiGameActivity : BaseActivity(), OnClickListener {
    private var mGameView: GameView? = null
    private var mGame: Game? = null
    private var me: Player? = null
    private var challenger: Player? = null
    // 胜局
    private var mBlackWin: TextView? = null
    private var mWhiteWin: TextView? = null
    // 当前落子方

    private var mBlackActive: ImageView? = null
    private var mWhiteActive: ImageView? = null
    // 姓名
    private var mBlackName: TextView? = null
    private var mWhiteName: TextView? = null
    // Control Button
    private var restart: Button? = null
    private var rollback: Button? = null
    private var requestEqual: Button? = null
    private var fail: Button? = null
    // 网络服务
    private var mService: ConnectedService? = null
    internal var isServer: Boolean = false
    internal var ip: String? = ""
    // 连接等待框
    private var waitDialog: ProgressDialog? = null
    private var isRequest: Boolean = false

    override fun getLayoutId(): Int {
        return R.layout.game_net
    }

    override fun init(savedInstanceState: Bundle?) {
        val b = intent.extras
        if (b == null) {
            Toast.makeText(this, "建立网络失败,请重试", Toast.LENGTH_SHORT).show()
            finish()
        }
        showProgressDialog(null, "建立连接中，请稍后")
        isServer = b!!.getBoolean("isServer")
        ip = b.getString("ip")

        initViews()
        initGame()
    }


    /**
     * 处理游戏回调信息，刷新界面
     */
    private val mRefreshHandler = object : Handler() {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "refresh action=" + msg.what)
            when (msg.what) {
                Constants.GAME_OVER -> {
                    if (msg.arg1 == me!!.type) {
                        showWinDialog("恭喜你！你赢了！")
                        me!!.win()
                    } else if (msg.arg1 == challenger!!.type) {
                        showWinDialog("很遗憾！你输了！")
                        challenger!!.win()
                    } else {
                        Log.d(TAG, "type=" + msg.arg1)
                    }
                    updateScore(me, challenger)
                }
                Constants.ADD_CHESS -> {
                    val x = msg.arg1
                    val y = msg.arg2
                    mService!!.addChess(x, y)
                    updateActive(mGame)
                }
                else -> {
                }
            }
        }
    }

    /**
     * 处理网络信息，更新界面
     */
    private val mRequestHandler = object : Handler() {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "net action=" + msg.what)
            when (msg.what) {
                GAME_CONNECTED -> waitDialog!!.dismiss()
                CONNECT_ADD_CHESS -> {
                    mGame!!.addChess(msg.arg1, msg.arg2, challenger)
                    mGameView!!.drawGame()
                    updateActive(mGame)
                }
                ROLLBACK_ASK -> showRollbackDialog()
                ROLLBACK_AGREE -> {
                    Toast.makeText(this@WifiGameActivity, "对方同意悔棋",
                            Toast.LENGTH_SHORT).show()
                    rollback()
                    isRequest = false
                }
                ROLLBACK_REJECT -> {
                    isRequest = false
                    Toast.makeText(this@WifiGameActivity, "对方拒绝了你的请求",
                            Toast.LENGTH_LONG).show()
                }
                else -> {
                }
            }
        }
    }

    private fun initViews() {
        mGameView = findViewById(R.id.game_view) as GameView
        mBlackName = findViewById(R.id.black_name) as TextView
        mBlackWin = findViewById(R.id.black_win) as TextView
        mBlackActive = findViewById(R.id.black_active) as ImageView
        mWhiteName = findViewById(R.id.white_name) as TextView
        mWhiteWin = findViewById(R.id.white_win) as TextView
        mWhiteActive = findViewById(R.id.white_active) as ImageView
        restart = findViewById(R.id.restart) as Button
        rollback = findViewById(R.id.rollback) as Button
        requestEqual = findViewById(R.id.requestEqual) as Button
        fail = findViewById(R.id.fail) as Button
        restart!!.setOnClickListener(this)
        rollback!!.setOnClickListener(this)
        requestEqual!!.setOnClickListener(this)
        fail!!.setOnClickListener(this)
    }

    private fun initGame() {

        mService = ConnectedService(mRequestHandler, ip, isServer)

        if (isServer) {
            me = Player(Game.BLACK)
            challenger = Player(Game.WHITE)
            mBlackName!!.setText(R.string.myself)
            mWhiteName!!.setText(R.string.challenger)
        } else {
            me = Player(Game.WHITE)
            challenger = Player(Game.BLACK)
            mWhiteName!!.setText(R.string.myself)
            mBlackName!!.setText(R.string.challenger)
        }
        mGame = Game(mRefreshHandler, me, challenger)
        mGame!!.mode = Constants.MODE_NET
        mGameView!!.setGame(mGame)
        updateActive(mGame)
        updateScore(me, challenger)
    }

    private fun updateActive(game: Game?) {
        if (game!!.active == Game.BLACK) {
            mBlackActive!!.visibility = View.VISIBLE
            mWhiteActive!!.visibility = View.INVISIBLE
        } else {
            mBlackActive!!.visibility = View.INVISIBLE
            mWhiteActive!!.visibility = View.VISIBLE
        }
    }

    private fun updateScore(me: Player?, challenger: Player?) {
        mBlackWin!!.text = me!!.win
        mWhiteWin!!.text = challenger!!.win
    }

    override fun onDestroy() {
        super.onDestroy()
        mService!!.stop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (mGame!!.active != me!!.type) {
            return true
        }
        if (isRequest) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showWinDialog(message: String) {
        val b = AlertDialog.Builder(this)
        b.setMessage(message)
        b.setPositiveButton("继续") { dialog, which ->
            mGame!!.reset()
            mGameView!!.drawGame()
        }
        b.setNegativeButton("退出") { dialog, which -> finish() }
        b.show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.restart -> {
                mGame!!.reset()
                updateActive(mGame)
                updateScore(me, challenger)
                mGameView!!.drawGame()
            }
            R.id.rollback -> {
                mService!!.rollback()
                isRequest = true
            }
            R.id.requestEqual -> {
            }
            R.id.fail -> {
            }
            else -> {
            }
        }
    }

    private fun rollback() {
        if (mGame!!.active == me!!.type) {
            mGame!!.rollback()
        }
        mGame!!.rollback()
        updateActive(mGame)
        mGameView!!.drawGame()
    }

    // 显示等待框
    private fun showProgressDialog(title: String?, message: String) {
        if (waitDialog == null) {
            waitDialog = ProgressDialog(this)
        }
        if (!TextUtils.isEmpty(title)) {
            waitDialog!!.setTitle(title)
        }
        waitDialog!!.setMessage(message)
        waitDialog!!.isIndeterminate = true
        waitDialog!!.setCancelable(true)
        waitDialog!!.show()
    }

    private fun showRollbackDialog() {
        val b = AlertDialog.Builder(this)
        b.setMessage("是否同意对方悔棋")
        b.setCancelable(false)
        b.setPositiveButton(R.string.agree
        ) { dialog, which ->
            mService!!.agreeRollback()
            rollback()
        }
        b.setNegativeButton(R.string.reject
        ) { dialog, which -> mService!!.rejectRollback() }
        b.show()
    }

    companion object {

        fun start(context: Context, server: Boolean, dstIp: String) {
            val intent = Intent(context, WifiGameActivity::class.java)
            val b = Bundle()
            b.putBoolean("isServer", server)
            b.putString("ip", dstIp)
            intent.putExtras(b)
            context.startActivity(intent)
        }
    }

}