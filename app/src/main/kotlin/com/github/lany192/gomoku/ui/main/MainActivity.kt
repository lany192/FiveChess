package com.github.lany192.gomoku.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.databinding.ActivityMainBinding
import com.github.lany192.gomoku.ui.bluetooth.BluetoothConnectActivity
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge
import com.github.lany192.gomoku.ui.connect.ConnectionActivity
import com.github.lany192.gomoku.ui.person.PersonGameActivity
import com.github.lany192.gomoku.ui.robot.RobotGameActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.newGame.setOnClickListener {
            startActivity(Intent(this@MainActivity, RobotGameActivity::class.java))
        }
        binding.fight.setOnClickListener {
            startActivity(Intent(this@MainActivity, PersonGameActivity::class.java))
        }
        binding.connFight.setOnClickListener {
            startActivity(Intent(this@MainActivity, ConnectionActivity::class.java))
        }
        binding.btFight.setOnClickListener {
            startActivity(Intent(this@MainActivity, BluetoothConnectActivity::class.java))
        }
        binding.connAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.about)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
