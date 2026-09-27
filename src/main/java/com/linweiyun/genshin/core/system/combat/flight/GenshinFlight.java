package com.linweiyun.genshin.core.system.combat.flight;

import com.linweiyun.genshin.config.GameplayConfig;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>原神模式下的「自由飞行」</b>——它本身没有自己的状态字段，只有一个判据：
 * <b>原神模式 + {@code abilities.flying}</b>（也就是原版创造飞行的那一套，能悬停、能上下）。
 *
 * <h2>怎么进来的</h2>
 * 二连跳：原版那套「双击跳跃 → 切换创造飞行」照用（原神模式里我们让 {@code mayFly()} 为真），
 * 但<b>不立刻起飞</b>——客户端先走一段前摇动画（{@code client.combat.GenshinFlightController}），
 * 前摇结束才把 {@code flying} 打开。前摇期间移动就作废。
 *
 * <h2>飞行期间的三条规矩</h2>
 * <ul>
 *   <li><b>不能换角色</b>：客户端拦 V 键，服务端 {@code NetworkManager#characterSelectionRPCPacket} 也拒；</li>
 *   <li><b>不能放技能</b>：写在技能基类（{@code SkillBase#canCast}）而不是拦按键 ——
 *       部分角色的部分技能本来就允许在空中放，那种角色覆盖基类即可；</li>
 *   <li><b>普攻 = 下落攻击</b>：飞行中按左键直接进入下落攻击（关掉飞行、扎下去、落地一巴掌）。</li>
 * </ul>
 *
 * <h2>代价：鞘翅耐久 × 倍率</h2>
 * 原版滑翔是每 {@value #VANILLA_GLIDE_DAMAGE_INTERVAL} 刻扣 1 点耐久；自由飞行按
 * {@code 20 / 倍率} 刻扣 1 点（默认倍率 2.5 → 每 8 刻 1 点，见 {@code GameplayConfig}）。
 * <b>没有滑翔装备就飞不起来</b>，飞着的时候装备碎了 / 被拿走也会立刻停飞。
 *
 * <p>滑翔装备的判定走 NeoForge 那一套（{@code GLIDING_FLIGHT} 属性 > 0，
 * 原版鞘翅就是靠它），所以以后加别的翅膀类装备不用改这里。
 */
public final class GenshinFlight {

    /** 原版滑翔的耐久节奏：每 20 刻扣 1 点。 */
    public static final int VANILLA_GLIDE_DAMAGE_INTERVAL = 20;

    /**
     * 「这份创造飞行能力是我们给的」——只记服务端、只记生存玩家。
     *
     * <p>原神模式给生存玩家打开 {@code mayFly}，退出时只收<b>我们自己发的这一份</b>：
     * 创造 / 旁观本来就有的飞行、别的 MOD 给的能力，一概不碰 ——
     * 不然就会出现「退出原神模式后创造飞行没了」这种事故。
     */
    private static final Set<UUID> GRANTED_MAYFLY = ConcurrentHashMap.newKeySet();

    private GenshinFlight() {
    }

    /** 这个玩家是不是开着原神模式。 */
    public static boolean isGenshinMode(Player player) {
        return player != null
                && Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT));
    }

    /**
     * 现在是不是「原神模式的自由飞行」中（旁观者不算 —— 他们的飞行归原版管）。
     *
     * <p>创造模式<b>算</b>：飞行期间的规矩（不能放技能 / 不能换角色 / 普攻转下落攻击）
     * 对创造模式一样生效；但创造模式<b>不设门槛也不收费</b>（不用鞘翅、不扣耐久、不要前摇），
     * 见 {@link #tick}。
     */
    public static boolean isFlying(Player player) {
        return player != null && isGenshinMode(player) && !player.isSpectator()
                && player.getAbilities().flying;
    }

    /** 身上有没有滑翔装备（鞘翅这类）—— 原神模式的飞行要有它才点得起来。 */
    public static boolean hasGlider(Player player) {
        return !gliderSlots(player).isEmpty();
    }

    /** 身上所有「能滑翔」的装备槽（原版就是按这个找鞘翅的）。 */
    public static List<EquipmentSlot> gliderSlots(Player player) {
        if (player == null) {
            return List.of();
        }
        return EquipmentSlot.VALUES.stream()
                .filter(slot -> LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot))
                .toList();
    }

    /** 飞行的耐久节奏：多少刻扣 1 点（倍率 0 = 不扣）。 */
    public static int glideDamageInterval() {
        double multiplier = GameplayConfig.flightElytraDurabilityMultiplier();
        if (multiplier <= 0.0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.round(VANILLA_GLIDE_DAMAGE_INTERVAL / multiplier));
    }

    /** 扣一点滑翔装备的耐久（随机挑一个装备槽，和原版一样）。 */
    public static void damageGlider(Player player) {
        List<EquipmentSlot> slots = gliderSlots(player);
        if (slots.isEmpty()) {
            return;
        }
        EquipmentSlot slot = slots.get(player.getRandom().nextInt(slots.size()));
        ItemStack stack = player.getItemBySlot(slot);
        stack.hurtAndBreak(1, player, slot);
    }

    /** 让这个玩家飞得起来 / 飞不起来（创造飞行那两面旗标里的一面）。 */
    public static void setMayFly(Player player, boolean value) {
        if (player.getAbilities().mayfly == value) {
            return;
        }
        player.getAbilities().mayfly = value;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }

    /** 停飞（服务端调用会把 new abilities 同步给客户端）。 */
    public static void stopFlying(Player player) {
        if (!player.getAbilities().flying) {
            return;
        }
        player.getAbilities().flying = false;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }

    /**
     * 服务端每刻维护一次：
     * <ul>
     *   <li>原神模式 → 允许创造飞行（{@code mayFly}）；退出原神模式 → 收回（创造 / 旁观者留着）；</li>
     *   <li>飞着 → 按倍率扣滑翔装备耐久；没装备了 / 倍率为 0 且没装备 → 停飞。</li>
     * </ul>
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        boolean nativeFlight = player.isCreative() || player.isSpectator();

        if (!isGenshinMode(player)) {
            // 原神模式之外：飞行是玩家自己的事（创造 / 旁观 / 别的 MOD），一律不碰。
            // 只把自己加的那份能力收回去 —— 收之前先停飞，否则会出现「不能飞却还在飞」
            if (GRANTED_MAYFLY.remove(player.getUUID())) {
                stopFlying(player);
                setMayFly(player, false);
            }
            return;
        }

        if (nativeFlight) {
            // 创造 / 旁观：飞行本来就有 —— 不设门槛、不收费、也不要我们再给旗标
            GRANTED_MAYFLY.remove(player.getUUID());
            return;
        }

        // 生存玩家：原神模式的创造飞行能力由我们给（别的地方清了回来就再打开一次）
        if (!GRANTED_MAYFLY.contains(player.getUUID()) || !player.getAbilities().mayfly) {
            setMayFly(player, true);
            GRANTED_MAYFLY.add(player.getUUID());
        }

        if (!player.getAbilities().flying) {
            return;
        }
        // 飞行要「有滑翔装备」兜底：飞起来之后鞘翅碎了 / 被拿走，立刻停飞
        if (!hasGlider(player)) {
            stopFlying(player);
            return;
        }

        int interval = glideDamageInterval();
        if (interval != Integer.MAX_VALUE && player.tickCount % interval == 0) {
            damageGlider(player);
        }
    }
}
