package com.linweiyun.genshin.core.character.catalyst.vodyanitsa;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects;
import com.linweiyun.genshin.content.entities.area.StellarVortexEntity;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沃雅妮莎的<b>天赋</b>（突破天赋 / 被动）。
 *
 * <p>这个类名以前是「技能」用的（现在技能搬去 {@link VodyanitsaSkill}）。
 * 这里放的是<b>不由某一招自己打出来</b>的那两段突破天赋：
 *
 * <h2>突破天赋 1（突破 ≥ 1）—— 流荡风旋</h2>
 * <ul>
 *   <li>{@link #songCovers}：遥久之歌期间，队伍附近的角色进入辉映·星扩散时持续时间 +4 秒
 *       （判定点在 {@code SwirlReaction.applyRadianceToParticipants}）；</li>
 *   <li>遥久之歌期间触发星扩散时改为创造「流荡风旋」（判定点 {@code SwirlReaction}）；</li>
 *   <li>创造 / 引爆流荡风旋时降低附近敌人风抗 35%（6 秒）：
 *       创造那边调 {@link #shredWindAround}，引爆那边在 {@code StellarVortexEntity.explode()}，
 *       到期由 {@link #clearExpiredWindShred} 清理（在唱的沃雅妮莎每刻调一次）；</li>
 *   <li>{@link #convertVorticesNearby}：施放战技时把当前场上的星辉风旋转化为流荡风旋。</li>
 * </ul>
 *
 * <h2>突破天赋 2（突破 ≥ 4）—— 领唱 / 重唱</h2>
 * {@link #grantAscend2SongStacks}：当前场上角色 25 层「领唱」、其余队伍角色 10 层「重唱」，
 * 各 30 秒；施放战技时刷新（效果类内部按「取更多」处理）。
 *
 * <p>⚠️ 风旋的「转化」和「改创」都只是给风旋<b>打标记</b>：降风抗的两处（创造 / 引爆）
 * 都调 {@link #shredWindAround}，风旋的其它行为一行没改。
 */
public class VodyanitsaTalent extends TalentBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 「遥久之歌」的覆盖范围（突破天赋 1 用）。 */
    public static final double SONG_AURA_RANGE = 13.0;

    /** 突破天赋 1：遥久之歌期间进入辉映·星扩散，持续时间 +4 秒。 */
    public static final int RADIANCE_EXTEND_TICKS = 4 * 20;

    /** 突破天赋 1 的解锁档 / 突破天赋 2 的解锁档。 */
    private static final int ASCEND1_PHASE = 1;
    private static final int ASCEND2_PHASE = 4;

    /**
     * 这个位置有没有被「遥久之歌」覆盖 —— 队伍里任一沃雅妮莎正在唱、且离该位置够近。
     *
     * <p>突破天赋 1 用：遥久之歌期间队伍附近的角色进入辉映·星扩散时，持续时间 +4 秒。
     */
    public static boolean songCovers(ServerLevel level, double x, double y, double z) {
        double rangeSqr = SONG_AURA_RANGE * SONG_AURA_RANGE;
        for (var serverPlayer : level.players()) {
            var attachment = serverPlayer.getData(
                    com.linweiyun.genshin.core.attachment.AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (attachment == null) continue;
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member instanceof Vodyanitsa vodyanitsa && vodyanitsa.isSongActive()
                        && serverPlayer.position().distanceToSqr(x, y, z) <= rangeSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 流荡风旋：降风抗 ====================

    /** 附近敌人风元素抗性 −35%，持续 6 秒。 */
    public static final float FLOWING_WIND_SHRED = 0.35f;
    public static final int FLOWING_WIND_SHRED_TICKS = 6 * 20;
    /** 「附近」的判定半径（格）。 */
    public static final double FLOWING_WIND_SHRED_RADIUS = 8.0;
    /** 风抗降低的修饰符来源。 */
    public static final String WIND_SHRED_SOURCE = "vodyanitsa_flowing_wind";

    /** 实体 UUID → 风抗降低到期时刻（跨反应，与角色实例解耦）。 */
    private static final Map<UUID, Long> WIND_SHRED_UNTIL = new HashMap<>();

    /**
     * 创造 / 引爆「流荡风旋」时调用：把周围敌人的风抗降低 {@value #FLOWING_WIND_SHRED}（35%），
     * 持续 {@link #FLOWING_WIND_SHRED_TICKS} 刻（6 秒）。
     */
    public static void shredWindAround(ServerLevel level, double x, double y, double z) {
        var box = new AABB(x, y, z, x, y, z)
                .inflate(FLOWING_WIND_SHRED_RADIUS);
        long until = level.getGameTime() + FLOWING_WIND_SHRED_TICKS;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target instanceof Player) continue;
            var stats = target.getData(
                    com.linweiyun.genshin.core.attachment.AttachmentRegistration.ENTITY_STATS);
            // 减抗是「基于目标自身抗性」的**乘算**：抗性 ×(1 - 降低比例)。
            // 属性系统里正好就是百分比修饰符：total = base × (1 + percent) + flat
            //   → 0.1 抗性 + percent(-0.165) = 0.1 × 0.835 = 0.0835 ✔
            // （写 flat 才是加算，那是错的；另外读取端必须读 total，见 TeyvatEntityStats）
            stats.attributes().setPercentModifier(
                    ModAttributes.ANEMO_RES.value(),
                    WIND_SHRED_SOURCE, -FLOWING_WIND_SHRED);
            WIND_SHRED_UNTIL.put(target.getUUID(), until);
        }
    }

    /** 摘掉已到期的风抗降低（由在唱的沃雅妮莎每刻调一次即可）。 */
    public static void clearExpiredWindShred(ServerLevel level) {
        if (WIND_SHRED_UNTIL.isEmpty()) return;
        long now = level.getGameTime();
        var iterator = WIND_SHRED_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now < entry.getValue()) continue;
            iterator.remove();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) continue;
            living.getData(AttachmentRegistration.ENTITY_STATS)
                    .attributes().removeModifier(
                            ModAttributes.ANEMO_RES.value(),
                            WIND_SHRED_SOURCE);
        }
    }

    // ==================== 突破天赋 1：施放战技时转化风旋 ====================

    /**
     * 突破天赋 1（突破 ≥ 1）：把当前场上的星辉风旋转化为「流荡风旋」。
     *
     * <p>转化之后它们在引爆时也会降风抗（创造那一次在 {@code SwirlReaction} 里判定）。
     * 判定框是「以玩家为中心、半径 = 索敌距离 × 2」（沿用原来的 {@code SONG_RANGE * 2.0}）。
     */
    public void convertVorticesNearby(Player player, Vodyanitsa vodyanitsa) {
        if (vodyanitsa.getData().getAscensionPhase() < ASCEND1_PHASE) return;

        var box = new AABB(player.position(), player.position())
                .inflate(VodyanitsaSkill.SONG_RANGE * 2.0);
        for (var vortex : player.level().getEntitiesOfClass(StellarVortexEntity.class, box)) {
            vortex.markFlowingSwirl();
        }
    }

    // ==================== 突破天赋 2：领唱 / 重唱 ====================

    /**
     * 突破天赋 2（突破 ≥ 4）：给当前场上角色 25 层「领唱」、其余队伍角色 10 层「重唱」（各 30 秒）。
     *
     * <p>不足突破 4 时只打一条日志（原来那句 {@code phase >= 4} 的分支挪到这里，
     * 日志文本一字不改）。
     */
    public void grantAscend2SongStacks(Player player, Vodyanitsa vodyanitsa) {
        int phase = vodyanitsa.getData().getAscensionPhase();
        if (phase >= ASCEND2_PHASE) {
            grantSongStacks(player, vodyanitsa);
        } else {
            LOGGER.info("[沃雅妮莎] 突破 {} 级 < 4，突破天赋 2（领唱/重唱）不发放", phase);
        }
    }

    private static void grantSongStacks(Player player, Vodyanitsa vodyanitsa) {
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null) return;
        PGCharacter onField = attachment.getCurrentCharacter();

        for (int i = 0; i < 4; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member == null) continue;

            boolean isOnField = member == onField;
            ICharacterEffect effect;
            int stacks;
            if (isOnField) {
                effect = ModCharacterEffects.VODYANITSA_ANTIPHON_EFFECT.get();
                stacks = VodyanitsaSongEffects.ANTIPHON_STACKS;
            } else {
                effect = ModCharacterEffects.VODYANITSA_REFRAIN_EFFECT.get();
                stacks = VodyanitsaSongEffects.REFRAIN_STACKS;
            }
            if (effect == null) continue;

            var instance = new CharacterEffectInstance(
                    effect, VodyanitsaSongEffects.DURATION_TICKS, 0, false);
            instance.setIntData(VodyanitsaSongEffects.STACK_KEY, stacks);
            CharacterEffectHelper.addEffect(player, member, instance);
            LOGGER.info("[沃雅妮莎] 突破天赋 2 → {} {} 层（{} 刻）",
                    member.getName().getString(), isOnField ? "领唱" : "重唱", stacks);
        }
    }
}
