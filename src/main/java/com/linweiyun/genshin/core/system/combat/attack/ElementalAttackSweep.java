package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.about.AttachmentProfile;
import com.linweiyun.genshin.core.system.about.block.BlockElementHelper;
import com.linweiyun.genshin.core.system.about.block.BlockElementRules;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * <b>攻击范围里的「可附着方块」</b> —— 一次攻击不只打实体，也把元素留给环境。
 *
 * <p>为什么需要它：原神模式下的每一次攻击都是「手动取范围 → 遍历 → 结算」，
 * 但那条遍历只找 {@code LivingEntity}，方块永远接不到元素 —— 于是「冰打水面不结冰、
 * 火打冰不融化」这类需求只能靠另开一条支线（例如监听左键)去补，内核就分裂了。
 *
 * <p>本类把这段范围遍历补齐，并且<b>不新开附着体系</b>：
 * <pre>
 *   实体（走伤害）    hurtServer → DirectDamagePipeline → ElementalAttachmentHelper.attach
 *   方块（走附着）    本类 → BlockElementHelper.applyElement → ElementalAttachmentHelper.attach
 * </pre>
 * 两条路在 {@code ElementalAttachmentHelper} 汇合，后面「附着 → 附着内反应 → 反应引发效果」
 * 是同一套代码。方块能不能被这个元素附着由 {@link BlockElementRules} 回答（宿主第一段筛查），
 * 能发生什么反应由 {@code BlockHost.acceptsReaction} + 反应注册表回答，这里一概不判断。
 *
 * <p>几何取「玩家眼睛 → 视线方向 × 这一招的生效攻击距离」，再按 {@link #INFLATE} 膨胀。
 * 之所以和角色天赋里那个「{@code player.position() → 视线 2.5 格、膨胀 1.0}」不完全同源：
 * 伤害的收集体现在每一条天赋里（每个角色的打击盒本来就不同），而方块附着希望只在一处接线、
 * 覆盖普攻/重击/战技/大招全部招式，所以这里用「这一招的攻击距离」做统一近似。
 */
public final class ElementalAttackSweep {

    private static final org.slf4j.Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /** 扫描盒的膨胀（格）—— 让「瞄着水面打」时能把水面那一格的邻居也带上。 */
    private static final double INFLATE = 1.0;

    /** 攻击距离的下限（格）：攻击距离没配或配得比近战盒还小时用它。 */
    private static final double MIN_REACH = 2.5;

    /** 扫描体上限，防止某个招式把攻击距离配成离谱的值时遍历爆炸。 */
    private static final int MAX_VOLUME = 4096;

    /** 方块侧的附着量 / 衰减：与实体普通攻击同一档（弱附着 1U）。 */
    private static final float GAUGE = AttachmentProfile.WEAK.getBaseQuantity();
    private static final float DECAY_PER_SECOND = AttachmentProfile.WEAK.getDecayPerSecond();

    private ElementalAttackSweep() {
    }

    /**
     * 动作的<b>伤害点</b>入口 —— 由 {@code ActionState} 在每个 {@code hits[]} 触发点调用一次，
     * 与实体伤害同一个时刻、同一招一次。
     *
     * @return 这次真正附着上的方块数（0 表示没有可附着的方块，或门禁没过）
     */
    public static int forAction(Player player, PGCharacter character, ActionDefinition definition) {
        if (definition == null || !appliesTo(definition.kind)) {
            return 0;
        }
        double reach = definition.step != null
                ? Math.max(MIN_REACH, definition.step.effectiveAttackRange())
                : MIN_REACH;
        return forPlayer(player, character == null ? null : character.getElemental(), reach);
    }

    /**
     * 哪些动作会把元素留在环境里。
     *
     * <p>闪避是纯位移，没有伤害点，也就不该附着；其余招式（普攻/重击/下落/战技/大招）都算。
     */
    public static boolean appliesTo(@Nullable ActionKind kind) {
        return kind != null && kind != ActionKind.DODGE;
    }

    /**
     * 玩家侧的附着入口 —— 门禁都在这里，顺序即语义：
     * <ol>
     *   <li><b>只在服务端</b>：客户端只是表现，权威结算在服务端；</li>
     *   <li><b>只在原神模式</b>：非原神模式（原版玩法）不产生任何元素附着；</li>
     *   <li>角色必须带元素（物理系 {@code FYSIKOS} 不附着）。</li>
     * </ol>
     *
     * @return 真正附着上的方块数
     */
    public static int forPlayer(Player player, @Nullable GenshinElement element, double reach) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        if (!player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)) {
            return 0;
        }
        if (element == null || element == ModElements.FYSIKOS.get()) {
            return 0;
        }
        Vec3 origin = player.getEyePosition();
        Vec3 direction = CombatAim.direction(player);
        return sweep(level, player, element, origin, direction, reach, INFLATE);
    }

    /**
     * 几何核心（与玩家、门禁无关，便于单独验证）。
     *
     * <p>从 {@code origin} 沿 {@code direction} 扫 {@code reach} 格，把这段盒子（按 {@code inflate}
     * 膨胀）里所有「收这个元素」的方块交给方块附着入口。
     * <b>攻击者自己身体占着的那一格会跳过</b>：不然站在水里挥一刀就把自己脚下的水冻住。
     */
    public static int sweep(ServerLevel level, @Nullable Entity attacker, GenshinElement element,
                            Vec3 origin, Vec3 direction, double reach, double inflate) {
        if (level == null || element == null || origin == null || direction == null) {
            return 0;
        }
        Vec3 normalized = direction.lengthSqr() < 1.0E-6 ? Vec3.ZERO : direction.normalize();
        AABB box = new AABB(origin, origin.add(normalized.scale(Math.max(0.0, reach))))
                .inflate(Math.max(0.0, inflate));

        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > MAX_VOLUME) {
            return 0;
        }

        AABB attackerBox = attacker == null ? null : attacker.getBoundingBox();
        int attached = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    if (attackerBox != null && attackerBox.intersects(new AABB(cursor))) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    // 先问规则表：大多数方块在这一步就被挡掉，不会为它们建元素容器。
                    if (!BlockElementRules.accepts(state, element)) {
                        continue;
                    }
                    BlockElementHelper.applyElement(level, cursor.immutable(), element,
                            GAUGE, DECAY_PER_SECOND);
                    attached++;
                }
            }
        }
        return attached;
    }
}
