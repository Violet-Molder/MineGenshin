package com.linweiyun.genshin.core.attachment;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatEntityStats;
import com.linweiyun.genshin.core.system.about.block.ChunkBlockElements;
import com.linweiyun.genshin.core.system.shield.ShieldState;
import com.linweiyun.genshin.core.system.poise.PoiseState;
import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class AttachmentRegistration {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Minegenshin.MOD_ID);
    public static final Supplier<AttachmentType<Integer>> PRIMOGEM_ATTACHMENT =
            ATTACHMENTS.register("player_primogem",
                    () -> AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT.fieldOf("primogem"))
                            .sync(StreamCodec.of(
                                    FriendlyByteBuf::writeInt,
                                    FriendlyByteBuf::readInt
                            ))
                            .copyOnDeath()
                            .build()
            );
    public static final Supplier<AttachmentType<Boolean>> GENSHIN_MODE_ATTACHMENT =
            ATTACHMENTS.register("player_genshin_mode",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("genshin_mode"))
                            .sync(StreamCodec.of(
                                    FriendlyByteBuf::writeBoolean,
                                    FriendlyByteBuf::readBoolean
                            ))
                            .copyOnDeath()
                            .build()
            );

    /**
     * 「这个玩家从原神模式里退出来过」。
     *
     * <p>非原神模式下要把当前角色的属性折算到玩家身上，但折算只能在
     * 「原神模式 → 非原神模式」这一个切换点上建立一次 —— 否则刚进世界的玩家会被直接改成
     * 「原版上限 + 角色上限 × 0.7」的血量。切回原神模式时这条记录清掉。
     *
     * <p>纯服务端记账，不需要 sync。
     */
    public static final Supplier<AttachmentType<Boolean>> COMPAT_LINK_ATTACHMENT =
            ATTACHMENTS.register("player_compat_link",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("compat_link"))
                            .copyOnDeath()
                            .build()
            );


    public static final Supplier<AttachmentType<PlayerCharactersAttachment>>
            PLAYER_CHARACTERS_ATTACHMENT =
            ATTACHMENTS.register(
                    "player_characters",
                    () -> AttachmentType.serializable(PlayerCharactersAttachment::new)
                            .sync(PlayerCharactersAttachment.STREAM_CODEC)
                            .copyOnDeath().build());

    public static final Supplier<AttachmentType<StatusContainer>> CONTAINER =
            ATTACHMENTS.register("status_container",
                    () -> AttachmentType.serializable(StatusContainer::new)
                            .sync(StatusContainer.STREAM_CODEC)
                            .copyOnDeath().build());

    public static final Supplier<AttachmentType<AdventurerInfoAttachment>> ADVENTURER_INFO_ATTACHMENT =
            ATTACHMENTS.register("adventurer_info",
                    () -> AttachmentType.serializable(AdventurerInfoAttachment::new)
                            .sync(AdventurerInfoAttachment.STREAM_CODEC)
                            .copyOnDeath().build());

    public static final Supplier<AttachmentType<Backpack>> BACKPACK_ATTACHMENT =
            ATTACHMENTS.register(
                    "backpack",
                    () -> AttachmentType.serializable(Backpack::new)
                            .sync(Backpack.STREAM_CODEC)
                            .copyOnDeath().build()
            );

    public static final Supplier<AttachmentType<TeyvatEntityStats>> ENTITY_STATS = ATTACHMENTS.register(
            "entity_stats",
            () -> AttachmentType.builder(() -> TeyvatEntityStats.DEFAULT)
                    .serialize(TeyvatEntityStats.CODEC.fieldOf("entity_stats"))
                    .sync(TeyvatEntityStats.STREAM_CODEC)
                    .copyOnDeath()
                    .build()
    );

    /**
     * 实体身上的护盾状态（还剩多少盾、什么分类、什么时候到期）。
     *
     * <p>用附件而不是 Attribute：盾除了「量」还有剩余时长、分类、朝向、最近受击刻，
     * Attribute 只能表示一个数。默认真造新实例（不是共享单例），
     * 免得一个实体改盾把别人也改了。
     *
     * <p>{@code sync} 是必须的 —— 客户端血条下面那条护盾条读的就是它。
     */
    public static final Supplier<AttachmentType<ShieldState>> SHIELD = ATTACHMENTS.register(
            "shield",
            () -> AttachmentType.builder(ShieldState::new)
                    .serialize(ShieldState.CODEC.fieldOf("shield"))
                    .sync(ShieldState.STREAM_CODEC)
                    .build()
    );

    /**
     * 实体身上的韧性状态（攒了多少削韧、破没破、驻留还剩多久）。
     *
     * <p>和 {@link #SHIELD} 同一套写法、同一个道理：韧性的「量 / 破韧标记 / 驻留计时」
     * 这几项不是属性（{@code ModAttributes.POISE} 那个属性表达的是<b>档位</b>），
     * 所以放附件。默认真造新实例，免得一个实体改韧性把别人也改了。
     *
     * <p>{@code sync} 是必须的 —— 客户端血条下面那条削韧条读的就是它。
     */
    public static final Supplier<AttachmentType<PoiseState>> POISE = ATTACHMENTS.register(
            "poise",
            () -> AttachmentType.builder(PoiseState::new)
                    .serialize(PoiseState.CODEC.fieldOf("poise"))
                    .sync(PoiseState.STREAM_CODEC)
                    .build()
    );

    /**
     * 玩家当前动作动画状态（参考2 的 {@code anime_state}）。
     *
     * <p>{@code serialize} + {@code sync} 两个调用就够了：服务端 setData 后调一次
     * {@code syncData}，NeoForge 会把它推给所有能收到这个玩家的客户端（含本人）。
     */
    public static final Supplier<AttachmentType<AnimationState>> ANIMATION_STATE_ATTACHMENT =
            ATTACHMENTS.register(
                    "animation_state",
                    () -> AttachmentType.builder(AnimationState::new)
                            .serialize(AnimationState.CODEC.fieldOf("animation_state"))
                            .sync(AnimationState.STREAM_CODEC)
                            .build()
            );

    /**
     * 玩家当前的<b>身体朝向</b>（{@code yBodyRot}，度）。
     *
     * <p>原版客户端只同步视线角，本模组「视角独立 / 视角跟随」改的是身体角，
     * 所以这份值必须自己同步，否则别人看到的是「身体被视线拖着走」的另一个版本
     * （详见 {@code BodyYawSync} 类注释）。
     *
     * <p>{@code sync} 是必须的；{@code NaN} 表示「还没收到过」，渲染时退回原版插值。
     */
    public static final Supplier<AttachmentType<Float>> BODY_YAW_ATTACHMENT =
            ATTACHMENTS.register(
                    "body_yaw",
                    () -> AttachmentType.builder(() -> Float.NaN)
                            .serialize(Codec.FLOAT.fieldOf("body_yaw"))
                            .sync(StreamCodec.of(
                                    FriendlyByteBuf::writeFloat,
                                    FriendlyByteBuf::readFloat
                            ))
                            .build()
            );

    public static final Supplier<AttachmentType<LockedTargetData>> LOCKED_TARGET =
            ATTACHMENTS.register("locked_target",
                    () -> AttachmentType.builder(() -> LockedTargetData.EMPTY)
                            .serialize(LockedTargetData.CODEC.fieldOf("locked_target"))
                            .build()
            );

    public static final Supplier<AttachmentType<ChunkBlockElements>> CHUNK_ELEMENTS =
            ATTACHMENTS.register("chunk_elements",
                    () -> AttachmentType.builder(ChunkBlockElements::new)
                            .serialize(ChunkBlockElements.CODEC.fieldOf("elements"))
                            // 方块元素只服务端用，不需要同步给客户端（每 tick 同步整 chunk 表会掉帧）
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
