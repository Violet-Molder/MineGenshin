package com.linweiyun.genshin.core.system.combat.action;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionManager {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /**
     * key = "C:UUID" / "S:UUID"
     * 单机时客户端和服务端在同一个 JVM，UUID 相同，必须用 side 区分。
     */
    private static final Map<String, ActionManager> MANAGERS = new ConcurrentHashMap<>();

    private ActionState current;
    private ActionDefinition buffered;

    /**
     * 当前正在驱动状态机的角色实例。
     * <p>
     * 一个 ActionManager 是 per-player 的，但一个玩家有多个 party 角色。
     * 服务端的 CharacterTickHandler 会 tick 所有 party 成员——如果每个成员的 tick
     * 都无条件推进状态机，会导致 party 之间的动作互相打断。
     * <p>
     * activeCharacter 记录"当前动作属于哪个角色"，只有它的 tick / request 才会
     * 驱动状态机；其他角色的 tick 直接忽略。
     * <p>
     * 玩家切换角色通过显式的 {@link #interrupt(InterruptReason)} 打断；request 时
     * 如果传入的 character 与 activeCharacter 不同，也会自动打断（兜底）。
     */
    private PGCharacter activeCharacter;

    private int lastComboIndex = 0;
    private long lastComboEndTick = Long.MIN_VALUE;

    public static ActionManager get(Player player) {
        String key = (player.level().isClientSide() ? "C:" : "S:") + player.getUUID();
        return MANAGERS.computeIfAbsent(key, k -> new ActionManager());
    }

    public static void remove(Player player) {
        String key = (player.level().isClientSide() ? "C:" : "S:") + player.getUUID();
        MANAGERS.remove(key);
    }

    public boolean requestNormalAttack(Player player, PGCharacter character) {
        return requestNormalAttack(player, character, -1);
    }

    /**
     * @param requestedStage 客户端算好的连段段数（1 起）；传 {@code <= 0} 表示让服务端自己推。
     *                       开启动作系统时客户端和服务端步伐一致，用客户端段数可以避免两边
     *                       因为丢包/延迟而错位；关闭动作系统时服务端状态机不推进，必须靠它。
     */
    public boolean requestNormalAttack(Player player, PGCharacter character, int requestedStage) {
        String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
        ActionSet set = character.getActionSet(player);
        if (set == null) {
            LOGGER.warn("[ActionManager] [{}] requestNormalAttack: actionSet=null (talent={})",
                    side, character.getTalentDebugInfo());
            return false;
        }
        if (set.getNormalComboSize() == 0) {
            LOGGER.warn("[ActionManager] [{}] requestNormalAttack: comboSize=0", side);
            return false;
        }

        int idx = resolveNextComboIndex(player, set, requestedStage);
        return request(player, character, set.getNormalAttack(idx));
    }

    public boolean requestChargedAttack(Player player, PGCharacter character) {
        String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
        ActionSet set = character.getActionSet(player);
        if (set == null) {
            LOGGER.warn("[ActionManager] [{}] requestChargedAttack: actionSet=null", side);
            return false;
        }
        return request(player, character, set.getChargedAttack());
    }

    public boolean requestElementalSkill(Player player, PGCharacter character, int skillTime) {
        String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[ActionManager] [{}] requestElementalSkill skillTime={}", side, skillTime);

        ActionSet set = character.getActionSet(player);
        if (set == null) {
            LOGGER.warn("[ActionManager] [{}] actionSet=null (talent={})",
                    side, character.getTalentDebugInfo());
            return false;
        }
        boolean longPress = skillTime >= 1000;
        ActionDefinition def = longPress ? set.getElementalSkillHold() : set.getElementalSkillTap();
        if (def == null) {
            LOGGER.warn("[ActionManager] [{}] elementalSkill{} def=null (comboSize={})",
                    side, longPress ? "Hold" : "Tap", set.getNormalComboSize());
            return false;
        }

        // 1. CD / 能量检查
        //    和客户端 {@code ActionCastGuard.canCast} 走的是同一个方法 ——
        //    一处规则两端一致：客户端不通过就连动画都不播，服务端这里是权威复核。
        if (!character.canCast(player, def.kind, skillTime)) {
            LOGGER.info("[ActionManager] [{}] canCast=false, rejected", side);
            character.sendCastFailedMessage(player, def.kind);
            return false;
        }

        // 2. 启动动作
        if (!request(player, character, def)) {
            LOGGER.info("[ActionManager] [{}] request() rejected (busy?)", side);
            return false;
        }

        // 3. 立即设 CD
        character.applyElementalSkillCooldown(player, skillTime);
        // 4. 武器被动：装备者施放战技（触发即生效，服务端权威）
        notifyWeaponAbilityCast(player, character, def.kind);
        return true;
    }

    /** 把「施放了一招」告诉装备者的武器（武器被动用）。 */
    private static void notifyWeaponAbilityCast(Player player, PGCharacter character, ActionKind kind) {
        if (player.level().isClientSide()) return;
        var weapon = character.getData().getWeapon();
        if (weapon != null && !weapon.isEmpty()
                && weapon.getItem() instanceof WeaponItem weaponItem) {
            weaponItem.onAbilityCast(player, character, kind);
        }
    }

    public boolean requestElementalBurst(Player player, PGCharacter character) {
        String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[ActionManager] [{}] requestElementalBurst", side);

        ActionSet set = character.getActionSet(player);
        if (set == null) {
            LOGGER.warn("[ActionManager] [{}] actionSet=null", side);
            return false;
        }
        ActionDefinition def = set.getElementalBurst();
        if (def == null) {
            LOGGER.warn("[ActionManager] [{}] burst def=null", side);
            return false;
        }

        if (!character.canCast(player, def.kind, 0)) {
            LOGGER.info("[ActionManager] [{}] burst canCast=false", side);
            character.sendCastFailedMessage(player, def.kind);
            return false;
        }

        if (!request(player, character, def)) {
            LOGGER.info("[ActionManager] [{}] burst request() rejected", side);
            return false;
        }

        character.applyElementalBurstCooldown(player);
        // 武器被动：装备者施放元素爆发
        notifyWeaponAbilityCast(player, character, ActionKind.ELEMENTAL_BURST);
        LOGGER.info("[ActionManager] [{}] burst STARTED", side);
        return true;
    }

    /** 闪避：只有表现和位移，没有 CD/能量门槛，所以直接进状态机。 */
    public boolean requestDodge(Player player, PGCharacter character) {
        ActionSet set = character.getActionSet(player);
        if (set == null) return false;

        ActionDefinition def = set.getDodge();
        if (def == null) return false;

        return request(player, character, def);
    }

    private boolean request(Player player, PGCharacter character, ActionDefinition def) {
        if (def == null) return false;

        if (activeCharacter != null && activeCharacter != character) {
            if (current != null && !current.isFinished()) {
                current.interrupt(InterruptReason.SWITCH_CHARACTER);
            }
            buffered = null;
            resetCombo();
        }
        activeCharacter = character;

        if (current != null && !current.isFinished()) {
            if (current.isProtected()) {
                // 执行期内不接受任何新动作 —— 和客户端 ActionStateMachine.canInterrupt 同一条规则，
                // 两端必须一致，否则会出现「客户端播了动画、服务端什么都没做」。
                //
                // 这一段是技能真正在发生（位移 + 动画 + 伤害点），被打断就是
                // 「CD 扣了、能量没了、效果没出来」；想接就得等执行期结束 —— 后摇才是取消窗口。
                LOGGER.info("[ActionManager] [{}] 当前动作在执行期内，请求被拒 kind={}",
                        player.level().isClientSide() ? "CLIENT" : "SERVER", def.kind);
                return false;
            }

            // 准备阶段 / 后摇 / 连击窗口：直接替换
            current.interrupt(InterruptReason.MANUAL);
            if (!def.isCombo()) resetCombo();
        }

        start(player, character, def);
        return true;
    }

    private void start(Player player, PGCharacter character, ActionDefinition def) {
        ActionContext ctx = new ActionContext(player, character, def);
        current = new ActionState(def, ctx);
        scheduleStepMovement(player, character, def);
    }

    /**
     * 把 {@code ActionStep.moves}（前冲/后撤位移）排进服务端时间轴。
     *
     * <p><b>锁着目标的时候不排</b>：近战的手感应该是「自动贴住对手」，
     * 而不是每段都往前推一段固定距离 —— 那样打两下人就穿到怪背后、丢目标了。
     * 贴上去的动作由客户端 {@code AttackApproach} 负责，没有目标时才走这里的固定位移。
     *
     * <p>伤害不在这里做 —— 伤害是角色天赋的事（{@link ActionDefinition#getOnActiveStart()}）。
     */
    private void scheduleStepMovement(Player player, PGCharacter character, ActionDefinition def) {
        if (player.level().isClientSide() || def.step == null) return;
        if (CombatTargeting.isLocked(player)) return;
        ServerActionExecutor.execute(player, def.step, character.getTextureId());
    }

    public void tick(Player player, PGCharacter character) {
        // ⭐ 只有当前活跃角色的 tick 才驱动状态机；
        //    party 里其他成员的 tick 直接忽略，避免互相打断。
        if (activeCharacter != null && activeCharacter != character) {
            return;
        }

        if (current == null || current.isFinished()) {
            if (buffered != null) {
                ActionDefinition next = buffered;
                buffered = null;
                start(player, character, next);
            }
            return;
        }

        current.tick();

        if (current.isFinished()) {
            ActionDefinition def = current.getDefinition();
            if (def.isCombo()) {
                lastComboIndex = def.comboIndex;
                lastComboEndTick = player.level().getGameTime();
            }
            if (buffered != null) {
                ActionDefinition next = buffered;
                buffered = null;
                start(player, character, next);
            }
        }
    }

    public void interrupt(InterruptReason reason) {
        if (current == null || current.isFinished()) return;

        // 持续型重击松手（大剑）：执行期 = 整段持续状态，所以它必须能穿过执行期
        // —— 不强制的话「松手」这个动作会被下面 isProtected 那一条直接丢掉，重击停不下来。
        //
        // ⚠️ 只认「正在跑的确实是持续重击」那一段：这条原因是从客户端发过来的，
        //    两端状态错位时（例如服务端本来就拒绝了这次重击）不能让它去误伤别的动作。
        if (reason == InterruptReason.CHARGE_RELEASE) {
            if (!isSustainedChargedAttack(current)) return;
            current.interrupt(reason);
            buffered = null;
            return;
        }

        // 其余强制打断：这些都是「玩家/规则已经决定这一招到此为止」。
        //   JUMP 跳跃（能打断准备阶段 / 后摇，执行期挡得住，和客户端那条规则一致）
        //   SWITCH_CHARACTER 换人 / DEATH 死亡（执行期也照打）
        boolean forced = reason == InterruptReason.SWITCH_CHARACTER
                || reason == InterruptReason.DEATH
                || reason == InterruptReason.JUMP;
        if (forced) {
            current.interrupt(reason);
            buffered = null;
            resetCombo();
            if (reason == InterruptReason.SWITCH_CHARACTER || reason == InterruptReason.DEATH) {
                activeCharacter = null;
            }
            return;
        }
        if (!current.isProtected()) {
            current.interrupt(reason);
            buffered = null;
        }
    }

    /**
     * 当前动作是不是<b>持续型重击</b>那一段（大剑）。
     *
     * <p>判据是「重击 + 循环动画」—— 循环标记只有持续型招式会打开
     * （见 {@code ActionStep.loopAnimation}），所以它就是「这是一段可以持续下去的招式」。
     */
    private static boolean isSustainedChargedAttack(@Nullable ActionState state) {
        if (state == null) return false;
        ActionDefinition def = state.getDefinition();
        return def != null
                && def.kind == ActionKind.CHARGED_ATTACK
                && def.step != null
                && def.step.loopAnimation;
    }

    public boolean isBusy() { return current != null && !current.isFinished(); }

    public boolean isMovementBlocked() {
        return current != null && !current.isFinished() && current.isProtected();
    }

    public boolean isAttackBlocked() {
        return current != null && !current.isFinished() && current.isProtected();
    }

    public ActionState getCurrent() { return current; }

    private int resolveNextComboIndex(Player player, ActionSet set) {
        return resolveNextComboIndex(player, set, -1);
    }

    private int resolveNextComboIndex(Player player, ActionSet set, int requestedStage) {
        // 客户端给了段数就直接用它（越界会被 mod 回环），两端步伐保持一致
        if (requestedStage >= 1) {
            return ((requestedStage - 1) % set.getNormalComboSize()) + 1;
        }

        if (current != null && !current.isFinished() && current.getDefinition().isCombo()) {
            return current.getDefinition().comboIndex + 1;
        }
        if (lastComboIndex <= 0) return 1;
        ActionDefinition lastDef = set.getNormalAttack(lastComboIndex);
        if (lastDef == null) return 1;
        long elapsed = player.level().getGameTime() - lastComboEndTick;
        if (elapsed > lastDef.comboWindow()) return 1;
        return lastComboIndex + 1;
    }

    public void resetCombo() {
        lastComboIndex = 0;
        lastComboEndTick = Long.MIN_VALUE;
        buffered = null;
    }

}
