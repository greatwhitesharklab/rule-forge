package com.ruleforge.v1.ast;

/**
 * 画布坐标(presentation-only)。运行时忽略,仅前端 React Flow 渲染用。
 * 不存 ReactFlow 完整 node JSON,只存坐标。
 */
public class Position {
    private double x;
    private double y;

    public Position() {
    }

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }
}
