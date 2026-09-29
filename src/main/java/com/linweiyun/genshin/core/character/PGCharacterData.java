// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.content.attribute.AttributeContainer;
import com.linweiyun.genshin.content.attribute.AttributeInstance;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.items.artifact.inventory.AllWeaponArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.util.appearance.CharacterAppearance;
import com.linweiyun.genshin.core.character.util.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.lowdragmc.lowdraglib2.syncdata.IManaged;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.ref.IRef;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.Generated;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PGCharacterData implements IPersistedSerializable, IManaged {
   private PGCharacter parentCharacter;
   private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
   @Persisted(key = "character_level")
   private int characterLevel;
   @Persisted(key = "max_exp")
   private int maxExp;
   @Persisted(key = "current_exp")
   private int currentExp;
   @Persisted(key = "current_hp")
   @DescSynced
   private double currentHP;
   @Persisted(key = "ascension_phase")
   private int ascensionPhase;
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
   @Persisted(key = "manual_normal_attack")
   private int manualNormalAttack;
   @Persisted(key = "manual_elemental_skill")
   private int manualElementalSkill;
   @Persisted(key = "manual_elemental_burst")
   private int manualElementalBurst;
   private transient boolean talentUpgradesMigrated;
   @Persisted(key = "current_obtaining_energy")
   @DescSynced
   private float currentObtainingEnergy;
   @Persisted(key = "skill_short_max_cooldown")
   @DescSynced
   private int skillShortMaxCooldownTick;
   @Persisted(key = "skill_long_max_cooldown")
   @DescSynced
   private int skillLongMaxCooldownTick;
   @Persisted(key = "burst_max_cooldown")
   @DescSynced
   private int burstMaxCooldownTick;
   @Persisted(key = "max_obtaining_energy")
   @DescSynced
   private float maxObtainingEnergy;
   @Persisted(key = "elemental_skill_cooldown_tick")
   @DescSynced
   private float elementalSkillCooldownTick;
   @Persisted(key = "elemental_burst_cooldown_tick")
   @DescSynced
   private float elementalBurstCooldownTick;
   @Persisted(key = "effect_data")
   private ListTag effectDataList = new ListTag();
   @Persisted(key = "elemental_skill_max_stacks")
   private int elementalSkillMaxStacks;
   @Persisted(key = "elemental_skill_stacks")
   @DescSynced
   private int elementalSkillStacks;
   @Persisted(key = "skill_list")
   protected List<Integer> skillCoolList;
   @Persisted(key = "status_container")
   private StatusContainer statusContainer = new StatusContainer();
   @Persisted(key = "owner_uuid")
   private UUID ownerUUID;
   @Persisted(key = "artifact_inventory")
   private ArtifactInventory artifactInventory = new ArtifactInventory();
   @Persisted(key = "weapon_base_atk")
   private double weaponBaseATK = 0.0;
   @Persisted(key = "leg_appearance")
   @DescSynced
   private int legAppearance = CharacterAppearance.DEFAULT_MASK;
   private Player ownerPlayer;
   private CharacterEffectContainer effectContainer;
   private boolean dirty = false;
   public static final int MAX_MANUAL_TALENT_UPGRADES = 9;
   public static final int TALENT_BASE_LEVEL = 1;
   public static final int C3_SKILL_LEVEL_BONUS = 3;
   public static final int C5_BURST_LEVEL_BONUS = 3;
   public static final int MAX_TALENT_LEVEL = 13;
   @DescSynced
   @Persisted(key = "weapon_passive_stage")
   private int weaponPassiveStage;
   @DescSynced
   @Persisted(key = "weapon_passive_gate_tick")
   private long weaponPassiveGateTick;
   @DescSynced
   @Persisted(key = "whirlflow_reaction_window_end")
   private long whirlflowReactionWindowEnd;
   @DescSynced
   @Persisted(key = "tenacity4_gate_tick")
   private long tenacity4GateTick;
   public static final int MAX_CONSTELLATION = 6;

   public IManagedStorage getSyncStorage() {
      return this.syncStorage;
   }

   public void notifyPersistence() {
   }

   void setParentCharacter(PGCharacter parentCharacter) {
      this.parentCharacter = parentCharacter;
   }

   public PGCharacterData() {
      this.characterLevel = 1;
      this.currentExp = 0;
      this.maxExp = 1000;
      this.ascensionPhase = 0;
      this.constellation = 0;
      this.normalAttackLevel = 1;
      this.elementalSkillLevel = 1;
      this.elementalBurstLevel = 1;
      this.currentObtainingEnergy = 0.0F;
      this.elementalSkillCooldownTick = 0.0F;
      this.elementalBurstCooldownTick = 0.0F;
      this.elementalSkillMaxStacks = 1;
      this.elementalSkillStacks = 1;
      this.attributes = new AttributeContainer();
      this.effectDataList = new ListTag();
      this.skillCoolList = new ArrayList<>();
      this.artifactInventory = new ArtifactInventory();
      this.initAllAttributes();
   }

   public void initBaseStats(double baseHP, double baseATK, double baseDEF) {
      this.attributes.setBaseValue((AttributeType)ModAttributes.MAX_HP.value(), baseHP);
      this.attributes.setBaseValue((AttributeType)ModAttributes.ATK.value(), baseATK);
      this.attributes.setBaseValue((AttributeType)ModAttributes.DEF.value(), baseDEF);
      this.currentHP = this.attributes.getTotalValue((AttributeType)ModAttributes.MAX_HP.value());
   }

   private void initAllAttributes() {
      for (AttributeType type : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
         this.attributes.getOrCreate(type);
      }
   }

   public AttributeInstance getAttribute(AttributeType type) {
      return this.attributes.get(type);
   }

   public double getAttributeBaseValue(AttributeType type) {
      return this.attributes.getBaseValue(type);
   }

   public double getAttributeTotalValue(AttributeType type) {
      return this.attributes.getTotalValue(type);
   }

   public double getAttributeFlatModifier(AttributeType type) {
      return this.attributes.getFlatModifier(type);
   }

   public double getAttributeTempFlatModifier(AttributeType type) {
      return this.attributes.getTempFlatModifier(type);
   }

   public double getAttributePercentModifier(AttributeType type) {
      return this.attributes.getPercentModifier(type);
   }

   public double getAttributePercentModifierDisplay(AttributeType type) {
      return this.attributes.getPercentModifierDisplay(type);
   }

   public double getAttributeTempPercentModifier(AttributeType type) {
      return this.attributes.getTempPercentModifier(type);
   }

   public double getAttributeTempPercentModifierDisplay(AttributeType type) {
      return this.attributes.getTempPercentModifierDisplay(type);
   }

   public void setAttributeBaseValue(AttributeType type, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setBaseValue(type, value));
      this.markDirty();
   }

   public void setAttributeBaseValue(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setBaseValue(type, source, value));
      this.markDirty();
   }

   public void removeAttributeBaseValue(AttributeType type, String source) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.removeBaseValue(type, source));
      this.markDirty();
   }

   public void addAttributeFlatModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.addFlatModifier(type, source, value));
      this.markDirty();
   }

   public void addAttributePercentModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.addPercentModifier(type, source, value));
      this.markDirty();
   }

   public void setAttributeFlatModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setFlatModifier(type, source, value));
      this.markDirty();
   }

   public void setAttributePercentModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setPercentModifier(type, source, value));
      this.markDirty();
   }

   public void addAttributeTempFlatModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.addTempFlatModifier(type, source, value));
      this.markDirty();
   }

   public void addAttributeTempPercentModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.addTempPercentModifier(type, source, value));
      this.markDirty();
   }

   public void setAttributeTempFlatModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setTempFlatModifier(type, source, value));
      this.markDirty();
   }

   public void setAttributeTempPercentModifier(AttributeType type, String source, double value) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.setTempPercentModifier(type, source, value));
      this.markDirty();
   }

   public void removeAttributeModifier(AttributeType type, String source) {
      this.preserveHpIfMaxHp(type, () -> this.attributes.removeModifier(type, source));
      this.markDirty();
   }

   private boolean isMaxHpAttribute(AttributeType type) {
      return type != null && type.id() != null ? type == ModAttributes.MAX_HP.get() || type.id().equals(ModAttributes.MAX_HP.getId()) : false;
   }

   private void preserveHpIfMaxHp(AttributeType type, Runnable action) {
      if (!this.isMaxHpAttribute(type)) {
         action.run();
      } else {
         double oldMaxHP = this.attributes.getTotalValue((AttributeType)ModAttributes.MAX_HP.get());
         double oldCurrentHP = this.currentHP;
         action.run();
         double newMaxHP = this.attributes.getTotalValue((AttributeType)ModAttributes.MAX_HP.get());
         if (newMaxHP != oldMaxHP) {
            double hpRatio = oldMaxHP > 0.0 ? oldCurrentHP / oldMaxHP : 1.0;
            this.currentHP = newMaxHP * hpRatio;
         }
      }
   }

   public int getLevel() {
      return this.characterLevel;
   }

   private void recalculateTalentLevels() {
      this.normalAttackLevel = 1 + this.manualNormalAttack;
      this.elementalSkillLevel = 1 + this.manualElementalSkill + (this.constellation >= 3 ? 3 : 0);
      this.elementalBurstLevel = 1 + this.manualElementalBurst + (this.constellation >= 5 ? 3 : 0);
   }

   private void migrateTalentUpgradesIfNeeded() {
      if (!this.talentUpgradesMigrated) {
         this.talentUpgradesMigrated = true;
         if (this.manualNormalAttack == 0 && this.manualElementalSkill == 0 && this.manualElementalBurst == 0) {
            if (this.normalAttackLevel > 1 || this.elementalSkillLevel > 1 || this.elementalBurstLevel > 1) {
               this.manualNormalAttack = clampManual(this.normalAttackLevel - 1);
               this.manualElementalSkill = clampManual(this.elementalSkillLevel - 1);
               this.manualElementalBurst = clampManual(this.elementalBurstLevel - 1);
               this.recalculateTalentLevels();
               this.markDirty();
            }
         }
      }
   }

   private static int clampManual(int value) {
      return Math.max(0, Math.min(9, value));
   }

   public boolean setManualTalentUpgrades(String kind, int count) {
      this.migrateTalentUpgradesIfNeeded();
      int clamped = clampManual(count);
      switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
         case "normal":
            this.manualNormalAttack = clamped;
            break;
         case "skill":
            this.manualElementalSkill = clamped;
            break;
         case "burst":
            this.manualElementalBurst = clamped;
            break;
         default:
            return false;
      }

      this.recalculateTalentLevels();
      this.markDirty();
      return true;
   }

   public int getManualNormalAttack() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualNormalAttack;
   }

   public int getNormalAttackLevel() {
      this.migrateTalentUpgradesIfNeeded();
      return this.normalAttackLevel;
   }

   public int getElementalSkillLevel() {
      this.migrateTalentUpgradesIfNeeded();
      return this.elementalSkillLevel;
   }

   public int getElementalBurstLevel() {
      this.migrateTalentUpgradesIfNeeded();
      return this.elementalBurstLevel;
   }

   public int getManualElementalSkill() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualElementalSkill;
   }

   public int getManualElementalBurst() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualElementalBurst;
   }

   public int getNormalAttackLevelCap() {
      return 10;
   }

   public int getElementalSkillLevelCap() {
      return 10 + (this.constellation >= 3 ? 3 : 0);
   }

   public int getElementalBurstLevelCap() {
      return 10 + (this.constellation >= 5 ? 3 : 0);
   }

   public boolean canUpgradeNormalAttack() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualNormalAttack < 9;
   }

   public boolean canUpgradeElementalSkill() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualElementalSkill < 9;
   }

   public boolean canUpgradeElementalBurst() {
      this.migrateTalentUpgradesIfNeeded();
      return this.manualElementalBurst < 9;
   }

   public boolean upgradeNormalAttack() {
      if (!this.canUpgradeNormalAttack()) {
         return false;
      }

      this.manualNormalAttack++;
      this.recalculateTalentLevels();
      this.markDirty();
      return true;
   }

   public boolean upgradeElementalSkill() {
      if (!this.canUpgradeElementalSkill()) {
         return false;
      }

      this.manualElementalSkill++;
      this.recalculateTalentLevels();
      this.markDirty();
      return true;
   }

   public boolean upgradeElementalBurst() {
      if (!this.canUpgradeElementalBurst()) {
         return false;
      }

      this.manualElementalBurst++;
      this.recalculateTalentLevels();
      this.markDirty();
      return true;
   }

   public void setWeaponBaseATK(double value) {
      this.weaponBaseATK = value;
      this.markDirty();
   }

   public int getAppearance() {
      return this.legAppearance;
   }

   public void setAppearance(int mask) {
      if (mask != this.legAppearance) {
         this.legAppearance = mask;
         if (this.artifactInventory instanceof AllWeaponArtifactInventory) {
            this.artifactInventory.markDirty(5);
         }

         this.markDirty();
      }
   }

   public ItemStack getFlower() {
      return this.artifactInventory.getItem(0);
   }

   public ItemStack getPlume() {
      return this.artifactInventory.getItem(1);
   }

   public ItemStack getSands() {
      return this.artifactInventory.getItem(2);
   }

   public ItemStack getGoblet() {
      return this.artifactInventory.getItem(3);
   }

   public ItemStack getCirclet() {
      return this.artifactInventory.getItem(4);
   }

   public ItemStack getWeapon() {
      return this.artifactInventory.getItem(this.activeWeaponSlot());
   }

   public int activeWeaponSlot() {
      if (!(this.artifactInventory instanceof AllWeaponArtifactInventory)) {
         return 5;
      }

      CharacterAppearanceData appearance = this.parentCharacter == null ? null : this.parentCharacter.appearanceData();
      int offset = appearance == null ? 0 : appearance.weaponSlotOffset(this.legAppearance);
      return 5 + offset;
   }

   public void installArtifactInventory(ArtifactInventory inventory) {
      if (inventory != null) {
         this.artifactInventory = inventory;
      }
   }

   public List<ItemStack> getAllArtifactsAsList() {
      return this.artifactInventory.getAllArtifactsAsList();
   }

   public int getWeaponPassiveStage() {
      return this.weaponPassiveStage;
   }

   public void setWeaponPassiveStage(int stage) {
      this.weaponPassiveStage = Math.max(0, stage);
      this.markDirty();
   }

   public long getWeaponPassiveGateTick() {
      return this.weaponPassiveGateTick;
   }

   public void setWeaponPassiveGateTick(long tick) {
      this.weaponPassiveGateTick = tick;
      this.markDirty();
   }

   public long getWhirlflowReactionWindowEnd() {
      return this.whirlflowReactionWindowEnd;
   }

   public void setWhirlflowReactionWindowEnd(long tick) {
      this.whirlflowReactionWindowEnd = tick;
      this.markDirty();
   }

   public boolean isWhirlflowReactionWindowActive(long gameTime) {
      return this.whirlflowReactionWindowEnd > 0L && gameTime < this.whirlflowReactionWindowEnd;
   }

   public long getTenacity4GateTick() {
      return this.tenacity4GateTick;
   }

   public void setTenacity4GateTick(long tick) {
      this.tenacity4GateTick = tick;
      this.markDirty();
   }

   public void setConstellation(int constellation) {
      this.migrateTalentUpgradesIfNeeded();
      this.constellation = Math.max(0, Math.min(6, constellation));
      this.recalculateTalentLevels();
      this.markDirty();
   }

   public boolean upgradeConstellation() {
      this.migrateTalentUpgradesIfNeeded();
      if (this.constellation >= 6) {
         return false;
      }

      this.constellation++;
      this.recalculateTalentLevels();
      this.markDirty();
      return true;
   }

   public void setCurrentExp(int currentExp) {
      this.currentExp = currentExp;
      this.markDirty();
   }

   public void setMaxExp(int maxExp) {
      this.maxExp = maxExp;
      this.markDirty();
   }

   public void setCurrentHP(double currentHP) {
      this.currentHP = currentHP;
      this.markDirty();
   }

   public void setAscensionPhase(int ascensionPhase) {
      this.ascensionPhase = ascensionPhase;
      this.markDirty();
   }

   public void setCurrentObtainingEnergy(float energy) {
      this.currentObtainingEnergy = energy;
      this.markDirty();
   }

   public void addElementalEnergy(float amount) {
      amount = (float)(amount * this.attributes.getTotalValue((AttributeType)ModAttributes.ER.get()));
      this.currentObtainingEnergy = Math.min(this.currentObtainingEnergy + amount, this.maxObtainingEnergy);
      this.markDirty();
   }

   public void setSkillShortMaxCooldownTick(int tick) {
      this.skillShortMaxCooldownTick = tick;
      this.markDirty();
   }

   public void setSkillLongMaxCooldownTick(int tick) {
      this.skillLongMaxCooldownTick = tick;
      this.markDirty();
   }

   public void setBurstMaxCooldownTick(int tick) {
      this.burstMaxCooldownTick = tick;
      this.markDirty();
   }

   public void setMaxObtainingEnergy(float energy) {
      this.maxObtainingEnergy = energy;
      this.markDirty();
   }

   public void setElementalSkillCooldownTick(float tick) {
      this.elementalSkillCooldownTick = tick;
      this.markDirty();
   }

   public void setElementalBurstCooldownTick(float tick) {
      this.elementalBurstCooldownTick = tick;
      this.markDirty();
   }

   public void setElementalSkillMaxStacks(int stacks) {
      this.elementalSkillMaxStacks = stacks;
      this.markDirty();
   }

   public void setElementalSkillStacks(int stacks) {
      this.elementalSkillStacks = stacks;
      this.markDirty();
   }

   public void setFlower(ItemStack stack) {
      this.artifactInventory.setItem(0, stack);
      this.markDirty();
   }

   public void setPlume(ItemStack stack) {
      this.artifactInventory.setItem(1, stack);
      this.markDirty();
   }

   public void setSands(ItemStack stack) {
      this.artifactInventory.setItem(2, stack);
      this.markDirty();
   }

   public void setGoblet(ItemStack stack) {
      this.artifactInventory.setItem(3, stack);
      this.markDirty();
   }

   public void setCirclet(ItemStack stack) {
      this.artifactInventory.setItem(4, stack);
      this.markDirty();
   }

   public void addLevel(int levels) {
      this.characterLevel = Math.max(1, Math.min(this.characterLevel + levels, 90));
      this.markDirty();
   }

   public void healHP(double amount) {
      this.currentHP = Math.min(this.currentHP + amount, this.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value()));
      this.markDirty();
   }

   public void hurtHP(float amount) {
      this.currentHP = Math.max(0.0, this.currentHP - amount);
      this.markDirty();
   }

   public void setOwnerUUID(UUID ownerUUID) {
      this.ownerUUID = ownerUUID;
      this.markDirty();
   }

   public void setOwnerPlayer(Player ownerPlayer) {
      this.ownerPlayer = ownerPlayer;
      if (ownerPlayer != null) {
         this.ownerUUID = ownerPlayer.getUUID();
      }

      this.markDirty();
   }

   public void tick() {
      if (this.elementalSkillCooldownTick > 0.0F) {
         this.elementalSkillCooldownTick--;
         this.markDirty();
         if (this.elementalSkillCooldownTick == 0.0F) {
            if (this.skillCoolList != null && !this.skillCoolList.isEmpty()) {
               this.skillCoolList.removeFirst();
               if (!this.skillCoolList.isEmpty()) {
                  this.elementalSkillCooldownTick = this.skillCoolList.getFirst().intValue();
                  this.markDirty();
               }
            }

            if (this.elementalSkillStacks < this.elementalSkillMaxStacks) {
               this.elementalSkillStacks++;
               this.markDirty();
            }
         }
      }

      if (this.elementalBurstCooldownTick > 0.0F) {
         this.elementalBurstCooldownTick--;
         this.markDirty();
      }

      this.statusContainer.tick();
   }

   public CharacterEffectContainer getEffectContainer() {
      if (this.effectContainer == null) {
         this.effectContainer = CharacterEffectContainer.fromListTag(this.effectDataList);
      }

      return this.effectContainer;
   }

   public void syncEffectsToTag() {
      if (this.effectContainer != null) {
         this.effectDataList = this.effectContainer.toListTag();
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
      copy.effectDataList = this.effectDataList.copy();
      copy.ownerUUID = this.ownerUUID;
      if (this.effectContainer != null) {
         copy.effectContainer = this.effectContainer.copy();
      }

      copy.artifactInventory = this.artifactInventory.copy();
      return copy;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public void syncToClient() {
      if (this.parentCharacter != null) {
         if (this.getOwnerPlayer() instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = serverPlayer.level();

            for (IRef<?> field : this.syncStorage.getNonLazyFields()) {
               field.update();
            }

            if (this.syncStorage.hasDirtySyncFields()) {
               IRef<?>[] syncedFields = this.syncStorage.getSyncFields();
               if (syncedFields.length != 0) {
                  BitSet changed = new BitSet();
                  byte[] data = ByteBufUtil.writeCustomData(buffer -> {
                     for (int i = 0; i < syncedFields.length; i++) {
                        IRef<?> field = syncedFields[i];
                        if (field.isSyncDirty()) {
                           changed.set(i);
                           field.readSyncToStream(buffer);
                           field.clearSyncDirty();
                        }
                     }
                  }, serverLevel.registryAccess());
                  if (!changed.isEmpty()) {
                     CompoundTag payload = new CompoundTag();
                     payload.putLongArray("changed", changed.toLongArray());
                     payload.putByteArray("data", data);
                     NetworkManager.sendCharacterSyncToPlayer(serverPlayer, this.parentCharacter.getCharacterUUID(), payload);
                  }
               }
            }
         }
      }
   }

   void receiveFromServer(CompoundTag payload, RegistryAccess registryAccess) {
      BitSet changed = BitSet.valueOf(payload.getLongArray("changed").orElse(new long[0]));
      byte[] buf = payload.getByteArray("data").orElse(new byte[0]);
      ByteBufUtil.readCustomData(buf, buffer -> {
         IRef<?>[] syncedFields = this.syncStorage.getSyncFields();

         for (int i = 0; i < syncedFields.length; i++) {
            if (changed.get(i)) {
               syncedFields[i].writeSyncFromStream(buffer);
            }
         }
      }, registryAccess);
   }

   @Generated
   public int getMaxExp() {
      return this.maxExp;
   }

   @Generated
   public int getCurrentExp() {
      return this.currentExp;
   }

   @Generated
   public double getCurrentHP() {
      return this.currentHP;
   }

   @Generated
   public int getAscensionPhase() {
      return this.ascensionPhase;
   }

   @Generated
   public int getConstellation() {
      return this.constellation;
   }

   @Generated
   public float getCurrentObtainingEnergy() {
      return this.currentObtainingEnergy;
   }

   @Generated
   public int getSkillShortMaxCooldownTick() {
      return this.skillShortMaxCooldownTick;
   }

   @Generated
   public int getSkillLongMaxCooldownTick() {
      return this.skillLongMaxCooldownTick;
   }

   @Generated
   public int getBurstMaxCooldownTick() {
      return this.burstMaxCooldownTick;
   }

   @Generated
   public float getMaxObtainingEnergy() {
      return this.maxObtainingEnergy;
   }

   @Generated
   public float getElementalSkillCooldownTick() {
      return this.elementalSkillCooldownTick;
   }

   @Generated
   public float getElementalBurstCooldownTick() {
      return this.elementalBurstCooldownTick;
   }

   @Generated
   public ListTag getEffectDataList() {
      return this.effectDataList;
   }

   @Generated
   public int getElementalSkillMaxStacks() {
      return this.elementalSkillMaxStacks;
   }

   @Generated
   public int getElementalSkillStacks() {
      return this.elementalSkillStacks;
   }

   @Generated
   public List<Integer> getSkillCoolList() {
      return this.skillCoolList;
   }

   @Generated
   public StatusContainer getStatusContainer() {
      return this.statusContainer;
   }

   @Generated
   public UUID getOwnerUUID() {
      return this.ownerUUID;
   }

   @Generated
   public ArtifactInventory getArtifactInventory() {
      return this.artifactInventory;
   }

   @Generated
   public double getWeaponBaseATK() {
      return this.weaponBaseATK;
   }

   @Generated
   public Player getOwnerPlayer() {
      return this.ownerPlayer;
   }

   @Generated
   public boolean isDirty() {
      return this.dirty;
   }
}