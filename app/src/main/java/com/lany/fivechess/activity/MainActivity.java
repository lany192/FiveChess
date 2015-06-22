package com.lany.fivechess.activity;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.lany.fivechess.R;


public class MainActivity extends BaseActivity {
    @Override
    protected boolean hasBackButton() {
        return false;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        findViewById(R.id.new_game).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        SingleGameActivity.class));
            }
        });

        findViewById(R.id.fight).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        FightGameActivity.class));
            }
        });

        findViewById(R.id.conn_fight).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        ConnectionActivity.class));
            }
        });

        findViewById(R.id.conn_about).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "关于", Toast.LENGTH_SHORT).show();
            }
        });
    }

}
