package com.sk.bds.ticket.api.data.model;

public class Pair<T1, T2> {
    private final T1 leftValue;
    private final T2 rightValue;

    public Pair(T1 leftValue, T2 rightValue) {
        this.leftValue = leftValue;
        this.rightValue = rightValue;
    }

    public T1 getLeftValue() {
        return leftValue;
    }

    public T2 getRightValue() {
        return rightValue;
    }
}