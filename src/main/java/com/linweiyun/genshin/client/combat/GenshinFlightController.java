package com.linweiyun.genshin.client.combat;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.combat.state.ActionInputFreeze;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.jetbrains.annotations.Nullable;

/**
 * <b>二连跳 → 前摇 → 自由飞行</b> 的客户端那一半。
 *
 * <h2>前摇期间<b>就已经是飞行状态</b>（只锁移动）</h2>
 * 开飞沿用原版那套创造飞行开关（双击跳跃，见 {@code LocalPlayer#aiStep}）——
 * 原神模式里我们把 {@code mayFly()} 打开，所以这一套本来就能用。
 *
 * <p>用户口径：「前摇期间角色要微微离地、不算触地，不然飞行模式一直打不开 ——
 * 相当于前摇期间已经是飞行模式了，只是这会不能动」。所以前摇<b>不</b>把 {@code flying}
 * 压回去，而是：
 * <ul>
 *   <li>{@code flying} 保持打开（所以前摇里是悬停、微微离地，不会落地）——
 *       真按回去的话，一跳的滞空时间不够长就会落地，而原版那句
 *       {@code onGround() && flying → flying = false} 会把飞行关掉，飞行模式永远开不起来；</li>
 *   <li><b>前摇期间不能动</b>（用户口径 2026-09-27：先是要「能往前、不能往上飞」，
 *       随后改成「把移动也禁止了」）—— 方向 / 跳跃 / 下蹲<b>全部清掉</b>
 *       （{@code MovementInputUpdateEvent} 里置空），高度自然也不变；</li>
 *   <li>动作锁铺满前摇：这段里按攻击 / 技能不会打断它（前摇很短，避免「起手就被自己打断」）。</li>
 * </ul>
 * 前摇走完只是解掉动作锁 + 收掉动画状态，飞行状态一路保持。
 *
 * <h2>前摇会被什么打断</h2>
 * 正常情况下走完就起飞；只有「被别的动作顶上来」才会作废（保底判断，见 {@link #tickWindUp}）。
 * 被打断 = 这次二连跳作废、飞行关掉，要飞得再跳一次。
 *
 * <h2>谁走前摇</h2>
 * <b>只有生存模式走</b>：创造 / 旁观直接走原版（双击就飞、不要装备、不扣耐久）——
 * 用户口径是「创造模式照常」，所以要在游戏里看出场动画得用生存。
 *
 * <h2>前摇刻数</h2>
 * 每个角色不一样（林薇云是每个武器形态一档），由 {@code SkillBase#flyStartTicks()} 给，
 * 动画名同样是角色的（{@code SkillBase#flyStartAnimation()}）。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class GenshinFlightController {

    /** 前摇进行中（还没起飞）。 */
    private static boolean windingUp;
    /** 前摇还剩几刻。 */
    private static int windUpRemaining;
    /** 这一轮前摇用的动画名（用来认出「被别的动作顶掉」）。 */
    private static String windUpAnimation;
    /** 客户端这份「原神飞行中」的镜像；权威在服务端（{@code abilities} 同步）。 */
    private static boolean flying;
    /**
     * 这一份镜像有没有跟当前玩家对齐过。
     *
     * <p>重进存档时 {@code abilities.flying} 是**存档里恢复的 true**（人还在飞），
     * 而我们的镜像从零开始 = false —— 于是第一次 tick 被误判成「刚打开飞行」，
     * 又走了一遍前摇（第一人称看不见，切第三人称那一下正好看到）。
     * 用户 2026-09-27 定位得很准：**fly 变量没保存**。第一次见到玩家就按实际状态对齐即可。
     */
    private static boolean synced;

    private GenshinFlightController() {
    }

    public static boolean isWindingUp() {
        return windingUp;
    }

    /** 原神模式自由飞行中（客户端镜像）。 */
    public static boolean isFlying() {
        return flying;
    }

    /** 前摇中或者正在飞 —— 「不许换角色」看这个。 */
    public static boolean isFlyingOrWindingUp() {
        return windingUp || flying;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reset();
            return;
        }
        // 创造 / 旁观：飞行本来就是他们的，原样走原版 —— 不拦二连跳、不走前摇（用户口径：创造模式照常）。
        // 所以**看出场动画要在生存模式**（创造是双击直接飞；用户 2026-09-27 已确认这条口径）。
        if (!GenshinFlight.isGenshinMode(player) || player.isSpectator() || player.isCreative()) {
            if (windingUp || flying) {
                windingUp = false;
                flying = false;
            }
            synced = true;
            return;
        }

        // 新的一局 / 重进存档：先按存档里的实际飞行状态对齐，**不要**补一次前摇
        if (!synced) {
            synced = true;
            flying = player.getAbilities().flying;
            if (flying) {
                return;
            }
        }

        // 下落攻击中：飞行一律按掉（否则「飞着按左键扎下去」会被飞行物理顶回来）
        if (PlungeAttack.isActive()) {
            if (windingUp) {
                abortWindUp(player);
                return;
            }
            if (player.getAbilities().flying) {
                setFlying(player, false);
            }
            return;
        }

        if (windingUp) {
            tickWindUp(player);
            return;
        }

        if (!player.getAbilities().flying) {
            // 原版把飞行关了（再双击一次 / 落地），镜像跟着收
            flying = false;
            return;
        }

        if (flying) {
            return;
        }

        // 原版刚把创造飞行打开（二连跳）= 起飞请求 → 走前摇（前摇期间保持飞行状态，只锁移动）
        if (!canStart(player)) {
            // 没滑翔装备：这次二连跳按掉（生存玩家才需要鞘翅；创造 / 旁观在上面就返回了）
            setFlying(player, false);
            return;
        }
        beginWindUp(player);
    }

    /**
     * 前摇期间<b>锁输入</b>：方向 / 跳跃 / 下蹲全部清掉（用户口径：前摇里不能动）。
     *
     * <p>为什么不只是「不给上升」：跳跃在创造飞行里是上升、下蹲是下降，而<b>一落地原版就会把飞行关掉</b>
     * （{@code LocalPlayer#aiStep} 里那句 {@code onGround() && flying}），飞行模式就再也开不起来了 ——
     * 所以前摇这一小段干脆全锁住，等它走完再交还操作。
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!windingUp || !(event.getEntity() instanceof LocalPlayer)) {
            return;
        }
        ActionInputFreeze.clear(event.getInput());
    }

    // ==================== 前摇 ====================

    /** 起点飞前摇（动画 + 计时都从角色身上取）。 */
    private static void beginWindUp(LocalPlayer player) {
        PGCharacter character = currentCharacter(player);
        int ticks = character != null ? character.getFlyStartTicks() : 8;
        String animation = character != null ? character.getFlyStartAnimation() : "fly_start";

        windingUp = true;
        // 前摇期间就已经是飞行状态：微微离地、不落地（落地会被原版关掉飞行）
        flying = true;
        windUpRemaining = Math.max(1, ticks);
        windUpAnimation = animation;

        // 动作锁铺满前摇（这段里别的动作插不进来）；移动不锁 —— 方向键照常，只有跳跃 / 下蹲被掐掉
        ActionStateMachine.changeState(animation, ActionStateMachine.PRIO_ATTACK,
                windUpRemaining, windUpRemaining, 0);
    }

    private static void tickWindUp(LocalPlayer player) {
        // 飞行状态要一直在：前摇里落地的话原版会把飞行关掉 → 飞行模式就再也开不起来了
        if (!player.getAbilities().flying) {
            setFlying(player, true);
        }
        // 输入已经锁了，这里再把水平惯性按住 —— 「禁止移动」要的是稳稳悬在起手那个点，
        // 而不是带着起手前的速度慢慢飘过去
        Vec3 delta = player.getDeltaMovement();
        if (delta.x != 0.0 || delta.z != 0.0) {
            player.setDeltaMovement(0.0, delta.y, 0.0);
        }

        // 输入已锁死，所以这里只剩「被别的动作顶掉」这一种打断
        boolean otherAction = !windUpAnimation.equals(ActionStateMachine.currentState)
                && !ActionStateMachine.DEFAULT_STATE.equals(ActionStateMachine.currentState);

        if (otherAction) {
            abortWindUp(player);
            return;
        }

        if (--windUpRemaining <= 0) {
            finishWindUp(player);
        }
    }

    /** 前摇走完 → 真正起飞。 */
    private static void finishWindUp(LocalPlayer player) {
        windingUp = false;
        windUpAnimation = null;
        ActionStateMachine.resetToDefault();
        setFlying(player, true);
    }

    /** 外部（下落攻击）要求把起飞前摇作废。 */
    public static void abortWindUp() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && windingUp) {
            abortWindUp(player);
        }
    }

    /** 前摇作废：这次二连跳白按了。 */
    private static void abortWindUp(LocalPlayer player) {
        windingUp = false;
        windUpAnimation = null;
        ActionStateMachine.resetToDefault();
        setFlying(player, false);
    }

    // ==================== 工具 ====================

    /**
     * 起飞要有滑翔装备（鞘翅这类），而且当前出战角色得是这套飞行的准入角色。
     * （创造 / 旁观在上面就返回了，走不到这里。）
     */
    private static boolean canStart(LocalPlayer player) {
        return GenshinFlight.allowsGenshinFlight(player) && GenshinFlight.hasGlider(player);
    }

    /** 改客户端那份 abilities 并同步给服务端（两边都别留在错的状态上）。 */
    private static void setFlying(LocalPlayer player, boolean value) {
        flying = value;
        if (player.getAbilities().flying == value) {
            return;
        }
        player.getAbilities().flying = value;
        player.onUpdateAbilities();
    }

    private static void reset() {
        windingUp = false;
        flying = false;
        windUpAnimation = null;
        synced = false;
    }

    @Nullable
    private static PGCharacter currentCharacter(LocalPlayer player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}
