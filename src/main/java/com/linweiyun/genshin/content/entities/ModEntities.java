package com.linweiyun.genshin.content.entities;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.entities.area.StellarVortexEntity;
import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
import com.linweiyun.genshin.content.entities.area.ThunderCloudEntity;
import com.linweiyun.genshin.content.entities.misc.ElementalOrb;
import com.linweiyun.genshin.content.entities.misc.IceBlockProjectile;
import com.linweiyun.genshin.content.entities.teyvat.monster.slime.LargeCryoSlime;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectile;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaSpiritSwordEntity;
import com.linweiyun.genshin.content.entities.teyvat.skill.miyabi.MiyabiSlashEffect;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Minegenshin.MOD_ID);
    // 修复：使用 ResourceKey 替代 String
    // MobCategory 用 MISC：它是「领域」不是生物。用 CREATURE 会让 NeoForge 在加载期
    // 报 `Entity minegenshin:talisman_spirit has no attributes`（那句只是噪音，
    // 但领域本来就不该占生物的类别/刷怪名额）。
    public static final Supplier<EntityType<TalismanSpiritArea>> FIELD_TALISMAN_SPIRIT =
            ENTITIES.register(
                    "talisman_spirit",
                    () -> EntityType.Builder.of(TalismanSpiritArea::new, MobCategory.MISC)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(Minegenshin.id("talisman_spirit").toString())
            );
    public static final Supplier<EntityType<ElementalOrb>> ELEMENTAL_ORB =
            ENTITIES.register(
                    "elemental_orb",
                    () -> EntityType.Builder.<ElementalOrb>of(ElementalOrb::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .eyeHeight(0.125F)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build(Minegenshin.id("elemental_orb").toString())
            );
    /**
     * 大型冰史莱姆。
     *
     * <p>注册 id 就是 {@code large_cryo_slime}，资源目录也跟着它：
     * {@code assets/minegenshin/entity/large_cryo_slime/} 下的
     * {@code large_cryo_slime.geo.json} / {@code large_cryo_slime.animation.json} /
     * {@code large_cryo_slime.png}，由 {@code CategoryGeoModel}（类别 + id）解析。
     */
    public static final Supplier<EntityType<LargeCryoSlime>> LARGE_CRYO_SLIME =
            ENTITIES.register(
                    "large_cryo_slime",
                    () ->
                            EntityType.Builder.of(LargeCryoSlime::new, MobCategory.MONSTER)
                                    .sized(1.3964844F, 1.6F)
                                    .eyeHeight(1.52F)
                                    .passengerAttachments(1.31875F)
                                    .clientTrackingRange(10)
                                    .build(Minegenshin.id("large_cryo_slime").toString()));

    public static final Supplier<EntityType<ThunderCloudEntity>> THUNDER_CLOUD =
            ENTITIES.register(
                    "thunder_cloud",
                    () -> EntityType.Builder.<ThunderCloudEntity>of(ThunderCloudEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .build(Minegenshin.id("thunder_cloud").toString()));

    public static final Supplier<EntityType<StellarVortexEntity>> STELLAR_VORTEX =
            ENTITIES.register(
                    "stellar_vortex",
                    () -> EntityType.Builder.<StellarVortexEntity>of(StellarVortexEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .build(Minegenshin.id("stellar_vortex").toString()));

    public static final Supplier<EntityType<VesnaAttackProjectile>> VESNA_ATTACK_PROJECTILE =
            ENTITIES.register(
                    "vesna_attack_projectile",
                    () -> EntityType.Builder.<VesnaAttackProjectile>of(VesnaAttackProjectile::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build(Minegenshin.id("vesna_attack_projectile").toString()));

    public static final Supplier<EntityType<VesnaSpiritSwordEntity>> VESNA_SPIRIT_SWORD =
            ENTITIES.register(
                    "vesna_spirit_sword",
                    () -> EntityType.Builder.<VesnaSpiritSwordEntity>of(VesnaSpiritSwordEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build(Minegenshin.id("vesna_spirit_sword").toString()));

    /**
     * 星见雅重击的斩击表现。
     *
     * <p>只画模型、不结算伤害，所以按「领域」登记在 {@link MobCategory#MISC} 下；
     * 追踪 64 格、每刻同步一次：它一秒飞 20 格，跟得慢了就会一跳一跳。
     */
    public static final Supplier<EntityType<MiyabiSlashEffect>> MIYABI_SLASH =
            ENTITIES.register(
                    "miyabi_slash",
                    () -> EntityType.Builder.<MiyabiSlashEffect>of(MiyabiSlashEffect::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(Minegenshin.id("miyabi_slash").toString()));

    /**
     * 技能投射物：一块悬空旋转、砸向玩家的原版冰块。
     *
     * <p>用 {@link MobCategory#MISC}：它不是生物，不占刷怪名额，也不需要属性
     * （属性缺失的报错只针对 CREATURE / MONSTER 之类的生物类别）。
     * {@code updateInterval} 给小值让位置同步跟得上高速飞行。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<IceBlockProjectile>> ICE_BLOCK =
            ENTITIES.register(
                    "ice_block",
                    () -> EntityType.Builder.<IceBlockProjectile>of(IceBlockProjectile::new, MobCategory.MISC)
                            .sized(0.9F, 0.9F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(Minegenshin.id("ice_block").toString())
            );

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
        eventBus.addListener(ModEntities::registerEntityAttributes);
        eventBus.addListener(ModEntities::registerSpawnPlacements);
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(LARGE_CRYO_SLIME.get(), LargeCryoSlime.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                LARGE_CRYO_SLIME.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> {
                    if (level instanceof ServerLevel sl) {
                        return TeyvatWorldInvasion.get(sl).isInvaded();
                    }
                    return false;
                },
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
