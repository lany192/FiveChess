package com.github.lany192.fivechess.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.ActivityMainBinding
import com.github.lany192.fivechess.ui.connect.ConnectionActivity
import com.github.lany192.fivechess.ui.person.PersonGameActivity
import com.github.lany192.fivechess.ui.robot.RobotGameActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.newGame.setOnClickListener {
            startActivity(Intent(this@MainActivity, RobotGameActivity::class.java))
        }
        binding.fight.setOnClickListener {
            startActivity(Intent(this@MainActivity, PersonGameActivity::class.java))
        }
        binding.connFight.setOnClickListener {
            startActivity(Intent(this@MainActivity, ConnectionActivity::class.java))
        }
        binding.connAbout.setOnClickListener {
            val b = AlertDialog.Builder(this@MainActivity)
            b.setTitle(R.string.about)
            b.setMessage("欢迎访问源代码\nhttps://github.com/lany192/FiveChess")
            b.setPositiveButton(R.string.ok) { dialog, which -> }
            b.show()
        }
    }
}
