package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.elementlib.core.module.ElibModuleRegistry;
import com.linweiyun.elementlib.core.module.ElibModuleSync;
import com.linweiyun.elementlib.core.module.ElibModuleTargetKinds;
import com.linweiyun.elementlib.core.module.ElibModuleType;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.poise.PoiseState;

/** 韧性模块类型：实体 / 角色用持久数据，方块用瞬态数据。 */
public final class ToughnessTypes {

    public static final ElibModuleType<PoiseState> TOUGHNESS = ElibModuleRegistry.register(
            ElibModuleType.builder(Minegenshin.id("toughness"), PoiseState.class)
                    .supports(ElibModuleTargetKinds.ENTITY, ElibModuleTargetKinds.BLOCK)
                    .persistent()
                    .sync(ElibModuleSync.SYNC_TO_CLIENT)
                    .factory(PoiseState::new)
                    .codec(PoiseState.CODEC)
                    .streamCodec(PoiseState.STREAM_CODEC)
                    .build());

    public static final ElibModuleType<BlockToughnessData> BLOCK_TOUGHNESS = ElibModuleRegistry.register(
            ElibModuleType.builder(Minegenshin.id("block_toughness"), BlockToughnessData.class)
                    .supports(ElibModuleTargetKinds.BLOCK)
                    .transientPerLoad()
                    .factory(BlockToughnessData::new)
                    .build());

    private ToughnessTypes() {
    }

    public static void init() {
    }
}
