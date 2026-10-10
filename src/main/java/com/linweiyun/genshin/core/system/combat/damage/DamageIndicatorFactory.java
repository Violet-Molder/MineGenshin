package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.genshin.config.WorldTextColorConfig;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.network.DamageIndicatorRpc;
import com.linweiyun.genshin.core.system.performance.BoundedLruMap;
import com.linweiyun.genshin.core.system.performance.DamageNumberThrottle;
import com.linweiyun.genshin.core.system.performance.DamageTextColorCache;
import com.linweiyun.elementlib.api.ElementalReactionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
/**
 * 伤害飘字工厂 —— 所有飘字的统一入口。
 *
 * 三类颜色模式：
 *   - 默认：从 DamageIndicatorConfig 按元素取色
 *   - Custom：从参数读单一十六进制色
 *   - Gradient：从参数读顶部 + 底部两个十六进制色，客户端做垂直渐变
 *
 * 五个飘字类型：
 *   - damage / crit / reaction / heal / text
 */
public final class DamageIndicatorFactory {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /**
     * 上一次给该目标生成飘字的位置，用来避免连续两条飘字重叠。
     *
     * <p>带上限的 LRU：访问即刷新，超过 {@value #MAX_LAST_SPAWN_POS} 个目标就淘汰最久没打过的
     * （无界表会让死掉的怪物条目长期留在静态表里）。</p>
     */
    private static final int MAX_LAST_SPAWN_POS = 1024;
    private static final Map<UUID, Vec3> LAST_SPAWN_POS =
            BoundedLruMap.create(MAX_LAST_SPAWN_POS);

    private DamageIndicatorFactory() {}

    public static int getColorForElement(GenshinElement element) {
        if (element == ModElements.PYRO.get()) return colorOf(WorldTextColorConfig.PYRO_COLOR);
        if (element == ModElements.HYDRO.get()) return colorOf(WorldTextColorConfig.HYDRO_COLOR);
        if (element == ModElements.DENDRO.get()) return colorOf(WorldTextColorConfig.DENDRO_COLOR);
        if (element == ModElements.ELECTRO.get()) return colorOf(WorldTextColorConfig.ELECTRO_COLOR);
        if (element == ModElements.ANEMO.get()) return colorOf(WorldTextColorConfig.ANEMO_COLOR);
        if (element == ModElements.CYRO.get()) return colorOf(WorldTextColorConfig.CYRO_COLOR);
        if (element == ModElements.FROZEN.get()) return colorOf(WorldTextColorConfig.FROZEN_COLOR);
        if (element == ModElements.GEO.get()) return colorOf(WorldTextColorConfig.GEO_COLOR);
        return colorOf(WorldTextColorConfig.PHYSICAL_COLOR);
    }

    public static int getColorForReaction(ElementalReactionType type) {
        if (ModReactionTypes.is(type, ModReactionTypes.ELECTRO_CHARGED)
                || ModReactionTypes.is(type, ModReactionTypes.LUNAR_CHARGED)) {
            return colorOf(WorldTextColorConfig.ELECTRO_CHARGED_COLOR);
        }
        if (ModReactionTypes.is(type, ModReactionTypes.SWIRL)) {
            return colorOf(WorldTextColorConfig.SWIRL_COLOR);
        }
        if (ModReactionTypes.is(type, ModReactionTypes.FROZEN)) {
            return colorOf(WorldTextColorConfig.FROZEN_COLOR);
        }
        if (ModReactionTypes.is(type, ModReactionTypes.STELLAR_SWIRL_WIND)
                || ModReactionTypes.is(type, ModReactionTypes.STELLAR_SWIRL_ICE)) {
            return colorOf(WorldTextColorConfig.STELLAR_BOTTOM_WIND_COLOR);
        }
        if (ModReactionTypes.is(type, ModReactionTypes.STELLAR_CONDUCE_ICE)) {
            return colorOf(WorldTextColorConfig.CYRO_COLOR);
        }
        if (ModReactionTypes.is(type, ModReactionTypes.STELLAR_CONDUCE_ELECTRO)) {
            return colorOf(WorldTextColorConfig.ELECTRO_COLOR);
        }
        return colorOf(WorldTextColorConfig.VAPORIZE_COLOR);
    }

    public static int getLunarTopColor() {
        return colorOf(WorldTextColorConfig.LUNAR_TOP_COLOR);
    }

    /**
     * 按配置项取颜色。
     *
     * <p>交给 {@link DamageTextColorCache#colorOf}：配置项值不变时直接命中上次结果，
     * 不做每次伤害一遍的「取字符串 → {@code Integer.decode}」。</p>
     */
    private static int colorOf(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> configValue) {
        return DamageTextColorCache.colorOf(configValue);
    }

    /**
     * 解析 {@code #RRGGBB} 形式的颜色串。
     *
     * @deprecated 走 {@link DamageTextColorCache#parseHex} / {@link #colorOf}，
     *             直接解析字符串拿不到「配置项值没变」这一层的缓存。
     */
    @Deprecated
    private static int parseColor(String hex) {
        return DamageTextColorCache.parseHex(hex);
    }

    public enum Style {
        NORMAL, CRIT, REACTION, HEAL, TEXT
    }

    public static final class Options {
        public static final float DEFAULT_BASE_SCALE   = 2.2f;
        public static final float DEFAULT_START_SCALE  = 6.2f;
        public static final long  DEFAULT_DURATION_MS  = 950L;

        public static final Options DEFAULT = new Options(
                DEFAULT_BASE_SCALE, DEFAULT_START_SCALE, DEFAULT_DURATION_MS);

        /**
         * 暴击飘字：更大的起跳与收束。
         *
         * <p>暴击是高频路径（攻速堆高后每几 tick 一次），这里用常量
         * （{@code baseScale 4.4f} / {@code startScale 12.4f}），不每次新建 Builder 与 Options。</p>
         */
        public static final Options CRIT = new Options(4.4f, 12.4f, DEFAULT_DURATION_MS);

        /** 月感电伤害数字（斜体 + 月色渐变）：普通 / 暴击两套尺寸 */
        public static final Options LUNAR = new Options(2.2f, 6.2f, 950L);
        public static final Options LUNAR_CRIT = new Options(2.6f, 7.0f, 1100L);

        public final float baseScale;
        public final float startScale;
        public final long  durationMs;

        public Options(float baseScale, float startScale, long durationMs) {
            this.baseScale = baseScale;
            this.startScale = startScale;
            this.durationMs = durationMs;
        }

        public static Options of(float baseScale, float startScale, long durationMs) {
            return new Options(baseScale, startScale, durationMs);
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private float baseScale = DEFAULT_BASE_SCALE;
            private float startScale = DEFAULT_START_SCALE;
            private long  durationMs = DEFAULT_DURATION_MS;

            public Builder baseScale(float v) { this.baseScale = v; return this; }
            public Builder startScale(float v) { this.startScale = v; return this; }
            public Builder durationMs(long v) { this.durationMs = v; return this; }
            public Options build() { return new Options(baseScale, startScale, durationMs); }
        }
    }

    /** 飘字广播半径（方块）—— 半径内谁收到由 {@code sendToNearby} 交给 PlayerList 筛 */
    private static final double BROADCAST_RADIUS = 48.0;

    // =====================================================================
    //  1. damage
    // =====================================================================

    public static void damage(LivingEntity target, DamageSource source, float finalDamage, GenshinElement element) {
        damage(target, source, finalDamage, element, Options.DEFAULT);
    }

    public static void damage(LivingEntity target, DamageSource source, float finalDamage, GenshinElement element, Options options) {
        if (source == null) return;
        int color = getColorForElement(element);
        spawnDamage(target, source, finalDamage, color, color, Style.NORMAL, options);
    }

    public static void damageCustom(LivingEntity target, DamageSource source, float finalDamage, int color) {
        damageCustom(target, source, finalDamage, color, Options.DEFAULT);
    }

    public static void damageCustom(LivingEntity target, DamageSource source, float finalDamage, int color, Options options) {
        if (source == null) return;
        spawnDamage(target, source, finalDamage, color, color, Style.NORMAL, options);
    }

    public static void damageGradient(LivingEntity target, DamageSource source, float finalDamage, int topColor, int bottomColor) {
        damageGradient(target, source, finalDamage, topColor, bottomColor, Options.DEFAULT);
    }

    public static void damageGradient(LivingEntity target, DamageSource source, float finalDamage, int topColor, int bottomColor, Options options) {
        if (source == null) return;
        spawnDamage(target, source, finalDamage, topColor, bottomColor, Style.NORMAL, options);
    }

    // =====================================================================
    //  2. crit
    // =====================================================================

    public static void crit(LivingEntity target, DamageSource source, float finalDamage, GenshinElement element) {
        crit(target, source, finalDamage, element, Options.DEFAULT);
    }

    public static void crit(LivingEntity target, DamageSource source, float finalDamage, GenshinElement element, Options options) {
        if (source == null) return;
        int color = getColorForElement(element);
        spawnDamage(target, source, finalDamage, color, color, Style.CRIT, options);
    }

    public static void critCustom(LivingEntity target, DamageSource source, float finalDamage, int color) {
        critCustom(target, source, finalDamage, color, Options.DEFAULT);
    }

    public static void critCustom(LivingEntity target, DamageSource source, float finalDamage, int color, Options options) {
        if (source == null) return;
        spawnDamage(target, source, finalDamage, color, color, Style.CRIT, options);
    }

    public static void critGradient(LivingEntity target, DamageSource source, float finalDamage, int topColor, int bottomColor) {
        critGradient(target, source, finalDamage, topColor, bottomColor, Options.DEFAULT);
    }

    public static void critGradient(LivingEntity target, DamageSource source, float finalDamage, int topColor, int bottomColor, Options options) {
        if (source == null) return;
        spawnDamage(target, source, finalDamage, topColor, bottomColor, Style.CRIT, options);
    }

    // =====================================================================
    //  3. reaction
    // =====================================================================

    public static void reaction(LivingEntity target, ElementalReactionType type) {
        reaction(target, type, Options.DEFAULT);
    }

    public static void reaction(LivingEntity target, ElementalReactionType type, Options options) {
        if (type == null) return;
        int color = getColorForReaction(type);
        spawnRaw(target, null, type.getTranslationKey(), color, color, Style.REACTION, options);
    }

    public static void reaction(LivingEntity target, Entity attacker, ElementalReactionType type) {
        reaction(target, attacker, type, Options.DEFAULT);
    }

    public static void reaction(LivingEntity target, Entity attacker, ElementalReactionType type, Options options) {
        if (type == null) return;
        int color = getColorForReaction(type);
        spawnRaw(target, attacker, type.getTranslationKey(), color, color, Style.REACTION, options);
    }

    public static void reactionCustom(LivingEntity target, ElementalReactionType type, int color) {
        reactionCustom(target, type, color, Options.DEFAULT);
    }

    public static void reactionCustom(LivingEntity target, ElementalReactionType type, int color, Options options) {
        if (type == null) return;
        spawnRaw(target, null, type.getTranslationKey(), color, color, Style.REACTION, options);
    }

    public static void reactionGradient(LivingEntity target, ElementalReactionType type, int topColor, int bottomColor) {
        reactionGradient(target, type, topColor, bottomColor, Options.DEFAULT);
    }

    public static void reactionGradient(LivingEntity target, ElementalReactionType type, int topColor, int bottomColor, Options options) {
        if (type == null) return;
        spawnRaw(target, null, type.getTranslationKey(), topColor, bottomColor, Style.REACTION, options);
    }

    // =====================================================================
    //  4. heal
    // =====================================================================

    public static void heal(LivingEntity target, float amount) {
        heal(target, amount, Options.DEFAULT);
    }

    public static void heal(LivingEntity target, float amount, Options options) {
        int color = getColorForElement(ModElements.DENDRO.get());
        spawnValue(target, amount, color, color, Style.HEAL, options);
    }

    public static void healCustom(LivingEntity target, float amount, int color) {
        healCustom(target, amount, color, Options.DEFAULT);
    }

    public static void healCustom(LivingEntity target, float amount, int color, Options options) {
        spawnValue(target, amount, color, color, Style.HEAL, options);
    }

    public static void healGradient(LivingEntity target, float amount, int topColor, int bottomColor) {
        healGradient(target, amount, topColor, bottomColor, Options.DEFAULT);
    }

    public static void healGradient(LivingEntity target, float amount, int topColor, int bottomColor, Options options) {
        spawnValue(target, amount, topColor, bottomColor, Style.HEAL, options);
    }

    // =====================================================================
    //  5. text
    // =====================================================================

    public static void text(LivingEntity target, String text) {
        text(target, text, Options.DEFAULT);
    }

    public static void text(LivingEntity target, String text, Options options) {
        int color = getColorForElement(ModElements.FYSIKOS.get());
        spawnRaw(target, null, text, color, color, Style.TEXT, options);
    }

    public static void text(LivingEntity target, Component text) {
        text(target, text, Options.DEFAULT);
    }

    public static void text(LivingEntity target, Component text, Options options) {
        text(target, text == null ? "" : text.getString(), options);
    }

    public static void textCustom(LivingEntity target, String text, int color) {
        textCustom(target, text, color, Options.DEFAULT);
    }

    public static void textCustom(LivingEntity target, String text, int color, Options options) {
        spawnRaw(target, null, text, color, color, Style.TEXT, options);
    }

    public static void textCustom(LivingEntity target, Component text, int color) {
        textCustom(target, text, color, Options.DEFAULT);
    }

    public static void textCustom(LivingEntity target, Component text, int color, Options options) {
        textCustom(target, text == null ? "" : text.getString(), color, options);
    }

    public static void textGradient(LivingEntity target, String text, int topColor, int bottomColor) {
        textGradient(target, text, topColor, bottomColor, Options.DEFAULT);
    }

    public static void textGradient(LivingEntity target, String text, int topColor, int bottomColor, Options options) {
        spawnRaw(target, null, text, topColor, bottomColor, Style.TEXT, options);
    }

    public static void textGradient(LivingEntity target, Component text, int topColor, int bottomColor) {
        textGradient(target, text, topColor, bottomColor, Options.DEFAULT);
    }

    public static void textGradient(LivingEntity target, Component text, int topColor, int bottomColor, Options options) {
        textGradient(target, text == null ? "" : text.getString(), topColor, bottomColor, options);
    }

    // =====================================================================
    //  6. 方块位置反应飘字
    // =====================================================================

    /**
     * 在方块位置显示反应飘字。
     * 以方块中心为锚点，target 和 origin 设为同一位置，复用现有 RPC 通道。
     */
    public static void reactionAtBlock(ServerLevel level, BlockPos pos,
                                       ElementalReactionType type) {
        if (type == null) return;
        int color = getColorForReaction(type);
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        // 一次编码、按半径广播（与 emit 同一条通道）
        DamageIndicatorRpc.sendToNearby(
                level,
                centerX, centerY, centerZ, BROADCAST_RADIUS,
                centerX, centerY, centerZ,
                centerX, centerY, centerZ,
                type.getTranslationKey(),
                color, color,
                (byte) Style.REACTION.ordinal(),
                false,
                Options.DEFAULT.baseScale, Options.DEFAULT.startScale,
                (int) Options.DEFAULT.durationMs,
                0, false
        );
    }

    // =====================================================================
    //  内部汇聚点
    // =====================================================================

    private static void spawnDamage(LivingEntity target, DamageSource source, float finalDamage,
                                    int topColor, int bottomColor, Style style, Options options) {
        spawnNumber(target, source == null ? null : source.getEntity(), finalDamage,
                topColor, bottomColor, style, options, false);
    }

    private static void spawnValue(LivingEntity target, float value,
                                   int topColor, int bottomColor, Style style, Options options) {
        spawnNumber(target, null, value, topColor, bottomColor, style, options, false);
    }

    /**
     * 数值类飘字的统一入口：伤害数字 / 治疗量都从这里走。
     *
     * <p>把 float 直接交给节流台账（{@link DamageNumberThrottle#planNumber}），
     * 省掉旧路径「{@code String.valueOf(Math.round(v))} 生成文本 → 台账再解析回数值累加」
     * 的那一趟格式化 + 解析。</p>
     */
    private static void spawnNumber(LivingEntity target, Entity attacker, float value,
                                    int topColor, int bottomColor, Style style, Options options,
                                    boolean italic) {
        if (target == null) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        if (options == null) options = Options.DEFAULT;

        // 生成侧合并 / 节流（性能系统·计算侧）：纯表现层决策，不参与任何伤害结算
        DamageNumberThrottle.Plan plan = DamageNumberThrottle.planNumber(
                target, value, (byte) style.ordinal(), topColor, bottomColor, italic);
        emit(level, target, attacker, plan.text, topColor, bottomColor, style, options, italic,
                plan.mergeKey, plan.merge);
    }

    private static void spawnRaw(LivingEntity target, Entity attacker, String text,
                                 int topColor, int bottomColor, Style style, Options options,
                                 boolean italic) {
        spawnRawInternal(target, attacker, text, topColor, bottomColor, style, options, italic);
    }

    private static void spawnRaw(LivingEntity target, Entity attacker, String text,
                                 int topColor, int bottomColor, Style style, Options options) {
        spawnRawInternal(target, attacker, text, topColor, bottomColor, style, options, false);
    }

    private static void spawnRawInternal(LivingEntity target, Entity attacker, String text,
                                         int topColor, int bottomColor, Style style, Options options,
                                         boolean italic) {
        if (target == null) return;
        if (text == null || text.isEmpty()) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        if (options == null) options = Options.DEFAULT;

        // 生成侧合并 / 节流（性能系统·计算侧）：
        // 攻速堆高后同一目标每 tick 都能刷出好几条飘字，这里把「同一目标的连续伤害」
        // 并成一条会累加的数字，客户端活跃条数从「随攻击次数线性增长」变成「每目标 1 条」。
        // 纯表现层决策，不参与任何伤害结算；关掉 performance.toml 的 merge 即回到逐条飘字。
        DamageNumberThrottle.Plan plan = DamageNumberThrottle.plan(
                target, text, (byte) style.ordinal(), topColor, bottomColor, italic);
        emit(level, target, attacker, plan.text, topColor, bottomColor, style, options, italic,
                plan.mergeKey, plan.merge);
    }

    /**
     * 位置抖动 + 半径广播，两个入口（数值类 / 文字类）共用。
     *
     * <h2>为什么这里全是基本量</h2>
     * 高攻速下这一段每次伤害都要跑一遍，而旧写法光是「试一个候选点」就建一个 {@link Vec3}
     * ——随机位置最多试 8 次，加上目标中心、攻击者中心、插值结果，一次伤害能造出十几个 {@link Vec3}，
     * 全是要被 GC 收掉的小对象。现在坐标一律用 {@code double} 算，
     * 只在「记下这个目标上次飘在哪」时落一个 {@link Vec3}（那张表需要按目标存一份位置）。
     *
     * <h2>广播方式</h2>
     * 逐玩家 {@code sendToPlayer} 会为每个接收者重复编码同一个包；这里换成
     * {@link DamageIndicatorRpc#sendToNearby}：编码一次，半径内的玩家由
     * {@code PlayerList} 统一投递。
     */
    private static void emit(ServerLevel level, LivingEntity target, Entity attacker, String text,
                             int topColor, int bottomColor, Style style, Options options,
                             boolean italic, int mergeKey, boolean merge) {
        RandomSource rand = level.getRandom();

        double targetX = target.getX();
        double targetY = target.getY() + target.getBbHeight() * 0.85;
        double targetZ = target.getZ();

        double extraY = (style == Style.REACTION) ? 0.6 : 0.0;
        double spreadH = 1.5;
        double verticalBase = 0.35;
        double verticalRange = 0.6;

        int maxRetries = 8;
        UUID targetId = target.getUUID();
        Vec3 lastPos = LAST_SPAWN_POS.get(targetId);
        double lastX = lastPos == null ? 0.0 : lastPos.x;
        double lastY = lastPos == null ? 0.0 : lastPos.y;
        double lastZ = lastPos == null ? 0.0 : lastPos.z;

        double finalX = 0.0;
        double finalY = 0.0;
        double finalZ = 0.0;
        boolean found = false;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            double cx = targetX + (rand.nextDouble() - 0.5) * spreadH;
            double cy = targetY + verticalBase + rand.nextDouble() * verticalRange + extraY;
            double cz = targetZ + (rand.nextDouble() - 0.5) * spreadH;
            if (lastPos == null || distSqr(cx, cy, cz, lastX, lastY, lastZ) > 0.25) {
                finalX = cx;
                finalY = cy;
                finalZ = cz;
                found = true;
                break;
            }
        }
        if (!found) {
            finalX = targetX + (rand.nextDouble() - 0.5) * spreadH;
            finalY = targetY + verticalBase + rand.nextDouble() * verticalRange + extraY;
            finalZ = targetZ + (rand.nextDouble() - 0.5) * spreadH;
        }
        LAST_SPAWN_POS.put(targetId, new Vec3(finalX, finalY, finalZ));

        double originX;
        double originY;
        double originZ;
        if (attacker != null && attacker != target && attacker.level() == level) {
            // 攻击者胸口 → 目标胸口，取 30% 处
            double attackerX = attacker.getX();
            double attackerY = attacker.getY() + attacker.getBbHeight() * 0.7;
            double attackerZ = attacker.getZ();
            originX = attackerX + (targetX - attackerX) * 0.3;
            originY = attackerY + (targetY - attackerY) * 0.3;
            originZ = attackerZ + (targetZ - attackerZ) * 0.3;
        } else {
            originX = targetX;
            originY = targetY + 0.6;
            originZ = targetZ;
        }

        try {
            DamageIndicatorRpc.sendToNearby(
                    level,
                    targetX, targetY, targetZ, BROADCAST_RADIUS,
                    finalX, finalY, finalZ,
                    originX, originY, originZ,
                    text,
                    topColor, bottomColor,
                    (byte) style.ordinal(),
                    italic,
                    options.baseScale, options.startScale,
                    (int) options.durationMs,
                    mergeKey, merge
            );
        } catch (Throwable t) {
            LOGGER.error("[DI-Factory] sendToNearby threw", t);
        }
    }

    private static double distSqr(double ax, double ay, double az,
                                  double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    // =====================================================================
    //  月感电 / 星扩散 —— 渐变 + 斜体
    // =====================================================================

    private static final int WHITE = 0xFFFFFF;

    public static void lunarDamageGradient(LivingEntity target, DamageSource source, float finalDamage) {
        lunarDamageGradient(target, source, finalDamage, Options.DEFAULT);
    }

    public static void lunarDamageGradient(LivingEntity target, DamageSource source, float finalDamage, Options options) {
        if (source == null) return;
        int topColor = getLunarTopColor();
        spawnNumber(target, source.getEntity(), finalDamage,
                topColor, WHITE, Style.NORMAL, options, true);
    }

    public static void lunarReactionGradient(LivingEntity target, ElementalReactionType type) {
        lunarReactionGradient(target, type, Options.DEFAULT);
    }

    public static void lunarReactionGradient(LivingEntity target, ElementalReactionType type, Options options) {
        if (type == null) return;
        int topColor = getLunarTopColor();
        spawnRawInternal(target, null, type.getTranslationKey(),
                topColor, WHITE, Style.REACTION, options, true);
    }

    public static void stellarWindDamageGradient(LivingEntity target, DamageSource source, float finalDamage) {
        stellarWindDamageGradient(target, source, finalDamage, Options.DEFAULT);
    }

    public static void stellarWindDamageGradient(LivingEntity target, DamageSource source, float finalDamage, Options options) {
        if (source == null) return;
        int bottomColor = colorOf(WorldTextColorConfig.STELLAR_BOTTOM_WIND_COLOR);
        spawnNumber(target, source.getEntity(), finalDamage,
                WHITE, bottomColor, Style.NORMAL, options, true);
    }

    /**
     * 星烁（星扩散 / 星超导）伤害数字 —— 按伤害元素选底部色，与反应文字同一套配色 + 斜体。
     *
     * <p>星扩散的风段走风色、星扩散的冰段走冰色，和 {@code stellarWindReactionGradient} /
     * {@code stellarIceReactionGradient} 对得上；没专用星辉底色的元素（例如星超导的雷段）
     * 退回元素自身颜色，斜体保持不变。</p>
     */
    public static void stellarDamageGradient(LivingEntity target, DamageSource source, float finalDamage,
                                             GenshinElement element, Options options) {
        if (source == null) return;
        if (element == ModElements.ANEMO.get()) {
            stellarWindDamageGradient(target, source, finalDamage, options);
            return;
        }
        if (element == ModElements.CYRO.get()) {
            stellarIceDamageGradient(target, source, finalDamage, options);
            return;
        }
        spawnNumber(target, source.getEntity(), finalDamage,
                WHITE, getColorForElement(element), Style.NORMAL, options, true);
    }

    /** 星超导伤害数字：冰段用冰元素色、雷段用雷元素色。 */
    public static void stellarDamageGradient(LivingEntity target, DamageSource source, float finalDamage,
                                             GenshinElement element, ElementalReactionType reactionType,
                                             Options options) {
        if (source == null) {
            return;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ICE)) {
            spawnNumber(target, source.getEntity(), finalDamage,
                    WHITE, colorOf(WorldTextColorConfig.CYRO_COLOR), Style.NORMAL, options, true);
            return;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ELECTRO)) {
            spawnNumber(target, source.getEntity(), finalDamage,
                    WHITE, colorOf(WorldTextColorConfig.ELECTRO_COLOR), Style.NORMAL, options, true);
            return;
        }
        stellarDamageGradient(target, source, finalDamage, element, options);
    }

    public static void stellarIceDamageGradient(LivingEntity target, DamageSource source, float finalDamage) {
        stellarIceDamageGradient(target, source, finalDamage, Options.DEFAULT);
    }

    public static void stellarIceDamageGradient(LivingEntity target, DamageSource source, float finalDamage, Options options) {
        if (source == null) return;
        int bottomColor = colorOf(WorldTextColorConfig.STELLAR_BOTTOM_ICE_COLOR);
        spawnNumber(target, source.getEntity(), finalDamage,
                WHITE, bottomColor, Style.NORMAL, options, true);
    }

    public static void stellarWindReactionGradient(LivingEntity target, ElementalReactionType type) {
        stellarWindReactionGradient(target, type, Options.DEFAULT);
    }

    public static void stellarWindReactionGradient(LivingEntity target, ElementalReactionType type, Options options) {
        if (type == null) return;
        int bottomColor = colorOf(WorldTextColorConfig.STELLAR_BOTTOM_WIND_COLOR);
        spawnRawInternal(target, null, type.getTranslationKey(),
                WHITE, bottomColor, Style.REACTION, options, true);
    }

    public static void stellarIceReactionGradient(LivingEntity target, ElementalReactionType type) {
        stellarIceReactionGradient(target, type, Options.DEFAULT);
    }

    public static void stellarIceReactionGradient(LivingEntity target, ElementalReactionType type, Options options) {
        if (type == null) return;
        int bottomColor = colorOf(WorldTextColorConfig.STELLAR_BOTTOM_ICE_COLOR);
        spawnRawInternal(target, null, type.getTranslationKey(),
                WHITE, bottomColor, Style.REACTION, options, true);
    }
}
