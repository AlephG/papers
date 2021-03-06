package ru.ifmo.steady.problem;

import java.util.Arrays;

import ru.ifmo.steady.Problem;
import ru.ifmo.steady.Solution;

public class WFG9 extends WFG {
    private static final Problem instance = new WFG9(2, 4, 2);
    public static Problem instance() { return instance; }

    public double frontMinX() { return 0; }
    public double frontMaxX() { return 2; }
    public double frontMinY() { return 0; }
    public double frontMaxY() { return 4; }

    protected WFG9(int k, int l, int m) {
        super(9, k, l, m);
        S = new double[m];
        for (int i = 0; i < m; ++i) {
            S[i] = 2 * (i + 1);
        }
        A = new double[m - 1];
        Arrays.fill(A, 1);
    }

    protected double[] evaluateImpl(double[] y) {
        normalize(y);
        y = t1(y);
        t2(y);
        y = t3(y);
        double[] x = calculateX(y);
        double[] rv = new double[m];
        for (int i = 1; i <= m; ++i) {
            rv[i - 1] = D * x[m - 1] + S[i - 1] * Common.concaveShape(x, i);
        }
        return rv;
    }

    private double[] t1(double[] z) {
        double[] rv = new double[z.length];
        double[] w = new double[z.length];
        Arrays.fill(w, 1);
        for (int i = 0; i < z.length - 1; ++i) {
            double aux = Common.rSum(z, w, i + 1, z.length - 1);
            rv[i] = Common.bParam(z[i], aux, 0.98 / 49.98, 0.02, 50);
        }
        rv[z.length - 1] = z[z.length - 1];
        return rv;
    }

    private void t2(double[] z) {
        for (int i = 0; i < k; ++i) {
            z[i] = Common.sDecept(z[i], 0.35, 0.001, 0.05);
        }
        for (int i = k; i < z.length; ++i) {
            z[i] = Common.sMulti(z[i], 30, 95, 0.35);
        }
    }
    private double[] t3(double[] z) {
        double[] r = new double[m];
        for (int i = 1; i < m; ++i) {
            int head = (i - 1) * k / (m - 1) + 1;
            int tail = i * k / (m - 1);
            r[i - 1] = Common.rNonSep(z, head - 1, tail - 1, k / (m - 1));
        }
        r[m - 1] = Common.rNonSep(z, k, z.length - 1, z.length - k);
        return r;
    }
}
