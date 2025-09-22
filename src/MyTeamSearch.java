package MyTeam.module.complex;

import adf.core.agent.info.*;
import adf.core.component.module.complex.Search;
import adf.core.component.module.algorithm.Clustering;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.develop.DevelopData;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import java.util.*;

public class MyTeamSearch extends Search {

    // クラスタリング器
    private Clustering clustering;
    // calc()で決定した探索対象(1件)．getTarget()で返す値
    private EntityID result;
    // 固定シード（1）の擬似乱数
    // （毎回同じ乱数列が生成され，結果が再現可能）
    private Random random = new Random(1);

    public MyTeamSearch
	(AgentInfo ai, WorldInfo wi, ScenarioInfo si,
	 ModuleManager mm, DevelopData dd)
    {
	super(ai, wi, si, mm, dd);

	// ClusteringKeyのベースとなる文字列を定義
	String clusteringKey = "SampleSearch.Clustering";
	// エージェントの種類を取得して文字列に追加
	switch (ai.me().getStandardURN())
	{
	    case FIRE_BRIGADE: 
		clusteringKey += ".Fire";
		break;
	    case AMBULANCE_TEAM:
		clusteringKey += ".Ambulance";
		break;
	    case POLICE_FORCE:
		clusteringKey += ".Police";
		break;
	}

	// clusteringKeyとmodule.cfgに従ってモジュールを取得
	this.clustering =
	    mm.getModule(clusteringKey,
			 "adf.impl.module.algorithm.KMeansClustering");
	// モジュールの組み込み
	this.registerModule(this.clustering);
    }

    @Override
    public Search calc()
    {
	// 自分のエージェントIDを取得
	EntityID me = this.agentInfo.getID();
	// 自分の担当クラスタ番号を取得
	int idx = this.clustering.getClusterIndex(me);
	// そのクラスタに属する全エンティティID集合を取得
	Collection<EntityID> cluster =
	    this.clustering.getClusterEntityIDs(idx);
        
	// 擬似乱数を用いてランダムに選択するために，Listに変換
	List<EntityID> list = new ArrayList<>(cluster);
    
	// 擬似乱数を用いてランダムに選択
	int n = list.size();
	int r = this.random.nextInt(n);
	this.result = list.get(r);

	return this;
    }

    // calc()で決定した探索対象をgetTarget()で返す
    @Override
    public EntityID getTarget()
    {
	return this.result;
    }
}
