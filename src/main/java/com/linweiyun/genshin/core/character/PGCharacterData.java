package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.attribute.AttributeContainer;
import com.linweiyun.genshin.content.attribute.AttributeInstance;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.inventory.AllWeaponArtifactInventory;
import com.linweiyun.genshin.core.character.attachment.CharacterAttachmentContainer;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearance;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.character.appearance.SockType;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.lowdragmc.lowdraglib2.syncdata.IManaged;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

public class PGCharacterData implements IPersistedSerializable, IManaged {

    private PGCharacter parentCharacter;
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Override public IManagedStorage getSyncStorage() { return syncStorage; }
    @Override public void notifyPersistence() { /* persistence handled by IPersistedSerializable */ }

    void setParentCharacter(PGCharacter parentCharacter) { this.parentCharacter = parentCharacter; }
    @Persisted(key = "character_level")
    private int characterLevel;
    @Getter
    @Persisted(key = "max_exp")
    private int maxExp;
    @Getter
    @Persisted(key = "current_exp")
    private int currentExp;
    @Getter
    @Persisted(key = "current_hp")
    @DescSynced
    private double currentHP;
    @Getter
    @Persisted(key = "ascension_phase")
    private int ascensionPhase;
    @Getter
    @Persisted(key = "constellation")
    @DescSynced
    private int constellation;
    @Persisted(key = "attributes")
    private AttributeContainer attributes = new AttributeContainer();
    @Persisted(key = "normal_attack_level")
    private int normalAttackLevel;
    @Persisted(key = "elemental_skill_level")
    private int elementalSkillLevel;
    @Persisted(key = "elemental_burst_level")
    private int elementalBurstLevel;

    // ==================== 天赋的「手动升级次数」 ====================
    //
    // 有效等级 = 基础 1 级 + 手动次数 + 命座/天赋加成（见 recalculateTalentLevels）。
    // 之所以单独记手动次数：**升级消耗只按手动次数算**。
    // 例：手动升到 4 级（3 次）后抽到 3 命（战技 +3）→ 显示 7 级，
    //     但下一次升级消耗的仍然是「4→5」那一档，因为手动次数只有 3。

    @Persisted(key = "manual_normal_attack")
    private int manualNormalAttack;
    @Persisted(key = "manual_elemental_skill")
    private int manualElementalSkill;
    @Persisted(key = "manual_elemental_burst")
    private int manualElementalBurst;

    /** 手动升级次数字段是否已经迁移过（老存档：没有这三个字段）。瞬态，不入档。 */
    private transient boolean talentUpgradesMigrated;
    @Getter
    @Persisted(key = "current_obtaining_energy")
    @DescSynced
    private float currentObtainingEnergy;
    @Getter
    @Persisted(key = "skill_short_max_cooldown")
    @DescSynced
    private int skillShortMaxCooldownTick;
    @Getter
    @Persisted(key = "skill_long_max_cooldown")
    @DescSynced
    private int skillLongMaxCooldownTick;
    @Getter
    @Persisted(key = "burst_max_cooldown")
    @DescSynced
    private int burstMaxCooldownTick;
    @Getter
    @Persisted(key = "max_obtaining_energy")
    @DescSynced
    private float maxObtainingEnergy;
    @Getter
    @Persisted(key = "elemental_skill_cooldown_tick")
    @DescSynced
    private float elementalSkillCooldownTick;
    @Getter
    @Persisted(key = "elemental_burst_cooldown_tick")
    @DescSynced
    private float elementalBurstCooldownTick;
    @Getter
    @Persisted(key = "effect_data")
    private ListTag effectDataList = new ListTag();
    @Getter
    @Persisted(key = "elemental_skill_max_stacks")
    private int elementalSkillMaxStacks;
    @Getter
    @Persisted(key = "elemental_skill_stacks")
    @DescSynced
    private int elementalSkillStacks;
    @Getter
    @Persisted(key = "skill_list")
    protected List<Integer> skillCoolList;
    @Getter
    @Persisted(key = "status_container")
    private StatusContainer statusContainer = new StatusContainer();
    //AI 所属玩家的序列化 UUID
    @Getter
    @Persisted(key = "owner_uuid")
    private UUID ownerUUID;

    @Getter
    @Persisted(key = "artifact_inventory")
    private ArtifactInventory artifactInventory = new ArtifactInventory();
    @Getter
    @Persisted(key = "weapon_base_atk")
    private double weaponBaseATK = 0;
    @Persisted(key = "character_attachments")
    private CharacterAttachmentContainer characterAttachments = new CharacterAttachmentContainer();

    // ==================== 外观选项 ====================
    //
    // 单开一个 @Persisted + @DescSynced 的字段，而不是挂到 PGCharacter 上：
    // 外观是「这个角色长什么样」，和 HP / 等级一样属于角色数据，退场、换人、
    // 存档往返都必须跟着走。规则与位布局见 CharacterAppearance / LegBoneRules / EarBoneRules。
    //
    // 字段名与档案键 "leg_appearance" 都保持不动 —— 掩码后来扩到了猫耳，但键一改
    // 老存档里这一项就读不回来（会退回默认外观），名字留旧是刻意的。
    @Persisted(key = "leg_appearance")
    @DescSynced
    private int legAppearance = CharacterAppearance.DEFAULT_MASK;


    //AI 运行时 Player 引用，设置时自动同步 UUID
    @Getter
    private Player ownerPlayer;
    // 效果容器的缓存引用，延迟加载，避免每次操作都重新反序列化
    private CharacterEffectContainer effectContainer;

    @Getter
    private boolean dirty = false;

    public PGCharacterData() {
        this.characterLevel = 1;
        this.currentExp = 0;
        this.maxExp = 1000;
        this.ascensionPhase = 0;
        this.constellation = 0;
        this.normalAttackLevel = 1;
        this.elementalSkillLevel = 1;
        this.elementalBurstLevel = 1;
        this.currentObtainingEnergy = 0;
        this.elementalSkillCooldownTick = 0;
        this.elementalBurstCooldownTick = 0;
        this.elementalSkillMaxStacks = 1;
        this.elementalSkillStacks = 1;
        this.attributes = new AttributeContainer();
        this.effectDataList = new ListTag();
        this.skillCoolList = new ArrayList<>();
        this.artifactInventory = new ArtifactInventory();
        initAllAttributes();

    }
    public void initBaseStats(double baseHP, double baseATK, double baseDEF) {
        this.attributes.setBaseValue(ModAttributes.MAX_HP.value(), baseHP);
        this.attributes.setBaseValue(ModAttributes.ATK.value(), baseATK);
        this.attributes.setBaseValue(ModAttributes.DEF.value(), baseDEF);
        this.currentHP = this.attributes.getTotalValue(ModAttributes.MAX_HP.value());
    }
    private void initAllAttributes() {
        for (AttributeType type : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
            attributes.getOrCreate(type);
        }
    }


    // ==== 属性数据获取 ====
    public AttributeInstance getAttribute(AttributeType type) {return attributes.get(type);}
    public double getAttributeBaseValue(AttributeType type) {return attributes.getBaseValue(type);}
    public double getAttributeTotalValue(AttributeType type) {return attributes.getTotalValue(type);}
    public double getAttributeFlatModifier(AttributeType type) {return attributes.getFlatModifier(type);}
    public double getAttributeTempFlatModifier(AttributeType type) {return attributes.getTempFlatModifier(type);}
    public double getAttributePercentModifier(AttributeType type) {return attributes.getPercentModifier(type);}
    public double getAttributePercentModifierDisplay(AttributeType type) {return attributes.getPercentModifierDisplay(type);}
    public double getAttributeTempPercentModifier(AttributeType type) {return attributes.getTempPercentModifier(type);}
    public double getAttributeTempPercentModifierDisplay(AttributeType type) {return attributes.getTempPercentModifierDisplay(type);}

    // ==== 属性数据修改 ====
    public void setAttributeBaseValue(AttributeType type, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setBaseValue(type, value));
        markDirty();
    }
    public void setAttributeBaseValue(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setBaseValue(type, source, value));
        markDirty();
    }
    public void removeAttributeBaseValue(AttributeType type, String source) {
        preserveHpIfMaxHp(type, () -> attributes.removeBaseValue(type, source));
        markDirty();
    }
    public void addAttributeFlatModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.addFlatModifier(type, source, value));
        markDirty();
    }
    public void addAttributePercentModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.addPercentModifier(type, source, value));
        markDirty();
    }
    public void setAttributeFlatModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setFlatModifier(type, source, value));
        markDirty();
    }
    public void setAttributePercentModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setPercentModifier(type, source, value));
        markDirty();
    }
    public void addAttributeTempFlatModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.addTempFlatModifier(type, source, value));
        markDirty();
    }
    public void addAttributeTempPercentModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.addTempPercentModifier(type, source, value));
        markDirty();
    }
    public void setAttributeTempFlatModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setTempFlatModifier(type, source, value));
        markDirty();
    }
    public void setAttributeTempPercentModifier(AttributeType type, String source, double value) {
        preserveHpIfMaxHp(type, () -> attributes.setTempPercentModifier(type, source, value));
        markDirty();
    }
    public void removeAttributeModifier(AttributeType type, String source) {
        preserveHpIfMaxHp(type, () -> attributes.removeModifier(type, source));
        markDirty();
    }

    private boolean isMaxHpAttribute(AttributeType type) {
        if (type == null || type.id() == null) return false;
        return type == ModAttributes.MAX_HP.get()
                || type.id().equals(ModAttributes.MAX_HP.getId());
    }

    private void preserveHpIfMaxHp(AttributeType type, Runnable action) {
        if (!isMaxHpAttribute(type)) {
            action.run();
            return;
        }
        double oldMaxHP = attributes.getTotalValue(ModAttributes.MAX_HP.get());
        double oldCurrentHP = currentHP;
        action.run();
        double newMaxHP = attributes.getTotalValue(ModAttributes.MAX_HP.get());
        if (newMaxHP != oldMaxHP) {
            double hpRatio = oldMaxHP > 0 ? oldCurrentHP / oldMaxHP : 1.0;
            currentHP = newMaxHP * hpRatio;
        }
    }

    public int getLevel() { return characterLevel; }

    public CharacterAttachmentContainer getAttachments() {return characterAttachments;}

    // ==================== 天赋等级 ====================

    /**
     * 手动升级次数上限（三个天赋各自）：手动最多 9 次 → <b>手动能到 10 级</b>。
     */
    public static final int MAX_MANUAL_TALENT_UPGRADES = 9;

    /** 天赋基础等级：0 次手动时是 1 级。 */
    public static final int TALENT_BASE_LEVEL = 1;

    /** 3 命：元素战技等级 +3，<b>上限同时 +3</b>（10 → 13）。全角色通用。 */
    public static final int C3_SKILL_LEVEL_BONUS = 3;

    /** 5 命：元素爆发等级 +3，<b>上限同时 +3</b>（10 → 13）。全角色通用。 */
    public static final int C5_BURST_LEVEL_BONUS = 3;

    /** 天赋等级的理论上限（含最高命座加成），给需要「绝对上限」的地方用。 */
    public static final int MAX_TALENT_LEVEL =
            TALENT_BASE_LEVEL + MAX_MANUAL_TALENT_UPGRADES + C3_SKILL_LEVEL_BONUS;

    /**
     * 把「手动次数 + 命座加成」重算成三个天赋的<b>有效等级</b>。
     *
     * <p>有效等级仍然写在 {@code normalAttackLevel} 那几个字段里（伤害计算、UI 都直接读它们），
     * 但它们是<b>派生值</b> —— 唯一可以手动改的只有上面的「手动次数」。
     */
    private void recalculateTalentLevels() {
        this.normalAttackLevel = TALENT_BASE_LEVEL + manualNormalAttack;
        this.elementalSkillLevel = TALENT_BASE_LEVEL + manualElementalSkill
                + (constellation >= 3 ? C3_SKILL_LEVEL_BONUS : 0);
        this.elementalBurstLevel = TALENT_BASE_LEVEL + manualElementalBurst
                + (constellation >= 5 ? C5_BURST_LEVEL_BONUS : 0);
    }

    /**
     * 老存档迁移：那时候只有「有效等级」、没有「手动次数」。
     *
     * <p>不倒推的话，老角色一旦升级就会先被算成 1 级 + 1 次，等级直接掉回去。
     * 当时没有命座加成，所以手动次数就是 {@code 有效等级 - 1}。
     */
    private void migrateTalentUpgradesIfNeeded() {
        if (talentUpgradesMigrated) return;
        talentUpgradesMigrated = true;

        if (manualNormalAttack != 0 || manualElementalSkill != 0 || manualElementalBurst != 0) {
            return;     // 已经有手动次数了，不用迁移
        }
        if (normalAttackLevel <= TALENT_BASE_LEVEL
                && elementalSkillLevel <= TALENT_BASE_LEVEL
                && elementalBurstLevel <= TALENT_BASE_LEVEL) {
            return;     // 全是 1 级，没什么可迁移的
        }

        manualNormalAttack = clampManual(normalAttackLevel - TALENT_BASE_LEVEL);
        manualElementalSkill = clampManual(elementalSkillLevel - TALENT_BASE_LEVEL);
        manualElementalBurst = clampManual(elementalBurstLevel - TALENT_BASE_LEVEL);
        recalculateTalentLevels();
        markDirty();
    }

    private static int clampManual(int value) {
        return Math.max(0, Math.min(MAX_MANUAL_TALENT_UPGRADES, value));
    }

    /**
     * 直接设置某个天赋的<b>手动升级次数</b>（命令 / GM 用）。
     *
     * @param kind {@code normal} / {@code skill} / {@code burst}
     * @return {@code false} = kind 写错了
     */
    public boolean setManualTalentUpgrades(String kind, int count) {
        migrateTalentUpgradesIfNeeded();
        int clamped = clampManual(count);
        switch (kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT)) {
            case "normal" -> manualNormalAttack = clamped;
            case "skill" -> manualElementalSkill = clamped;
            case "burst" -> manualElementalBurst = clamped;
            default -> {
                return false;
            }
        }
        recalculateTalentLevels();
        markDirty();
        return true;
    }

    /** 手动升级次数（三个天赋）。 */
    public int getManualNormalAttack() {
        migrateTalentUpgradesIfNeeded();
        return manualNormalAttack;
    }

    /**
     * 普通攻击的<b>有效等级</b>（= 基础 + 手动次数 + 命座/天赋加成）。
     *
     * <p>这几个 getter 是手写的（不用 Lombok）：顺手做一次「老存档迁移 + 重算」，
     * 保证从磁盘读出来的旧数据在第一次读取时就被修正 ——
     * 例如一个早就 3 命的老角色，战技等级要立刻补上 +3。
     */
    public int getNormalAttackLevel() {
        migrateTalentUpgradesIfNeeded();
        return normalAttackLevel;
    }

    public int getElementalSkillLevel() {
        migrateTalentUpgradesIfNeeded();
        return elementalSkillLevel;
    }

    public int getElementalBurstLevel() {
        migrateTalentUpgradesIfNeeded();
        return elementalBurstLevel;
    }

    public int getManualElementalSkill() {
        migrateTalentUpgradesIfNeeded();
        return manualElementalSkill;
    }

    public int getManualElementalBurst() {
        migrateTalentUpgradesIfNeeded();
        return manualElementalBurst;
    }

    /** 普攻等级上限：手动 9 次 → 10 级（命座不加普攻）。 */
    public int getNormalAttackLevelCap() {
        return TALENT_BASE_LEVEL + MAX_MANUAL_TALENT_UPGRADES;
    }

    /** 战技等级上限：10 级；3 命 +3 → 13 级。 */
    public int getElementalSkillLevelCap() {
        return TALENT_BASE_LEVEL + MAX_MANUAL_TALENT_UPGRADES
                + (constellation >= 3 ? C3_SKILL_LEVEL_BONUS : 0);
    }

    /** 元素爆发等级上限：10 级；5 命 +3 → 13 级。 */
    public int getElementalBurstLevelCap() {
        return TALENT_BASE_LEVEL + MAX_MANUAL_TALENT_UPGRADES
                + (constellation >= 5 ? C5_BURST_LEVEL_BONUS : 0);
    }

    public boolean canUpgradeNormalAttack() {
        migrateTalentUpgradesIfNeeded();
        return manualNormalAttack < MAX_MANUAL_TALENT_UPGRADES;
    }

    public boolean canUpgradeElementalSkill() {
        migrateTalentUpgradesIfNeeded();
        return manualElementalSkill < MAX_MANUAL_TALENT_UPGRADES;
    }

    public boolean canUpgradeElementalBurst() {
        migrateTalentUpgradesIfNeeded();
        return manualElementalBurst < MAX_MANUAL_TALENT_UPGRADES;
    }

    public boolean upgradeNormalAttack() {
        if (!canUpgradeNormalAttack()) return false;
        manualNormalAttack++;
        recalculateTalentLevels();
        markDirty();
        return true;
    }

    public boolean upgradeElementalSkill() {
        if (!canUpgradeElementalSkill()) return false;
        manualElementalSkill++;
        recalculateTalentLevels();
        markDirty();
        return true;
    }

    public boolean upgradeElementalBurst() {
        if (!canUpgradeElementalBurst()) return false;
        manualElementalBurst++;
        recalculateTalentLevels();
        markDirty();
        return true;
    }

    public void setWeaponBaseATK(double value) { weaponBaseATK = value; markDirty(); }

    // ==================== 外观选项 ====================

    /** 外观位掩码（两条腿的鞋/袜子 + 猫耳），位布局见 {@link CharacterAppearance}。 */
    public int getAppearance() {
        return legAppearance;
    }

    public void setAppearance(int mask) {
        if (mask == legAppearance) return;
        legAppearance = mask;
        // 全武器类角色：外观里选的武器种类就是"身上那一把"，换了种类就得重算武器属性
        if (artifactInventory instanceof AllWeaponArtifactInventory) {
            artifactInventory.markDirty(ArtifactInventory.SLOT_WEAPON);
        }
        markDirty();
    }

    // 注意：掩码的<b>读法</b>（哪几段是袜子 / 鞋 / 猫耳）不在这里 ——
    // 那是"申鹤的外观数据"（{@code ShenheAppearanceData}）该知道的事。
    // 这里只负责把那个 int 存住、标记脏、跟着存档与同步走。

    public ItemStack getFlower() { return artifactInventory.getItem(ArtifactInventory.SLOT_FLOWER); }
    public ItemStack getPlume() { return artifactInventory.getItem(ArtifactInventory.SLOT_PLUME); }
    public ItemStack getSands() { return artifactInventory.getItem(ArtifactInventory.SLOT_SANDS); }
    public ItemStack getGoblet() { return artifactInventory.getItem(ArtifactInventory.SLOT_GOBLET); }
    public ItemStack getCirclet() { return artifactInventory.getItem(ArtifactInventory.SLOT_CIRCLET); }
    public ItemStack getWeapon() { return artifactInventory.getItem(activeWeaponSlot()); }

    /**
     * 「身上那一把」武器在哪一格。
     *
     * <p>普通角色恒为 {@link ArtifactInventory#SLOT_WEAPON}（唯一那个武器槽）；
     * 背包是 {@link AllWeaponArtifactInventory} 的全武器类角色（林薇云）则跟着
     * 外观里选中的武器种类走 —— 选了单手剑，身上那把就是单手剑那一格。
     */
    public int activeWeaponSlot() {
        if (!(artifactInventory instanceof AllWeaponArtifactInventory)) {
            return ArtifactInventory.SLOT_WEAPON;
        }
        CharacterAppearanceData appearance = parentCharacter == null ? null : parentCharacter.appearanceData();
        int offset = appearance == null ? 0 : appearance.weaponSlotOffset(legAppearance);
        return ArtifactInventory.SLOT_WEAPON + offset;
    }

    /**
     * 换一份背包实例 —— 全武器类角色在<b>角色构造期</b>把默认那 6 格换成 11 格的子类。
     *
     * <p>时机很重要：LDLib2 的 {@code @Persisted} 反序列化是"写进现成实例"
     * （见 {@code PersistedParser#deserializeInternal}），实例类型不对的话多出来的槽位读不回来。
     */
    public void installArtifactInventory(ArtifactInventory inventory) {
        if (inventory != null) {
            this.artifactInventory = inventory;
        }
    }

    public List<ItemStack> getAllArtifactsAsList() {
        return artifactInventory.getAllArtifactsAsList();
    }


    // ==================== 武器被动状态 ====================
    //
    // 放在角色上而不是武器物品 NBT 上：武器被动是「装备者」的属性，
    // 退场要能重置（见 WeaponItem#onLeaveField）。

    /** 武器被动的轮换序号（如蝶变的三种风：0 忠忱 / 1 叛弃 / 2 丰获）。 */
    @DescSynced
    @Persisted(key = "weapon_passive_stage")
    private int weaponPassiveStage;

    /** 武器被动的限速闸门（游戏刻）：下一次允许触发的时间。 */
    @DescSynced
    @Persisted(key = "weapon_passive_gate_tick")
    private long weaponPassiveGateTick;

    public int getWeaponPassiveStage() {
        return weaponPassiveStage;
    }

    public void setWeaponPassiveStage(int stage) {
        this.weaponPassiveStage = Math.max(0, stage);
        markDirty();
    }

    public long getWeaponPassiveGateTick() {
        return weaponPassiveGateTick;
    }

    public void setWeaponPassiveGateTick(long tick) {
        this.weaponPassiveGateTick = tick;
        markDirty();
    }

    /**
     * 漩流颂歌（{@code whirlflow_hymn}）：「附近队友触发冻结 / 星扩散」的窗口<b>到期刻</b>。
     *
     * <p>用绝对时刻而不是倒计时，读的时候拿当前游戏刻比一下就行。
     * 老存档里没有这个字段，读出来就是 {@code 0} —— 语义正是「从没触发过」，
     * 所以不需要自愈 getter（和蝶变的轮换序号那种「新常量被旧值盖掉」的情况不同）。
     */
    @DescSynced
    @Persisted(key = "whirlflow_reaction_window_end")
    private long whirlflowReactionWindowEnd;

    public long getWhirlflowReactionWindowEnd() {
        return whirlflowReactionWindowEnd;
    }

    public void setWhirlflowReactionWindowEnd(long tick) {
        this.whirlflowReactionWindowEnd = tick;
        markDirty();
    }

    /** 现在（{@code gameTime}）是否还在「冻结 / 星扩散」的 5 秒窗口内。 */
    public boolean isWhirlflowReactionWindowActive(long gameTime) {
        return whirlflowReactionWindowEnd > 0L && gameTime < whirlflowReactionWindowEnd;
    }

    /**
     * 千岩牢固（{@code tenacity_of_the_millelith}）四件套的触发闸门：
     * 「元素战技命中敌人」下次<b>允许</b>触发的游戏刻（每 0.5 秒至多触发一次）。
     *
     * <p>和蝶变的 {@code weaponPassiveGateTick} 同一形态 —— 用绝对时刻而不是倒计时，
     * 换人 / 存档 / 重登都不会串。老存档读出来是 {@code 0}，语义正是「从没触发过」，不需要自愈。
     */
    @DescSynced
    @Persisted(key = "tenacity4_gate_tick")
    private long tenacity4GateTick;

    public long getTenacity4GateTick() {
        return tenacity4GateTick;
    }

    public void setTenacity4GateTick(long tick) {
        this.tenacity4GateTick = tick;
        markDirty();
    }

    // ==================== 命座 ====================

    /** 命座上限（满命）。 */
    public static final int MAX_CONSTELLATION = 6;

    /** 直接设置命座等级（命令 / GM 用），自动 clamp 到 0~6。 */
    public void setConstellation(int constellation) {
        migrateTalentUpgradesIfNeeded();     // ⚠️ 必须先迁移：否则老存档重算会把手动次数当 0，等级掉回去
        this.constellation = Math.max(0, Math.min(MAX_CONSTELLATION, constellation));
        // 3 命 +战技等级 / 5 命 +爆发等级 → 有效等级要跟着重算
        recalculateTalentLevels();
        markDirty();
    }

    /**
     * 提升一级命座 —— 抽到<b>已有</b>角色时调用。
     *
     * @return {@code true} = 这次真的升了一级；{@code false} = 已经满命，
     *         调用方应该改走满命补偿（随机一套圣遗物）
     */
    public boolean upgradeConstellation() {
        migrateTalentUpgradesIfNeeded();     // 同上：先迁移再重算
        if (this.constellation >= MAX_CONSTELLATION) {
            return false;
        }
        this.constellation++;
        recalculateTalentLevels();
        markDirty();
        return true;
    }

    public void setCurrentExp(int currentExp) { this.currentExp = currentExp;markDirty();}    public void setMaxExp(int maxExp) { this.maxExp = maxExp;markDirty();}
    public void setCurrentHP(double currentHP) { this.currentHP = currentHP;markDirty();}
    public void setAscensionPhase(int ascensionPhase) { this.ascensionPhase = ascensionPhase;markDirty();}
    public void setCurrentObtainingEnergy(float energy) { this.currentObtainingEnergy = energy;markDirty();}
    public void addElementalEnergy(float amount) {
        amount = (float) (amount * attributes.getTotalValue(ModAttributes.ER.get()));
        this.currentObtainingEnergy = Math.min(this.currentObtainingEnergy + amount, this.maxObtainingEnergy);
        markDirty();
    }
    public void setSkillShortMaxCooldownTick(int tick) { this.skillShortMaxCooldownTick = tick; markDirty(); }
    public void setSkillLongMaxCooldownTick(int tick) { this.skillLongMaxCooldownTick = tick; markDirty(); }
    public void setBurstMaxCooldownTick(int tick) { this.burstMaxCooldownTick = tick; markDirty(); }
    public void setMaxObtainingEnergy(float energy) { this.maxObtainingEnergy = energy; markDirty(); }
    public void setElementalSkillCooldownTick(float tick) { this.elementalSkillCooldownTick = tick;markDirty();}
    public void setElementalBurstCooldownTick(float tick) { this.elementalBurstCooldownTick = tick;markDirty();}
    public void setElementalSkillMaxStacks(int stacks) { this.elementalSkillMaxStacks = stacks;markDirty();}
    public void setElementalSkillStacks(int stacks) { this.elementalSkillStacks = stacks;markDirty();}

    public void setFlower(ItemStack stack) { artifactInventory.setItem(ArtifactInventory.SLOT_FLOWER, stack); markDirty(); }
    public void setPlume(ItemStack stack) { artifactInventory.setItem(ArtifactInventory.SLOT_PLUME, stack); markDirty(); }
    public void setSands(ItemStack stack) { artifactInventory.setItem(ArtifactInventory.SLOT_SANDS, stack); markDirty(); }
    public void setGoblet(ItemStack stack) { artifactInventory.setItem(ArtifactInventory.SLOT_GOBLET, stack); markDirty(); }
    public void setCirclet(ItemStack stack) { artifactInventory.setItem(ArtifactInventory.SLOT_CIRCLET, stack); markDirty(); }


    public void addLevel(int levels) {this.characterLevel = Math.max(1, Math.min(this.characterLevel + levels, 90));markDirty();}
    public void healHP(double amount) {this.currentHP = Math.min(this.currentHP + amount, getAttributeTotalValue(ModAttributes.MAX_HP.value()));markDirty();}
    public void hurtHP(float amount) {this.currentHP = Math.max(0, this.currentHP - amount);markDirty();}

    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; markDirty(); }

    public void setOwnerPlayer(Player ownerPlayer) {
        this.ownerPlayer = ownerPlayer;
        if (ownerPlayer != null) this.ownerUUID = ownerPlayer.getUUID();
        markDirty();
    }

    /**
     * 获取角色的效果容器
     * 采用延迟加载模式：首次调用时从NBT标签反序列化，后续直接返回缓存
     * @return 效果容器
     */
    public void tick() {
        if (elementalSkillCooldownTick > 0) {
            elementalSkillCooldownTick--;
            markDirty();
            if (elementalSkillCooldownTick == 0) {
                if (skillCoolList != null && !skillCoolList.isEmpty()) {  // ← 增加空检查
                    skillCoolList.removeFirst();
                    if (!skillCoolList.isEmpty()) {
                        elementalSkillCooldownTick = skillCoolList.getFirst();
                        markDirty();

                    }
                }
                if (elementalSkillStacks < elementalSkillMaxStacks) {
                    elementalSkillStacks++;
                    markDirty();
                }
            }
        }
        if (elementalBurstCooldownTick > 0) {
            elementalBurstCooldownTick--;
            markDirty();
        }
        statusContainer.tick();
    }
    public CharacterEffectContainer getEffectContainer() {
        if (effectContainer == null) {                                     // 首次调用时加载
            effectContainer = CharacterEffectContainer.fromListTag(effectDataList); // 从NBT反序列化
        }
        return effectContainer;                                            // 返回缓存
    }

    /**
     * 将效果容器的当前状态同步写回NBT标签
     * 在修改效果数据后必须调用此方法，确保下次持久化时数据正确
     */
    public void syncEffectsToTag() {
        if (effectContainer != null) {                                     // 容器已加载时才同步
            effectDataList = effectContainer.toListTag();                  // 序列化回NBT标签
        }
    }


    public PGCharacterData copy() {
        PGCharacterData copy = new PGCharacterData();
        copy.characterLevel = this.characterLevel;
        copy.currentExp = this.currentExp;
        copy.maxExp = this.maxExp;
        copy.currentHP = this.currentHP;
        copy.ascensionPhase = this.ascensionPhase;
        copy.constellation = this.constellation;
        copy.attributes = this.attributes.copy();
        copy.normalAttackLevel = this.normalAttackLevel;
        copy.elementalSkillLevel = this.elementalSkillLevel;
        copy.elementalBurstLevel = this.elementalBurstLevel;
        copy.manualNormalAttack = this.manualNormalAttack;
        copy.manualElementalSkill = this.manualElementalSkill;
        copy.manualElementalBurst = this.manualElementalBurst;
        copy.currentObtainingEnergy = this.currentObtainingEnergy;
        copy.elementalSkillCooldownTick = this.elementalSkillCooldownTick;
        copy.elementalBurstCooldownTick = this.elementalBurstCooldownTick;
        copy.elementalSkillMaxStacks = this.elementalSkillMaxStacks;
        copy.elementalSkillStacks = this.elementalSkillStacks;
        copy.skillCoolList = this.skillCoolList;
        copy.effectDataList = this.effectDataList.copy();                  // 深拷贝效果NBT数据
        copy.ownerUUID = this.ownerUUID; //AI 序列化的 UUID 一起拷贝
        if (this.effectContainer != null) {                                // 如果容器已加载
            copy.effectContainer = this.effectContainer.copy();            // 也深拷贝容器缓存
        }
        copy.artifactInventory = this.artifactInventory.copy();
        return copy;
    }

    /**
     * 标记这份数据「下一 tick 需要整包同步给客户端」。
     *
     * <p>同步有两条路：
     * <ol>
     *   <li>{@link #syncToClient()} —— LDLib2 增量包。<b>角色身上收不到</b>：
     *       客户端没绑 ownerPlayer（{@code ISyncCharacter.handleCharacterSyncPacket} 会直接返回），
     *       而且它和「角色自己的字段」共用同一个包名，索引空间不同，不能混用。</li>
     *   <li>{@code CharacterTickHandler} 里检查 {@code isDirty()} → 整包
     *       （{@code syncSingleCharacterToPlayer}）。<b>这条路是通的</b>，
     *       所以「想让客户端看到什么」就得标 dirty。</li>
     * </ol>
     * 角色子类自己的状态（例如薇斯娜的剑气 / 巡风列装）也走第 2 条 ——
     * 它们不在这个类里，但整包同步会把角色对象的 {@code @Persisted} 字段一起带上。
     */
    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() { dirty = false; }

    /**
     * Sync dirty @DescSynced fields to the owning player's client.
     * Call after any state change (hurtHP, healHP, setCurrentObtainingEnergy, tick, etc.)
     */
    public void syncToClient() {
        if (parentCharacter == null) return;
        var player = getOwnerPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.level();

        for (var field : syncStorage.getNonLazyFields()) {
            field.update();
        }
        if (!syncStorage.hasDirtySyncFields()) return;

        var syncedFields = syncStorage.getSyncFields();
        if (syncedFields.length == 0) return;

        var changed = new BitSet();
        var data = ByteBufUtil.writeCustomData(buffer -> {
            for (int i = 0; i < syncedFields.length; i++) {
                var field = syncedFields[i];
                if (field.isSyncDirty()) {
                    changed.set(i);
                    field.readSyncToStream(buffer);
                    field.clearSyncDirty();
                }
            }
        }, serverLevel.registryAccess());

        if (changed.isEmpty()) return;

        var payload = new CompoundTag();
        payload.putLongArray("changed", changed.toLongArray());
        payload.putByteArray("data", data);

        NetworkManager.sendCharacterSyncToPlayer(serverPlayer, parentCharacter.getCharacterUUID(), payload);
    }

    void receiveFromServer(CompoundTag payload, net.minecraft.core.RegistryAccess registryAccess) {
        var changed = BitSet.valueOf(payload.getLongArray("changed").orElse(new long[0]));
        var buf = payload.getByteArray("data").orElse(new byte[0]);

        ByteBufUtil.readCustomData(buf, buffer -> {
            var syncedFields = syncStorage.getSyncFields();
            for (int i = 0; i < syncedFields.length; i++) {
                if (changed.get(i)) {
                    syncedFields[i].writeSyncFromStream(buffer);
                }
            }
        }, registryAccess);
    }
}
