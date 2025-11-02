package com.github.lany192.fivechess.game;

public class RobotAI {
    private int mWidth = 0;
    private int mHeight = 0;
    private static final int MAX_DEPTH = 4; // 搜索深度
    
    // 评分表：连子数对应的分数
    private static final int[] SCORE = {
        0,           // 0个子
        1,           // 1个子
        10,          // 2个子
        100,         // 3个子
        10000,       // 4个子
        1000000      // 5个子（胜利）
    };
    
    public RobotAI(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * 获取最佳下棋位置
     * @param map 当前棋盘状态
     * @return 最佳位置
     */
    public Point getPosition(int[][] map) {
        int[][] tempMap = new int[mWidth][mHeight];
        // 复制当前棋盘状态
        for (int i = 0; i < mWidth; i++) {
            System.arraycopy(map[i], 0, tempMap[i], 0, mHeight);
        }
        
        // 使用MiniMax算法寻找最佳位置
        Result result = miniMax(tempMap, MAX_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, true, Game.WHITE);
        return new Point(result.x, result.y);
    }
    
    /**
     * MiniMax算法实现，带Alpha-Beta剪枝
     * @param map 当前棋盘
     * @param depth 搜索深度
     * @param alpha Alpha值
     * @param beta Beta值
     * @param maximizingPlayer 是否为最大化玩家
     * @param currentPlayer 当前玩家
     * @return 包含位置和评分的结果
     */
    private Result miniMax(int[][] map, int depth, int alpha, int beta, boolean maximizingPlayer, int currentPlayer) {
        // 检查终止条件
        if (depth == 0) {
            return new Result(-1, -1, evaluate(map, currentPlayer));
        }
        
        // 生成可能的走法
        Point[] moves = generateMoves(map);
        if (moves.length == 0) {
            return new Result(-1, -1, evaluate(map, currentPlayer));
        }
        
        Result bestResult = new Result(-1, -1, maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE);
        
        for (Point move : moves) {
            // 执行走法
            map[move.getX()][move.getY()] = currentPlayer;
            
            // 递归调用
            Result result = miniMax(map, depth - 1, alpha, beta, !maximizingPlayer, 
                                  currentPlayer == Game.BLACK ? Game.WHITE : Game.BLACK);
            
            // 撤销走法
            map[move.getX()][move.getY()] = 0;
            
            if (maximizingPlayer) {
                if (result.score > bestResult.score) {
                    bestResult.score = result.score;
                    bestResult.x = move.getX();
                    bestResult.y = move.getY();
                }
                alpha = Math.max(alpha, bestResult.score);
            } else {
                if (result.score < bestResult.score) {
                    bestResult.score = result.score;
                    bestResult.x = move.getX();
                    bestResult.y = move.getY();
                }
                beta = Math.min(beta, bestResult.score);
            }
            
            // Alpha-Beta剪枝
            if (beta <= alpha) {
                break;
            }
        }
        
        return bestResult;
    }
    
    /**
     * 评估当前局面分数
     * @param map 棋盘状态
     * @param player 当前玩家
     * @return 局面评分
     */
    private int evaluate(int[][] map, int player) {
        int score = 0;
        
        // 评估所有行
        for (int i = 0; i < mWidth; i++) {
            for (int j = 0; j < mHeight - 4; j++) {
                score += evaluateLine(map, player, i, j, 0, 1); // 横向
            }
        }
        
        // 评估所有列
        for (int i = 0; i < mWidth - 4; i++) {
            for (int j = 0; j < mHeight; j++) {
                score += evaluateLine(map, player, i, j, 1, 0); // 纵向
            }
        }
        
        // 评估正对角线
        for (int i = 0; i < mWidth - 4; i++) {
            for (int j = 0; j < mHeight - 4; j++) {
                score += evaluateLine(map, player, i, j, 1, 1); // 正对角线
            }
        }
        
        // 评估反对角线
        for (int i = 4; i < mWidth; i++) {
            for (int j = 0; j < mHeight - 4; j++) {
                score += evaluateLine(map, player, i, j, -1, 1); // 反对角线
            }
        }
        
        return score;
    }
    
    /**
     * 评估一条线上的得分
     * @param map 棋盘
     * @param player 玩家
     * @param x 起始x坐标
     * @param y 起始y坐标
     * @param dx x方向增量
     * @param dy y方向增量
     * @return 得分
     */
    private int evaluateLine(int[][] map, int player, int x, int y, int dx, int dy) {
        int playerCount = 0;      // 玩家棋子数
        int opponentCount = 0;    // 对手棋子数
        int emptyCount = 0;       // 空位数
        
        // 统计连续5个位置的情况
        for (int i = 0; i < 5; i++) {
            int curX = x + i * dx;
            int curY = y + i * dy;
            
            // 边界检查
            if (curX < 0 || curX >= mWidth || curY < 0 || curY >= mHeight) {
                return 0;
            }
            
            if (map[curX][curY] == player) {
                playerCount++;
            } else if (map[curX][curY] == 0) {
                emptyCount++;
            } else {
                opponentCount++;
            }
        }
        
        // 如果同时包含双方棋子，则该线路无效
        if (playerCount > 0 && opponentCount > 0) {
            return 0;
        }
        
        // 计算得分
        if (playerCount > 0) {
            // 我方有利局面
            return SCORE[playerCount] + emptyCount;
        } else if (opponentCount > 0) {
            // 对手有利局面，需要防守
            return -(SCORE[opponentCount] + emptyCount);
        }
        
        return 0;
    }
    
    /**
     * 生成可能的走法
     * @param map 当前棋盘状态
     * @return 可能的走法列表
     */
    private Point[] generateMoves(int[][] map) {
        // 先找周围有棋子的位置，提高效率
        Point[] moves = new Point[mWidth * mHeight];
        int count = 0;
        
        for (int i = 0; i < mWidth; i++) {
            for (int j = 0; j < mHeight; j++) {
                if (map[i][j] == 0 && hasNeighbor(map, i, j)) {
                    moves[count++] = new Point(i, j);
                }
            }
        }
        
        // 如果没有可选位置，返回中心位置
        if (count == 0) {
            moves[count++] = new Point(mWidth / 2, mHeight / 2);
        }
        
        // 创建实际大小的数组
        Point[] result = new Point[count];
        System.arraycopy(moves, 0, result, 0, count);
        return result;
    }
    
    /**
     * 检查指定位置周围是否有棋子
     * @param map 棋盘
     * @param x x坐标
     * @param y y坐标
     * @return 是否有邻居
     */
    private boolean hasNeighbor(int[][] map, int x, int y) {
        int minX = Math.max(0, x - 2);
        int maxX = Math.min(mWidth - 1, x + 2);
        int minY = Math.max(0, y - 2);
        int maxY = Math.min(mHeight - 1, y + 2);
        
        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                if (map[i][j] != 0) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 结果类，包含位置和评分
     */
    private static class Result {
        int x, y, score;
        
        Result(int x, int y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }
    
    // 废弃原有方法，因为不再使用
    // updateValue方法已废弃
    // plaValue和cpuValue数组已废弃
    // black和white数组已废弃
}