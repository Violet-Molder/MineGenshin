package com.linweiyun.genshin.client.combat;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * <b>下落攻击的客户端那一半</b>：触发判定、加速下坠、动作状态、落地收尾。
 * （落地伤害在服务端，见 {@code CharacterTickHandler#tickPlungeAttack}。）
 *
 * <h2>怎么触发</h2>
 * 「坠落中（离地、已经掉了 {@value #MIN_FALL_DISTANCE} 格以上）按普攻」——
 * 由 {@code ActionStateMachine.pressAttack} 在普攻 / 重击那一套之前问一次；
 * 触发后<b>不再</b>走普攻，也不走大剑的「按住先蓄力」。
 *
 * <h2>触发之后是什么状态</h2>
 * 一个<b>任何输入都拦得住</b>的持续状态（用户口径）：切角色 / 攻击 / 移动 / 技能 /
 * 切原神模式 / 开界面全部无效，直到落地。
 *
 * <ul>
 *   <li><b>攻击 / 技能 / 闪避 / 大招</b>：动作状态机用 {@link ActionStateMachine#PRIO_FINAL}
 *       占住优先级 —— 这四个入口都过 {@code canInterrupt}，优先级 4 之后一律拒绝；</li>
 *   <li><b>移动</b>：{@code movementLock} 铺满整个状态，移动输入被清空
 *       （{@code MovementInputUpdateEvent} 那条路）；</li>
 *   <li><b>切人 / 切模式 / 开界面</b>：{@code KeyInputHandler} 看
 *       {@link #isActive()} 直接不接。</li>
 * </ul>
 *
 * <h2>加速下坠</h2>
 * 每刻把垂直速度直接压到 {@link PGCharacter#getPlungingFallSpeed()}（默认 1.5 格/刻，
 * 原版自由落体的终端速度约 0.98 格/刻），水平不给输入、但保留原有惯性 ——
 * 所以看起来是「猛地扎下去」，而不是原地掉。
 *
 * <p>{@code fallDistance} <b>不重置</b>：摔落伤害按「从开始掉到落地一共多少格」算
 * （下落攻击那条曲线把免伤区间抬到 38 格）。
 */
public final class PlungeAttack {

    /** 触发所需的最小坠落高度（格）——用户口径：坠空高度 &gt; 2。 */
    public static final double MIN_FALL_DISTANCE = 2.0;

    private static boolean active;

    private PlungeAttack() {
    }

    /** 现在是不是处于下落攻击状态（「输入全拦」的判据）。 */
    public static boolean isActive() {
        return active;
    }

    // ==================== 触发 ====================

    /**
     * 坠落中按下普攻那一刻问一次「这一下是不是下落攻击」。
     *
     * @return true = 已经进入下落攻击（调用方不要再走普攻 / 重击那一套）
     */
    public static boolean tryBegin(Player player) {
        if (!(player instanceof LocalPlayer local)) {
            return false;
        }
        if (!canTrigger(local)) {
            return false;
        }
        PGCharacter character = currentCharacter(local);
        if (character == null) {
            return false;
        }
        // 大招那种「绝对霸体」（优先级 4）期间不给进：和普攻 / 技能遵守同一套打断规则
        if (!ActionStateMachine.canInterrupt(ActionStateMachine.PRIO_ATTACK)) {
            return false;
        }

        begin(local, character);
        return true;
    }

    private static void begin(LocalPlayer player, PGCharacter character) {
        active = true;
        // 从飞行里扎下去：先把飞行关掉，高度从「松手那一刻」重新算（用户口径里的下落攻击高度）
        if (GenshinFlight.isFlying(player)) {
            GenshinFlight.stopFlying(player);
            // 客户端这边 core 那份 stopFlying 只改本地旗标，同步给服务端要自己来
            player.onUpdateAbilities();
            player.fallDistance = 0.0;
        }
        // 万一还挂着原版滑翔姿态（进原神模式之前就在滑），也一并停掉
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        // 起飞前摇还在走的时候按了普攻：前摇作废，这一下当下落攻击
        GenshinFlightController.abortWindUp();
        PlungeState.begin(player, character.getCharacterUUID());
        ActionServer.performPlungingAttackToServer();
        ActionStateMachine.clearFollowUpState();
        // 优先级 4 + 硬直 / 移动锁铺满整个状态：攻击 / 技能 / 闪避 / 大招 / 移动全部拦在里面。
        // 动画循环播（下落姿态一直摆着），真正的结束由落地那一刻决定（见 tick）。
        ActionStateMachine.changeState(
                character.getPlungingAnimation(),
                ActionStateMachine.PRIO_FINAL,
                PlungeState.MAX_TICKS,
                PlungeState.MAX_TICKS,
                PlungeState.MAX_TICKS,
                0,
                true);
    }

    /** 现在按普攻能不能触发下落攻击。 */
    public static boolean canTrigger(LocalPlayer player) {
        if (active
                || player.onGround()
                || player.isInWater()
                || player.onClimbable()
                || player.isPassenger()
                || player.isSpectator()) {
            return false;
        }
        // 自由飞行中：普攻直接扎下去（用户口径）
        if (GenshinFlight.isFlying(player)) {
            return true;
        }
        // 平时：坠落超过 2 格（滑翔姿态也算「在空中」，按下去就把滑翔停掉改扎）
        return player.fallDistance > MIN_FALL_DISTANCE || player.isFallFlying();
    }

    // ==================== 每刻 ====================

    /** 每客户端 tick 调一次（在状态机 tick 之前，和突进 / 下坠大招同一批）。 */
    public static void tick(LocalPlayer player) {
        if (!active) {
            return;
        }
        if (shouldStop(player)) {
            end(player);
            return;
        }

        PGCharacter character = currentCharacter(player);
        double fallSpeed = character != null
                ? character.getPlungingFallSpeed()
                : SkillBase.DEFAULT_PLUNGING_FALL_SPEED;
        // 只压垂直那一轴：水平保持惯性（输入已经被锁，不会再加速）
        player.setDeltaMovement(player.getDeltaMovement().x, -fallSpeed, player.getDeltaMovement().z);
        player.hurtMarked = true;
    }

    /**
     * 到该结束的时候了 —— 落地，或者这一下已经打不成了。
     *
     * <p>落地（砸到地面）在服务端结算伤害，客户端只负责收状态。
     */
    private static boolean shouldStop(LocalPlayer player) {
        if (player.onGround()) {
            return true;
        }
        if (player.isInWater() || player.onClimbable() || player.isFallFlying()
                || player.getAbilities().flying || player.isSpectator() || player.isPassenger()) {
            return true;
        }
        return PlungeState.elapsedTicks(player) > PlungeState.MAX_TICKS;
    }

    /** 收状态（换角色 / 下线 / 重登都走这里）。 */
    public static void cancel() {
        LocalPlayer player = Minecraft.getInstance().player;
        active = false;
        if (player != null) {
            PlungeState.clear(player);
            ActionStateMachine.resetToDefault();
        }
    }

    private static void end(LocalPlayer player) {
        active = false;
        PlungeState.end(player);
        ActionStateMachine.resetToDefault();
    }

    @Nullable
    private static PGCharacter currentCharacter(LocalPlayer player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}
