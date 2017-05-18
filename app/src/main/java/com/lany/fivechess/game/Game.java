package com.lany.fivechess.game;

import java.util.Deque;
import java.util.LinkedList;

import android.os.Handler;
import android.os.Message;

/**
 * 处理游戏逻辑
 */
public class Game {
	public static final int SCALE_SMALL = 11;
	public static final int SCALE_MEDIUM = 15;
	public static final int SCALE_LARGE = 19;
	// 自己
	private Player me;
	// 对手
	private Player challenger;

	private int mMode = 0;

	// 默认黑子先出
	private int mActive = 1;
	private int mGameWidth = 0;
	private int mGameHeight = 0;
	private int[][] mGameMap = null;
	private Deque<Point> mActions;

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
		mActions = new LinkedList<Point>();
	}

	public void setMode(int mode) {
		this.mMode = mode;
	}

	public int getMode() {
		return mMode;
	}

	/**
	 * 悔棋一子
	 *
	 * @return 是否可以悔棋
	 */
	public boolean rollback() {
		Point c = mActions.pollLast();
		if (c != null) {
			mGameMap[c.getX()][c.getY()] = 0;
			changeActive();
			return true;
		}
		return false;
	}

	/**
	 * 游戏宽度
	 *
	 * @return 棋盘的列数
	 */
	public int getWidth() {
		return mGameWidth;
	}

	/**
	 * 游戏高度
	 *
	 * @return 棋盘横数
	 */
	public int getHeight() {
		return mGameHeight;
	}

	/**
	 * 落子
	 *
	 * @param x
	 *            横向下标
	 * @param y
	 *            纵向下标
	 * @return 当前位置是否可以下子
	 */
	public boolean addChess(int x, int y) {
		if (mMode == Constants.MODE_FIGHT) {
			if (mGameMap[x][y] == 0) {
				if (mActive == BLACK) {
					mGameMap[x][y] = BLACK;
				} else {
					mGameMap[x][y] = WHITE;
				}
				if (!isGameEnd(x, y, me.getType())) {
					changeActive();
					sendAddChess(x, y);
					mActions.add(new Point(x, y));
				}
				return true;
			}
		} else if (mMode == Constants.MODE_NET) {
			if (mActive == me.getType() && mGameMap[x][y] == 0) {
				mGameMap[x][y] = me.getType();
				mActive = challenger.getType();
				if (!isGameEnd(x, y, me.getType())) {
					mActions.add(new Point(x, y));
				}
				sendAddChess(x, y);
				return true;
			}
		} else if (mMode == Constants.MODE_SINGLE) {
			if (mActive == me.getType() && mGameMap[x][y] == 0) {
				mGameMap[x][y] = me.getType();
				mActive = challenger.getType();
				if (!isGameEnd(x, y, me.getType())) {
					sendAddChess(x, y);
					mActions.add(new Point(x, y));
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * 落子
	 *
	 * @param x
	 *            横向下标
	 * @param y
	 *            纵向下标
	 * @param player
	 *            游戏选手
	 */
	public void addChess(int x, int y, Player player) {
		if (mGameMap[x][y] == 0) {
			mGameMap[x][y] = player.getType();
			mActions.add(new Point(x, y));
			boolean isEnd = isGameEnd(x, y, player.getType());
			mActive = me.getType();
			if (!isEnd) {
				mNotify.sendEmptyMessage(Constants.ACTIVE_CHANGE);
			}
		}
	}

	/**
	 * 落子
	 *
	 * @param c
	 *            下子位置
	 * @param player
	 *            游戏选手
	 */
	public void addChess(Point c, Player player) {
		addChess(c.getX(), c.getY(), player);
	}

	public static int getFighter(int type) {
		if (type == BLACK) {
			return WHITE;
		} else {
			return BLACK;
		}
	}

	/**
	 * 返回当前落子方
	 *
	 * @return mActive
	 */
	public int getActive() {
		return mActive;
	}

	/**
	 * 获取棋盘
	 *
	 * @return 棋盘数据
	 */
	public int[][] getChessMap() {
		return mGameMap;
	}

	/**
	 * 获取棋盘历史
	 *
	 * @return mActions
	 */
	public Deque<Point> getActions() {
		return mActions;
	}

	/**
	 * 重置游戏
	 */
	public void reset() {
		mGameMap = new int[mGameWidth][mGameHeight];
		mActive = BLACK;
		mActions.clear();
	}

	/**
	 * 不需要更新落子方，谁输谁先手
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
		msg.what = Constants.ADD_CHESS;
		msg.arg1 = x;
		msg.arg2 = y;
		mNotify.sendMessage(msg);
	}

	// 判断是否五子连珠
	private boolean isGameEnd(int x, int y, int type) {
		int leftX = x - 4 > 0 ? x - 4 : 0;
		int rightX = x + 4 < mGameWidth - 1 ? x + 4 : mGameWidth - 1;
		int topY = y - 4 > 0 ? y - 4 : 0;
		int bottomY = y + 4 < mGameHeight - 1 ? y + 4 : mGameHeight - 1;

		int horizontal = 1;
		// 横向向左
		for (int i = x - 1; i >= leftX; --i) {
			if (mGameMap[i][y] != type) {
				break;
			}
			++horizontal;
		}
		// 横向向右
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
		// 纵向向上
		for (int j = y - 1; j >= topY; --j) {
			if (mGameMap[x][j] != type) {
				break;
			}
			++vertical;
		}
		// 纵向向下
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
		// 左斜向上
		for (int i = x + 1, j = y - 1; i <= rightX && j >= topY; ++i, --j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++leftOblique;
		}
		// 左斜向下
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
		// 右斜向上
		for (int i = x - 1, j = y - 1; i >= leftX && j >= topY; --i, --j) {
			if (mGameMap[i][j] != type) {
				break;
			}
			++rightOblique;
		}
		// 右斜向下
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
		msg.what = Constants.GAME_OVER;
		msg.arg1 = player;
		mNotify.sendMessage(msg);
	}
}