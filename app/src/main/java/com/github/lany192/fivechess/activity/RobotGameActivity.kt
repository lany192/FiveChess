package com.github.lany192.fivechess.activity

import android.os.*
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.GameSingleBinding
import com.github.lany192.fivechess.game.*

/**
 * 人机对战
 */
class RobotGameActivity : AppCompatActivity(){
    private var mGame: Game? = null
    private var me: Player? = null
    private var computer: Player? = null
    private var ai: RobotAI? = null
    private var isRollback: Boolean = false
    private lateinit var binding: GameSingleBinding

    /**
     * 处理游戏回调信息，刷新界面
     */
    private var mComputerHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameSingleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initGame()
        initComputer()
        binding.restart.setOnClickListener {
            mGame?.reset()
            updateActive(mGame)
            updateScore(me, computer)
            binding.gameView.drawGame()
        }
        binding.rollback.setOnClickListener { if (mGame?.active != me?.type) {
            isRollback = true
        } else {
            rollback()
        } }
    }

    private fun initGame() {
        me = Player(getString(R.string.myself), Game.BLACK)
        computer = Player(getString(R.string.computer), Game.WHITE)
        mGame = Game(mRefreshHandler, me, computer)
        mGame?.mode = Constants.MODE_SINGLE
        binding.gameView.setGame(mGame)
        updateActive(mGame)
        updateScore(me, computer)
        ai = RobotAI(mGame?.width ?:0, mGame?.height ?:0)
    }

    private fun initComputer() {
        val thread = HandlerThread("computerAi")
        thread.start()
        mComputerHandler = ComputerHandler(thread.looper)
    }

    private fun updateActive(game: Game?) {
        if (game?.active == Game.BLACK) {
            binding.blackActive.visibility = View.VISIBLE
            binding.whiteActive.visibility = View.INVISIBLE
        } else {
            binding.blackActive.visibility = View.INVISIBLE
            binding.whiteActive.visibility = View.VISIBLE
        }
    }

    private fun updateScore(black: Player?, white: Player?) {
        binding.blackWin.text = black?.win
        binding.whiteWin.text = white?.win
    }


    /**
     * 处理游戏回调信息，刷新界面
     */
    private val mRefreshHandler = object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
//            Log.d(TAG, "refresh action=" + msg.what)
            when (msg.what) {
                Constants.GAME_OVER -> {
                    if (msg.arg1 == Game.BLACK) {
                        showWinDialog("黑方胜！")
                        me?.win()
                    } else if (msg.arg1 == Game.WHITE) {
                        showWinDialog("白方胜！")
                        computer?.win()
                    }
                    updateScore(me, computer)
                }

                Constants.ACTIVE_CHANGE -> updateActive(mGame)
                Constants.ADD_CHESS -> {
                    updateActive(mGame)
                    if (mGame?.active == computer?.type) {
                        mComputerHandler?.sendEmptyMessage(0)
                    }
                }

                else -> {
                }
            }
        }
    }

    override fun onDestroy() {
        mComputerHandler?.looper?.quit()
        super.onDestroy()
    }

    private fun showWinDialog(message: String) {
        val b = AlertDialog.Builder(this)
        b.setCancelable(false)
        b.setMessage(message)
        b.setPositiveButton("继续") { _, _ ->
            mGame?.reset()
            binding.gameView.drawGame()
        }
        b.setNegativeButton("退出") { _, _ -> finish() }
        b.show()
    }

    private fun rollback() {
        mGame?.rollback()
        mGame?.rollback()
        updateActive(mGame)
        binding.gameView.drawGame()
    }

    internal inner class ComputerHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            // 移除了对已删除的updateValue方法的调用
            val c = ai?.getPosition(mGame?.chessMap)
            mGame?.addChess(c, computer)
            binding.gameView.drawGame()
            if (isRollback) {
                rollback()
                isRollback = false
            }
        }
    }
}