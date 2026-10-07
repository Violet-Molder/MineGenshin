package com.linweiyun.genshin.content.items.artifact;

import com.linweiyun.genshin.content.effect.character.artifact.ArtifactSetEffect;
import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.artifact.stat.ArtifactMainStatGenerator;
import com.linweiyun.genshin.content.items.artifact.stat.ArtifactSubStatGenerator;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;

public class ArtifactItem extends TeyvatItem {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);
    protected ArtifactType type;
    protected DeferredHolder<ArtifactSet, ArtifactSet> set;

    public ArtifactItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack artifactStack = player.getItemInHand(hand);
        ArtifactItem.initializeArtifactStackIfNeeded(artifactStack);
        return super.use(level, player, hand);
    }

    public static void initializeArtifactStackIfNeeded(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArtifactItem artifact)) return;
        ArtifactStatsComponent stats = stack.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(),
                ArtifactStatsComponent.DEFAULT
        );

        // 判定要「未激活 **且** 主词条还没抽」两个条件同时成立才重抽。
        //
        // ⚠️ 只看 `activated` 会出大问题：只要有**任何一条路径**漏了把 activated 置 true
        //    （客户端本地补激活、圣遗物 UI 里升级解锁词条、装备栏 ↔ 背包来回搬……），
        //    下一次本方法被调用（右键 / 装备 / 卸下）就会**整件重抽一遍主副词条**
        //    —— 玩家看到的就是「穿一次再卸下来，副词条变了一套，之后又正常了」。
        //    已经有词条就说明它早就抽过了，缺的只是那个标记，补上即可，绝不重抽。
        boolean blankStats = stats.mainStat == null || !stats.mainStat.isInitialized();
        if (!stats.activated && blankStats) {
            ArtifactStatsComponent generated = buildInitialStats(artifact.getStar(), artifact.getType());
            generated.activated = true;
            stack.set(ModDataComponents.ARTIFACT_STATS.get(), generated);
        } else if (!stats.activated) {
            ArtifactStatsComponent healed = stats.copy();
            healed.activated = true;
            stack.set(ModDataComponents.ARTIFACT_STATS.get(), healed);
            LOGGER.info("[圣遗物] 词条已存在但 activated=false，只补标记、不重抽 | item={}",
                    stack.getItem());
        }
    }
    protected static ArtifactStatsComponent buildInitialStats(int star, ArtifactType type) {
        Random random = new Random();
        TeyvatItemStat mainStat = ArtifactMainStatGenerator.generate(type, star, random);
        List<TeyvatItemStat> subStats = ArtifactSubStatGenerator.generateAll(star, type, mainStat.getAttribute(), mainStat.getKind(), random);
        return new ArtifactStatsComponent(0, 0, mainStat, subStats);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
        ArtifactStatsComponent stats = stack.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);

        // 第一行：小字，圣遗物类型
        builder.add(Component.translatable("artifact.type." + type.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        builder.add(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD));
        builder.add(Component.literal("+" + stats.level).withStyle(ChatFormatting.GRAY));

        // 主词条（大字 + 星级颜色）
        // mainStat.getAttribute() 为 null 说明未初始化（创造栏默认组件），跳过不显示
        if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            ChatFormatting starColor = getStarColor();
            builder.add(Component.literal(buildStatText(stats.mainStat)).withStyle(starColor, ChatFormatting.BOLD));
        }

        // 副词条
        if (stats.subStats != null && !stats.subStats.isEmpty()) {
            boolean hasUnlocked = false;
            for (var stat : stats.subStats) {
                // attribute 为 null 跳过这个空壳子属性
                if (!stat.isInitialized()) continue;
                if (!hasUnlocked) {
                    builder.add(Component.empty());
                    hasUnlocked = true;
                }
                String text = buildStatText(stat);
                if (stat.isUnlocked()) {
                    builder.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
                } else {
                    builder.add(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }

        // 套装信息
        if (set != null) {
            ArtifactSet artifactSet = set.get();
            builder.add(Component.empty());
            var setKey = ModRegistries.ARTIFACT_SET_REGISTRY.getKey(artifactSet);
            String setNameKey = setKey != null ? "artifact_set." + setKey.getPath() : "artifact_set.unknown";
            builder.add(Component.translatable(setNameKey).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

            var twoPc = artifactSet.twoPcEffect().get();
            if (twoPc instanceof ArtifactSetEffect setEffect) {
                builder.add(Component.translatable("artifact_set.effect.2pc").withStyle(ChatFormatting.YELLOW).append(": ").append(setEffect.getDescription()));
            }
            if (star >= 4 && artifactSet.hasFourPcEffect()) {
                var fourPc = artifactSet.fourPcEffect().get();
                if (fourPc instanceof ArtifactSetEffect setEffect) {
                    builder.add(Component.translatable("artifact_set.effect.4pc").withStyle(ChatFormatting.YELLOW).append(": ").append(setEffect.getDescription()));
                }
            }
            // 圣遗物文本
            if (setKey != null) {
                builder.add(Component.translatable("artifact.desc."+ setKey.getPath() +"." + type.name().toLowerCase()).withStyle(ChatFormatting.WHITE));
            }
        }

    }
    private String buildStatText(TeyvatItemStat stat) {
        if (!stat.isInitialized()) return "";
        String attrName = Component.translatable(stat.getAttribute().translationKey()).getString();
        if (stat.getKind() == TeyvatItemStat.StatKind.PERCENT) {
            return attrName + " +" + String.format("%.1f%%", stat.getValue() * 100);
        } else {
            return attrName + " +" + String.format("%.0f", stat.getValue());
        }
    }

    private ChatFormatting getStarColor() {
        return switch (star) {
            case 5 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.LIGHT_PURPLE;
            case 3 -> ChatFormatting.AQUA;
            case 2 -> ChatFormatting.WHITE;
            default -> ChatFormatting.GRAY;
        };
    }

    /**
     * 获取圣遗物 UID。
     *
     * 格式：31TIII
     *   3    固定前缀，表示属于“圣遗物类”UID
     *   1    表示这是“圣遗物物品”（区别于套装的 2）
     *   T    部位编号：1=生之花，2=死之羽，3=时之沙，4=空之杯，5=理之冠
     *   III  套装编号，例如魔女套为 001
     *
     * 例如：魔女套生之花 = 311001，魔女套死之羽 = 312001，
     * 魔女套时之沙 = 313001，魔女套空之杯 = 314001，魔女套理之冠 = 315001。
     *
     * @return 圣遗物 UID；若数据不完整则返回 0
     */
    public int getUID() {
        if (set == null || set.get() == null || type == null) return 0;
        int typeDigit = type.ordinal() + 1;     // FLOWER=1, PLUME=2, SANDS=3, GOBLET=4, CIRCLET=5
        return 310000 + typeDigit * 1000 + set.get().setId();
    }

    public ArtifactType getType() {return type;}
    public DeferredHolder<ArtifactSet, ArtifactSet> getSet() {return set;}
}