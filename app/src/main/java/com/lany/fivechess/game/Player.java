package com.lany.fivechess.game;

public class Player {
	String mName;
	// ���ӻ��Ǻ���
	int type;
	// ʤ��
	int mWin;
	// �ܾ�
	int mLose;

	public Player(String name, int type) {
		this.mName = name;
		// this.mIp = ip;
		this.type = type;
	}

	public Player(int type) {
		if (type == Game.WHITE) {
			this.mName = "White";
		} else if (type == Game.BLACK) {
			this.mName = "Black";
		}
		this.type = type;
	}

	public int getType() {
		return this.type;
	}

	/**
	 * ʤһ��
	 */
	public void win() {
		mWin += 1;
	}

	public String getWin() {
		return String.valueOf(mWin);
	}

	/**
	 * ��һ��
	 */
	public void lose() {
		mLose += 1;
	}
}
