package com.lany.fivechess.net;

import static com.lany.fivechess.net.ConnectConstants.CONNECT_ADD_CHESS;
import static com.lany.fivechess.net.ConnectConstants.GAME_CONNECTED;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_AGREE;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_ASK;
import static com.lany.fivechess.net.ConnectConstants.ROLLBACK_REJECT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * ������<br>
 * ����������Ϣ��Է�
 */
public class ConnectedService {

	public static final String TAG = "ConnnectedService";
	private static final boolean DEBUG = true;

	private String mIp;

	private Socket mSocket;
	private GameReceiver mReceiver;
	private GameSender mSender;
	private boolean isServer;

	private static final int TCP_PORT = 8899;

	private Handler mRequestHandler;

	public ConnectedService(Handler handler, String ip, boolean isServer) {
		mRequestHandler = handler;
		this.isServer = isServer;
		this.mIp = ip;
		mReceiver = new GameReceiver();
		mReceiver.start();

		HandlerThread sendThread = new HandlerThread("GameSender");
		sendThread.start();
		mSender = new GameSender(sendThread.getLooper());
	}

	/**
	 * ����
	 * 
	 * @param x
	 * @param y
	 */
	public void addChess(int x, int y) {
		byte[] data = new byte[4];
		data[0] = 4;
		data[1] = ConnectConstants.CONNECT_ADD_CHESS;
		data[2] = (byte) x;
		data[3] = (byte) y;
		Message msg = new Message();
		msg.obj = data;
		mSender.sendMessage(msg);
	}

	/**
	 * �������
	 */
	public void rollback() {
		byte[] data = new byte[2];
		data[0] = 2;
		data[1] = ConnectConstants.ROLLBACK_ASK;
		Message msg = new Message();
		msg.obj = data;
		mSender.sendMessage(msg);
	}

	/**
	 * ͬ�����
	 */
	public void agreeRollback() {
		byte[] data = new byte[2];
		data[0] = 2;
		data[1] = ConnectConstants.ROLLBACK_AGREE;
		Message msg = new Message();
		msg.obj = data;
		mSender.sendMessage(msg);
	}

	/**
	 * �ܾ����
	 */
	public void rejectRollback() {
		byte[] data = new byte[2];
		data[0] = 2;
		data[1] = ConnectConstants.ROLLBACK_REJECT;
		Message msg = new Message();
		msg.obj = data;
		mSender.sendMessage(msg);
	}

	public void stop() {
		mSender.quit();
		mReceiver.quit();
	}

	/**
	 * TCP��Ϣ�����߳�
	 */
	class GameReceiver extends Thread {

		byte[] buf = new byte[1024];
		boolean isStop = false;

		ServerSocket server;

		public GameReceiver() {
		}

		@Override
		public void run() {
			try {
				if (isServer) {
					server = new ServerSocket(TCP_PORT);
					mSocket = server.accept();
					Log.d(TAG, "server:net connected");
					mRequestHandler.sendEmptyMessage(GAME_CONNECTED);
				} else {
					Socket s = new Socket();
					InetSocketAddress addr = new InetSocketAddress(mIp,
							TCP_PORT);
					/*
					 * ����ʧ�ܳ�������������8�� ��Ϊ�������ܲ�һ���ܱ�֤��ΪServer�˵�Activity���ڿͻ�������
					 */
					int retryCount = 0;
					while (retryCount < 8) {
						try {
							s.connect(addr);
							mSocket = s;
							mRequestHandler.sendEmptyMessage(GAME_CONNECTED);
							Log.d(TAG, "client:net connected");
							break;
						} catch (IOException e) {
							retryCount++;
							s = new Socket();
							try {
								Thread.sleep(200);
							} catch (InterruptedException e1) {
							}
							Log.d(TAG, "connect exception ��" + e.getMessage()
									+ "  retry count=" + retryCount);
						}
					}
					if (retryCount >= 8) {
						// TODO
						return;
					}
				}
			} catch (IOException e) {
				Log.d(TAG, "socket exception:" + e.getMessage());
				// TODO
				return;
			}
			InputStream is;
			try {
				is = mSocket.getInputStream();
				while (!isStop) {
					if (is.read(buf) == -1) {
						// ���ӶϿ�
						break;
					}
					if (DEBUG) {
						Log.d(TAG, "tcp received:" + Arrays.toString(buf));
					}
					int length = buf[0];
					// ��buffer�н�ȡ�յ������
					byte[] body = new byte[length];
					System.arraycopy(buf, 1, body, 0, length);
					processNetData(body);
				}
			} catch (IOException e) {
				Log.d(TAG, "IOException:"
						+ "an error occurs while receiving data");
				// TODO ��ʾ���ӶϿ�
			}

		}

		public void quit() {
			try {
				isStop = true;
				if (mSocket != null) {
					mSocket.close();
				}
				if (server != null) {
					server.close();
				}
			} catch (IOException e) {
				Log.d(TAG, "close Socket Exception:" + e.getMessage());
			}
		}

	}

	/**
	 * ����Ϣ����TCP�����̷߳���
	 */
	class GameSender extends Handler {

		public GameSender(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			byte[] data = (byte[]) msg.obj;
			try {
				Socket s = mSocket;
				if (s == null) {
					// onError(SOCKET_NULL);
					Log.d(TAG, "Send fail,socket is null");
					return;
				}
				OutputStream os = s.getOutputStream();
				// �������
				os.write(data);
				os.flush();
			} catch (IOException e) {
				Log.d(TAG, "tcp socket error:" + e.getMessage());
				// onError(SOCKET_NULL);
			}

		}

		public void quit() {
			getLooper().quit();
		}

	}

	// ������Ϣ
	private void processNetData(byte[] data) {
		int type = data[0];
		switch (type) {
		case CONNECT_ADD_CHESS:
			notifyAddChess(data[1], data[2]);
			break;
		case ROLLBACK_ASK:
			mRequestHandler.sendEmptyMessage(ROLLBACK_ASK);
			break;
		case ROLLBACK_AGREE:
			mRequestHandler.sendEmptyMessage(ROLLBACK_AGREE);
			break;
		case ROLLBACK_REJECT:
			mRequestHandler.sendEmptyMessage(ROLLBACK_REJECT);
			break;
		default:
			break;
		}
	}

	private void notifyAddChess(int x, int y) {
		Message msg = Message.obtain();
		msg.what = CONNECT_ADD_CHESS;
		msg.arg1 = x;
		msg.arg2 = y;
		mRequestHandler.sendMessage(msg);
	}
}
