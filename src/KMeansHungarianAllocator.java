package MyTeam.module.algorithm;

import adf.core.agent.info.*;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.algorithm.StaticClustering;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.precompute.PrecomputeData;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.standard.entities.*;
import static rescuecore2.standard.entities.StandardEntityURN.*;
import java.util.*;
import static java.util.Comparator.*;

public class KMeansHungarianAllocator extends StaticClustering
{
    // --- フィールド ---

    // エージェントID → クラスタ番号 の対応表
    private final Map<EntityID, Integer> assignment = new HashMap<>();

    // k-means++ のクラスタリング実行オブジェクト
    private KMeansPP clusterer;

    // クラスタ数（未設定時は 0 として扱う）
    private int n = 0;

    // このモジュールを動かしている自エージェントの種別（FIRE_BRIGADE 等）
    private final StandardEntityURN urn;

    // --- 定数 ---

    // k-means++ の繰り返し回数
    private static final int REP_PRECOMPUTE = 20;
    private static final int REP_PREPARE = 20;

    // 事前計算の保存キー（urn で名前空間化する）
    private static final String MODULE_NAME =
	"MyTeam.module.algorithm.KMeansHungarianAllocator";
    private static final String PD_CLUSTER_N = MODULE_NAME + ".n";
    private static final String PD_CLUSTER_M = MODULE_NAME + ".m";
    private static final String PD_CLUSTER_A = MODULE_NAME + ".a";

    // --- コンストラクタ ---

    // KMeansHungarianAllocatorオブジェクト生成時に呼び出される初期化処理
    // クラス生成時にエージェント種別を urn に格納
    public KMeansHungarianAllocator(
            AgentInfo ai, WorldInfo wi, ScenarioInfo si,
            ModuleManager mm, DevelopData dd)
    {
        super(ai, wi, si, mm, dd);
        this.urn = this.agentInfo.me().getStandardURN();
    }

    // --- 外部公開API ---
    
    // --- 事前計算ありの場合 ---

    // 計算実行＆結果保存
    @Override
    public Clustering precompute(PrecomputeData pd)
    {
	// 重複した処理の実行を回避
	super.precompute(pd);
	if (this.getCountPrecompute() > 1) return this;

	// 念のため前回結果をクリア
        this.assignment.clear();

	this.initN();                  //クラスタ数を決定
	this.initClusterer();          // k-means++の初期セントロイドを用意
	this.clusterer.execute(REP_PRECOMPUTE); // k-means++を実行
	this.assignAgentsToClusters(); // Hungarianで1対1割当を決定

	// 結果をPrecomputeDataに保存（urnで名前空間化）
	pd.setInteger(this.addSuffixToKey(PD_CLUSTER_N), this.n);
	for (Map.Entry<EntityID, Integer> e : this.assignment.entrySet())
        {
	    EntityID agent = e.getKey();
	    int i = e.getValue();
	    // i番目のクラスタの全要素を取得
	    Collection<EntityID> cluster =
		this.clusterer.getClusterMembers(i);
	    // i番目のクラスタの要素を保存
	    pd.setEntityIDList(
                this.addSuffixToKey(PD_CLUSTER_M, i),
		new ArrayList<>(cluster));
	    // i番目のクラスタに対応するエージェントを保存
	    pd.setEntityID(this.addSuffixToKey(PD_CLUSTER_A, i), agent);
	}
	return this;
    }
    
    // 計算結果読み込み
    @Override
    public Clustering resume(PrecomputeData pd)
    {
        super.resume(pd);
        // 重複した処理の実行を回避
        if (this.getCountResume() > 1) return this;

	// 念のため前回結果をクリア
        this.assignment.clear();

	// Precomputeから保存された結果の読み込み
	// クラスタ数の読み込み
	this.n = pd.getInteger(this.addSuffixToKey(PD_CLUSTER_N));
	// 各クラスタに属するエンティティID群を格納するリストを用意
	// リスト全体の要素数 = クラスタ数（n）
	// リストの各要素（Collection<EntityID>）=
	//                    1つのクラスタを構成するEntityIDの集合
	List<Collection<EntityID>> clusters = new ArrayList<>(this.n);
	for (int i=0; i<this.n; ++i)
        {
	    // i番目のクラスタの要素の読み込み
	    List<EntityID> cluster =
                pd.getEntityIDList(this.addSuffixToKey(PD_CLUSTER_M, i));
	    // i番目のクラスタと結び付けられたエージェントの読み込み
	    EntityID agent =
                pd.getEntityID(this.addSuffixToKey(PD_CLUSTER_A, i));

	    clusters.add(cluster);
	    // エージェントとクラスタの結び付きを復元
	    this.assignment.put(agent, i);
	}

	// PrecomputeDataから読み込んだクラスタ情報を用いて
	// KMeansPPクラスタリングのインスタンスを復元
	this.clusterer = new KMeansPP(this.n, clusters);
	
        return this;
    }
    
    // --- 事前計算なしの場合 ---
    
    @Override
    public Clustering preparate()
    {
        super.preparate();
        // 重複した処理の実行を回避
        if (this.getCountPreparate() > 1) return this;

	this.initN();                  //クラスタ数を決定
	this.initClusterer();          // k-means++の初期セントロイドを用意
	this.clusterer.execute(REP_PREPARE); // k-means++を実行
	this.assignAgentsToClusters(); // Hungarianで1対1割当を決定
 	
        return this;
    }

    // --- クラスタリング計算本体（この例では未使用）---
    
    @Override
    public Clustering calc()
    {
        return this;
    }

    // --- アクセサ（Getter）API ---
    
    // 他のモジュールがクラスタ数を取得する際に使用
    @Override
    public int getClusterNumber()
    {
        return this.n;
    }

    // 他のモジュールがentityに関連したクラスタの番号を取得する際に使用
    @Override
    public int getClusterIndex(StandardEntity entity)
    {
        return this.getClusterIndex(entity.getID());
    }

    // 他のモジュールがentityに関連したクラスタの番号を取得する際に使用
    @Override
    public int getClusterIndex(EntityID id)
    {
        if (!this.assignment.containsKey(id)) return -1;
        return this.assignment.get(id);
    }

    // 他のモジュールがi番目のクラスタ要素をStandardEntityで取得する際に
    // 使用
    @Override
    public Collection<StandardEntity> getClusterEntities(int i)
    {
	if (i < 0 || i >= this.n) return Collections.emptyList();

        Collection<EntityID> ids = this.getClusterEntityIDs(i);
        Collection<StandardEntity> ret = new ArrayList<>(ids.size());
        for (EntityID id : ids)
        {
            ret.add(this.worldInfo.getEntity(id));
        }
        return ret;
    }

    // 他のモジュールがi番目のクラスタ要素をEntityIDで取得する際に使用
    @Override
    public Collection<EntityID> getClusterEntityIDs(int i)
    {
	if (i < 0 || i >= this.n || this.clusterer == null)
	    return Collections.emptyList();
        return this.clusterer.getClusterMembers(i);
    }

    // --- 内部処理 ---
    
    // k-means++のクラスタ数の決定処理
    private void initN()
    {
	switch (this.urn)
	    {
	    // 消防隊
	    case FIRE_BRIGADE:
		// グループ数 = シミュレーション全体の消防隊数
		this.n = this.scenarioInfo.getScenarioAgentsFb();
		break;
	    // 土木隊
	    case POLICE_FORCE:
		// グループ数 = シミュレーション全体の土木隊数
		this.n = this.scenarioInfo.getScenarioAgentsPf();
		break;
            // 救急隊
	    case AMBULANCE_TEAM:
		// グループ数 = シミュレーション全体の救急隊数
		this.n = this.scenarioInfo.getScenarioAgentsAt();
		break;
	    default:
		this.n = 0;
	    }
    }

    // k-means++の初期セントロイドの決定処理
    private void initClusterer()
    {
	// 次のオブジェクトを全て取得
	// 道路/消火栓
	// 建物/ガソリンスタンド
	// 避難所
	// 土木隊司令所/消防隊司令所/救急隊司令所
	List<StandardEntity> entities = new ArrayList<>(
        this.worldInfo.getEntitiesOfType(
            ROAD, HYDRANT,
            BUILDING, GAS_STATION,
            REFUGE,
            POLICE_OFFICE, FIRE_STATION, AMBULANCE_CENTRE));
	// リストをIDでソート
	entities.sort(comparing(e -> e.getID().getValue()));

	// データをID/X座標/Y座標の配列に整形
	int size = entities.size();
	EntityID[] is = new EntityID[size];
	double[] xs = new double[size];
	double[] ys = new double[size];

	for (int i=0; i<size; ++i)
	{
	    // StandardEntityクラスでは座標を取得不可
	    // 地図上のオブジェクトを表すAreaクラスにダウンキャスト
	    // (エージェントの場合はHumanクラス)
	    Area area = (Area)entities.get(i);
	    is[i] = area.getID();
	    xs[i] = area.getX();
	    ys[i] = area.getY();
	}

	// KMeansPP の初期化
	this.clusterer = new KMeansPP(is, xs, ys, this.n);
    }

    // Hungarianアルゴリズムを用いてエージェントにクラスタを割当
    // 前提：エージェント数とクラスタ数が一致（1対1の割当）
    private void assignAgentsToClusters()
    {
	// 同種類のエージェントを全て取得
	List<StandardEntity> agents = new ArrayList<>(
            this.worldInfo.getEntitiesOfType(this.urn));
	// 取得したエージェントをIDでソート
	agents.sort(comparing(e -> e.getID().getValue()));

	// 「エージェント数 = クラスタ数」（前提）をチェック
	if (agents.size() != this.n) {
            throw new IllegalStateException(
		"前提条件違反: agents.size()=" +
		agents.size() + " と n=" + this.n + " が一致していません。");
        }
	
	// エージェントとクラスタの距離をコスト行列（n × n）に格納
	int[][] costs = new int[this.n][this.n];
	for (int row=0; row<this.n; ++row)
	{
	    Human agent = (Human)agents.get(row);
	    double x = agent.getX();
	    double y = agent.getY();

	    for (int col=0; col<this.n; ++col)
            {
		// col番目のクラスタのX/Y座標を取得
		double cx = this.clusterer.getClusterX(col);
		double cy = this.clusterer.getClusterY(col);
		// エージェントの座標とクラスタの座標間の距離を計算
		costs[row][col] = (int)Math.hypot(cx-x, cy-y);
	    }
	}

	// Hungarianで最適な1対1割当を決定
	int[] result = Hungarian.execute(costs);
	for (int row=0; row<agents.size(); ++row)
        {
	    EntityID id = agents.get(row).getID();
	    // エージェントのIDにクラスタの番号を割り当てて保存
	    this.assignment.put(id, result[row]);
        }
    }

    // --- 補助メソッド ---

    // urn（エージェント種別）単位でキーを名前空間化

    // 保存用キーにエージェントの種類を区別するための接尾辞を足すメソッド
    private String addSuffixToKey(String path)
    {
	return path + "." + this.urn;
    }

    // 保存用キーにエージェントの種類と要素番号を区別するための
    // 接尾辞を足すメソッド
    private String addSuffixToKey(String path, int i)
    {
	return this.addSuffixToKey(path) + "." + i;
    }

// クラスの終わり
}
