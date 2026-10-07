package com.linweiyun.genshin.content.effect.character;

import com.linweiyun.genshin.core.system.registry.ModRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class CharacterEffectInstance {

    // 无限持续时间常量
    public static final int INFINITE = -1;

    /** 已经因为「查不到注册名」而打过日志的 id —— 避免每刻刷屏。 */
    private static final java.util.Set<ResourceLocation> WARNED_UNKNOWN_IDS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ========== 核心字段 ==========

    // 效果实例（直接持有，类型安全）
    private final ICharacterEffect effect;
    // 效果实例自己的 id（参数化效果用来区分不同参数的那一份；单例效果等于注册名）
    private final ResourceLocation effectId;
    /**
     * <b>注册名</b>（= 效果<b>类型</b>，用于序列化/反序列化）。
     *
     * <p>⚠️ 和 {@link #effectId} 不是一回事：
     * {@code effectId} 是「这个实例自己的名字」（参数化效果用它区分「短按那套 / 长按那套」），
     * 而 {@code effectTypeId} 才是注册表里的键。读档时只能用后者查表 ——
     * 混用就是 2026-09-21 那次空指针崩溃的根因。
     *
     * <p>不用 {@code final}：构造期注册表可能还没就绪（类匹配会失败），
     * 所以 {@link #getEffectTypeId()} 允许在存盘那一刻再补解析一次。
     */
    private ResourceLocation effectTypeId;
    // 效果剩余持续时间（单位：tick），-1表示无限持续
    private int duration;
    // 效果等级/强度
    private int amplifier;
    // 是否在HUD上隐藏该效果
    private boolean hidden;
    // 效果特有额外数据（如冰凌数量、血契数值等）
    private CompoundTag data;

    // ========== 构造函数 ==========

    /**
     * 完整构造函数 —— 直接传 ICharacterEffect 实例
     *
     * @param effect 效果实例（如 IcyQuillEffect.INSTANCE）
     * @param duration 持续时间（tick）
     * @param amplifier 效果等级
     * @param hidden 是否隐藏
     * @param data 额外数据标签
     */
    public CharacterEffectInstance(ICharacterEffect effect, int duration, int amplifier, boolean hidden, CompoundTag data) {
        this.effect = effect;
        // 注册表里那一份：实例名 = 注册名。参数化实例（没进注册表）退回按类推出的类型名，
        // 至少保证「身份」不会变成 empty 占位。
        ResourceLocation key = registeredKeyOf(effect);
        ResourceLocation fallback = key != null ? key : resolveTypeId(null, effect);
        this.effectId = fallback;
        this.effectTypeId = fallback;
        this.duration = duration;
        this.amplifier = amplifier;
        this.hidden = hidden;
        this.data = data != null ? data : new CompoundTag();
    }

    /**
     * 显式指定 effectId 的构造函数 —— 用于参数化效果实例（如 DamageBonusEffect）
     * 当传递的不是注册表单例，而是参数化的新实例时使用。
     *
     * <p>{@code effectId} 是<b>这个实例自己的名字</b>（参数化效果靠它区分不同参数的那几份，
     * 例如申鹤短按/长按那两套 15% 加成）。注册名（类型）会自动推导：
     * <ol>
     *   <li>传进来的 id 本身就在注册表里 → 就是它；</li>
     *   <li>否则按<b>效果的类</b>去注册表里找同类原型（例如 {@code DamageBonusEffect}
     *       → {@code minegenshin:damage_bonus}）。</li>
     * </ol>
     * 这样参数化效果不用改任何调用点，读档也能查到正确的类型。
     *
     * @param effectId 效果实例自己的 ResourceLocation
     */
    public CharacterEffectInstance(ResourceLocation effectId, ICharacterEffect effect, int duration, int amplifier, boolean hidden, CompoundTag data) {
        this.effect = effect;
        this.effectId = effectId;
        this.effectTypeId = resolveTypeId(effectId, effect);
        this.duration = duration;
        this.amplifier = amplifier;
        this.hidden = hidden;
        this.data = data != null ? data : new CompoundTag();
    }

    /**
     * 推导「效果类型」的注册名：先用 id 直接查注册表，查不到再按类匹配注册表里的原型。
     *
     * @return 注册名；实在找不到就返回传入的 id（读档时会被当成未知 id → 占位效果 + 日志）
     */
    private static ResourceLocation resolveTypeId(ResourceLocation instanceId, ICharacterEffect effect) {
        if (instanceId != null && ModRegistries.CHARACTER_EFFECT_REGISTRY.containsKey(instanceId)) {
            return instanceId;
        }
        if (effect != null) {
            for (var entry : ModRegistries.CHARACTER_EFFECT_REGISTRY.entrySet()) {
                if (entry.getValue().getClass() == effect.getClass()) {
                    return entry.getKey().location();
                }
            }
        }
        return instanceId;
    }

    /**
     * 取这个效果实例在注册表里的键 —— <b>取不到就返回 null</b>。
     *
     * <p>不能直接信 {@code getKey}：这张表是 {@code DefaultedMappedRegistry}，
     * 对「没注册过的实例」它不会返回 null，而是返回<b>默认键</b>（{@code minegenshin:empty}），
     * 于是参数化实例（如申鹤那两套 DamageBonusEffect）会被误当成占位效果。
     * 所以这里再确认一次「键存在、而且取出来的就是它自己」。
     */
    @Nullable
    private static ResourceLocation registeredKeyOf(@Nullable ICharacterEffect effect) {
        if (effect == null) return null;
        ResourceLocation key = ModRegistries.CHARACTER_EFFECT_REGISTRY.getKey(effect);
        if (key == null || !ModRegistries.CHARACTER_EFFECT_REGISTRY.containsKey(key)) {
            return null;
        }
        return ModRegistries.CHARACTER_EFFECT_REGISTRY.get(key) == effect ? key : null;
    }

    /** 不带额外数据的构造函数 */
    public CharacterEffectInstance(ICharacterEffect effect, int duration, int amplifier, boolean hidden) {
        this(effect, duration, amplifier, hidden, new CompoundTag());
    }

    /** 不带隐藏和额外数据的构造函数 */
    public CharacterEffectInstance(ICharacterEffect effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, new CompoundTag());
    }

    /**
     * 显式指定 effectId，不带额外数据
     */
    public CharacterEffectInstance(ResourceLocation effectId, ICharacterEffect effect, int duration, int amplifier) {
        this(effectId, effect, duration, amplifier, false, new CompoundTag());
    }

    // ========== Getter 方法 ==========

    public ICharacterEffect getEffect() {
        return effect;
    }

    /** 获取效果的注册名 ResourceLocation */
    public ResourceLocation getEffectId() {
        return effectId;
    }

    /**
     * 获取效果的<b>注册名（类型）</b> —— 序列化/反序列化只用这个。
     *
     * <p>如果构造时没解析出来（比如在注册表就绪之前就创建了实例），这里再补一次。
     */
    public ResourceLocation getEffectTypeId() {
        if (effectTypeId == null
                || !ModRegistries.CHARACTER_EFFECT_REGISTRY.containsKey(effectTypeId)) {
            ResourceLocation resolved = resolveTypeId(effectId, effect);
            if (resolved != null) {
                effectTypeId = resolved;
            }
        }
        return effectTypeId;
    }

    /**
     * 获取效果ID的字符串形式 —— 这是「效果身份」，同类效果去重/覆盖/移除都靠它比。
     *
     * <p>理论上不该为 null（构造时一定会推一个出来），但这里是好几处
     * {@code equals} 比较的入参，null 一下就是 NPE，所以兜一个空串。
     */
    public String getEffectIdString() {
        return effectId == null ? "" : effectId.toString();
    }

    /** 获取效果剩余持续时间 */
    public int getDuration() {
        return duration;
    }

    /** 设置效果剩余持续时间 */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /** 获取效果等级 */
    public int getAmplifier() {
        return amplifier;
    }

    /** 设置效果等级 */
    public void setAmplifier(int amplifier) {
        this.amplifier = amplifier;
    }

    /** 获取是否隐藏 */
    public boolean isHidden() {
        return hidden;
    }

    /** 设置是否隐藏 */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /** 获取额外数据标签 */
    public CompoundTag getData() {
        return data;
    }

    /** 设置额外数据标签 */
    public void setData(CompoundTag data) {
        this.data = data != null ? data : new CompoundTag();
    }

    // ========== 额外数据快捷访问方法 ==========

    /** 从额外数据中读取整数值 */
    public int getIntData(String key) {
        return data.getInt(key);
    }

    /** 从额外数据中读取字符串值 */
    public String getStringData(String key) {
        return data.getString(key);
    }

    /** 从额外数据中读取布尔值 */
    public boolean getBooleanData(String key) {
        return data.getBoolean(key);
    }

    /** 从额外数据中读取浮点数值 */
    public float getFloatData(String key) {
        return data.getFloat(key);
    }

    /** 从额外数据中读取双精度浮点数值 */
    public double getDoubleData(String key) {
        return data.getDouble(key);
    }

    /** 从额外数据中读取嵌套CompoundTag */
    public CompoundTag getCompoundData(String key) {
        return data.getCompound(key);
    }

    /** 向额外数据中写入整数值 */
    public void setIntData(String key, int value) {
        data.putInt(key, value);
    }

    /** 向额外数据中写入字符串值 */
    public void setStringData(String key, String value) {
        data.putString(key, value);
    }

    /** 向额外数据中写入布尔值 */
    public void setBooleanData(String key, boolean value) {
        data.putBoolean(key, value);
    }

    /** 向额外数据中写入浮点数值 */
    public void setFloatData(String key, float value) {
        data.putFloat(key, value);
    }

    /** 向额外数据中写入双精度浮点数值 */
    public void setDoubleData(String key, double value) {
        data.putDouble(key, value);
    }

    /** 向额外数据中写入嵌套CompoundTag */
    public void setCompoundData(String key, CompoundTag value) {
        data.put(key, value);
    }

    // ========== 数据副本 ==========

    /**
     * 创建一个带有新额外数据的效果实例副本
     */
    public CharacterEffectInstance withData(CompoundTag newData) {
        return new CharacterEffectInstance(effect, duration, amplifier, hidden, newData);
    }

    /**
     * 深拷贝当前效果实例
     */
    public CharacterEffectInstance copy() {
        return new CharacterEffectInstance(effect, duration, amplifier, hidden, data.copy());
    }

    // ========== 序列化/反序列化 ==========

    /**
     * 将效果实例序列化为CompoundTag
     *
     * <p>写两个 id：
     * <ul>
     *   <li>{@code effect_type} = <b>注册名</b>（读档时查表用）；</li>
     *   <li>{@code effect_id} = 实例自己的名字（参数化效果用来区分不同的那一份，读档时保留）。</li>
     * </ul>
     * 另外让<b>参数化效果自己</b>把构造参数写进 {@code data}（否则读档回来会变成一个没参数的实例）。
     */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ResourceLocation typeId = getEffectTypeId();
        tag.putString("effect_type", typeId == null ? "unknown" : typeId.toString());
        tag.putString("effect_id", effectId == null ? "unknown" : effectId.toString());
        tag.putInt("duration", duration);
        tag.putInt("amplifier", amplifier);
        tag.putBoolean("hidden", hidden);

        CompoundTag payload = data.copy();
        if (effect != null) {
            effect.writeInstanceData(payload);       // 参数化效果把参数写出来
        }
        tag.put("data", payload);
        return tag;
    }

    /**
     * 从CompoundTag反序列化效果实例。
     *
     * <h2>两张 id 的分工（这里是崩溃修复的核心）</h2>
     * 查注册表只能用 {@code effect_type}（注册名）；{@code effect_id} 是实例自己的名字，
     * 参数化效果（如申鹤短按/长按那两套 15% 加成）的实例名<b>本来就不在注册表里</b>，
     * 拿它查表必然查不到 —— 而注册表被配成「查不到就返回默认值」，
     * 默认键又没注册，于是直接 NPE 崩服。
     *
     * <p>兼容老存档：只有 {@code effect_id} 时按它查一次；查不到就用占位效果并<b>打日志点名</b>。
     */
    public static CharacterEffectInstance fromTag(CompoundTag tag) {
        ResourceLocation typeId = parseId(tag.getString("effect_type"));
        ResourceLocation instanceId = parseId(tag.getString("effect_id"));

        int duration = tag.getInt("duration");
        int amplifier = tag.getInt("amplifier");
        boolean hidden = tag.getBoolean("hidden");
        CompoundTag data = tag.getCompound("data");

        // 1) 优先用注册名查表
        ICharacterEffect effect = lookupEffect(typeId);
        // 2) 老存档没有 effect_type：退回用 effect_id 查（单例效果两者相同）
        if (effect == null && instanceId != null) {
            effect = lookupEffect(instanceId);
        }
        // 3) 查到的是「类型原型」：参数化效果用 data 里的参数重建自己
        if (effect != null) {
            effect = effect.createFromInstanceData(data);
        } else {
            ResourceLocation missing = typeId != null ? typeId : instanceId;
            if (missing != null && WARNED_UNKNOWN_IDS.add(missing)) {
                ICharacterEffect.LOGGER.warn(
                        "[角色效果] 存档里的效果 id={} 不在注册表里 → 用占位效果（这条效果读档后失效）", missing);
            }
            effect = DummyEffect.INSTANCE;
        }

        // 实例名沿用存档里的；没有就用注册名（同样要防 DefaultedMappedRegistry 把未知实例报成 empty）
        ResourceLocation finalInstanceId = instanceId != null ? instanceId : registeredKeyOf(effect);
        return new CharacterEffectInstance(finalInstanceId, effect, duration, amplifier, hidden, data);
    }

    private static ResourceLocation parseId(String raw) {
        if (raw == null || raw.isEmpty() || "unknown".equals(raw)) {
            return null;
        }
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全地查效果：<b>先确认键存在再取值</b>。
     *
     * <p>不能直接 {@code getValue(id)} —— 这个注册表是 {@code DefaultedMappedRegistry}
     * （配了 {@code defaultKey}），但默认键一旦没有对应注册项，它内部的默认 Holder 就是 null，
     * 查询不存在的键会在 {@code getValue} 里空指针崩溃（而不是返回 null）。
     */
    @Nullable
    private static ICharacterEffect lookupEffect(@Nullable ResourceLocation id) {
        if (id == null || !ModRegistries.CHARACTER_EFFECT_REGISTRY.containsKey(id)) {
            return null;
        }
        return ModRegistries.CHARACTER_EFFECT_REGISTRY.get(id);
    }

    // ========== 调试输出 ==========

    @Override
    public String toString() {
        return "EffectInstance{" +
                "effect=" + effectId +
                ", duration=" + duration +
                ", amplifier=" + amplifier +
                ", hidden=" + hidden +
                '}';
    }

    /**
     * 占位效果 —— 当反序列化时找不到注册的效果时使用
     * 防止NPE，标记为unknown状态
     */
    public static class DummyEffect implements ICharacterEffect {
        public static final DummyEffect INSTANCE = new DummyEffect();

        @Override
        public String toString() {
            return "DummyEffect(unknown)";
        }
    }
}