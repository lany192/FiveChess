package com.github.lany192.fivechess.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.content.ContextCompat
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side

/**
 * 棋盘自绘 View（SurfaceView）
 *
 * - [render] 是唯一渲染入口，可在任意线程调用，内部切主线程绘制
 * - 触摸事件通过 [onCellTapped] 回调上抛，本 View 不持有任何游戏逻辑
 */
class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    /** 确认落子回调（x, y 为棋盘格下标） */
    var onCellTapped: ((x: Int, y: Int) -> Unit)? = null

    private val chessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = WIN_LINE_WIDTH
        color = WIN_LINE_COLOR
    }

    private var boardWidth = DEFAULT_BOARD_SIZE
    private var boardHeight = DEFAULT_BOARD_SIZE

    private var cellSize = 0
    private var blackBitmap: Bitmap? = null
    private var whiteBitmap: Bitmap? = null
    private var blackLastBitmap: Bitmap? = null
    private var whiteLastBitmap: Bitmap? = null
    private var focusBitmap: Bitmap? = null

    private var renderState: BoardRenderState? = null
    private var focusX = 0
    private var focusY = 0
    private var drawFocus = false

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        boardPaint.strokeWidth = resources.getDimensionPixelSize(R.dimen.boardWidth).toFloat()
        boardPaint.color = Color.BLACK
        isFocusable = true
    }

    /**
     * 设置棋盘规格（默认 15×15），须在 [render] 前调用
     */
    fun configure(width: Int, height: Int) {
        boardWidth = width
        boardHeight = height
        requestLayout()
        post { drawFrame() }
    }

    /**
     * 唯一渲染入口，任意线程可调用
     */
    fun render(state: BoardRenderState) {
        renderState = state
        if (Looper.myLooper() == Looper.getMainLooper()) {
            drawFrame()
        } else {
            post { drawFrame() }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = View.MeasureSpec.getSize(widthMeasureSpec)
        val remainder = width % boardWidth
        if (remainder != 0) {
            width -= remainder
        }
        val height = width * boardHeight / boardWidth
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        cellSize = (right - left) / boardWidth
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cellSize <= 0) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                focusX = (event.x / cellSize).toInt()
                focusY = (event.y / cellSize).toInt()
                drawFocus = true
                post { drawFrame() }
            }
            MotionEvent.ACTION_UP -> {
                drawFocus = false
                val newX = (event.x / cellSize).toInt()
                val newY = (event.y / cellSize).toInt()
                if (isNearFocus(newX, newY)) {
                    onCellTapped?.invoke(focusX, focusY)
                } else {
                    post { drawFrame() }
                }
            }
        }
        return true
    }

    /** 沿用旧 View 的 ±3 格容差确认手感 */
    private fun isNearFocus(x: Int, y: Int): Boolean =
        x < focusX + TAP_TOLERANCE && x > focusX - TAP_TOLERANCE &&
                y < focusY + TAP_TOLERANCE && y > focusY - TAP_TOLERANCE

    // ---------- 绘制 ----------

    private fun drawFrame() {
        val canvas = try {
            holder.lockCanvas()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            canvas.drawPaint(clearPaint)
            drawBoard(canvas)
            val state = renderState
            if (state != null && cellSize > 0) {
                drawStones(canvas, state)
                drawWinLine(canvas, state)
            }
            drawFocus(canvas)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // surface 已销毁
            }
        }
    }

    private fun drawBoard(canvas: Canvas) {
        val size = cellSize
        if (size <= 0) return
        val startX = size / 2
        val startY = size / 2
        val endX = startX + size * (boardWidth - 1)
        val endY = startY + size * (boardHeight - 1)
        for (i in 0 until boardWidth) {
            canvas.drawLine(
                (startX + i * size).toFloat(), startY.toFloat(),
                (startX + i * size).toFloat(), endY.toFloat(), boardPaint,
            )
        }
        for (i in 0 until boardHeight) {
            canvas.drawLine(
                startX.toFloat(), (startY + i * size).toFloat(),
                endX.toFloat(), (startY + i * size).toFloat(), boardPaint,
            )
        }
        // 中心锚点
        drawAnchor(canvas, startX + size * (boardWidth / 2), startY + size * (boardHeight / 2))
        // 四角锚点
        drawAnchor(canvas, startX + size * (boardWidth / 4), startY + size * (boardHeight / 4))
        drawAnchor(canvas, startX + size * (boardWidth / 4 + boardWidth / 2 + 1), startY + size * (boardHeight / 4))
        drawAnchor(canvas, startX + size * (boardWidth / 4), startY + size * (boardHeight / 4 + boardHeight / 2 + 1))
        drawAnchor(canvas, startX + size * (boardWidth / 4 + boardWidth / 2 + 1), startY + size * (boardHeight / 4 + boardHeight / 2 + 1))
    }

    private fun drawAnchor(canvas: Canvas, cx: Int, cy: Int) {
        val radius = resources.getDimensionPixelSize(R.dimen.anchorWidth).toFloat()
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius, boardPaint)
    }

    private fun drawStones(canvas: Canvas, state: BoardRenderState) {
        val black = blackBitmap
        val white = whiteBitmap
        for (x in 0 until state.width) {
            for (y in 0 until state.height) {
                when (state.cells[x][y]) {
                    Side.BLACK -> black?.let { canvas.drawBitmap(it, (x * cellSize).toFloat(), (y * cellSize).toFloat(), chessPaint) }
                    Side.WHITE -> white?.let { canvas.drawBitmap(it, (x * cellSize).toFloat(), (y * cellSize).toFloat(), chessPaint) }
                    null -> Unit
                }
            }
        }
        // 最新落子高亮
        state.lastMove?.let { last ->
            val bitmap = when (state.cells[last.x][last.y]) {
                Side.BLACK -> blackLastBitmap
                Side.WHITE -> whiteLastBitmap
                null -> null
            }
            bitmap?.let { canvas.drawBitmap(it, (last.x * cellSize).toFloat(), (last.y * cellSize).toFloat(), chessPaint) }
        }
    }

    private fun drawWinLine(canvas: Canvas, state: BoardRenderState) {
        if (state.winLine.isEmpty()) return
        val radius = cellSize / 2f - WIN_LINE_WIDTH
        state.winLine.forEach { point ->
            val cx = point.x * cellSize + cellSize / 2f
            val cy = point.y * cellSize + cellSize / 2f
            canvas.drawCircle(cx, cy, radius, winPaint)
        }
    }

    private fun drawFocus(canvas: Canvas) {
        if (!drawFocus) return
        focusBitmap?.let {
            canvas.drawBitmap(it, (focusX * cellSize).toFloat(), (focusY * cellSize).toFloat(), chessPaint)
        }
    }

    // ---------- Surface 生命周期 ----------

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        recreateChessBitmaps(width)
        drawFrame()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun recreateChessBitmaps(viewWidth: Int) {
        blackBitmap?.recycle()
        whiteBitmap?.recycle()
        blackLastBitmap?.recycle()
        whiteLastBitmap?.recycle()
        focusBitmap?.recycle()
        val tileSize = maxOf(1, viewWidth / boardWidth)
        blackBitmap = createChessBitmap(tileSize, R.drawable.black_chess)
        // 白子沿用 red_chess 资源的对比色设计
        whiteBitmap = createChessBitmap(tileSize, R.drawable.red_chess)
        blackLastBitmap = createChessBitmap(tileSize, R.mipmap.black_new)
        whiteLastBitmap = createChessBitmap(tileSize, R.mipmap.white_new)
        focusBitmap = createChessBitmap(tileSize, R.mipmap.focus)
    }

    private fun createChessBitmap(tileSize: Int, drawableRes: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable: Drawable = ContextCompat.getDrawable(context, drawableRes)
            ?: throw IllegalArgumentException("drawable not found: $drawableRes")
        drawable.setBounds(0, 0, tileSize, tileSize)
        drawable.draw(canvas)
        return bitmap
    }

    private companion object {
        const val DEFAULT_BOARD_SIZE = 15
        const val TAP_TOLERANCE = 3
        const val WIN_LINE_WIDTH = 6f
        val WIN_LINE_COLOR = 0x80FF4444.toInt()
    }
}
