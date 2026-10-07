package com.linweiyun.genshin.content.entities.area;

import net.minecraft.nbt.CompoundTag;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 领域实体基类 —— 所有领域（Field/Barrrier）的抽象基类
 *
 * 设计参考原版 AreaEffectCloud，但独立实现：
 * - 移除了药水（PotionContents）相关的所有代码
 * - 移除了粒子特效（粒子由子类自行管理）
 * - 移除了同一目标重复触发的CD（victims map / reapplicationDelay）
 * - 移除了增加持续时间（durationOnUse）和扩大范围（radiusOnUse）的代码
 * - 移除了等待时间（waitTime）
 *
 * 数据持久化：寿命与拥有者写在实体自己的存档 NBT 里（见 readAdditionalSaveData / addAdditionalSaveData）
 * 数据同步：半径（radius）通过 SynchedEntityData 实时同步给客户端
 */
public abstract class AreaEntity extends Entity {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    /**
     * 寿命诊断日志开关（排查「领域不会自然消亡」用，平时留 false）。
     *
     * <p>打开后每 100 刻打一条「剩余 N 刻」；不管开关如何，
     * <b>到期移除</b>那条日志始终会打（一次领域一行，很便宜，也是排查的关键证据）。
     */
    public static boolean LIFETIME_DEBUG_LOG = false;

    // ========== SynchedEntityData 同步字段 ==========

    // 半径数据同步器 —— 用于实时同步半径到客户端（影响碰撞箱和显示）
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(AreaEntity.class, EntityDataSerializers.FLOAT);

    // ========== LDLib2 持久化字段 ==========

    // 触发领域的玩家UUID —— 记录是哪个玩家释放了这个领域
    @Persisted(key = "owner")
    protected Player owner;

    // 触发领域的角色UUID —— 记录是玩家的哪个角色（PGCharacterData）触发了领域
    @Persisted(key = "character")
    protected PGCharacter character;

    // 领域剩余持续时间（tick）—— -1 表示无限持续
    @Persisted(key = "duration")
    protected int duration = -1;

    /**
     * 到期时刻（绝对游戏时间 {@code level().getGameTime()}）；{@code -1} = 还没定。
     *
     * <p><b>为什么用「绝对时刻」而不是 {@code tickCount}</b>（这两个都是一次真事故）：
     * <ol>
     *   <li>{@code tickCount} <b>不落盘</b>（{@code Entity} 只存档
     *       {@code addAdditionalSaveData} 写进去的东西），而 {@code @Persisted} 对<b>实体</b>是无效的
     *       （LDLib2 只 mixin 了 BlockEntity）；于是区块每卸载重载一次，
     *       实体都被构造器「满血复活」成 duration=600 / tickCount=0 ——
     *       表现就是「领域一直存在，不会自然消亡」；</li>
     *   <li>实体离开 ENTITY_TICKING 距离后服务端根本不 tick 它，相对倒计时会<b>冻结</b>；
     *       换成绝对时刻后，它只要再被 tick 一次就会发现自己早就过期了。</li>
     * </ol>
     */
    protected long expireGameTime = -1L;

    /** 领域拥有者玩家 UUID —— 实体重载后 {@code owner} 引用会丢，靠它找回。 */
    protected UUID ownerUUID;

    /** 触发领域的角色 UUID（{@code PGCharacter.getCharacterUUID()}）。 */
    protected int characterUUID;

    // 领域形状类型 —— 球体或圆柱体
    @Persisted(key = "shape_type")
    protected AreaShapeType shapeType = AreaShapeType.SPHERE;

    // 水平半径 —— 领域的水平方向半径
    @Persisted(key = "horizontal_radius")
    protected float horizontalRadius = 3.0f;

    // 垂直半径 —— 领域的垂直方向半径（仅圆柱体使用）
    @Persisted(key = "vertical_radius")
    protected float verticalRadius = 3.0f;

    // 领域唯一ID —— 用于标识特定领域类型（如612001为镇灵之鼎）
    @Persisted(key = "field_uuid")
    protected int fieldUUID = 0;

    // ========== 构造函数 ==========

    /**
     * 领域实体构造函数
     * @param type 实体类型
     * @param level 所在关卡
     */
    public AreaEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;  // 禁用物理碰撞（领域是虚拟区域）
    }

    // ========== 同步数据定义 ==========

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.0f);  // 默认半径3格
    }

    // ========== Tick 逻辑 ==========

    @Override
    public void tick() {
        super.tick();
        // 服务端执行持续时间倒计时
        if (!this.level().isClientSide()) {
            this.serverTick();
        } else {
            this.clientTick();
        }
    }

    /**
     * 服务端Tick —— 处理持续时间、领域效果等
     */
    protected void serverTick() {
        if (this.duration == -1) {
            return;                     // 无限持续的领域：没有到期时刻
        }

        long now = this.level().getGameTime();
        if (this.expireGameTime < 0L) {
            // 第一次 tick 才定死到期时刻（构造期没有可靠的游戏时间）
            this.expireGameTime = now + this.duration;
        }

        if (now >= this.expireGameTime) {
            LOGGER.info("[领域] {} 到期移除（原定 {} 刻）",
                    this.getType().getDescription().getString(), this.duration);
            this.discard();  // 到期后移除实体
            return;
        }

        if (LIFETIME_DEBUG_LOG && now % 100L == 0L) {
            // 心跳：有这条日志 = 这个领域确实在被 tick；没有 = 它根本没在走寿命
            LOGGER.info("[领域] {} 剩余 {} 刻", this.getType().getDescription().getString(),
                    this.expireGameTime - now);
        }
    }

    /**
     * 重新起算寿命（「被刷新就续命」的领域用，例如雷暴云）。
     *
     * <p>不要再自己去写 {@code tickCount = 0} —— 寿命现在记在 {@link #expireGameTime} 上。
     */
    public void refreshLifetime() {
        this.expireGameTime = this.duration == -1 ? -1L : this.level().getGameTime() + this.duration;
    }

    /**
     * 客户端Tick —— 处理粒子等视觉效果（由子类覆盖）
     */
    protected void clientTick() {
        // 基类默认无客户端逻辑，子类可覆盖
    }

    // ========== 领域属性访问 ==========

    /** 获取领域形状类型 */
    public AreaShapeType getShapeType() {
        return shapeType;
    }

    /** 设置领域形状类型 */
    public void setShapeType(AreaShapeType shapeType) {
        this.shapeType = shapeType;
    }

    /** 获取水平半径 */
    public float getHorizontalRadius() {
        return horizontalRadius;
    }

    /** 设置水平半径（同时更新SynchedEntityData） */
    public void setHorizontalRadius(float radius) {
        this.horizontalRadius = radius;
        if (!this.level().isClientSide()) {
            this.getEntityData().set(DATA_RADIUS, Mth.clamp(radius, 0.5f, 32.0f));
        }
    }

    /** 获取垂直半径 */
    public float getVerticalRadius() {
        return verticalRadius;
    }

    /** 设置垂直半径 */
    public void setVerticalRadius(float radius) {
        this.verticalRadius = radius;
    }

    /** 获取领域UUID */
    public int getFieldUUID() {
        return fieldUUID;
    }

    /** 设置领域UUID */
    public void setFieldUUID(int uuid) {
        this.fieldUUID = uuid;
    }

    /** 获取领域持续时间（tick），-1表示无限 */
    public int getDuration() {
        return duration;
    }

    /** 设置领域持续时间（tick），-1表示无限 */
    public void setDuration(int duration) {
        this.duration = duration;
        this.expireGameTime = -1L;      // 下一 tick 按当时的游戏时间重新定死
    }

    /** 获取当前同步的半径值（用于碰撞箱计算） */
    public float getRadius() {
        return this.getEntityData().get(DATA_RADIUS);
    }

    // ========== 拥有者相关 ==========

    /**
     * 获取触发领域的玩家（拥有者）
     * @return 玩家实体，可能为null（玩家离线或不存在）
     */
    @Nullable
    public Player getOwner() {
        // 实体重载后引用会丢（@Persisted 对实体无效）→ 按 UUID 从玩家列表里找回来
        if (this.owner == null && this.ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            this.owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerUUID);
        }
        return this.owner;
    }

    /**
     * 获取触发领域的角色数据（PGCharacterData）
     * 从拥有者玩家的附件系统中查找对应角色
     * @return 角色数据，可能为null
     */
    @Nullable
    public PGCharacter getOwnerCharacter() {
        if (this.character == null && this.characterUUID != 0) {
            Player owner = getOwner();
            if (owner != null) {
                PlayerCharactersAttachment attachment =
                        owner.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                if (attachment != null) {
                    this.character = attachment.getCharacterByUUID(this.characterUUID);
                }
            }
        }
        return this.character;
    }

    /**
     * 设置领域拥有者
     * @param owner 触发领域的玩家
     * @param character 触发领域的角色数据（PGCharacterData）
     */
    public void setOwner(Player owner, PGCharacter character) {
        this.owner = owner;
        this.ownerUUID = owner == null ? null : owner.getUUID();
        this.character = character;
        this.characterUUID = character == null ? 0 : character.getCharacterUUID();
    }


    /** 获取触发角色的数据 */
    public PGCharacter getCharacter() {
        return character;
    }

    // ========== 碰撞与维度 ==========

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // 球体：直径为水平半径的2倍，高度也是直径
        // 圆柱体：直径为水平半径的2倍，高度为垂直半径的2倍
        if (shapeType == AreaShapeType.CYLINDER) {
            return EntityDimensions.scalable(horizontalRadius * 2.0f, verticalRadius * 2.0f);
        } else {
            float diameter = horizontalRadius * 2.0f;
            return EntityDimensions.scalable(diameter, diameter);
        }
    }

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);  // 刷新维度后保持位置
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;  // 领域不被活塞推动
    }

    @Override
    public final boolean hurt(net.minecraft.world.damagesource.DamageSource source, float damage) {
        return false;  // 领域实体不可被伤害
    }

    // ========== 数据同步回调 ==========

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_RADIUS.equals(accessor)) {
            this.refreshDimensions();  // 半径变化时刷新碰撞箱
        }
        super.onSyncedDataUpdated(accessor);
    }

    // ========== 序列化 ==========
    //
    // ⚠️ 这里的 @Persisted 只是标注，对**实体**不生效（LDLib2 只 mixin 了 BlockEntity），
    //    寿命与拥有者必须自己写进存档 NBT ——
    //    否则区块一重载，领域就被构造器「满血复活」，看起来永远不会消亡。

    @Override
    public void readAdditionalSaveData(CompoundTag input) {
        this.duration = input.contains("mg_duration") ? input.getInt("mg_duration") : this.duration;
        this.expireGameTime = input.contains("mg_expire") ? input.getLong("mg_expire") : -1L;

        this.characterUUID = input.getInt("mg_character");
        this.owner = null;
        this.character = null;      // 让 getOwner()/getOwnerCharacter() 按 UUID 重新解析

        String ownerId = input.getString("mg_owner");
        if (!ownerId.isEmpty()) {
            try {
                this.ownerUUID = UUID.fromString(ownerId);
            } catch (IllegalArgumentException ignored) {
                this.ownerUUID = null;
            }
        }
        // 注意：不在这里 discard() —— 读档期移除实体容易踩到加载流程；
        // 到期时刻留在过去，第一个 serverTick 就会把它清掉。
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        output.putInt("mg_duration", this.duration);
        output.putLong("mg_expire", this.expireGameTime);
        output.putInt("mg_character", this.characterUUID);
        if (this.ownerUUID != null) {
            output.putString("mg_owner", this.ownerUUID.toString());
        }
    }
}