package com.lany.fivechess.activity

import android.os.*
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.lany.fivechess.R
import com.lany.fivechess.game.*

/**
 * 人机对战
 */
class RobotGameActivity : BaseActivity(), OnClickListener {
    private var mGameView: GameView? = null

    private var mGame: Game? = null
    private var me: Player? = null
    private var computer: Player? = null
    private var ai: RobotAI? = null
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

    private var isRollback: Boolean = false

    /**
     * 处理游戏回调信息，刷新界面
     */
    private var mComputerHandler: Handler? = null


    override fun getLayoutId(): Int {
        return R.layout.game_single
    }


    override fun init(savedInstanceState: Bundle?) {
        initViews()
        initGame()
        initComputer()
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

        restart!!.setOnClickListener(this)
        rollback!!.setOnClickListener(this)
    }

    private fun initGame() {
        me = Player(getString(R.string.myself), Game.BLACK)
        computer = Player(getString(R.string.computer), Game.WHITE)
        mGame = Game(mRefreshHandler, me, computer)
        mGame!!.mode = Constants.MODE_SINGLE
        mGameView!!.setGame(mGame)
        updateActive(mGame)
        updateScore(me, computer)
        ai = RobotAI(mGame!!.width, mGame!!.height)
    }

    private fun initComputer() {
        val thread = HandlerThread("computerAi")
        thread.start()
        mComputerHandler = ComputerHandler(thread.looper)
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

    private fun updateScore(black: Player?, white: Player?) {
        mBlackWin!!.text = black!!.win
        mWhiteWin!!.text = white!!.win
    }


    /**
     * 处理游戏回调信息，刷新界面
     */
    private val mRefreshHandler = object : Handler() {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "refresh action=" + msg.what)
            when (msg.what) {
                Constants.GAME_OVER -> {
                    if (msg.arg1 == Game.BLACK) {
                        showWinDialog("黑方胜！")
                        me!!.win()
                    } else if (msg.arg1 == Game.WHITE) {
                        showWinDialog("白方胜！")
                        computer!!.win()
                    }
                    updateScore(me, computer)
                }
                Constants.ACTIVE_CHANGE -> updateActive(mGame)
                Constants.ADD_CHESS -> {
                    updateActive(mGame)
                    if (mGame!!.active == computer!!.type) {
                        mComputerHandler!!.sendEmptyMessage(0)
                    }
                }
                else -> {
                }
            }
        }
    }

    override fun onDestroy() {
        mComputerHandler!!.looper.quit()
        super.onDestroy()
    }

    private fun showWinDialog(message: String) {
        val b = AlertDialog.Builder(this)
        b.setCancelable(false)
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
                updateScore(me, computer)
                mGameView!!.drawGame()
            }
            R.id.rollback -> if (mGame!!.active != me!!.type) {
                isRollback = true
            } else {
                rollback()
            }
            else -> {
            }
        }

    }

    private fun rollback() {
        mGame!!.rollback()
        mGame!!.rollback()
        updateActive(mGame)
        mGameView!!.drawGame()
    }

    internal inner class ComputerHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            ai!!.updateValue(mGame!!.chessMap)
            val c = ai!!.getPosition(mGame!!.chessMap)
            mGame!!.addChess(c, computer)
            mGameView!!.drawGame()
            if (isRollback) {
                rollback()
                isRollback = false
            }
        }

    }
}