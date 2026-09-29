package com.linweiyun.genshin.core.status;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * StatusInstance 子类的类型注册表工具类。
 * <p>
 * 注册入口在 {@link com.linweiyun.genshin.core.system.registry.register.ModStatusInstanceTypes}，
 * 使用 NeoForge 的 {@code DeferredRegister<StatusInstanceType<?>>} 注册到
 * {@link ModRegistries#STATUS_INSTANCE_TYPE_REGISTRY} 真正的 Minecraft 注册表中。
 * <p>
 * 序列化时 StatusContainer 遍历 instances 列表，对每个元素写入其 typeId
 * （由各子类 {@code @Persisted} 字段自动携带），反序列化时按 typeId 从注册表
 * 查找构造器，创建正确子类实例后再调用 PersistedParser 填充字段。
 * <p>
 * 添加新状态类型只需三步：
 * <ol>
 *   <li>继承 {@link StatusInstance}，在字段上加 {@code @Persisted} 注解</li>
 *   <li>在无参构造里设置 {@code this.typeId = "your_type_id"}</li>
 *   <li>在 {@code ModStatusInstanceTypes} 中调用
 *       {@code STATUS_INSTANCE_TYPES.register("your_type_id", () -> new StatusInstanceType<>("your_type_id", YourClass::new))}</li>
 * </ol>
 */
public final class StatusInstanceTypes {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    private StatusInstanceTypes() {}

    /**
     * 根据 typeId 创建一个新的 StatusInstance 子类实例。
     * <p>
     * typeId 会被转换为 {@code minegenshin:typeId} 的 {@link Identifier}，
     * 然后从 {@link ModRegistries#STATUS_INSTANCE_TYPE_REGISTRY} 查找对应条目。
     * 如果未注册，输出警告日志并返回一个无害的占位实例（会立即过期）。
     *
     * @param typeId 类型标识字符串（如 "elemental_attachment"）
     * @return 新创建的实例
     */
    public static StatusInstance create(String typeId) {
        Identifier id = typeId.contains(":") ? Identifier.parse(typeId) : Minegenshin.id(typeId);
        StatusInstanceType<?> type = ModRegistries.STATUS_INSTANCE_TYPE_REGISTRY.getOptional(id).orElse(null);
        if (type != null && type.constructor() != null) {
            return type.constructor().get();
        }
        LOGGER.warn("[StatusInstanceTypes] 未找到 typeId='{}' (id={}) 的注册条目，返回占位实例", typeId, id);
        return new StatusInstance() {
            @Override
            public void tick() {}

            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public StatusInstance copy() {
                return this;
            }
        };
    }
}