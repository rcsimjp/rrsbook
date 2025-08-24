package MyTeam.module.algorithm;

import rescuecore2.worldmodel.EntityID;
import java.util.*;
import static java.util.Comparator.*;

public class KMeansPP
{
    private EntityID[] targets;
    private double[] xs;
    private double[] ys;

    private Cluster[] result;

    private int n;

    private static final int COMMON_SEED = 123456789;

    public KMeansPP(
        EntityID[] targets, double[] xs, double[] ys, int n)
    {
        this.targets = targets;
        this.xs = xs;
        this.ys = ys;
        this.n = n;
    }

    public KMeansPP(
        int n, List<Collection<EntityID>> memberz)
    {
        this.result = new Cluster[n];
        this.n = n;

        for (int i=0; i<n; ++i)
        {
            Collection<EntityID> members = memberz.get(i);
            this.result[i] = new Cluster(members);
        }
    }

    public void execute(int rep)
    {
        this.result = init(this.targets, this.xs, this.ys, this.n);

        for (int i=0; i<rep; ++i)
        {
            Arrays.stream(this.result).forEach(Cluster::clearMembers);
            for (int j=0; j<this.targets.length; ++j)
                assign(this.result, this.targets[j], this.xs[j], this.ys[j]);
            Arrays.stream(this.result).forEach(Cluster::updateCenter);
        }
    }

    public int getClusterNumber()
    {
        return (this.result == null) ? this.n : this.result.length;
    }

    public double getClusterX(int i)
    {
	checkResultReady();
	checkIndex(i);
	return this.result[i].getCX();
    }

    public double getClusterY(int i)
    {
	checkResultReady();
	checkIndex(i);
	return this.result[i].getCY();
    }

    public Collection<EntityID> getClusterMembers(int i)
    {
	checkResultReady();
	checkIndex(i);
	return this.result[i].getMembers();
    }

    private static Cluster[] init(
        EntityID[] targets, double[] xs, double[] ys, int n)
    {
	if (n <= 0) throw new IllegalArgumentException("n must be positive");
	if (n > targets.length)
	    throw new IllegalArgumentException("n must be <= number of points");

        Cluster[] ret = new Cluster[n];
        for (int i=0; i<n; ++i) ret[i] = new Cluster();

        Random random = new Random(COMMON_SEED);

	// 1.「1つ目」の初期セントロイドは一様ランダムに選ぶ
        int r = random.nextInt(targets.length);
        ret[0].addMember(targets[r], xs[r], ys[r]);
        ret[0].updateCenter();

	// 2. 各点の D(x)^2 = 既選中心までの最小二乗距離 を管理
	double[] d2 = new double[targets.length];
	Arrays.fill(d2, Double.POSITIVE_INFINITY);

	// 「1つ目」に基づき d2 を更新
	for (int j=0; j<targets.length; ++j)
	{
	    double dx = xs[j] - ret[0].getCX();
	    double dy = ys[j] - ret[0].getCY();
	    double dist2 = dx*dx + dy*dy;
	    d2[j] = Math.min(d2[j], dist2);
	}

	// 3. 残りの初期セントロイドを選ぶ
	for (int i=1; i<n; ++i)
        {
	    double sum = 0.0;
	    for (double v : d2) sum += v;

	    // 例外：全て0（同一点群等）の場合は一様選択
	    int nextIndex;
	    if (sum == 0.0)
	    {
		nextIndex = random.nextInt(targets.length);
	    } else {
		double r = random.nextDouble() * sum; // D(x)^2に比例した確率
		double acc = 0.0;
		nextIndex = 0;
		for (int j=0; j<targets.length; ++j)
	        {
		    acc += d2[j];
		    if (acc >= r) { nextIndex = j; break; }
		}
	    }

	    ret[i].addMember(targets[nextIndex], xs[nextIndex], ys[nextIndex]);
	    ret[i].updateCenter();

	    // 新しいセントロイドでd2を更新(最小二乗距離)
	    double cx = ret[i].getCX();
	    double cy = ret[i].getCY();
	    for (int j=0; j<targets.length; ++j)
	    {
		double dx = xs[j] - cx;
		double dy = ys[j] - cy;
		double dist2 = dx*dx + dy*dy;
		d2[j] = Math.min(d2[j], dist2);
	    }
	}

	return ret;
    }

    private static void assign(
        Cluster[] clusters, EntityID id, double x, double y)
    {
        Optional<Cluster> cluster = Arrays.stream(clusters)
            .min(comparing(c -> {
                double cx = c.getCX();
                double cy = c.getCY();
                return Math.hypot(cx-x, cy-y);
            }));

        cluster.get().addMember(id, x, y);
    }

    private static class Cluster
    {
        private double cx = 0.0;
        private double cy = 0.0;

        private Collection<EntityID> members = new LinkedList<>();

        private double sumx = 0.0;
        private double sumy = 0.0;

        public Cluster() {}

        public Cluster(Collection<EntityID> members)
        {
	    this.members = (members == null) ? new LinkedList<>() : new LinkedList<>(members);
        }

        public void addMember(EntityID id, double x, double y)
        {
            this.members.add(id);
            this.sumx += x;
            this.sumy += y;
        }

        public Collection<EntityID> getMembers()
        {
            return new ArrayList<>(this.members);
        }

        public void clearMembers()
        {
            this.members.clear();
            this.sumx = 0.0;
            this.sumy = 0.0;
        }

        public void updateCenter()
        {
            if (this.members.isEmpty()) return;

            this.cx = this.sumx / this.members.size();
            this.cy = this.sumy / this.members.size();
        }

        public double getCX()
        {
            return this.cx;
        }

        public double getCY()
        {
            return this.cy;
        }
    }

    private void checkResultReady()
    {
	if (this.result == null)
	{
	    throw new IllegalStateException("KMeans++ has not been executed yet.");
	}
    }

    private void checkIndex(int i)
    {
	if (i < 0 || i >= this.result.length)
	{
	    throw new IndexOutOfBoundsException("Invalid cluster index: " + i);
	}
    }
}
