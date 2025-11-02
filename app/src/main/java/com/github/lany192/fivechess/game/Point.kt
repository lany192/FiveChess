package com.github.lany192.fivechess.game

/**
 * 坐标类
 */
class Point {
    private var x: Int = 0
    private var y: Int = 0

    constructor() {

    }

    constructor(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun setX(x: Int) {
        this.x = x
    }

    fun getX(): Int {
        return x
    }

    fun setY(y: Int) {
        this.y = y
    }

    fun getY(): Int {
        return y
    }

}