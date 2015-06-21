package com.lany.fivechess.game;

/**
 * 坐标类
 */
public class Coordinate {
	public int x;
	public int y;

	public Coordinate() {

	}

	public Coordinate(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void set(int x, int y) {
		this.x = x;
		this.y = y;
	}

}
