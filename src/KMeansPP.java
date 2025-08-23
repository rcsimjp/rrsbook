package MyTeam.module.algorithm;

import rescuecore2.worldmodel.EntityID;
import java.util.*;
import static java.util.Comparator.*;

/**
 * KMeans++ 初期化 + K-means 反復の実装
 *
 * API互換:
 *  - new KMeansPP(EntityID[] targets, double[] xs, double[] ys, int n)
 *  - new KMeansPP(int n, List<Collection<EntityID>> memberz)  // resume 用
 *  - void execute(int rep)
 *  - int getClusterNumber()
 *  - double getClusterX(int i), getClusterY(int i)
 *  - Collection<EntityID> getClusterMembers(int i)
 */
public class KMeansPP {

    private EntityID[] targets;   // 対象ID
    private double[] xs, ys;      // 対象の座標
    private Cluster[] result;     // 計算結果クラスタ
    private int n;                // クラスタ数

    private static final int COMMON_SEED = 123456789;

    // 通常利用：データ点とクラスタ数を与える
    public KMeansPP(EntityID[] targets, double[] xs, double[] ys, int n) {
        if (targets == null || xs == null || ys == null)
            throw new IllegalArgumentException("targets/xs/ys must not be null");
        if (targets.length != xs.length || xs.length != ys.length)
            throw new IllegalArgumentException("length mismatch among targets/xs/ys");
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        if (n > targets.length)
            throw new IllegalArgumentException("n must be <= number of points");

        this.targets = targets;
        this.xs = xs;
        this.ys = ys;
        this.n = n;
    }

    // resume 用：既知のメンバー集合からクラスタを復元
    public KMeansPP(int n, List<Collection<EntityID>> memberz) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        if (memberz == null || memberz.size() != n)
            throw new IllegalArgumentException("member list size must equal n");

        this.n = n;
        this.result = new Cluster[n];
        for (int i = 0; i < n; ++i) {
            Collection<EntityID> members = memberz.get(i);
            this.result[i] = new Cluster(members);
        }
    }

    /** K-means の反復を rep 回実行（初期中心は k-means++ で選択） */
    public void execute(int rep) {
        if (rep <= 0) throw new IllegalArgumentException("rep must be positive");

        // --- k-means++ 初期化 ---
        this.result = initKMeansPlusPlus(this.targets, this.xs, this.ys, this.n);

        // --- K-means 反復 ---
        for (int it = 0; it < rep; ++it) {
            for (Cluster c : this.result) c.clearMembers();

            // 各点を最も近い中心へ割当
            for (int j = 0; j < this.targets.length; ++j) {
                assign(this.result, this.targets[j], this.xs[j], this.ys[j]);
            }
            // 各クラスタ中心を更新
            for (Cluster c : this.result) c.updateCenter();
        }
    }

    public int getClusterNumber() {
        return (this.result == null) ? 0 : this.result.length;
    }

    public double getClusterX(int i) {
        checkResultReady();
        checkIndex(i);
        return this.result[i].getCX();
    }

    public double getClusterY(int i) {
        checkResultReady();
        checkIndex(i);
        return this.result[i].getCY();
    }

    public Collection<EntityID> getClusterMembers(int i) {
        checkResultReady();
        checkIndex(i);
        return this.result[i].getMembers(); // defensive copy
    }

    // ---------------- 内部実装 ----------------

    /** 厳密な k-means++ 初期化：D(x)^2 に比例して中心をサンプリング */
    private static Cluster[] initKMeansPlusPlus(EntityID[] targets, double[] xs, double[] ys, int n) {
        Cluster[] centers = new Cluster[n];
        for (int i = 0; i < n; ++i) centers[i] = new Cluster();

        Random random = new Random(COMMON_SEED);

        // 1) 最初の中心：一様ランダムに1点選択
        int first = random.nextInt(targets.length);
        centers[0].addMember(targets[first], xs[first], ys[first]);
        centers[0].updateCenter();

        // 2) 各点の「既選中心までの最小二乗距離」を保持
        double[] d2 = new double[targets.length];
        Arrays.fill(d2, Double.POSITIVE_INFINITY);
        updateMinSquaredDistances(d2, xs, ys, centers[0].getCX(), centers[0].getCY());

        // 3) 残り n-1 個の中心を D(x)^2 に比例して選ぶ
        for (int i = 1; i < n; ++i) {
            double sum = 0.0;
            for (double v : d2) sum += v;

            int nextIndex;
            if (sum == 0.0) {
                // 全点が同一点など：一様ランダムにフォールバック
                nextIndex = random.nextInt(targets.length);
            } else {
                double r = random.nextDouble() * sum;
                double acc = 0.0;
                nextIndex = 0;
                for (int j = 0; j < targets.length; ++j) {
                    acc += d2[j];
                    if (acc >= r) { nextIndex = j; break; }
                }
            }

            centers[i].addMember(targets[nextIndex], xs[nextIndex], ys[nextIndex]);
            centers[i].updateCenter();
            updateMinSquaredDistances(d2, xs, ys, centers[i].getCX(), centers[i].getCY());
        }

        return centers;
    }

    /** d2[j] を「(x_j, y_j) と最近中心の最小二乗距離」に更新 */
    private static void updateMinSquaredDistances(double[] d2, double[] xs, double[] ys, double cx, double cy) {
        for (int j = 0; j < xs.length; ++j) {
            double dx = xs[j] - cx;
            double dy = ys[j] - cy;
            double dist2 = dx * dx + dy * dy;
            if (dist2 < d2[j]) d2[j] = dist2;
        }
    }

    /** 最も近い中心のクラスタに (id, x, y) を追加 */
    private static void assign(Cluster[] clusters, EntityID id, double x, double y) {
        Cluster best = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (Cluster c : clusters) {
            double dx = c.getCX() - x;
            double dy = c.getCY() - y;
            double d = Math.hypot(dx, dy);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        // n>=1 前提で best は必ず非null
        best.addMember(id, x, y);
    }

    private void checkResultReady() {
        if (this.result == null)
            throw new IllegalStateException("KMeansPP: execute() or resume-constructor must be called before reading results.");
        if (this.result.length != this.n)
            throw new IllegalStateException("KMeansPP: internal cluster array size mismatch.");
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= this.result.length)
            throw new IndexOutOfBoundsException("cluster index out of range: " + i);
    }

    // ---------------- クラスタ表現 ----------------

    private static class Cluster {
        private double cx = 0.0;
        private double cy = 0.0;

        // メンバー集合（防御的コピーで外部へ返す）
        private Collection<EntityID> members = new LinkedList<>();

        // 重心更新用の座標合計
        private double sumx = 0.0, sumy = 0.0;

        Cluster() {}

        // resume 用：既知メンバーから構築（中心は後から updateCenter で再計算される想定）
        Cluster(Collection<EntityID> members) {
            if (members != null) this.members = new LinkedList<>(members);
            // sumx/sumy はこの ctor では与えられないため、
            // resume 側では KMeansHungarianAllocator が中心座標を使わない限り問題にならない。
            // 中心が必要なら別途（ID→座標）で再計算する実装に拡張して下さい。
        }

        void addMember(EntityID id, double x, double y) {
            this.members.add(id);
            this.sumx += x;
            this.sumy += y;
        }

        Collection<EntityID> getMembers() {
            return new ArrayList<>(this.members);
        }

        void clearMembers() {
            this.members.clear();
            this.sumx = 0.0;
            this.sumy = 0.0;
        }

        void updateCenter() {
            if (this.members.isEmpty()) return;
            int sz = this.members.size();
            this.cx = this.sumx / sz;
            this.cy = this.sumy / sz;
        }

        double getCX() { return this.cx; }
        double getCY() { return this.cy; }
    }
}
