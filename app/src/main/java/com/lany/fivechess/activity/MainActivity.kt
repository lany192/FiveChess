package com.lany.fivechess.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.lany.fivechess.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity() {

    override fun hasBackButton(): Boolean {
        return false
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_main
    }

    override fun init(savedInstanceState: Bundle?) {
        new_game.setOnClickListener {
            startActivity(Intent(this@MainActivity, RobotGameActivity::class.java))
        }
        fight.setOnClickListener {
            startActivity(Intent(this@MainActivity, PersonGameActivity::class.java))
        }
        conn_fight.setOnClickListener {
            startActivity(Intent(this@MainActivity, ConnectionActivity::class.java))
        }
        conn_about.setOnClickListener {
            val b = AlertDialog.Builder(this@MainActivity)
            b.setTitle(R.string.about)
            b.setMessage("欢迎访问源代码\nhttps://github.com/lany192/FiveChess")
            b.setPositiveButton(R.string.ok) { dialog, which -> }
            b.show()
        }
    }
}
