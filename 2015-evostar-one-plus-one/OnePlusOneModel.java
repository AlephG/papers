import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class OnePlusOneModel {
    static class RunResult {
        final int calls;
        final int violations;

        public RunResult(int calls, int violations) {
            this.calls = calls;
            this.violations = violations;
        }
    }

    static double binarySearch(double l, double r) {
        while (r - l > 1e-15) {
            double m = (l + r) / 2;
            double f = 1 - 4.0 / 7.0 / m - Math.exp((7 * m - 8) / (8 - 14 * m));
            if (f >= 0) {
                r = m;
            } else {
                l = m;
            }
        }
        return r;
    }

    static final double maxC = 1 + binarySearch(1, 100);
    static final int runs = 100;
    static ExecutorService par;

    static List<RunResult> processConfiguration(final int N, final double gamma)
        throws ExecutionException, InterruptedException, IOException
    {
        File file = new File(String.format(Locale.US, "logs/one-plus-one-%d-%f.log", N, gamma));
        file.getParentFile().mkdirs();
        List<RunResult> rv = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = in.readLine()) != null) {
                    int ws = line.indexOf(' ');
                    int calls = Integer.parseInt(line.substring(0, ws));
                    int violations = Integer.parseInt(line.substring(ws + 1));
                    rv.add(new RunResult(calls, violations));
                }
            }
        } else {
            List<Callable<RunResult>> tasks = new ArrayList<>();
            for (int t = 0; t < runs; ++t) {
                tasks.add(new OnePlusOne(N, gamma));
            }
            List<Future<RunResult>> result = par.invokeAll(tasks);
            try (PrintWriter out = new PrintWriter(file)) {
                for (int t = 0; t < runs; ++t) {
                    RunResult r = result.get(t).get();
                    rv.add(r);
                    out.println(r.calls + " " + r.violations);
                }
            }
        }
        return rv;
    }

    static String latexExp(double v, int digits) {
        if (v == 0) {
            return "0";
        }
        double scaled = v;
        int exponent = 0;
        while (scaled >= 10) {
            exponent += 1;
            scaled /= 10;
        }
        while (scaled < 1) {
            exponent -= 1;
            scaled *= 10;
        }
        for (int i = 0; i < digits; ++i) scaled *= 10;
        String rounded = String.valueOf((long) Math.round(scaled));
        return "$" + rounded.charAt(0) + (rounded.length() > 1 ? "." + rounded.substring(1) : "") + "\\cdot10^{" + exponent + "}$";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Proven upper bound: 1+C = " + maxC);

        par = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        boolean latexOutput = args.length > 0 && "--latex".equals(args[0]);

        if (latexOutput) {
            System.out.println("\\begin{tabular}{c|cc|c|cc|c}");
            System.out.println("N & \\multicolumn{2}{c|}{Average FF calls} & Average false & $2 e N \\log N$ & $(1+C) e N \\log N$ & Ratio to\\\\");
            System.out.println("& $\\gamma = 1/N$ & $\\gamma = 1$ & queries, $\\gamma=1$ & & & $\\gamma = 1/N$ \\\\\\hline");
        }

        for (final int N : new int[] {10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000}) {
            double maxCV = maxC * Math.E * N * Math.log(N);
            double twoV = 2 * Math.E * N * Math.log(N);

            if (latexOutput) {
                System.out.print(latexExp(N, 0));
            }

            double avg1N = -1;

            for (final double gamma : new double[] { 1.0 / N, 1.0 }) {
                double sum = 0;
                double sumSq = 0;

                double falseSum = 0;

                List<RunResult> results = processConfiguration(N, gamma);
                for (int t = 0; t < results.size(); ++t) {
                    RunResult r = results.get(t);
                    double v = r.calls;
                    sum += v;
                    sumSq += v * v;
                    falseSum += r.violations;
                }

                double avg = sum / runs;
                if (gamma != 1.0) {
                    avg1N = avg;
                }
                double dev = Math.sqrt(sumSq / runs - avg * avg);

                if (!latexOutput) {
                    System.out.printf(Locale.US,
                        "N: %d, gamma: %f, runs: %d: avg = %.2f, 2 e N log N = %.2f, C e N log N = %.2f, dev = %.2f, fq = %f\n",
                        N, gamma, results.size(), avg, twoV, maxCV,
                        dev, falseSum / runs
                    );
                } else {
                    System.out.print(" & " + latexExp(avg, 3));
                    if (gamma == 1.0) {
                        System.out.print(" & " + latexExp(falseSum / runs, 3));
                    }
                }
            }

            if (latexOutput) {
                System.out.print(" & " + latexExp(twoV, 3));
                System.out.print(" & " + latexExp(maxCV, 3));
                System.out.printf(Locale.US, " & %.02f", maxCV / avg1N);
                System.out.println("\\\\");
            }
        }

        if (latexOutput) {
            System.out.println("\\end{tabular}");
        }

        par.shutdownNow();
    }

    static class OnePlusOne implements Callable<RunResult> {
        private final int n;
        private final double gamma;
        private final double log1n;

        public OnePlusOne(int n, double gamma) {
            this.n = n;
            this.gamma = gamma;
            this.log1n = Math.log(1 - 1.0 / n);
        }

        /**
         * Returns the number of a bit that is next to flip.
         * The value will always be greater than zero.
         */
        public int nextOffset() {
            double r01 = r().nextDouble();
            return 1 + (int) (Math.log(r01) / log1n);
        }

        @Override
        public RunResult call() {
            int falseQueries = 0;
            // Initialize all the variables
            BitSet x = new BitSet(n);
            BitSet t = new BitSet(n);
            double[][] q = new double[n + 1][2];
            int xf = 0;
            int count = 1;
            // Main loop
            while (xf < n) {
                // Mutation
                t.clear();
                t.xor(x);
                for (int i = nextOffset() - 1; i < n; i += nextOffset()) {
                    t.flip(i);
                }
                // Mutant fitness computation
                int tf = t.cardinality();
                ++count;
                // Choosing whether OneMax or ZeroMax is used
                if (q[xf][0] > q[xf][1]) ++falseQueries;
                boolean use0 = q[xf][0] > q[xf][1] || q[xf][0] == q[xf][1] && r().nextBoolean();
                int idx = use0 ? 0 : 1;
                // If there is an update for the chosen function...
                if (use0 && tf <= xf || !use0 && tf >= xf) {
                    // Replace the parent with the mutant
                    BitSet tmp = x;
                    x = t;
                    t = tmp;
                    // Recompute the Q values
                    q[xf][idx] /= 2;
                    q[xf][idx] += 0.5 * ((tf - xf) + gamma * Math.max(q[tf][0], q[tf][1]));
                    xf = tf;
                } else {
                    // Recompute the Q values
                    q[xf][idx] /= 2;
                    q[xf][idx] += 0.5 * (gamma * Math.max(q[xf][0], q[xf][1]));
                }
            }
            return new RunResult(count, falseQueries);
        }
    }

    static FastRandom r() {
        return FastRandom.THREAD_LOCAL.get();
    }

    /**
     * CMWC-4096 random generator.
     */
    static class FastRandom extends Random {
        private static final ThreadLocal<FastRandom> THREAD_LOCAL = new ThreadLocal<FastRandom>() {
            @Override
            protected FastRandom initialValue() {
                return new FastRandom();
            }
        };

        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;

        private static final int Q_SIZE = 4096;
        private static final long a = 18782;
        private static final int r = 0xfffffffe;

        private int[] Q;
        private int c;
        private int idx;

        public FastRandom() {
            super();
        }

        public FastRandom(long seed) {
            super(seed);
        }

        private long nextSeed(long seed) {
            return (seed & mask) * multiplier + addend + (seed >>> 47);
        }

        public final void setSeed(long seed) {
            super.setSeed(seed);
            if (Q == null) {
                Q = new int[Q_SIZE];
            }
            seed = nextSeed(seed);
            c = ((int) (seed >>> 16)) % (809430660);
            seed = nextSeed(seed);
            for (int i = 0; i < Q_SIZE; ++i) {
                Q[i] = (int) (seed >>> 16);
                seed = nextSeed(seed);
            }
            this.idx = 0;
        }

        @Override
        protected final int next(int nBits) {
            idx = (idx + 1) & (Q_SIZE - 1);
            long t = a * Q[idx] + c;
            c = (int) (t >>> 32);
            int x = (int) t + c;
            if (x < c) {
                x++;
                c++;
            }
            int rv = Q[idx] = r - x;
            return rv >>> (32 - nBits);
        }
    }
}
