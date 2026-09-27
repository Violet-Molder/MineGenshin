package com.linweiyun.genshin.content.effect.character.vodyanitsa;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModCharacters;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 沃雅妮莎突破天赋 2（突破 ≥ 4）的「领唱 / 重唱」两套层数效果。
 *
 * <h2>加伤加在哪</h2>
 * <b>基础区的「附加伤害」项</b>（和基础值加算），也就是和申鹤冰凌同一类 ——
 * 用 {@link ModDamageSpec#withFlatDamageBonus(float)}，<b>不是</b>倍率也不是增伤区。
 *
 * <h2>为什么和冰凌长得一样</h2>
 * 冰凌（{@code IcyQuillEffect}）已经把这套机制走通了：效果挂在<b>受益人</b>身上、
 * 在 {@link ICharacterEffect#onAttacked} 里看这一次伤害的元素、够条件就
 * ①把固定值塞进这次的 spec ②消耗一层，层数存在效果实例的 {@code intData} 里。
 * 这里照抄同一套，只是判定条件换成「水/冰伤害」或「星扩散反应伤害」。
 *
 * <h2>领唱 / 重唱的区别</h2>
 * <b>只在于挂给谁</b>：领唱挂当前场上角色（25 层），重唱挂后台/附近的其它角色（10 层）；
 * 两者的触发与消耗逻辑完全一致，所以 {@link Refrain} 直接继承 {@link Antiphon}。
 */
public final class VodyanitsaSongEffects {

    /** 日志：突破天赋 2 的判定过程（排查「为什么附加伤害是 0」用）。 */
    public static final org.slf4j.Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private VodyanitsaSongEffects() {
    }

    /** 层数存在效果实例的 intData 里（和冰凌同一个做法）。 */
    public static final String STACK_KEY = "vodyanitsa_song_stacks";

    /** 层数效果持续 30 秒；施放战技时刷新层数。 */
    public static final int DURATION_TICKS = 30 * 20;

    /** 突破天赋 2 的层数：领唱 25 层、重唱 10 层。 */
    public static final int ANTIPHON_STACKS = 25;
    public static final int REFRAIN_STACKS = 10;

    /** 加伤档位：生命值上限超过 40000 的部分，每 1000 点 → 星扩散 +260 / 水冰 +140。 */
    public static final double HP_THRESHOLD = 40000.0;
    public static final double HP_PER_STEP = 1000.0;
    public static final float STELLAR_FLAT_PER_STEP = 260f;
    public static final float ELEMENT_FLAT_PER_STEP = 140f;
    public static final float STELLAR_FLAT_CAP = 6500f;
    public static final float ELEMENT_FLAT_CAP = 3500f;

    /** 「场上有没有流荡风旋」的判定半径（格）。 */
    public static final double FLOWING_CHECK_RADIUS = 20.0;

    // ==================== 领唱（挂当前场上角色）====================

    /** 领唱：当前场上角色造成水/冰伤害（或星扩散反应伤害）时消耗 1 层并加伤。 */
    public static class Antiphon implements ICharacterEffect {

        @Override
        public void onAttacked(Player holder, PGCharacter character, LivingEntity target,
                               CharacterEffectInstance instance, ModDamageSource damageSource) {
            ModDamageSpec spec = damageSource.getSpec();
            if (spec == null) return;

            boolean stellarDamage = spec.isStellarReactionDamage();
            boolean hydroOrCryo = spec.getElement() == ModElements.HYDRO.get()
                    || spec.getElement() == ModElements.CYRO.get();

            // **二选一**（口径已确认）：场上有流荡风旋（或刚引爆完 5 秒内）→ 只认**星扩散反应伤害**，
            // 水/冰增伤此时失效；否则只认水/冰伤害。
            // ⚠️ 之前「附加伤害一直是 0」不是这里写错，而是 flowingSwirlActive 因为
            //    lastDetonationTick 初值溢出而**恒返回 true**（详见那个方法的注释）。
            boolean stellarMode = flowingSwirlActive(holder);
            if (stellarMode ? !stellarDamage : !hydroOrCryo) {
                return;     // 不满足条件 → 不触发也**不消耗**层数
            }

            float bonus = flatBonus(holder, stellarMode);
            if (bonus <= 0f) return;

            // 加在基础区的「附加伤害」上（和基础值加算），并且是**叠加**到已有附加伤害上
            damageSource.setSpec(spec.withFlatDamageBonus(spec.getFlatDamageBonus() + bonus));
            consumeOneStack(holder, character, instance);
        }

        @Override
        public void onEffectOverride(Player holder, PGCharacter character,
                                     CharacterEffectInstance existingInstance,
                                     CharacterEffectInstance newInstance) {
            // 施放战技时刷新：时长取更长的、层数取更多的（和冰凌一致）
            newInstance.setDuration(Math.max(existingInstance.getDuration(), newInstance.getDuration()));
            newInstance.setIntData(STACK_KEY, Math.max(existingInstance.getIntData(STACK_KEY),
                    newInstance.getIntData(STACK_KEY)));
        }
    }

    /** 重唱：挂后台/附近的其他角色，逻辑与领唱完全一致（只是受益人不同）。 */
    public static class Refrain extends Antiphon {
    }

    // ==================== 工具 ====================

    /** 消耗一层；层数耗尽就把效果摘掉。 */
    private static void consumeOneStack(Player holder, PGCharacter character,
                                        CharacterEffectInstance instance) {
        int remaining = instance.getIntData(STACK_KEY) - 1;
        if (remaining <= 0) {
            CharacterEffectHelper.removeEffect(holder, character, instance.getEffect());
        } else {
            instance.setIntData(STACK_KEY, remaining);
        }
    }

    /** 按沃雅妮莎的生命值上限算这一层值多少（星扩散 260/档、水冰 140/档，各有上限）。 */
    private static float flatBonus(Player holder, boolean stellar) {
        PGCharacter vodyanitsa = findVodyanitsa(holder);
        if (vodyanitsa == null) return 0f;

        double hp = vodyanitsa.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
        double over = Math.max(0.0, hp - HP_THRESHOLD);
        float steps = (float) (over / HP_PER_STEP);
        float perStep = stellar ? STELLAR_FLAT_PER_STEP : ELEMENT_FLAT_PER_STEP;
        float cap = stellar ? STELLAR_FLAT_CAP : ELEMENT_FLAT_CAP;
        return Math.min(cap, steps * perStep);
    }

    /** 沃雅妮莎生命值上限的 1%（1 命的攻击力加成用）。 */
    public static float onePercentOfMaxHp(Player holder) {
        PGCharacter vodyanitsa = findVodyanitsa(holder);
        if (vodyanitsa == null) return 0f;
        double hp = vodyanitsa.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
        return (float) (hp * 0.01);
    }

    /** 从队伍里找沃雅妮莎（和冰凌找申鹤同一套写法）。 */
    private static PGCharacter findVodyanitsa(Player holder) {
        PlayerCharactersAttachment attachment = holder.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null) return null;
        var holder0 = ModCharacters.VODYANITSA;
        return attachment.getCharacterByUUID(holder0.get().getCharacterUUID());
    }

    /** 最近一次「流荡风旋引爆」的游戏刻；{@code -1} = 从来没引爆过。 */
    private static long lastDetonationTick = -1L;

    /** 流荡风旋引爆时盖章，供上面那个 5 秒窗口判断。 */
    public static void markFlowingDetonation(long gameTime) {
        lastDetonationTick = gameTime;
    }

    /**
     * 场上（以玩家为球心半径 20 格）有没有流荡风旋，或者刚引爆完 5 秒内。
     *
     * <p>⚠️ 这里以前踩过一个**恒真**的坑：{@code lastDetonationTick} 初始化成
     * {@code Long.MIN_VALUE}，于是 {@code gameTime - lastDetonationTick} 直接**溢出**，
     * 结果「刚引爆过」这个条件在从没引爆过时也成立 —— 表现就是：
     * 场上明明没有流荡风旋，突破天赋 2 却一直按「星扩散模式」走，
     * 沃雅妮莎自己的水伤一层都不消耗（附加伤害永远是 0）。
     * 现在用 {@code -1} 当哨兵值，并且要求 {@code lastDetonationTick >= 0}。
     */
    public static boolean flowingSwirlActive(Player holder) {
        if (holder == null || !(holder.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();

        // 「引爆后的 5 秒」窗口（由 StellarVortexEntity 引爆时盖章）
        if (lastDetonationTick >= 0L && now >= lastDetonationTick
                && now - lastDetonationTick <= 5 * 20) {
            LOGGER.info("[沃雅妮莎] 流荡风旋判定 = true（引爆窗口）：引爆刻={} 现在={} 距今={}刻 | 玩家={}",
                    lastDetonationTick, now, now - lastDetonationTick, holder.getName().getString());
            return true;
        }

        var box = new net.minecraft.world.phys.AABB(holder.position(), holder.position())
                .inflate(FLOWING_CHECK_RADIUS);
        for (var vortex : level.getEntitiesOfClass(
                com.linweiyun.genshin.content.entities.area.StellarVortexEntity.class, box)) {
            if (vortex.isFlowingSwirl()) {
                LOGGER.info("[沃雅妮莎] 流荡风旋判定 = true（场上有风旋）：坐标=({}, {}, {}) 距离={}格 | 玩家={}",
                        String.format("%.2f", vortex.getX()), String.format("%.2f", vortex.getY()),
                        String.format("%.2f", vortex.getZ()),
                        String.format("%.1f", Math.sqrt(vortex.distanceToSqr(holder.position()))),
                        holder.getName().getString());
                return true;
            }
        }
        return false;
    }
}
