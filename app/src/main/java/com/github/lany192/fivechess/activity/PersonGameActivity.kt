package com.github.lany192.fivechess.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.appcompat.app.AlertDialog
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.GameFightBinding
import com.github.lany192.fivechess.game.Constants
import com.github.lany192.fivechess.game.Game
import com.github.lany192.fivechess.game.Player

class PersonGameActivity : AppCompatActivity() {
    private var mGame: Game? = null
    private var black: Player? = null
    private var white: Player? = null
    private lateinit var binding: GameFightBinding

    private val mRefreshHandler = object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                Constants.GAME_OVER -> {
                    if (msg.arg1 == Game.BLACK) {
                        showWinDialog("黑方胜！")
                        black!!.win()
                    } else if (msg.arg1 == Game.WHITE) {
                        showWinDialog("白方胜！")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameFightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initGame()
        binding.restart.setOnClickListener{
            mGame!!.reset()
            updateActive(mGame)
            updateScore(black, white)
            binding.gameView.drawGame()
        }
        binding.rollback.setOnClickListener {
            mGame?.rollback()
            updateActive(mGame)
            binding.gameView.drawGame()
        }
    }

    private fun initGame() {
        black = Player(Game.BLACK)
        white = Player(Game.WHITE)
        mGame = Game(mRefreshHandler, black, white)
        mGame!!.mode = Constants.MODE_FIGHT
        binding.gameView.setGame(mGame)
        updateActive(mGame)
        updateScore(black, white)
    }

    private fun updateActive(game: Game?) {
        if (game!!.active == Game.BLACK) {
            binding.blackActive.visibility = View.VISIBLE
            binding.whiteActive.visibility = View.INVISIBLE
        } else {
            binding.blackActive.visibility = View.INVISIBLE
            binding.whiteActive.visibility = View.VISIBLE
        }
    }

    private fun updateScore(black: Player?, white: Player?) {
        binding.blackWin.text = black!!.win
        binding.whiteWin.text = white!!.win
    }

    private fun showWinDialog(message: String) {
        val b = AlertDialog.Builder(this)
        b.setCancelable(false)
        b.setMessage(message)
        b.setPositiveButton(R.string.Continue) { _, _ ->
            mGame!!.reset()
            binding.gameView.drawGame()
        }
        b.setNegativeButton(R.string.exit) { _, _ -> finish() }
        b.show()
    }
}