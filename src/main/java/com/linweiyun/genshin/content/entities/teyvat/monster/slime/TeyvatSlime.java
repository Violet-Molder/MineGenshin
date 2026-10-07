package com.linweiyun.genshin.content.entities.teyvat.monster.slime;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import com.linweiyun.genshin.content.entities.teyvat.monster.TeyvatMonster;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TeyvatSlime extends TeyvatMonster implements GeoEntity {
    // 预定义跳跃动画：从 "move.jump" 开始并循环播放
    protected final RawAnimation JUMPING_START = RawAnimation.begin().thenPlay("move.jump");
    // GeckoLib 动画实例缓存（每个实体一个）
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final EntityDataAccessor<String> DATA_ELEMENT =
            SynchedEntityData.defineId(TeyvatSlime.class, EntityDataSerializers.STRING);
    protected TeyvatSlime(EntityType<? extends TeyvatMonster> type, Level level) {
        super(type, level);
    }

    @Override
    public HumanoidArm getMainArm() {
        return null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ELEMENT, "minegenshin:anemo");
    }

    //AI逻辑注册

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    // 创建实体属性（注册时调用）
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 35.0D)   // 跟随范围 35格
                .add(Attributes.MOVEMENT_SPEED, 0.4f)   // 移动速度 0.4
                .add(Attributes.ATTACK_DAMAGE, 3.0D);   // 攻击力 3.0
    }
    // GeckoLib 接口：返回动画缓存实例
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
    //注册Geckolib动画控制器
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("jump", 0, this::jumpingState));
    }

    private PlayState jumpingState(AnimationTest<GeoAnimatable> geoAnimatableAnimationTest) {
        if (!this.onGround()) {       // 不在地面（跳跃/下落中）
            return geoAnimatableAnimationTest.setAndContinue(JUMPING_START);  // 播放跳跃动画
        }
        return PlayState.STOP;
    }
    public GenshinElement getElement() {
        String id = this.entityData.get(DATA_ELEMENT);
        if (id != null && !id.isEmpty()) {
            String[] parts = id.split(":", 2);
            if (parts.length == 2) {
                net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.fromNamespaceAndPath(parts[0], parts[1]);
                GenshinElement resolved = com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY.get(identifier).map(r -> r.value()).orElse(null);
                if (resolved != null) return resolved;
            }
        }
        return ModElements.FYSIKOS.get();
    }
    // 设置元素类型
    public void setElement(GenshinElement element) {
        net.minecraft.resources.Identifier key = com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY.getKey(element);
        this.entityData.set(DATA_ELEMENT, key != null ? key.toString() : "minegenshin:fysikos");
    }

    /**
     * 史莱姆对自己那种元素免疫。
     *
     * <p><b>只免疫伤害，不拒收附着</b>：打上去照样挂元素、照样能反应，只是这一下不掉血。
     *
     * <p>附着走默认实现（全收）：{@code onAttachElement} 与 {@code hurtServer} 都不拦附着。
     */
    @Override
    public boolean isImmuneToElementDamage(GenshinElement element) {
        return element != null && element == this.getElement();
    }

    static class SlimeMoveControl extends MoveControl {
        private float yRot;              //旋转角度
        private int jumpDelay;           //跳跃间隔
        private final TeyvatSlime slime;       //控制实体
        private boolean isAggressive;    //是否处于攻击模式

        public SlimeMoveControl(TeyvatSlime slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.getYRot() / (float) Math.PI;
        }
        // 设置移动方向和攻击状态
        public void setDirection(float yRot, boolean aggressive) {
            this.yRot = yRot;
            this.isAggressive = aggressive;
        }
        // 设置期望移动速度（倍率）
        public void setWantedMovement(double speed) {
            this.speedModifier = speed;
            this.operation = Operation.MOVE_TO;  // 标记为"需要移动"
        }
        public void tick() {
            // 平滑旋转朝向目标角度
            //rotlerp()方法说明：
            //参数1：当前角度
            //参数2：目标角度
            //参数3：每一帧的最大旋转角度
            this.slime.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.slime.yHeadRot = this.slime.getYRot();     //头部跟随
            this.slime.yBodyRot = this.slime.getYRot();     //身体跟随
            if (this.operation != Operation.MOVE_TO) {      //若无移动指令，停止前进
                this.slime.setZza(0.0f);;                   //Z轴（前后）移动向量
            } else {
                this.operation = Operation.WAIT;
                if (this.slime.onGround()) {                //判断是否在地面
                    this.slime.setSpeed((float)
                            // 移动速度 = 速度修饰符 x 基础速度
                            (this.speedModifier * this.slime.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                    if (this.isAggressive && this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.slime.getJumpDelay();
                        this.slime.getJumpControl().jump();
                    }
                }
            }
        }
    }

    // 获取跳跃间隔（10~29刻随机）
    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void playerTouch(Player player) {
        this.dealDamage(player);
    }

    // 对目标实体造成近战伤害
    protected void dealDamage(LivingEntity target) {
        // 前提：自身存活 + 目标在近战范围内 + 有视线
        if (this.isAlive() && this.isWithinMeleeAttackRange(target) && this.hasLineOfSight(target)) {
            float damage = getAttackDamage();
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.MONSTER, this.getElement())
                    .multiplier(1.0f)
                    .elementAmount(0.0f)
                    .build();
            ModDamageSource damageSource = ModDamageSource.from(spec, this);
            if (this.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, damageSource, damage);
            }
        }
    }
    // 获取攻击力数值
    protected float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }
}
