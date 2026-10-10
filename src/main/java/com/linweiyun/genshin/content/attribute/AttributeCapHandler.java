package com.linweiyun.genshin.content.attribute;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.config.entity.EntityAttributeCapConfig;
import com.linweiyun.genshin.mixin.mixins.AccessorRangedAttribute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
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

    /**
     * 按属性的<b>短名</b>取配置里的上限。
     *
     * <p>同一个属性在不同版本里的登记名不一样：有的带分类前缀（{@code generic.max_health}），
     * 有的就是光名字（{@code max_health}）。按全名匹配的话，带前缀的那种一条都对不上，
     * 于是整个解除会在「扫描 N 条、放宽 0 条」里静默失效，所以这里先砍掉前缀再比。
     */
    private static double resolveCap(String attributePath, double fallback) {
        int dot = attributePath.lastIndexOf('.');
        String key = dot >= 0 ? attributePath.substring(dot + 1) : attributePath;
        return switch (key) {
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
