package com.lany.fivechess.game

class Player {
    private var mName: String? = null
    // 白子还是黑子
    var type: Int = 0
        private set
    // 胜局
    private var mWin: Int = 0
    // 败局
    private var mLose: Int = 0

    constructor(name: String, type: Int) {
        this.mName = name
        // this.mIp = ip;
        this.type = type
    }

    constructor(type: Int) {
        if (type == Game.WHITE) {
            this.mName = "White"
        } else if (type == Game.BLACK) {
            this.mName = "Black"
        }
        this.type = type
    }

    /**
     * 胜一局
     */
    fun win() {
        mWin += 1
    }

    val win: String
        get() = mWin.toString()

    /**
     * 负一局
     */
    fun lose() {
        mLose += 1
    }
}