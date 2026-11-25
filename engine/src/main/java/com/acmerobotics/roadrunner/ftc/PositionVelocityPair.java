package com.acmerobotics.roadrunner.ftc;

public final class PositionVelocityPair {
    public final int position;
    public final Integer velocity;
    public final int rawPosition;
    public final Integer rawVelocity;

    public PositionVelocityPair(int position, Integer velocity, int rawPosition, Integer rawVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.rawPosition = rawPosition;
        this.rawVelocity = rawVelocity;
    }
}
