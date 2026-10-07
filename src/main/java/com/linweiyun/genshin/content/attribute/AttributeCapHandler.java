package com.linweiyun.genshin.content.attribute;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.config.entity.EntityAttributeCapConfig;
import com.linweiyun.genshin.mixin.mixins.AccessorRangedAttribute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public class AttributeCapHandler {

    public static void applyCapRelief() {
        if (!EntityAttributeCapConfig.isEnabled()) {
            Minegenshin.LOGGER.info("[AttributeCap] 已关闭属性上限解除");
            return;
        }

        int scanned = 0;
        int changed = 0;

        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            if (!(attribute instanceof RangedAttribute ranged) || !(attribute instanceof AccessorRangedAttribute accessor)) {
                continue;
            }

            ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            if (id == null || !id.getNamespace().equals("minecraft")) {
                continue;
            }

            double originalMax = ranged.getMaxValue();
            double newMax = resolveCap(id.getPath(), originalMax);
            scanned++;

            if (newMax > originalMax) {
                accessor.minegenshin$setMaxValue(newMax);
                changed++;
                Minegenshin.LOGGER.info("[AttributeCap] {} 上限从 {} 提高到 {}", id, originalMax, newMax);
            }
        }

        // 一定要有一条汇总：没有它的话「跑了但一条都没改」和「根本没跑」在日志里长得一样。
        Minegenshin.LOGGER.info("[AttributeCap] 属性上限解放完成：扫描 {} 条原版属性，放宽 {} 条",
                scanned, changed);
    }

    private static double resolveCap(String attributePath, double fallback) {
        return switch (attributePath) {
            case "max_health" -> EntityAttributeCapConfig.getMaxHealthCap();
            case "attack_damage" -> EntityAttributeCapConfig.getAttackDamageCap();
            case "armor" -> EntityAttributeCapConfig.getArmorCap();
            case "armor_toughness" -> EntityAttributeCapConfig.getArmorToughnessCap();
            case "attack_speed" -> EntityAttributeCapConfig.getAttackSpeedCap();
            case "movement_speed" -> EntityAttributeCapConfig.getMovementSpeedCap();
            case "luck" -> EntityAttributeCapConfig.getLuckCap();
            default -> fallback;
        };
    }
}