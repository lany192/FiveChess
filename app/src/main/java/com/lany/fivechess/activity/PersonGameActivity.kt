package com.lany.fivechess.activity

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.lany.fivechess.R
import com.lany.fivechess.game.Constants
import com.lany.fivechess.game.Game
import com.lany.fivechess.game.GameView
import com.lany.fivechess.game.Player

class PersonGameActivity : BaseActivity(), OnClickListener {
    private var mGameView: GameView? = null
    private var mGame: Game? = null
    private var black: Player? = null
    private var white: Player? = null
    private var mBlackWin: TextView? = null
    private var mWhiteWin: TextView? = null


    private var mBlackActive: ImageView? = null
    private var mWhiteActive: ImageView? = null

    // Control Button
    private var restart: Button? = null
    private var rollback: Button? = null

    private val mRefreshHandler = object : Handler() {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "refresh action=" + msg.what)
            when (msg.what) {
                Constants.GAME_OVER -> {
                    if (msg.arg1 == Game.BLACK) {
                        showWinDialog("黑方�?")
                        black!!.win()
                    } else if (msg.arg1 == Game.WHITE) {
                        showWinDialog("白方�?")
                        white!!.win()
                    }
                    updateScore(black, white)
                }
                Constants.ADD_CHESS -> updateActive(mGame)
                else -> {
                }
            }
        }
    }


    override fun getLayoutId(): Int {
        return R.layout.game_fight
    }

    override fun init(savedInstanceState: Bundle?) {
        initViews()
        initGame()
    }


    private fun initViews() {
        mGameView = findViewById(R.id.game_view) as GameView
        mBlackWin = findViewById(R.id.black_win) as TextView
        mBlackActive = findViewById(R.id.black_active) as ImageView
        mWhiteWin = findViewById(R.id.white_win) as TextView
        mWhiteActive = findViewById(R.id.white_active) as ImageView
        restart = findViewById(R.id.restart) as Button
        rollback = findViewById(R.id.rollback) as Button
        restart!!.setOnClickListener(this)
        rollback!!.setOnClickListener(this)
    }

    private fun initGame() {
        black = Player(Game.BLACK)
        white = Player(Game.WHITE)
        mGame = Game(mRefreshHandler, black, white)
        mGame!!.mode = Constants.MODE_FIGHT
        mGameView!!.setGame(mGame)
        updateActive(mGame)
        updateScore(black, white)
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

    private fun showWinDialog(message: String) {
        val b = AlertDialog.Builder(this)
        b.setCancelable(false)
        b.setMessage(message)
        b.setPositiveButton(R.string.Continue
        ) { dialog, which ->
            mGame!!.reset()
            mGameView!!.drawGame()
        }
        b.setNegativeButton(R.string.exit
        ) { dialog, which -> finish() }
        b.show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.restart -> {
                mGame!!.reset()
                updateActive(mGame)
                updateScore(black, white)
                mGameView!!.drawGame()
            }
            R.id.rollback -> {
                mGame!!.rollback()
                updateActive(mGame)
                mGameView!!.drawGame()
            }
            else -> {
            }
        }

    }
}
