package tutorial.module.algorithm;

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
import static java.util.stream.Collectors.*;
import static java.util.Comparator.*;

public class KmeansPPClustering extends StaticClustering
{
    // エージェントとクラスタの結びつけを保存
    private Map<EntityID, Integer> assignment = new HashMap<>();
    // グループ分けとグループの保存
    private KmeansPP clusterer;
    // グループ数
    private int n = 0;
    // エージェントの種類
    private StandardEntityURN urn;

    public KmeansPPClustering(
            AgentInfo ai, WorldInfo wi, ScenarioInfo si,
            ModuleManager mm, DevelopData dd)
    {
        super(ai, wi, si, mm, dd);
        this.urn = this.agentInfo.me().getStandardURN();
    }

    // 事前計算 - 計算実行 & 結果保存部
    @Override
    public Clustering precompute(PrecomputeData pd)
    {
        super.precompute(pd);
        // 重複した処理の実行を回避
        if (this.getCountPrecompute() > 1) return this;

        return this;
    }

    // 事前計算 - 計算結果読み込み部
    @Override
    public Clustering resume(PrecomputeData pd)
    {
        super.resume(pd);
        // 重複した処理の実行を回避
        if (this.getCountResume() > 1) return this;

        return this;
    }

    // 事前計算がおこなわれない場合の処理
    @Override
    public Clustering preparate()
    {
        super.preparate();
        // 重複した処理の実行を回避
        if (this.getCountPreparate() > 1) return this;

        return this;
    }

    @Override
    public Clustering calc()
    {
        return this;
    }

    // 他のモジュールがクラスタ数を取得する際に使います
    @Override
    public int getClusterNumber()
    {
        return this.n;
    }

    // 他のモジュールがentityに関連したクラスタの番号を取得する際に使います
    @Override
    public int getClusterIndex(StandardEntity entity)
    {
        return this.getClusterIndex(entity.getID());
    }

    // 他のモジュールがidに関連したクラスタの番号を取得する際に使います
    @Override
    public int getClusterIndex(EntityID id)
    {
        if (!this.assignment.containsKey(id)) return -1;
        return this.assignment.get(id);
    }

    // 他のモジュールがi番目のクラスタ要素をStandardEntityで取得する際に使います
    @Override
    public Collection<StandardEntity> getClusterEntities(int i)
    {
        if (i < 0 || i >= this.n) return null;

        Collection<EntityID> ids = this.getClusterEntityIDs(i);
        Collection<StandardEntity> ret = new ArrayList<>(ids.size());
        for (EntityID id : ids)
        {
            ret.add(this.worldInfo.getEntity(id));
        }
        return ret;
    }

    // 他のモジュールがi番目のクラスタ要素をEntityIDで取得する際に使います
    @Override
    public Collection<EntityID> getClusterEntityIDs(int i)
    {
        if (i < 0 || i >= this.n) return null;
        return this.clusterer.getClusterMembers(i);
    }
}
