package com.lany.fivechess.game;

/**
 * 坐标类
 */
public class Point {
    private int X;
    private int Y;

    public Point() {

    }

    public Point(int x, int y) {
        X = x;
        Y = y;
    }

    public int getX() {
        return X;
    }

    public void setX(int x) {
        X = x;
    }

    public int getY() {
        return Y;
    }

    public void setY(int y) {
        Y = y;
    }
}
