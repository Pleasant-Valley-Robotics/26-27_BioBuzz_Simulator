package com.qualcomm.robotcore.hardware;

public class PIDFCoefficients {
    public double p;
    public double i;
    public double d;
    public double f;
    public MotorControlAlgorithm algorithm;

    public PIDFCoefficients() { this(0, 0, 0, 0); }

    public PIDFCoefficients(double p, double i, double d, double f) {
        this(p, i, d, f, MotorControlAlgorithm.PIDF);
    }

    public PIDFCoefficients(double p, double i, double d, double f, MotorControlAlgorithm algorithm) {
        this.p = p; this.i = i; this.d = d; this.f = f; this.algorithm = algorithm;
    }

    public PIDFCoefficients(PIDFCoefficients them) {
        this(them.p, them.i, them.d, them.f, them.algorithm);
    }

    public PIDFCoefficients(PIDCoefficients them) {
        this(them.p, them.i, them.d, 0, MotorControlAlgorithm.LegacyPID);
    }

    @Override public String toString() {
        return String.format("PIDFCoefficients(p=%f i=%f d=%f f=%f alg=%s)", p, i, d, f, algorithm);
    }
}
