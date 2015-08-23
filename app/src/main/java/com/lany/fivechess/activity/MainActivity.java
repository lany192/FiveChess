package com.lany.fivechess.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;

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
                        RobotGameActivity.class));
            }
        });

        findViewById(R.id.fight).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        PersonGameActivity.class));
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
                showAboutDialog();
            }
        });
    }

    private void showAboutDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.about);
        b.setMessage("欢迎访问源代码\nhttps://github.com/lany192/FiveChess");
        b.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        b.show();
    }

}
