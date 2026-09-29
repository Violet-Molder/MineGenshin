package com.linweiyun.genshin.content.items.artifact.stat;

import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArtifactSubStatGenerator {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    // 副词条可选池（原神规则：不能与主词条同属性）
    // 基础4个固定副词条 + 元素精通：固定值ATK/HP/DEF，百分比ATK%/HP%/DEF%/充能/暴击/暴伤
    private static final List<TeyvatItemStat.SubStatOption> SUB_POOL = List.of(
            // 固定值
            new TeyvatItemStat.SubStatOption(ModAttributes.ATK.get(), TeyvatItemStat.StatKind.FLAT),
            new TeyvatItemStat.SubStatOption(ModAttributes.MAX_HP.get(), TeyvatItemStat.StatKind.FLAT),
            new TeyvatItemStat.SubStatOption(ModAttributes.DEF.get(), TeyvatItemStat.StatKind.FLAT),
            new TeyvatItemStat.SubStatOption(ModAttributes.ELEMENTAL_MASTERY.get(), TeyvatItemStat.StatKind.FLAT),
            // 百分比
            new TeyvatItemStat.SubStatOption(ModAttributes.ATK.get(), TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat.SubStatOption(ModAttributes.MAX_HP.get(), TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat.SubStatOption(ModAttributes.DEF.get(), TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat.SubStatOption(ModAttributes.ER.get(), TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat.SubStatOption(ModAttributes.CR.get(), TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat.SubStatOption(ModAttributes.CDG .get(), TeyvatItemStat.StatKind.PERCENT)
    );

    /**
     * 生成所有副词条（按用户要求：一开始全抽好，未启用的只是 unlocked=false）
     * @param star 圣遗物星级
     * @param mainStatAttribute 主词条属性（副词条不能重复）
     * @param mainStatKind 主词条形态
     */
    public static List<TeyvatItemStat> generateAll(int star, ArtifactType type,
                                                   AttributeType mainStatAttribute,
                                                   TeyvatItemStat.StatKind mainStatKind,
                                                   Random random) {
//        LOGGER.info("[ArtifactSubStatGenerator] generateAll | star={} | type={} | mainAttr={} | mainKind={}",
//                star, type, mainStatAttribute, mainStatKind);

        List<TeyvatItemStat> result = new ArrayList<>();
        // 最多4条副词条
        // 1-2星 最多2条, 3星3条, 4-5星4条
        int maxSubStats = Math.min(star, 4) - 1; // star=1→0, star=2→1... 不对
        // 用户说一开始全抽好，那直接按 max 数量生成
        int count = switch (star) {
            case 1, 2 -> 2;
            case 3 -> 3;
            case 4, 5 -> 4;
            default -> 2;
        };

        // 过滤：排除与主词条相同属性（注意 ATK/HP/DEF 有 FLAT 和 PERCENT 两种，主词条是固定值的话，副词条百分比是允许的？）
        // 原神规则：主词条是HP固定值，副词条不能有HP固定值，但可以有HP百分比
        // 这里简化处理：主词条的 AttributeType + StatKind 组合不能重复
        List<TeyvatItemStat.SubStatOption> pool = new ArrayList<>(SUB_POOL);
        pool.removeIf(opt -> opt.attribute == mainStatAttribute && opt.kind == mainStatKind);

        for (int i = 0; i < count; i++) {
            TeyvatItemStat.SubStatOption picked = pool.remove(random.nextInt(pool.size()));
            int tier = random.nextInt(4) + 1;
            double value = ArtifactStatData.getSubStatTierValue(picked.attribute, picked.kind, star, tier);
//            LOGGER.info("[ArtifactSubStatGenerator] subStat[{}] | attr={} | kind={} | tier={} | value={}", i, picked.attribute, picked.kind, tier, value);
            result.add(new TeyvatItemStat(picked.attribute, value, picked.kind, true, tier));
        }

        // 初始解锁数量：有概率全解锁（约25%），否则少一个
        // 原神机制：五星初始3词条概率约75%，4词条约25%
        int maxUnlock = count;
        int minUnlock = Math.max(1, count - 1);
        boolean fullUnlock = random.nextDouble() < 0.25;
        int initialUnlock = fullUnlock ? maxUnlock : minUnlock;
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setUnlocked(i < initialUnlock);
        }

//        LOGGER.info("[ArtifactSubStatGenerator] done | totalGenerated={} | initialUnlock={}", result.size(), initialUnlock);
        return result;
    }

    // 内部数据类：词条选项（attribute + kind 组合）
    public static class SubStatOption {
        public final AttributeType attribute;
        public final TeyvatItemStat.StatKind kind;
        public SubStatOption(AttributeType attribute, TeyvatItemStat.StatKind kind) {
            this.attribute = attribute;
            this.kind = kind;
        }
    }
}