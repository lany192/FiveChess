package com.lany.fivechess.activity;

import static com.lany.fivechess.net.ConnectConstants.CONNECT_ADD_CHESS;
import static com.lany.fivechess.net.ConnectConstants.GAME_CONNECTED;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_AGREE;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_ASK;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_REJECT;

import com.lany.fivechess.R;
import com.lany.fivechess.game.Game;
import com.lany.fivechess.game.GameConstants;
import com.lany.fivechess.game.GameView;
import com.lany.fivechess.game.Player;
import com.lany.fivechess.net.ConnectedService;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class NetGameActivity extends AppCompatActivity implements OnClickListener {
	private static final String TAG = "GameActivity";
	private GameView mGameView;
	private Game mGame;
	private Player me;
	private Player challenger;
	// ʤ��
	private TextView mBlackWin;
	private TextView mWhiteWin;
	// ��ǰ���ӷ�
	private ImageView mBlackActive;
	private ImageView mWhiteActive;
	// ����
	private TextView mBlackName;
	private TextView mWhiteName;
	// Control Button
	private Button restart;
	private Button rollback;
	private Button requestEqual;
	private Button fail;
	// �������
	private ConnectedService mService;
	boolean isServer;
	String ip;
	// ���ӵȴ��
	private ProgressDialog waitDialog;
	private boolean isRequest;

	public static void startActivity(Context context, boolean server,
			String dstIp) {
		Intent intent = new Intent(context, NetGameActivity.class);
		Bundle b = new Bundle();
		b.putBoolean("isServer", server);
		b.putString("ip", dstIp);
		intent.putExtras(b);
		context.startActivity(intent);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.game_net);
		Bundle b = getIntent().getExtras();
		if (b == null) {
			Toast.makeText(this, "��������ʧ��,������", Toast.LENGTH_SHORT).show();
			finish();
		}
		showProgressDialog(null, "���������У����Ժ�");
		isServer = b.getBoolean("isServer");
		ip = b.getString("ip");

		initViews();
		initGame();
	}

	/**
	 * ������Ϸ�ص���Ϣ��ˢ�½���
	 */
	private Handler mRefreshHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "refresh action=" + msg.what);
			switch (msg.what) {
			case GameConstants.GAME_OVER:
				if (msg.arg1 == me.getType()) {
					showWinDialog("��ϲ�㣡��Ӯ�ˣ�");
					me.win();
				} else if (msg.arg1 == challenger.getType()) {
					showWinDialog("���ź��������ˣ�");
					challenger.win();
				} else {
					Log.d(TAG, "type=" + msg.arg1);
				}
				updateScore(me, challenger);
				break;
			case GameConstants.ADD_CHESS:
				int x = msg.arg1;
				int y = msg.arg2;
				mService.addChess(x, y);
				updateActive(mGame);
				break;
			default:
				break;
			}
		}
	};

	/**
	 * ����������Ϣ�����½���
	 */
	private Handler mRequestHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "net action=" + msg.what);
			switch (msg.what) {
			case GAME_CONNECTED:
				waitDialog.dismiss();
				break;
			case CONNECT_ADD_CHESS:
				mGame.addChess(msg.arg1, msg.arg2, challenger);
				mGameView.drawGame();
				updateActive(mGame);
				break;
			case ROLLBACK_ASK:
				showRollbackDialog();
				break;
			case ROLLBACK_AGREE:
				Toast.makeText(NetGameActivity.this, "�Է�ͬ�����",
						Toast.LENGTH_SHORT).show();
				rollback();
				isRequest = false;
				break;
			case ROLLBACK_REJECT:
				isRequest = false;
				Toast.makeText(NetGameActivity.this, "�Է��ܾ����������",
						Toast.LENGTH_LONG).show();
				break;
			default:
				break;
			}
		}
	};

	private void initViews() {
		mGameView = (GameView) findViewById(R.id.game_view);
		mBlackName = (TextView) findViewById(R.id.black_name);
		mBlackWin = (TextView) findViewById(R.id.black_win);
		mBlackActive = (ImageView) findViewById(R.id.black_active);
		mWhiteName = (TextView) findViewById(R.id.white_name);
		mWhiteWin = (TextView) findViewById(R.id.white_win);
		mWhiteActive = (ImageView) findViewById(R.id.white_active);
		restart = (Button) findViewById(R.id.restart);
		rollback = (Button) findViewById(R.id.rollback);
		requestEqual = (Button) findViewById(R.id.requestEqual);
		fail = (Button) findViewById(R.id.fail);
		restart.setOnClickListener(this);
		rollback.setOnClickListener(this);
		requestEqual.setOnClickListener(this);
		fail.setOnClickListener(this);
	}

	private void initGame() {

		mService = new ConnectedService(mRequestHandler, ip, isServer);

		if (isServer) {
			me = new Player(Game.BLACK);
			challenger = new Player(Game.WHITE);
			mBlackName.setText(R.string.myself);
			mWhiteName.setText(R.string.challenger);
		} else {
			me = new Player(Game.WHITE);
			challenger = new Player(Game.BLACK);
			mWhiteName.setText(R.string.myself);
			mBlackName.setText(R.string.challenger);
		}
		mGame = new Game(mRefreshHandler, me, challenger);
		mGame.setMode(GameConstants.MODE_NET);
		mGameView.setGame(mGame);
		updateActive(mGame);
		updateScore(me, challenger);
	}

	private void updateActive(Game game) {
		if (game.getActive() == Game.BLACK) {
			mBlackActive.setVisibility(View.VISIBLE);
			mWhiteActive.setVisibility(View.INVISIBLE);
		} else {
			mBlackActive.setVisibility(View.INVISIBLE);
			mWhiteActive.setVisibility(View.VISIBLE);
		}
	}

	private void updateScore(Player me, Player challenger) {
		mBlackWin.setText(me.getWin());
		mWhiteWin.setText(challenger.getWin());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mService.stop();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (mGame.getActive() != me.getType()) {
			return true;
		}
		if (isRequest) {
			return true;
		}
		return super.dispatchTouchEvent(ev);
	}

	private void showWinDialog(String message) {
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setMessage(message);
		b.setPositiveButton("����", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				mGame.reset();
				mGameView.drawGame();
			}
		});
		b.setNegativeButton("�˳�", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
		b.show();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.restart:
			mGame.reset();
			updateActive(mGame);
			updateScore(me, challenger);
			mGameView.drawGame();
			break;
		case R.id.rollback:
			mService.rollback();
			isRequest = true;
			break;
		case R.id.requestEqual:

			break;
		case R.id.fail:

			break;
		default:
			break;
		}
	}

	private void rollback() {
		if (mGame.getActive() == me.getType()) {
			mGame.rollback();
		}
		mGame.rollback();
		updateActive(mGame);
		mGameView.drawGame();
	}

	// ��ʾ�ȴ��
	private void showProgressDialog(String title, String message) {
		if (waitDialog == null) {
			waitDialog = new ProgressDialog(this);
		}
		if (!TextUtils.isEmpty(title)) {
			waitDialog.setTitle(title);
		}
		waitDialog.setMessage(message);
		waitDialog.setIndeterminate(true);
		waitDialog.setCancelable(true);
		waitDialog.show();
	}

	private void showRollbackDialog() {
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setMessage("�Ƿ�ͬ��Է�����");
		b.setCancelable(false);
		b.setPositiveButton(R.string.agree,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mService.agreeRollback();
						rollback();
					}
				});
		b.setNegativeButton(R.string.reject,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mService.rejectRollback();
					}
				});
		b.show();
	}

}