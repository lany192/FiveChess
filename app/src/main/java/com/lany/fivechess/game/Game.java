package com.lany.fivechess.game;

import java.util.Deque;
import java.util.LinkedList;

import android.os.Handler;
import android.os.Message;

/**
 * ������Ϸ�߼�
 */
public class Game {
	public static final int SCALE_SMALL = 11;
	public static final int SCALE_MEDIUM = 15;
	public static final int SCALE_LARGE = 19;
	// �Լ�
	Player me;
	// ����
	Player challenger;

	private int mMode = 0;

	// Ĭ�Ϻ����ȳ�
	private int mActive = 1;
	int mGameWidth = 0;
	int mGameHeight = 0;
	int[][] mGameMap = null;
	Deque<Coordinate> mActions;

	public static final int BLACK = 1;
	public static final int WHITE = 2;

	private Handler mNotify;

	public Game(Handler h, Player me, Player challenger) {
		this(h, me, challenger, SCALE_MEDIUM, SCALE_MEDIUM);
	}

	public Game(Handler h, Player me, Player challenger, int width, int height) {
		mNotify = h;
		this.me = me;
		this.challenger = challenger;
		mGameWidth = width;
		mGameHeight = height;
		mGameMap = new int[mGameWidth][mGameHeight];
		mActions = new LinkedList<Coordinate>();
	}

	public void setMode(int mode) {
		this.mMode = mode;
	}

	public int getMode() {
		return mMode;
	}

	/**
	 * ����һ��
	 * 
	 * @return �Ƿ���Ի���
	 */
	public boolean rollback() {
		Coordinate c = mActions.pollLast();
		if (c != null) {
			mGameMap[c.x][c.y] = 0;
			changeActive();
			return true;
		}
		return false;
	}

	/**
	 * ��Ϸ���
	 * 
	 * @return ���̵�����
	 */
	public int getWidth() {
		return mGameWidth;
	}

	/**
	 * ��Ϸ�߶�
	 * 
	 * @return ���̺���
	 */
	public int getHeight() {
		return mGameHeight;
	}

	/**
	 * ����
	 * 
	 * @param x
	 *            �����±�
	 * @param y
	 *            �����±�
	 * @return ��ǰλ���Ƿ��������
	 */
	public boolean addChess(int x, int y) {
		if (mMode == GameConstants.MODE_FIGHT) {
			if (mGameMap[x][y] == 0) {
				if (mActive == BLACK) {
					mGameMap[x][y] = BLACK;
				} else {
					mGameMap[x][y] = WHITE;
				}
				if (!isGameEnd(x, y, me.type)) {
					changeActive();
					sendAddChess(x, y);
					mActions.add(new Coordinate(x, y));
				}
				return true;
			}
		} else if (mMode == GameConstants.MODE_NET) {
			if (mActive == me.type && mGameMap[x][y] == 0) {
				mGameMap[x][y] = me.type;
				mActive = challenger.type;
				if (!isGameEnd(x, y, me.type)) {
					mActions.add(new Coordinate(x, y));
				}
				sendAddChess(x, y);
				return true;
			}
		} else if (mMode == GameConstants.MODE_SINGLE) {
			if (mActive == me.type && mGameMap[x][y] == 0) {
				mGameMap[x][y] = me.type;
				mActive = challenger.type;
				if (!isGameEnd(x, y, me.type)) {
					sendAddChess(x, y);
					mActions.add(new Coordinate(x, y));
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * ����
	 * 
	 * @param x
	 *            �����±�
	 * @param y
	 *            �����±�
	 * @param player
	 *            ��Ϸѡ��
	 */
	public void addChess(int x, int y, Player player) {
		if (mGameMap[x][y] == 0) {
			mGameMap[x][y] = player.type;
			mActions.add(new Coordinate(x, y));
			boolean isEnd = isGameEnd(x, y, player.type);
			mActive = me.type;
			if (!isEnd) {
				mNotify.sendEmptyMessage(GameConstants.ACTIVE_CHANGE);
			}
		}
	}

	/**
	 * ����
	 * 
	 * @param c
	 *            ����λ��
	 * @param player
	 *            ��Ϸѡ��
	 */
	public void addChess(Coordinate c, Player player) {
		addChess(c.x, c.y, player);
	}

	public static int getFighter(int type) {
		if (type == BLACK) {
			return WHITE;
		} else {
			return BLACK;
		}
	}

	/**
	 * ���ص�ǰ���ӷ�
	 * 
	 * @return mActive
	 */
	public int getActive() {
		return mActive;
	}

	/**
	 * ��ȡ����
	 * 
	 * @return �������
	 */
	public int[][] getChessMap() {
		return mGameMap;
	}

	/**
	 * ��ȡ������ʷ
	 * 
	 * @return mActions
	 */
	public Deque<Coordinate> getActions() {
		return mActions;
	}

	/**
	 * ������Ϸ
	 */
	public void reset() {
		mGameMap = new int[mGameWidth][mGameHeight];
		mActive = BLACK;
		mActions.clear();
	}

	/**
	 * ����Ҫ�������ӷ���˭��˭����
	 */
	public void resetNet() {
		mGameMap = new int[mGameWidth][mGameHeight];
		mActions.clear();
	}

	private void changeActive() {
		if (mActive == BLACK) {
			mActive = WHITE;
		} else {
			mActive = BLACK;
		}
	}

	private void sendAddChess(int x, int y) {
		Message msg = new Message();
		msg.what = GameConstants.ADD_CHESS;
		msg.arg1 = x;
		msg.arg2 = y;
		mNotify.sendMessage(msg);
	}

	// �ж��Ƿ���������
	private boolean isGameEnd(int x, int y, int type) {
		int leftX = x - 4 > 0 ? x - 4 : 0;
		int rightX = x + 4 < mGameWidth - 1 ? x + 4 : mGameWidth - 1;
		int topY = y - 4 > 0 ? y - 4 : 0;
		int bottomY = y + 4 < mGameHeight - 1 ? y + 4 : mGameHeight - 1;

		int horizontal = 1;
		// ��������
		for (int i = x - 1; i >= leftX; --i) {
			if (mGameMap[i][y] != type) {
				break;
			}
			++horizontal;
		}
		// ��������
		for (int i = x + 1; i <= rightX; ++i) {
			if (mGameMap[i][y] != type) {
				break;
			}
			++horizontal;
		}
		if (horizontal >= 5) {
			sendGameResult(type);
			return true;
		}

		int vertical = 1;
		// ��������
		for (int j = y - 1; j >= topY; --j) {
			if (mGameMap[x][j] != type) {
				break;
			}
			++vertical;
		}
		// ��������
		for (int j = y + 1; j <= bottomY; ++j) {
			if (mGameMap[x][j] != type) {
				break;
			}
			++vertical;
		}
		if (vertical >= 5) {
			sendGameResult(type);
			return true;
		}

		int leftOblique = 1;
		// ��б����
		for (int i = x + 1, j = y - 1; i <= rightX && j >= topY; ++i, --j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++leftOblique;
		}
		// ��б����
		for (int i = x - 1, j = y + 1; i >= leftX && j <= bottomY; --i, ++j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++leftOblique;
		}
		if (leftOblique >= 5) {
			sendGameResult(type);
			return true;
		}

		int rightOblique = 1;
		// ��б����
		for (int i = x - 1, j = y - 1; i >= leftX && j >= topY; --i, --j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++rightOblique;
		}
		// ��б����
		for (int i = x + 1, j = y + 1; i <= rightX && j <= bottomY; ++i, ++j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++rightOblique;
		}
		if (rightOblique >= 5) {
			sendGameResult(type);
			return true;
		}

		return false;
	}

	private void sendGameResult(int player) {
		Message msg = Message.obtain();
		msg.what = GameConstants.GAME_OVER;
		msg.arg1 = player;
		mNotify.sendMessage(msg);
	}
}
