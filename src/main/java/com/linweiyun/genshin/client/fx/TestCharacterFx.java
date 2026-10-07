package com.linweiyun.genshin.client.fx;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.client.render.character.AttachmentHelper;
import com.linweiyun.genshin.client.render.character.WeaponAnchorCache;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.character.claymore.sandrone.TestAnimations;
import com.linweiyun.genshin.core.character.claymore.sandrone.SandroneCharacter;
import com.linweiyun.genshin.core.character.talent.SkillCastHooks;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>test 角色的四个 Photon 特效范例。</b>
 *
 * <p>这是「什么时候召唤粒子」这个问题的一份完整答案，四种典型时机各一个：
 *
 * <table border="1">
 *   <caption>四个范例</caption>
 *   <tr><th>#</th><th>时机</th><th>锚点</th><th>用到的 Photon API</th></tr>
 *   <tr>
 *     <td>1</td><td>常驻，跟随角色<br>（仅原神模式 + test 出战）</td>
 *     <td>角色本身</td><td>内置 {@link EntityEffectExecutor}</td>
 *   </tr>
 *   <tr>
 *     <td>2</td><td>常驻，跟随手上的武器</td>
 *     <td>武器骨骼 {@code long}</td><td>{@link FxAnchor} + {@code WeaponAnchorGeoLayer}</td>
 *   </tr>
 *   <tr>
 *     <td>3</td><td>仅在攻击动画期间，跟随武器尖</td>
 *     <td>武器骨骼 + 沿骨骼轴向的偏移</td><td>{@link FxAnchor} + {@link ActionStateMachine}</td>
 *   </tr>
 *   <tr>
 *     <td>4</td><td>释放战技后，在身前生成并朝前飞</td>
 *     <td>一次性世界坐标</td><td>{@link FixedPointExecutor}</td>
 *   </tr>
 * </table>
 *
 * <h2>特效资源从哪来</h2>
 * 下面四个 id 对应四份 {@code .fx}，需要你在游戏内的 Photon 编辑器里做好后导出到：
 * <pre>
 * 资源 id                                       文件位置
 * minegenshin:character/test/aura_body      →  assets/minegenshin/fx/character/test/aura_body.fx
 * minegenshin:character/test/aura_weapon    →  assets/minegenshin/fx/character/test/aura_weapon.fx
 * minegenshin:character/test/attack_tip     →  assets/minegenshin/fx/character/test/attack_tip.fx
 * minegenshin:character/test/skill_projectile → assets/minegenshin/fx/character/test/skill_projectile.fx
 * </pre>
 * 建议用 <b>File → Export → FX Pack</b> 导出，然后把 pack 里的 {@code assets/} 拷进
 * {@code src/main/resources/}（FX Pack 会连材质/Graph/贴图一起收集）。
 *
 * <p><b>资源还没做出来也能跑</b>：{@code FXHelper.getFX} 返回 {@code null} 时这里全程静默空转，
 * 不会崩、不会刷日志。做完一个就亮一个。
 *
 * <h2>常驻特效记得开 Looping</h2>
 * 范例 1 / 2 / 3 都是「常驻」语义，编辑器里要把 Emitter 的 <b>Looping</b> 打开。
 * 没开也不会出事 —— 播完会被自动重新创建 —— 但会多一次重建，没必要。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class TestCharacterFx {

    // ==================== 特效资源 id ====================

    public static final ResourceLocation FX_BODY = fx("aura_body");
    public static final ResourceLocation FX_WEAPON = fx("aura_weapon");
    public static final ResourceLocation FX_ATTACK_TIP = fx("attack_tip");
    public static final ResourceLocation FX_SKILL_PROJECTILE = fx("skill_projectile");

    // ==================== 可调参数 ====================

    /** 范例 3：武器尖离骨骼原点多远（格）。骨骼轴向的正方向，长柄一般是 +Y。 */
    private static final float WEAPON_TIP_OFFSET = 1.20f;

    /** 范例 4：特效在身前多远处生成。 */
    private static final double SKILL_SPAWN_DISTANCE = 1.0;
    /** 范例 4：生成点的高度偏移（相对脚底）。 */
    private static final double SKILL_SPAWN_HEIGHT = 0.3;
    /** 范例 4：每刻前进多少格。0 = 位移完全交给特效自己（Velocity over Lifetime）。 */
    private static final double SKILL_FORWARD_SPEED = 0.6;
    /** 范例 4：最多活多少刻，到点自动销毁。 */
    private static final int SKILL_LIFETIME_TICKS = 60;

    // ==================== 运行时状态 ====================

    /** 范例 1：每个玩家一个跟角色的常驻实例。 */
    private static final Map<UUID, EntityEffectExecutor> BODY = new HashMap<>();
    /** 范例 2：每个玩家一个跟武器的常驻实例。 */
    private static final Map<UUID, FxAnchor> WEAPON = new HashMap<>();
    /** 范例 3：每个玩家一个跟武器尖的攻击实例。 */
    private static final Map<UUID, FxAnchor> TIP = new HashMap<>();
    /** 范例 4：在飞的一次性实例。 */
    private static final List<FixedPointExecutor> PROJECTILES = new ArrayList<>();

    // ==================== 生命周期 ====================

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 招式本体（公共包）→ 表现层（客户端）的唯一通路，见 SkillCastHooks 的类注释
        SkillCastHooks.register(TestCharacterFx::onSkillCast);
        // ClientTickEvent 是游戏总线事件，显式注册而不是让 @EventBusSubscriber 去猜总线
        // （和 MinegenshinClient 里注册 FirstPersonCharacterRenderer 是同一个理由）
        NeoForge.EVENT_BUS.addListener(TestCharacterFx::onClientTick);
    }

    /** 每客户端 tick 推进四类特效的状态。 */
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            releaseAll();
            return;
        }

        var local = Minecraft.getInstance().player;
        Set<UUID> present = new HashSet<>();
        for (Player player : level.players()) {
            present.add(player.getUUID());
            tickPlayer(player, level);
        }
        if (local != null && present.add(local.getUUID())) {
            tickPlayer(local, level);
        }

        releaseAbsent(present);
        tickProjectiles();
    }

    /** 玩家离开视野 / 断线之后，把还挂在他身上的实例收掉，别让它们永远活着。 */
    private static void releaseAbsent(Set<UUID> present) {
        BODY.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            destroyQuietly(entry.getValue().getRuntime());
            return true;
        });
        WEAPON.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().destroy(false);
            return true;
        });
        TIP.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().destroy(false);
            return true;
        });
    }

    /** 客户端每 tick 对每个可见玩家跑一次。 */
    private static void tickPlayer(Player player, ClientLevel level) {
        boolean active = isTestCharacterActive(player);

        tickBody(player, level, active);
        tickWeapon(player, active);
        tickAttackTip(player, active && inAttackAnimation());
    }

    /**
     * <b>范例 1 的判定</b>：只有「在游戏里 + 原神模式 + 当前出战角色是 test」才亮。
     *
     * <p>三个条件都要，缺一个都不播：
     * <ul>
     *   <li>非原神模式（比如原版生存）下角色系统整个不参与，不该有任何角色特效；</li>
     *   <li>切到别的角色时本函数自己变 false，下面就会把实例销毁 —— 不需要额外的切人事件；</li>
     *   <li>{@code getActiveCharacterId} 取的是同步过来的出战角色，两端一致。</li>
     * </ul>
     */
    public static boolean isTestCharacterActive(Player player) {
        return AttachmentHelper.isGenshinMode(player)
                && SandroneCharacter.ID.equals(CharacterHelper.getActiveCharacterId(player));
    }

    /** <b>范例 3 的判定</b>：当前动作状态是不是普攻。 */
    public static boolean inAttackAnimation() {
        String state = ActionStateMachine.currentState;
        if (state == null) {
            return false;
        }
        // 优先用动画名单精确匹配；再加一层宽松兜底，防止将来状态名改了以后静默失效
        return TestAnimations.SPECIAL_ANIMS.contains(state) || state.contains("attack");
    }

    // ==================== 范例 1：常驻跟随角色 ====================

    private static void tickBody(Player player, ClientLevel level, boolean active) {
        UUID id = player.getUUID();
        EntityEffectExecutor executor = BODY.get(id);

        if (!active) {
            if (executor != null) {
                destroyQuietly(executor.getRuntime());
                BODY.remove(id);
            }
            return;
        }

        // 用 isValid() 判断存活：切世界、/photon_client clear_particles 之后它会变 false，
        // 这时候必须重建，否则特效永远不再出现（详见 FXRuntime#isValid 的注释）。
        FXRuntime runtime = executor == null ? null : executor.getRuntime();
        if (runtime != null && runtime.isValid() && !runtime.isFinished()) {
            return;
        }

        FX fx = FXHelper.getFX(FX_BODY);
        if (fx == null) {
            return;   // 资源还没导出
        }

        var created = new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.NONE);
        created.setOffset(0, 0, 0);          // 相对眼睛位置的偏移，按需调
        created.setScale(1, 1, 1);
        created.setForcedDeath(true);        // 锚点（角色）没了就立刻收干净
        created.setAllowMulti(true);         // 去重由我们自己管，避免 start() 被静默跳过
        created.start();
        BODY.put(id, created);
    }

    // ==================== 范例 2：常驻跟随武器 ====================

    private static void tickWeapon(Player player, boolean active) {
        FxAnchor anchor = WEAPON.get(player.getUUID());
        if (anchor == null) {
            if (!active) {
                return;
            }
            anchor = new FxAnchor(FX_WEAPON, player, TestCharacterFx::weaponPose);
            WEAPON.put(player.getUUID(), anchor);
        }
        anchor.setWanted(active);
        anchor.tick();
    }

    // ==================== 范例 3：攻击时跟随武器尖 ====================

    private static void tickAttackTip(Player player, boolean active) {
        FxAnchor anchor = TIP.get(player.getUUID());
        if (anchor == null) {
            if (!active) {
                return;
            }
            anchor = new FxAnchor(FX_ATTACK_TIP, player, TestCharacterFx::weaponTipPose);
            TIP.put(player.getUUID(), anchor);
        }
        anchor.setWanted(active);
        anchor.tick();
    }

    // ==================== 范例 4：技能后定点生成 + 朝前飞 ====================

    /**
     * 由 {@code TestSkillLogic} 在客户端分支调用（经 {@link SkillCastHooks}）。
     *
     * @param skillType {@code < 1000} 点按，{@code 1000} 长按 —— 想给两种形态不同特效就在这里分叉
     */
    public static void onSkillCast(Player player, int skillType) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || player != mc.player) {
            // 只负责本地玩家：别的玩家的特效由他们自己的客户端放
            return;
        }

        FX fx = FXHelper.getFX(FX_SKILL_PROJECTILE);
        if (fx == null) {
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 origin = player.position().add(
                look.x * SKILL_SPAWN_DISTANCE,
                SKILL_SPAWN_HEIGHT,
                look.z * SKILL_SPAWN_DISTANCE);

        // 朝向用玩家的偏航角；位移由 FixedPointExecutor 每刻推进
        var executor = new FixedPointExecutor(level, origin, player.getYRot(), SKILL_FORWARD_SPEED)
                .lifetime(SKILL_LIFETIME_TICKS);
        executor.start(fx);
        PROJECTILES.add(executor);
    }

    /** 每 tick 把播完的一次性实例摘掉。 */
    private static void tickProjectiles() {
        for (Iterator<FixedPointExecutor> it = PROJECTILES.iterator(); it.hasNext(); ) {
            if (it.next().isDone()) {
                it.remove();
            }
        }
    }

    // ==================== 位姿提供者 ====================

    /** 范例 2：武器骨骼原点。骨骼还没渲染过（第一人称 / 离屏）时返回 null = 保持上一帧位姿。 */
    @Nullable
    private static AnchorPose weaponPose(Player player, float partialTicks) {
        WeaponAnchorCache.Entry entry = WeaponAnchorCache.fresh(player);
        return entry == null ? null : entry.toPose();
    }

    /**
     * 范例 3：武器<b>尖</b>。
     *
     * <p>骨骼原点在握把附近，所以沿骨骼自己的 +Y 轴推出去 {@link #WEAPON_TIP_OFFSET} 格。
     * 偏移用骨骼的世界旋转来换算，所以挥砍时特效会跟着刀尖走，而不是绕着一个固定的世界方向跑。
     *
     * <p>⚠️ 不同武器的「尖」方向不一样（有的是 −Y，有的是 +Z），
     * 也可能需要更长的偏移 —— 改 {@link #WEAPON_TIP_OFFSET} 和下面的轴向即可。
     */
    @Nullable
    private static AnchorPose weaponTipPose(Player player, float partialTicks) {
        WeaponAnchorCache.Entry entry = WeaponAnchorCache.fresh(player);
        if (entry == null) {
            return null;
        }
        Quaternionf rotation = new Quaternionf(entry.rotation());
        Vector3f tip = rotation.transform(new Vector3f(0f, WEAPON_TIP_OFFSET, 0f));
        Vector3f at = new Vector3f(entry.position()).add(tip);
        return new AnchorPose(at, rotation, new Vector3f(1, 1, 1));
    }

    // ==================== 收尾 ====================

    private static void releaseAll() {
        for (EntityEffectExecutor executor : BODY.values()) {
            destroyQuietly(executor.getRuntime());
        }
        BODY.clear();
        for (FxAnchor anchor : WEAPON.values()) {
            anchor.destroy(false);
        }
        WEAPON.clear();
        for (FxAnchor anchor : TIP.values()) {
            anchor.destroy(false);
        }
        TIP.clear();
        PROJECTILES.clear();
    }

    private static void destroyQuietly(@Nullable FXRuntime runtime) {
        if (runtime != null) {
            runtime.destroy(false);
        }
    }

    private static ResourceLocation fx(String path) {
        return Minegenshin.id("character/test/" + path);
    }

    private TestCharacterFx() {
    }
}
