package com.linweiyun.genshin.content.items.artifact.stat;

import com.linweiyun.genshin.config.artifact.ArtifactMainStatConfig;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;

public class ArtifactMainStatGenerator {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    // SANDS 可选池：HP% / ATK% / DEF% / 元素精通 / 充能效率
    private static final List<TeyvatItemStat> SANDS_POOL = List.of(
            new TeyvatItemStat(ModAttributes.MAX_HP.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ATK.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.DEF.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ELEMENTAL_MASTERY.get(), 0, TeyvatItemStat.StatKind.FLAT),
            new TeyvatItemStat(ModAttributes.ER.get(), 0, TeyvatItemStat.StatKind.PERCENT)
    );

    // GOBLET 可选池：8种元素伤害加成 + 物理伤害加成 + ATK% / HP% / DEF% / 元素精通
    private static final List<TeyvatItemStat> GOBLET_POOL = List.of(
            new TeyvatItemStat(ModAttributes.PYRO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.HYDRO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.CYRO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ELECTRO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ANEMO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.GEO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.DENDRO_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.PHYSICAL_BONUS.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ATK.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.MAX_HP.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.DEF.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ELEMENTAL_MASTERY.get(), 0, TeyvatItemStat.StatKind.FLAT)
    );

    // CIRCLET 可选池：暴击率 / 暴击伤害 / 治疗加成 / HP% / ATK% / DEF% / 元素精通
    private static final List<TeyvatItemStat> CIRCLET_POOL = List.of(
            new TeyvatItemStat(ModAttributes.CR.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.CDG.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.HB.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.MAX_HP.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ATK.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.DEF.get(), 0, TeyvatItemStat.StatKind.PERCENT),
            new TeyvatItemStat(ModAttributes.ELEMENTAL_MASTERY.get(), 0, TeyvatItemStat.StatKind.FLAT)
    );

    public static TeyvatItemStat generate(ArtifactType type, int star, Random random) {
        return switch (type) {
            case FLOWER -> {
                double initialValue = ArtifactStatData.getMainStatBase(ModAttributes.MAX_HP.get(), TeyvatItemStat.StatKind.FLAT, star);
                yield new TeyvatItemStat(ModAttributes.MAX_HP.get(), initialValue, TeyvatItemStat.StatKind.FLAT);
            }
            case PLUME -> {
                double initialValue = ArtifactStatData.getMainStatBase(ModAttributes.ATK.get(), TeyvatItemStat.StatKind.FLAT, star);
                yield new TeyvatItemStat(ModAttributes.ATK.get(), initialValue, TeyvatItemStat.StatKind.FLAT);
            }
            case SANDS -> generateSands(star, random);
            case GOBLET -> generateGoblet(star, random);
            case CIRCLET -> generateCirclet(star, random);
        };
    }

    private static TeyvatItemStat generateSands(int star, Random random) {
        List<Double> weights = List.of(
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_SANDS_HP_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_SANDS_ATK_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_SANDS_DEF_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_SANDS_EM_FLAT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_SANDS_ER_PERCENT.get())
        );
        TeyvatItemStat template = weightedPick(SANDS_POOL, weights, random);
//        LOGGER.info("[ArtifactMainStatGenerator] generateSands picked | attr={}", template.getAttribute());
        return buildFromTemplate(template, star);
    }

    private static TeyvatItemStat generateGoblet(int star, Random random) {
        List<Double> weights = List.of(
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_PYRO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_HYDRO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_CYRO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_ELECTRO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_ANEMO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_GEO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_DENDRO_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_PHYSICAL_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_ATK_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_HP_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_DEF_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_GOBLET_EM_FLAT.get())
        );
        TeyvatItemStat template = weightedPick(GOBLET_POOL, weights, random);
//        LOGGER.info("[ArtifactMainStatGenerator] generateGoblet picked | attr={}", template.getAttribute());
        return buildFromTemplate(template, star);
    }

    private static TeyvatItemStat generateCirclet(int star, Random random) {
        List<Double> weights = List.of(
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_CR_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_CDG_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_HB_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_HP_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_ATK_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_DEF_PERCENT.get()),
                Double.parseDouble(ArtifactMainStatConfig.WEIGHT_CIRCLET_EM_FLAT.get())
        );
        TeyvatItemStat template = weightedPick(CIRCLET_POOL, weights, random);
//        LOGGER.info("[ArtifactMainStatGenerator] generateCirclet picked | attr={}", template.getAttribute());
        return buildFromTemplate(template, star);
    }

    private static TeyvatItemStat buildFromTemplate(TeyvatItemStat template, int star) {
        double base = ArtifactStatData.getMainStatBase(template.getAttribute(), template.getKind(), star);
//        LOGGER.info("[ArtifactMainStatGenerator] buildFromTemplate | attr={} | kind={} | star={} | base={}",
//                template.getAttribute(), template.getKind(), star, base);
        return new TeyvatItemStat(template.getAttribute(), base, template.getKind());
    }

    private static <T> T weightedPick(List<T> items, List<Double> weights, Random random) {
        double totalWeight = 0;
        for (double w : weights) {
            totalWeight += w;
        }
        if (totalWeight <= 0) {
            return items.get(random.nextInt(items.size()));
        }
        double r = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < items.size(); i++) {
            cumulative += weights.get(i);
            if (r < cumulative) {
                return items.get(i);
            }
        }
        return items.getLast();
    }
}
