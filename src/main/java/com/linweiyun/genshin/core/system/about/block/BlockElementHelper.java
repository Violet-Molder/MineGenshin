package com.linweiyun.genshin.core.system.about.block;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.AttachmentProfile;
import com.linweiyun.genshin.core.system.about.AttachmentSource;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.about.host.BlockHost;
import com.linweiyun.genshin.core.system.about.host.CharacterHost;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionManager;
import com.linweiyun.genshin.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.core.status.StatusAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * 方块元素行为 —— 现在是「宿主适配 + 反应结果驱动的状态迁移」这一层，<b>不再是第二套附着实现</b>。
 *
 * <p>方块的附着、筛查、反应全部走与生物相同的入口：
 * <pre>
 *   applyElement()
 *     → BlockHost（实现 ElementalHost：容器在 Chunk 数据里、能否附着查 BlockElementRules）
 *        → ElementalAttachmentHelper.attach(host, ...)    ← 与生物同一个入口
 *        → 入口内部接着触发反应（ElementalReactionManager.tryReactFor）← 与实体端同一个执行体
 *     → migrateBlockState()                               ← 只有这一步是方块独有的
 * </pre>
 *
 * <p>也就是说，<b>新增一种「与元素有关的方块」不再需要动本类的规则判断</b>：注册一条
 * {@link BlockElementRules} 规则 + 在 {@link #migrateBlockState} 里补上它自己的状态迁移
 * （如果它的表现需要变方块的话）。水与冰族是内置的两条示范。
 *
 * <p>规则：
 * <pre>
 *   水 + 冰        → 附着 → 冻结反应 → 浮冰（FROZEN 存在容器里，随时间衰减）
 *   浮冰 + 火      → 附着 → 融化反应 → 水
 *   生物在水中/雨中 → 周期给「出战角色」挂弱水（宿主是 CharacterHost，跟着角色走）
 *   玩家进水 / 进火 → 当刻就给「出战角色」挂弱水 / 弱火（不等 1 秒一轮的批量扫描）
 *   大型冰史莱姆踏水/踏冰 → 给脚下方块附着冰/冻
 * </pre>
 */
public final class BlockElementHelper {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    private static final int WATER_CHECK_INTERVAL = 20;
    private static int waterEntityCheckCounter = 0;

    private BlockElementHelper() {
    }

    // ==================== 方块附着入口（唯一） ====================

    /**
     * 对方块附着元素 —— 唯一入口。
     *
     * <p>本方法不做「能不能附着」的前置判断：那是宿主的活（{@link BlockElementRules}）。
     * 它只负责把附着与反应跑完，然后让方块状态跟上容器里的事实。
     *
     * @param gauge       附着量（U），作为这条附着的初始量（方块侧无损耗）
     * @param decayPerSec 衰减率（U/s）
     */
    public static void applyElement(ServerLevel level, BlockPos pos,
                                    GenshinElement element,
                                    float gauge, float decayPerSec) {
        BlockHost host = BlockHost.of(level, pos);
        if (host == null || !host.isValid() || gauge <= 0f) {
            return;
        }

        // ⚡ 零分配预筛：这个方块对这个元素压根不感兴趣 → 连容器都不建、不落盘。
        //    以前是先 host.container()（computeIfAbsent 建一个空容器写进 chunk）再让宿主筛查拒掉，
        //    扫一片石头就会给每一格都造一份空状态 —— 那是真正的开销来源。
        if (!BlockElementRules.accepts(host.state(), element)) {
            return;
        }

        StatusContainer container = host.container();
        if (container == null) {
            return;
        }

        AttachmentProfile profile = new AttachmentProfile(gauge, 1.0f, decayPerSec, 999f);

        // ① 附着 —— 入口内部会接着尝试反应（与实体端同一套；飘字出口按宿主选方块位置）
        boolean attached = ElementalAttachmentHelper.attach(
                host, element, AttachmentSource.ENVIRONMENTAL, profile).attached();
        if (!attached) {
            // 没挂上就什么都不落：不提交、也不跑迁移（否则「冰族没冰就化水」的规则会把误触当融化）
            return;
        }

        // ② 落盘 + 让方块状态跟上
        host.commit(container);
        // 只有这次调用真的动了元素（挂上去了）才谈得上迁移：否则水攻击冰块也会被
        // 「冰族 + 容器里没有冰/冻 → 化水」这条规则误判成融化。
        if (attached) {
            migrateBlockState(host, container);
        }
    }

    /**
     * 方块表现迁移 —— 交给 {@link BlockElementMigrations} 注册表，核心不再认识任何具体方块。
     *
     * <p>「元素附着是因、方块状态是果」：迁移只读容器里的事实、只改方块状态，
     * 所以任何来源（玩家左键、冰史莱姆踩、相邻火焰）走的都是同一条路；
     * 新增一种可附着方块也只需要注册「规则 + 迁移」两条，不必回来改本类。
     */
    private static void migrateBlockState(BlockHost host, StatusContainer container) {
        BlockElementMigrations.runAll(host, container);
    }

    // ==================== 水环境给实体挂水 ====================

    /**
     * 水/雨环境附着 —— 给「出战角色」挂弱水。
     *
     * <p>宿主是 {@link CharacterHost}：附着跟着角色走，切人就是换一个附着，
     * 切回来只要没掉就还在（与 {@code CharacterChillHandler} 是同一套语义）。
     */
    public static void checkAndApplyWaterToEntity(LivingEntity entity) {
        boolean raining = entity.level() instanceof ServerLevel level && level.isRaining();
        checkAndApplyWaterToEntity(entity, raining);
    }

    /**
     * 带上「当前维度是否在下雨」的重载（{@link #onServerTick} 的批量扫描用）。
     *
     * <p>雨没下的时候「淋雨挂水」这条根本不可能成立，而 {@code isRainingAt} 要走
     * 生物群系降水查询、{@code canSeeSky} 要走高度图 —— 都是每实体上百纳秒的活。
     * 所以把布尔判断提出来，非雨天每个实体只剩一次 {@code isInWater()}。</p>
     */
    private static void checkAndApplyWaterToEntity(LivingEntity entity, boolean raining) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.isSpectator()) return;

        // 玩家：水（含淋雨）与火都是环境元素源，两种都挂在「出战角色」身上
        if (entity instanceof Player player) {
            if (entity.isInWater() || (raining && isRainedOn(level, entity))) {
                attachToCurrentCharacter(player, ModElements.HYDRO.get(), AttachmentProfile.WEAK);
            }
            if (isInFire(player)) {
                attachToCurrentCharacter(player, ModElements.PYRO.get(), AttachmentProfile.WEAK);
            }
            return;
        }

        boolean inWater = entity.isInWater();
        if (!inWater) {
            if (!raining) return;
            if (!isRainedOn(level, entity)) return;
        }

        MobCategory cat = entity.getType().getCategory();
        if (cat == MobCategory.WATER_CREATURE || cat == MobCategory.WATER_AMBIENT) return;

        StatusContainer container = entity.getData(AttachmentRegistration.CONTAINER);
        if (container == null) return;
        ElementalAttachmentHelper.attach(entity, container, ModElements.HYDRO.get(),
                AttachmentSource.ENVIRONMENTAL, AttachmentProfile.WEAK);
    }

    /** 这个位置是不是正被雨淋着（生物群系在下雨 + 头顶见天）。 */
    private static boolean isRainedOn(ServerLevel level, LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        return level.isRainingAt(pos) && level.canSeeSky(pos);
    }

    /**
     * 玩家专用的环境附着检查 —— <b>每 tick</b> 跑一次，负责「刚进水 / 刚踩进火」那一刻就挂上元素。
     *
     * <p>1 秒一轮的批量扫描对「一直站在水里」是够用的（水会被它持续补满），但「刚进水」最多要等
     * 1 秒才挂上，玩起来就是附着慢半拍。这里只补「身上还没有这个元素」的情况：
     * 站着不动不会每 tick 往容器里灌，也不会把附着刷成无限量，节奏仍由上面那条 1 秒扫描维持。
     */
    public static void checkPlayerEnvironment(Player player) {
        if (player.isSpectator()) return;

        boolean inWater = player.isInWater();
        boolean inFire = isInFire(player);
        if (!inWater && !inFire) return;

        StatusContainer container = currentCharacterContainer(player);
        if (container == null) return;

        if (inWater && !hasElement(container, ModElements.HYDRO.get())) {
            attachToCurrentCharacter(player, ModElements.HYDRO.get(), AttachmentProfile.WEAK);
        }
        if (inFire && !hasElement(container, ModElements.PYRO.get())) {
            attachToCurrentCharacter(player, ModElements.PYRO.get(), AttachmentProfile.WEAK);
        }
    }

    /** 站在火里：岩浆里、身上烧着，或者脚下方块就是火 / 灵魂火。 */
    private static boolean isInFire(Player player) {
        if (player.isInLava() || player.isOnFire()) {
            return true;
        }
        BlockState feet = player.level().getBlockState(player.blockPosition());
        return feet.is(Blocks.FIRE) || feet.is(Blocks.SOUL_FIRE);
    }

    /** 出战角色的元素容器（没选角色 / 没有角色数据时返回 {@code null}）。 */
    private static StatusContainer currentCharacterContainer(Player player) {
        PlayerCharactersAttachment chars =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = chars == null ? null : chars.getCurrentCharacter();
        return character == null ? null : StatusAccessor.of(character.getData());
    }

    /**
     * 把元素挂到出战角色身上 —— 玩家身上的环境附着（水 / 火）都走这一条。
     *
     * <p>宿主是 {@link CharacterHost}：附着跟着角色走，切人就是换一个附着，切回来只要没掉就还在。
     */
    private static void attachToCurrentCharacter(Player player, GenshinElement element,
                                                 AttachmentProfile profile) {
        PlayerCharactersAttachment chars =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = chars == null ? null : chars.getCurrentCharacter();
        if (character == null) return;
        ElementalAttachmentHelper.attach(CharacterHost.of(character), element,
                AttachmentSource.ENVIRONMENTAL, profile);
    }

    public static void onServerTick(ServerLevel level) {
        // 玩家每 tick 单独过一遍：进水 / 进火要「立刻」附着，不能等下一轮 1 秒的批量扫描
        for (ServerPlayer player : level.players()) {
            checkPlayerEnvironment(player);
        }

        if (++waterEntityCheckCounter < WATER_CHECK_INTERVAL) return;
        waterEntityCheckCounter = 0;

        // 这一轮是不是雨天：降水量这类查询提到循环外，别每个实体都重问一遍
        boolean raining = level.isRaining();
        for (Entity e : level.getEntities().getAll()) {
            if (e instanceof LivingEntity living) {
                checkAndApplyWaterToEntity(living, raining);
            }
        }
    }

    // ==================== 冻结位置登记（集中式推进用） ====================

    /**
     * 当前冻结着、需要推进衰减的方块位置（每个世界一份）。
     *
     * <p>不再依赖原版方块 tick 链：链条一旦断掉（重复排期/邻居变化/区块边界都可能），
     * 那格就会永远冻着 —— 用户报的「边缘逐个化、中间一直冻着」就是这个。
     * 改为在这里登记，由 {@code BlockElementTicker} 每 tick 统一推进。
     */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            java.util.Set<Long>> FROZEN_SITES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 登记一个需要推进的冻结方块。 */
    public static void trackFrozen(ServerLevel level, BlockPos pos) {
        FROZEN_SITES.computeIfAbsent(level.dimension(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(pos.asLong());
    }

    /** 取消登记（融化/清空时）。 */
    public static void untrackFrozen(ServerLevel level, BlockPos pos) {
        java.util.Set<Long> set = FROZEN_SITES.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }



    /** 每 tick 最多推进多少格：大范围冻结时不让单 tick 一次性跑完整张表。 */
    private static final int TRACK_LIMIT_PER_TICK = 256;

    /**
     * 每一 tick 统一推进登记在案的冻结方块。
     *
     * <p>两个卫生要点（都是「表只增不减」会带来的隐性开销）：
     * <ul>
     *   <li><b>已经不冻的位置顺手摘掉</b>：方块被破坏、被别的机制换成别的方块之后，
     *       这条记录如果留着，就会永远占着每 tick 的检查额度，越积越多；</li>
     *   <li><b>没加载的位置不占额度</b>：区块没加载就推不动，跳过即可
     *       （区块重新加载时 {@code BlockElementTicker.onChunkLoad} 会把该补的补回来），
     *       否则一张满是未加载位置的表会把额度吃光，已加载的反而推不到。</li>
     * </ul>
     */
    public static void trackedTick(ServerLevel level) {
        java.util.Set<Long> set = FROZEN_SITES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        int processed = 0;
        java.util.Iterator<Long> it = set.iterator();
        while (it.hasNext() && processed < TRACK_LIMIT_PER_TICK) {
            BlockPos pos = BlockPos.of(it.next());
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!level.getBlockState(pos).is(Blocks.FROSTED_ICE)) {
                it.remove();
                continue;
            }
            tickBlockElementDecay(level, pos);
            processed++;
        }
    }

    // ==================== 方块元素推进（由 FrostedIceMixin.tick / BlockElementTicker 调用） ====================

    /**
     * 推进这个方块的元素容器：跑衰减、跑容器自己的动态状态（冻元素衰减率），
     */
    public static void tickBlockElementDecay(ServerLevel level, BlockPos pos) {
        // 先用只读路径看一眼：没有元素数据的方块（例如原版冰霜行者踩出来、元素体系从没插手过的浮冰）
        // 不归我们管，顺手从推进表里摘掉 —— 它的存亡交还原版逻辑（与 FrostedIceMixin 同一条判据）。
        StatusContainer container = BlockElementStore.peek(level, pos);
        if (container == null) {
            untrackFrozen(level, pos);
            return;
        }
        // 同一 game tick 内只推进一次：身上挂了几条排期都无所谓（重复排期不再让某格多走一步）
        if (!BlockElementStore.beginDecayStep(level, pos)) {
            return;
        }
        // 自愈：只要还冻着就保证它在推进表里（覆盖历史遗留 / 重启后丢表的情况）
        if (level.getBlockState(pos).is(Blocks.FROSTED_ICE)) {
            trackFrozen(level, pos);
        }

        container.tick();

        BlockElementMigrations.runAll(BlockHost.of(level, pos), container);

        if (container.isEmpty()) {
            BlockElementStore.clear(level, pos);
            untrackFrozen(level, pos);
        }
        // 否则不 commit：容器是原地改的对象，存档时自然带上；每 tick 提交会同步整 chunk 元素表。
    }

    // ==================== 内部：容器查询 ====================

    private static boolean hasElement(StatusContainer container, GenshinElement target) {
        return sumElementQuantity(container, target) > 0f;
    }


    private static float sumElementQuantity(StatusContainer container, GenshinElement target) {
        float sum = 0f;
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (inst instanceof ElementalAttachmentInstance ea
                    && ea.getElement() == target) {
                sum += ea.getUnit();
            }
        }
        return sum;
    }



}
