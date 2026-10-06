// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.config.character.CharacterXpConfig;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.artifact.ArtifactSetEffect;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactLevelData;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.WeaponLevelData;
import com.linweiyun.genshin.content.skill_node.ElementalOrbSpawner;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.util.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.character.util.appearance.DefaultAppearanceData;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.character.util.config.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.util.type.CharacterWeaponClass;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.sync.ISyncCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.compat.PlayerStatBridge;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Generated;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class PGCharacter implements IPersistedSerializable, ISyncCharacter {
   private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
   @Persisted(key = "character_uuid")
   protected int characterUUID;
   @Persisted(key = "star_rating")
   protected int starRating;
   @Persisted(key = "name")
   protected Component name;
   @Persisted(key = "elemental")
   protected String elementalId;
   private transient GenshinElement elemental;
   @Persisted(key = "ascend_attribute")
   protected CharacterAscendAttribute ascendAttribute;
   @Persisted(key = "texture_id")
   protected String textureId;
   @Persisted(key = "data")
   protected PGCharacterData data;
   private static final String SOURCE_WEAPON = "weapon";
   protected transient SkillBase skill;
   protected transient TalentBase talent;
   protected transient ConstellationBase constellation;
   protected transient ICharacterConfigUI configUI;
   protected transient CharacterAppearanceData appearanceData = DefaultAppearanceData.INSTANCE;
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
   private final Map<String, ActionSet> actionSetCache = new HashMap<>();

   public IManagedStorage getSyncStorage() {
      return this.syncStorage;
   }

   @Override
   public PGCharacter getSelfCharacter() {
      return this;
   }

   public void notifyPersistence() {
   }

   @Nullable
   public ICharacterConfigUI getConfigUI() {
      return this.configUI;
   }

   public boolean hasConfigUI() {
      return this.configUI != null;
   }

   public CharacterAppearanceData appearanceData() {
      return this.appearanceData;
   }

   @Nullable
   public SkillBase getSkill() {
      return this.skill;
   }

   public TalentBase getTalent() {
      return this.talent == null ? TalentBase.DEFAULT : this.talent;
   }

   @Nullable
   public ConstellationBase getConstellationObj() {
      return this.constellation;
   }

   public PGCharacter() {
      this.data = new PGCharacterData();
      this.data.setParentCharacter(this);
   }

   public PGCharacter(
      int characterUUID,
      int starRating,
      Component name,
      String elementalId,
      CharacterAscendAttribute ascendAttribute,
      int skillMaxCooldownTick,
      int burstMaxCooldownTick,
      float maxObtainingEnergy,
      String textureId,
      Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap
   ) {
      this.characterUUID = characterUUID;
      this.starRating = starRating;
      this.name = name;
      this.elementalId = elementalId;
      this.ascendAttribute = ascendAttribute;
      this.data = new PGCharacterData();
      this.data.setParentCharacter(this);
      this.data.setSkillShortMaxCooldownTick(skillMaxCooldownTick);
      this.data.setSkillLongMaxCooldownTick(skillMaxCooldownTick);
      this.data.setBurstMaxCooldownTick(burstMaxCooldownTick);
      this.data.setMaxObtainingEnergy(maxObtainingEnergy);
      this.textureId = textureId;
   }

   public PGCharacter(
      int characterUUID,
      int starRating,
      Component name,
      String elementalId,
      CharacterAscendAttribute ascendAttribute,
      int skillShortMaxCooldownTick,
      int skillLongMaxCooldownTick,
      int burstMaxCooldownTick,
      float maxObtainingEnergy,
      String textureId,
      Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap
   ) {
      this.characterUUID = characterUUID;
      this.starRating = starRating;
      this.name = name;
      this.elementalId = elementalId;
      this.ascendAttribute = ascendAttribute;
      this.data = new PGCharacterData();
      this.data.setParentCharacter(this);
      this.data.setSkillShortMaxCooldownTick(skillShortMaxCooldownTick);
      this.data.setSkillLongMaxCooldownTick(skillLongMaxCooldownTick);
      this.data.setBurstMaxCooldownTick(burstMaxCooldownTick);
      this.data.setMaxObtainingEnergy(maxObtainingEnergy);
      this.textureId = textureId;
   }

   private AttributeType resolveType(Identifier id) {
      return (AttributeType)((Registry)ModAttributes.ATTRIBUTES.getRegistry().get()).getValue(id);
   }

   public Class<? extends WeaponItem> getAllowedWeaponClass() {
      return WeaponItem.class;
   }

   public CharacterWeaponClass characterWeaponClass() {
      return CharacterWeaponClass.of(WeaponPoiseTable.weaponOfClass(this.getAllowedWeaponClass()));
   }

   public boolean isAllWeaponCharacter() {
      return this.characterWeaponClass() == CharacterWeaponClass.ALL_WEAPON;
   }

   public WeaponPoiseTable.WeaponClass currentWeaponType() {
      WeaponPoiseTable.WeaponClass type = this.characterWeaponClass().weaponType();
      return type == null ? WeaponPoiseTable.WeaponClass.UNKNOWN : type;
   }

   public boolean isWeaponCharacter(WeaponPoiseTable.WeaponClass weaponType) {
      return weaponType != null && weaponType != WeaponPoiseTable.WeaponClass.UNKNOWN && this.currentWeaponType() == weaponType;
   }

   public boolean isSwordCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.SWORD);
   }

   public boolean isClaymoreCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.CLAYMORE);
   }

   public boolean isPolearmCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.POLEARM);
   }

   public boolean isCatalystCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.CATALYST);
   }

   public boolean isBowCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.BOW);
   }

   public boolean isFistCharacter() {
      return this.isWeaponCharacter(WeaponPoiseTable.WeaponClass.FIST);
   }

   public boolean canEquipWeapon(ItemStack stack) {
      if (!(stack != null && stack.getItem() instanceof WeaponItem weapon)) {
         return false;
      }

      if (!this.getAllowedWeaponClass().isInstance(weapon)) {
         return false;
      }

      WeaponPoiseTable.WeaponClass current = this.currentWeaponType();
      return current == WeaponPoiseTable.WeaponClass.UNKNOWN || WeaponPoiseTable.weaponOfClass(weapon.getClass()) == current;
   }

   public String getActionStateKey(Player player) {
      return "default";
   }

   public boolean runsTalentOnClient() {
      return false;
   }

   public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
      return 0.0F;
   }

   public float getElevationBonus(StellarGlimmerBranch branch) {
      return this.data.getEffectContainer().getElevationBonus(branch) + this.getOwnElevationBonus(branch);
   }

   public float getOwnElevationBonus(StellarGlimmerBranch branch) {
      return 0.0F;
   }

   public float getCritDamageBonus(GenshinElement element, boolean stellarReaction) {
      return this.data.getEffectContainer().getCritDamageBonus(element, stellarReaction);
   }

   public float getHealingBonus() {
      float total = this.data.getEffectContainer().getHealingBonus();
      ItemStack weapon = this.data.getWeapon();
      if (weapon != null && !weapon.isEmpty() && weapon.getItem() instanceof WeaponItem weaponItem) {
         total += weaponItem.getHealingBonus();
      }

      return total;
   }

   public float getSovereigntyBonus() {
      return 0.0F;
   }

   public CharacterActionData getActionData() {
      return null;
   }

   public final ActionSet getActionSet(Player player) {
      String key = this.getActionStateKey(player);
      return this.actionSetCache.computeIfAbsent(key, k -> {
         SkillBase current = this.getSkill();
         ActionSet built = current != null ? current.buildActionSet(this, k) : null;
         return built != null ? built : this.buildFallbackActionSet();
      });
   }

   protected ActionSet buildFallbackActionSet() {
      return ActionSet.builder().build();
   }

   protected void invalidateActionSetCache() {
      this.actionSetCache.clear();
   }

   public String getTalentDebugInfo() {
      String skillPart = this.skill == null ? "null(class=" + this.getClass().getSimpleName() + ")" : this.skill.getClass().getSimpleName();
      return skillPart + " [talent=" + debugNameOf(this.talent) + ", constellation=" + debugNameOf(this.constellation) + "]";
   }

   private static String debugNameOf(Object collaborator) {
      return collaborator == null ? "null" : collaborator.getClass().getSimpleName();
   }

   public boolean canCast(Player player, ActionKind kind, int skillTime) {
      // 招式自己的门禁先问一次（基类那条「飞行中不能放技能 / 大招」就在里面）
      SkillBase current = this.getSkill();
      if (current != null && !current.canCast(player, kind)) {
         return false;
      }
      return switch (kind) {
         case ELEMENTAL_SKILL_TAP, ELEMENTAL_SKILL_HOLD -> this.canUseElementalSkill(player, skillTime);
         case ELEMENTAL_BURST -> this.canUseElementalBurst(player);
         default -> true;
      };
   }

   public void sendCastFailedMessage(Player player, ActionKind kind) {
      switch (kind) {
         case ELEMENTAL_SKILL_TAP:
         case ELEMENTAL_SKILL_HOLD:
            this.sendSkillCooldownMessage(player);
            break;
         case ELEMENTAL_BURST:
            this.sendBurstCooldownMessage(player);
      }
   }

   public boolean canUseElementalSkill(Player player, int skillTime) {
      return this.data.getElementalSkillCooldownTick() == 0.0F;
   }

   public void applyElementalSkillCooldown(Player player, int skillTime) {
      this.data.setElementalSkillStacks(this.data.getElementalSkillStacks() - 1);
      if (skillTime < 1000) {
         this.data.setElementalSkillCooldownTick(this.data.getSkillShortMaxCooldownTick());
      } else {
         this.data.setElementalSkillCooldownTick(this.data.getSkillLongMaxCooldownTick());
      }

      this.syncRealtimeState();
   }

   public void sendSkillCooldownMessage(Player player) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.skill_cooldown", new Object[]{this.data.getElementalSkillCooldownTick()}));
   }

   public boolean canUseElementalBurst(Player player) {
      return this.data.getElementalBurstCooldownTick() > 0.0F ? false : this.data.getCurrentObtainingEnergy() >= this.data.getMaxObtainingEnergy();
   }

   public void applyElementalBurstCooldown(Player player) {
      this.data.setCurrentObtainingEnergy(0.0F);
      this.data.setElementalBurstCooldownTick(this.data.getBurstMaxCooldownTick());
      this.syncRealtimeState();
   }

   public void sendBurstCooldownMessage(Player player) {
      if (this.data.getElementalBurstCooldownTick() > 0.0F) {
         player.sendSystemMessage(Component.translatable("message.minegenshin.skill_cooldown"));
      } else {
         player.sendSystemMessage(Component.translatable("message.minegenshin.not_enough_energy"));
      }
   }

   public void performNormalAttack(Player player, int comboStage) {
      SkillBase current = this.getSkill();
      if (current != null) {
         current.attack(player, this, comboStage);
      }

      if (!player.level().isClientSide() && this.spawnsNormalAttackParticle()) {
         this.trySpawnNormalAttackParticle(player);
      }
   }

   public boolean spawnsNormalAttackParticle() {
      return true;
   }

   public void trySpawnNormalAttackParticle(Player player) {
      if (!(player.level().getRandom().nextFloat() >= 0.5F)) {
         GenshinElement element = this.getElemental();
         if (element != null && element != ModElements.FYSIKOS.get()) {
            new ElementalOrbSpawner(player.level(), element, 1, true, player.position()).execute();
         }
      }
   }

   public void performChargedAttack(Player player) {
      SkillBase current = this.getSkill();
      if (current != null) {
         current.chargeAttack(player, this);
      }
   }

   /**
    * 下落攻击的<b>落地结算</b>——由服务端的落地检测调一次。
    *
    * <p>用哪个技能、怎么打，全在 {@link SkillBase#plungingAttack}（大部分角色就是基类那份）；
    * 这里只负责转发，和 {@link #performNormalAttack} / {@link #performChargedAttack} 同一套路子。
    */
   public void performPlungingAttack(Player player) {
      SkillBase current = this.getSkill();
      if (current != null) {
         current.plungingAttack(player, this);
      }
   }

   /** 下落攻击播放的动画名（没接技能时用基类默认名）。 */
   public String getPlungingAnimation() {
      SkillBase current = this.getSkill();
      return current != null ? current.plungingAnimation() : SkillBase.DEFAULT_PLUNGING_ANIM;
   }

   /** 下落攻击的加速下坠速度（格 / 刻，<b>正数 = 向下</b>）。 */
   public double getPlungingFallSpeed() {
      SkillBase current = this.getSkill();
      return current != null ? current.plungingFallSpeed() : SkillBase.DEFAULT_PLUNGING_FALL_SPEED;
   }

   /** 二连跳之后、起飞之前那段前摇的刻数。 */
   public int getFlyStartTicks() {
      SkillBase current = this.getSkill();
      return current != null ? current.flyStartTicks() : SkillBase.DEFAULT_FLY_START_TICKS;
   }

   /** 起飞前摇播的动画名。 */
   public String getFlyStartAnimation() {
      SkillBase current = this.getSkill();
      return current != null ? current.flyStartAnimation() : SkillBase.DEFAULT_FLY_START_ANIM;
   }

   public int getChargedAttackChargeTicks() {
      SkillBase current = this.getSkill();
      return current != null ? current.getChargeTicks() : 20;
   }

   public boolean isSustainedChargedAttack() {
      SkillBase current = this.getSkill();
      return current != null && current.isSustainedChargedAttack();
   }

   public int getChargedAttackMaxTicks() {
      SkillBase current = this.getSkill();
      return current != null ? current.getChargedAttackMaxTicks() : 0;
   }

   public void frontTick(Player player) {
   }

   public void backTick(Player player) {
   }

   public void tick(Player player) {
      this.data.tick();
      this.recalculateDirtyArtifactSlots();
      this.frontTick(player);
      this.backTick(player);
      ActionManager.get(player).tick(player, this);
      if (!player.level().isClientSide()) {
         this.syncRealtimeState();
      }
   }

   public void equipArtifact(ArtifactType type, ItemStack artifactStack) {
      if (artifactStack.getItem() instanceof ArtifactItem artifactItem) {
         if (artifactItem.getType() != type) {
            return;
         }

         int slot = ArtifactInventory.typeToSlot(type);
         this.data.getArtifactInventory().setItem(slot, artifactStack.copy());
      }
   }

   public void unequipArtifact(ArtifactType type) {
      int slot = ArtifactInventory.typeToSlot(type);
      this.data.getArtifactInventory().setItem(slot, ItemStack.EMPTY);
   }

   public void recalculateDirtyArtifactSlots() {
      ArtifactInventory inv = this.data.getArtifactInventory();
      if (inv.hasDirtySlots()) {
         for (int i = 0; i < inv.slotCount(); i++) {
            if (inv.isDirty(i)) {
               this.recalculateArtifactSlot(i);
               inv.clearDirty(i);
            }
         }

         this.refreshArtifactSetEffects();
      }
   }

   private void recalculateArtifactSlot(int slotIndex) {
      if (slotIndex >= 5) {
         this.recalculateWeaponSlot();
      } else {
         ArtifactType type = ArtifactInventory.slotToType(slotIndex);
         String source = null;
         if (type != null) {
            source = type.name().toLowerCase();
         }

         for (AttributeType attrType : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
            this.data.removeAttributeModifier(attrType, source);
         }

         ItemStack stack = this.data.getArtifactInventory().getItem(slotIndex);
         if (!stack.isEmpty() && stack.getItem() instanceof ArtifactItem) {
            ArtifactStatsComponent stats = (ArtifactStatsComponent)stack.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            this.applyStatWithSet(stats.mainStat, source);

            for (TeyvatItemStat subStat : stats.subStats) {
               if (subStat.isUnlocked()) {
                  this.applyStatWithSet(subStat, source);
               }
            }
         }
      }
   }

   public void recalculateWeaponSlot() {
      for (AttributeType attrType : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
         this.data.removeAttributeModifier(attrType, "weapon");
      }

      this.data.removeAttributeBaseValue((AttributeType)ModAttributes.ATK.get(), "weapon");
      this.data.setWeaponBaseATK(0.0);
      ItemStack stack = this.data.getWeapon();
      if (!stack.isEmpty() && stack.getItem() instanceof WeaponItem weapon) {
         WeaponStatsComponent stats = (WeaponStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
         );
         if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            double mainValue = stats.mainStat.getValue() + weapon.getMainStatDelta();
            this.data.setWeaponBaseATK(mainValue);
            this.data.setAttributeBaseValue((AttributeType)ModAttributes.ATK.get(), "weapon", mainValue);
         }

         if (stats.subStat != null && stats.subStat.isInitialized()) {
            AttributeType attr = stats.subStat.getAttribute();
            double value = stats.subStat.getValue();
            if (isBaseAttribute(attr)) {
               this.data.setAttributePercentModifier(attr, "weapon", value);
            } else {
               this.data.setAttributeFlatModifier(attr, "weapon", value);
            }
         }
      }
   }

   private void applyStatWithSet(TeyvatItemStat stat, String source) {
      if (stat.isInitialized()) {
         AttributeType attr = stat.getAttribute();
         double value = stat.getValue();
         boolean isBaseAttr = isBaseAttribute(attr);
         if (isBaseAttr) {
            if (stat.getKind() == TeyvatItemStat.StatKind.FLAT) {
               this.data.setAttributeFlatModifier(attr, source, value);
            } else {
               this.data.setAttributePercentModifier(attr, source, value);
            }
         } else {
            this.data.setAttributeFlatModifier(attr, source, value);
         }
      }
   }

   private static boolean isBaseAttribute(AttributeType attr) {
      if (attr != null && attr.id() != null) {
         AttributeType registryAttr = (AttributeType)ModRegistries.ATTRIBUTE_TYPE_REGISTRY.getValue(attr.id());
         return registryAttr == ModAttributes.MAX_HP.get() || registryAttr == ModAttributes.ATK.get() || registryAttr == ModAttributes.DEF.get();
      } else {
         return false;
      }
   }

   private void refreshArtifactSetEffects() {
      CharacterEffectContainer container = this.data.getEffectContainer();

      for (ICharacterEffect effect : container.getEffects()
         .stream()
         .map(CharacterEffectInstance::getEffect)
         .filter(effectx -> effectx instanceof ArtifactSetEffect)
         .toList()) {
         CharacterEffectHelper.removeEffect(this.data.getOwnerPlayer(), this, effect);
      }

      Map<ArtifactSet, Integer> setCountMap = new HashMap<>();

      for (ItemStack stack : this.data.getAllArtifactsAsList()) {
         if (stack.getItem() instanceof ArtifactItem art) {
            ArtifactSet set = (ArtifactSet)art.getSet().get();
            setCountMap.merge(set, 1, Integer::sum);
         }
      }

      setCountMap.forEach((set, count) -> {
         if (count >= 2) {
            CharacterEffectInstance inst = new CharacterEffectInstance((ICharacterEffect)set.twoPcEffect().get(), -1, 1, true);
            CharacterEffectHelper.addEffect(this.data.getOwnerPlayer(), this, inst);
         }

         if (count >= 4 && set.hasFourPcEffect()) {
            CharacterEffectInstance inst = new CharacterEffectInstance((ICharacterEffect)set.fourPcEffect().get(), -1, 1, true);
            CharacterEffectHelper.addEffect(this.data.getOwnerPlayer(), this, inst);
         }

         this.data.syncEffectsToTag();
      });
   }

   public void hurt(float amount) {
      double before = this.data.getCurrentHP();
      this.data.hurtHP(amount);
      this.syncRealtimeState();
      if (this.data.getCurrentHP() <= 0.0 && before > 0.0) {
         this.incapacitate();
      }
   }

   public void incapacitate() {
      Player player = this.data.getOwnerPlayer();
      if (player != null) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         int currentIndex = attachment.getCurrentCharacterIndex();

         for (int offset = 1; offset <= 4; offset++) {
            int nextIndex = (currentIndex + offset) % 4;
            PGCharacter nextChar = attachment.getPartyCharacter(nextIndex);
            if (nextChar != null && nextChar.getData().getCurrentHP() > 0.0) {
               attachment.setCurrentCharacterIndex(nextIndex);
               if (player instanceof ServerPlayer sp) {
                  NetworkManager.setCharacterSelectionToPlayer(sp, nextIndex);
               }

               return;
            }
         }

         player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, false);
         if (player instanceof ServerPlayer sp) {
            NetworkManager.setGenshinModeToPlayer(sp, false);
         }

         PlayerStatBridge.onGenshinModeChanged(player, false);
      }
   }

   public void revive(int hp) {
      float maxHp = (float)this.data.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value());
      this.data.setCurrentHP(Math.min(hp, maxHp));
      this.enableDeployIfNeeded();
      this.syncRealtimeState();
   }

   public void revive(float percent) {
      float maxHp = (float)this.data.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value());
      this.data.setCurrentHP(Math.min(maxHp * percent, maxHp));
      this.enableDeployIfNeeded();
      this.syncRealtimeState();
   }

   public void revive(float percent, int extraHp) {
      float maxHp = (float)this.data.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value());
      this.data.setCurrentHP(Math.min(maxHp * percent + extraHp, maxHp));
      this.enableDeployIfNeeded();
      this.syncRealtimeState();
   }

   private void enableDeployIfNeeded() {
      if (!(this.data.getCurrentHP() <= 0.0)) {
         Player player = this.data.getOwnerPlayer();
         if (player != null) {
            Boolean genshinMode = (Boolean)player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
            if (!genshinMode) {
               player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, true);
               if (player instanceof ServerPlayer sp) {
                  NetworkManager.setGenshinModeToPlayer(sp, true);
               }
            }
         }
      }
   }

   public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
      return Map.of();
   }

   public int getStatAtLevel(AttributeType type, int levelIndex) {
      Supplier<List<? extends Integer>> supplier = this.getStatGrowthMap().get(type.id());
      if (supplier == null) {
         return 0;
      }

      List<? extends Integer> list = supplier.get();
      return levelIndex >= 0 && levelIndex < list.size() ? list.get(levelIndex) : 0;
   }

   public double getBaseStat(AttributeType type) {
      Supplier<List<? extends Integer>> supplier = this.getStatGrowthMap().get(type.id());
      if (supplier == null) {
         return type.defaultValue();
      }

      List<? extends Integer> list = supplier.get();
      return list.isEmpty() ? type.defaultValue() : list.getFirst().intValue();
   }

   public Set<AttributeType> getStatGrowthTypes() {
      return this.getStatGrowthMap().keySet().stream().map(this::resolveType).collect(Collectors.toSet());
   }

   public void addExp(int amount) {
      if (this.data.getLevel() < 90) {
         List<Integer> expList = CharacterXpConfig.getAllXp();
         long totalMaxExp = 0L;

         for (int i = 0; i < 89; i++) {
            totalMaxExp += expList.get(i).intValue();
         }

         long currentSpent = 0L;

         for (int i = 0; i < this.data.getLevel() - 1; i++) {
            currentSpent += expList.get(i).intValue();
         }

         long remaining = totalMaxExp - currentSpent - this.data.getCurrentExp();
         if (amount > remaining) {
            this.data.setCurrentExp(this.data.getCurrentExp() + (int)remaining);
         } else {
            this.data.setCurrentExp(this.data.getCurrentExp() + amount);
         }

         this.tryLevelUp();
      }
   }

   public long characterExpRoom() {
      List<Integer> expList = CharacterXpConfig.getAllXp();
      int cap = this.data.getAscensionPhase() == 0 ? 20 : Math.min((this.data.getAscensionPhase() + 3) * 10, 90);
      long room = 0L;
      int level = this.data.getLevel();
      int current = Math.max(0, this.data.getCurrentExp());

      while (level < cap) {
         int need = Math.max(1, expList.get(Math.max(0, Math.min(level - 1, expList.size() - 1))));
         room += Math.max(0, need - current);
         current = 0;
         level++;
      }

      return room;
   }

   public long weaponExpRoom() {
      ItemStack weapon = this.data.getWeapon();
      if (!(weapon.getItem() instanceof WeaponItem item)) {
         return 0L;
      } else {
         WeaponStatsComponent var9 = (WeaponStatsComponent)weapon.getOrDefault(
            (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
         );
         long room = 0L;

         for (int lv = var9.level; lv < var9.getMaxLevel(); lv++) {
            long need = WeaponLevelData.getExpToNextLevel(item.getStar(), lv);
            if (need <= 0L) {
               break;
            }

            room += Math.max(0L, need - (lv == var9.level ? var9.exp : 0));
         }

         return room;
      }
   }

   public long artifactExpRoom(int slot) {
      ItemStack artifact = this.data.getArtifactInventory().getItem(slot);
      if (!(artifact.getItem() instanceof ArtifactItem item)) {
         return 0L;
      } else {
         ArtifactStatsComponent stats = (ArtifactStatsComponent)artifact.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         if (!stats.activated) {
            return 0L;
         }

         long room = 0L;
         int maxLevel = stats.getMaxLevel(item.getStar());

         for (int lv = stats.level; lv < maxLevel; lv++) {
            long need = ArtifactLevelData.getExpToNextLevel(item.getStar(), lv);
            if (need <= 0L) {
               break;
            }

            room += Math.max(0L, need - (lv == stats.level ? stats.exp : 0));
         }

         return room;
      }
   }

   public void tryLevelUp() {
      List<Integer> expList = CharacterXpConfig.getAllXp();
      int totalExpConsumed = 0;
      int levelsToGain = 0;
      int oldLevel = this.data.getLevel();
      int currentAscensionPhase = this.data.getAscensionPhase();

      while (oldLevel < 90) {
         int expNeeded = expList.get(oldLevel - 1);
         if (this.data.getCurrentExp() - totalExpConsumed < expNeeded) {
            break;
         }

         int maxLevelForPhase = currentAscensionPhase == 0 ? 20 : Math.min((currentAscensionPhase + 3) * 10, 90);
         if (oldLevel >= maxLevelForPhase) {
            break;
         }

         totalExpConsumed += expNeeded;
         levelsToGain++;
         oldLevel++;
      }

      if (levelsToGain != 0) {
         this.data.setCurrentExp(this.data.getCurrentExp() - totalExpConsumed);
         this.data.addLevel(levelsToGain);
         int statIndex = this.data.getLevel() - 1 + this.data.getAscensionPhase();
         this.updateBaseStatsFromConfig(statIndex);
         this.data.setCurrentHP(this.data.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value()));
         if (this.data.getLevel() < 90) {
            this.data.setMaxExp(expList.get(this.data.getLevel() - 1));
         }
      }
   }

   public void ascend() {
      int maxLevelForPhase = this.data.getAscensionPhase() == 0 ? 20 : Math.min((this.data.getAscensionPhase() + 3) * 10, 90);
      if (this.data.getLevel() == maxLevelForPhase) {
         int newPhase = this.data.getAscensionPhase() + 1;
         this.data.setAscensionPhase(newPhase);
         int statIndex = maxLevelForPhase - 1 + newPhase;
         this.updateBaseStatsFromConfig(statIndex);
         int starRating = this.getStarRating();
         CharacterAscendAttribute ascendAttr = this.getAscendAttribute();
         AttributeType targetAttrType = this.getAscendAttributeType(ascendAttr);
         if (CharacterAscendAttribute.PERCENT_STATS.contains(ascendAttr)) {
            this.data.removeAttributeModifier(targetAttrType, "ascension_bonus");
         } else {
            this.data.removeAttributeBaseValue(targetAttrType, "character_ascend");
         }

         int bonusCount = getAscensionBonusCount(newPhase);
         if (bonusCount > 0) {
            this.applyAscendBonus(ascendAttr, targetAttrType, starRating, bonusCount);
         }

         this.tryLevelUp();
      }
   }

   private void applyAscendBonus(CharacterAscendAttribute attr, AttributeType type, int starRating, int bonusCount) {
      int factor = starRating + 1;
      String baseKey = "character_ascend";
      switch (attr) {
         case ATK:
         case HP:
            this.data.addAttributePercentModifier(type, "ascension_bonus", 0.012F * factor * bonusCount);
            break;
         case DEF:
            this.data.addAttributePercentModifier(type, "ascension_bonus", 0.015F * factor * bonusCount);
            break;
         case CR:
            this.data.setAttributeBaseValue(type, baseKey, 0.008 * factor * bonusCount);
            break;
         case CDG:
            this.data.setAttributeBaseValue(type, baseKey, 0.016 * factor * bonusCount);
            break;
         case HB:
            this.data.setAttributeBaseValue(type, baseKey, (starRating == 5 ? 0.056 : 0.047) * bonusCount);
            break;
         case ELEMENTAL_BONUS:
            this.data.setAttributeBaseValue(type, baseKey, 0.012 * factor * bonusCount);
            break;
         case EM:
            this.data.setAttributeBaseValue(type, baseKey, (double)(starRating == 5 ? 29 : 24) * bonusCount);
            break;
         case ER:
            this.data.setAttributeBaseValue(type, baseKey, 0.013333 * factor * bonusCount);
      }
   }

   private static int getAscensionBonusCount(int phase) {
      return switch (phase) {
         case 0, 1 -> 0;
         case 2 -> 1;
         case 3, 4 -> 2;
         case 5 -> 3;
         case 6 -> 4;
         default -> 0;
      };
   }

   private AttributeType getAscendAttributeType(CharacterAscendAttribute ascendAttr) {
      return switch (ascendAttr) {
         case ATK -> (AttributeType)ModAttributes.ATK.value();
         case HP -> (AttributeType)ModAttributes.MAX_HP.value();
         case DEF -> (AttributeType)ModAttributes.DEF.value();
         case CR -> (AttributeType)ModAttributes.CR.value();
         case CDG -> (AttributeType)ModAttributes.CDG.value();
         case HB -> (AttributeType)ModAttributes.HB.value();
         case ELEMENTAL_BONUS -> this.getElementalDamageBonusType();
         case EM -> (AttributeType)ModAttributes.ELEMENTAL_MASTERY.value();
         case ER -> (AttributeType)ModAttributes.ER.value();
      };
   }

   private AttributeType getElementalDamageBonusType() {
      GenshinElement element = this.getElemental();
      if (element == ModElements.PYRO.get()) {
         return (AttributeType)ModAttributes.PYRO_BONUS.value();
      } else if (element == ModElements.HYDRO.get()) {
         return (AttributeType)ModAttributes.HYDRO_BONUS.value();
      } else if (element == ModElements.DENDRO.get()) {
         return (AttributeType)ModAttributes.DENDRO_BONUS.value();
      } else if (element == ModElements.ELECTRO.get()) {
         return (AttributeType)ModAttributes.ELECTRO_BONUS.value();
      } else if (element == ModElements.ANEMO.get()) {
         return (AttributeType)ModAttributes.ANEMO_BONUS.value();
      } else if (element == ModElements.CYRO.get()) {
         return (AttributeType)ModAttributes.CYRO_BONUS.value();
      } else if (element == ModElements.GEO.get()) {
         return (AttributeType)ModAttributes.GEO_BONUS.value();
      } else {
         return element == ModElements.FYSIKOS.get()
            ? (AttributeType)ModAttributes.PHYSICAL_BONUS.value()
            : (AttributeType)ModAttributes.PHYSICAL_BONUS.value();
      }
   }

   public void upgradeNormalAttack() {
      this.data.upgradeNormalAttack();
   }

   public void upgradeElementalSkill() {
      this.data.upgradeElementalSkill();
   }

   public void upgradeElementalBurst() {
      this.data.upgradeElementalBurst();
   }

   public GenshinElement getElemental() {
      if (this.elemental != null) {
         return this.elemental;
      } else if (this.elementalId != null && !this.elementalId.isEmpty()) {
         String[] parts = this.elementalId.split(":", 2);
         Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
         this.elemental = com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY.get(id).<GenshinElement>map(Reference::value).orElse(null);
         return this.elemental;
      } else {
         return (GenshinElement)ModElements.FYSIKOS.get();
      }
   }

   public int getSkillShortMaxCooldownTick() {
      return this.data.getSkillShortMaxCooldownTick();
   }

   public int getSkillLongMaxCooldownTick() {
      return this.data.getSkillLongMaxCooldownTick();
   }

   public int getBurstMaxCooldownTick() {
      return this.data.getBurstMaxCooldownTick();
   }

   public float getMaxObtainingEnergy() {
      return this.data.getMaxObtainingEnergy();
   }

   public float getSkillDisplayCooldown() {
      return this.data.getElementalSkillCooldownTick();
   }

   public int getSkillDisplayMaxCooldown() {
      return this.data.getSkillShortMaxCooldownTick();
   }

   public float getBurstDisplayCooldown() {
      return this.data.getElementalBurstCooldownTick();
   }

   public int getBurstDisplayMaxCooldown() {
      return this.data.getBurstMaxCooldownTick();
   }

   private void updateBaseStatsFromConfig(int statIndex) {
      for (AttributeType type : this.getStatGrowthTypes()) {
         int value = this.getStatAtLevel(type, statIndex);
         this.data.setAttributeBaseValue(type, value);
      }
   }

   public void syncRealtimeState() {
      this.data.syncToClient();
      this.syncToClient();
   }

   public int getConstellation() {
      return this.data.getConstellation();
   }

   public boolean hasConstellation(int level) {
      return this.data.getConstellation() >= level;
   }

   public boolean isConstellationMax() {
      return this.data.getConstellation() >= 6;
   }

   public boolean addConstellation() {
      if (!this.data.upgradeConstellation()) {
         return false;
      }

      this.syncRealtimeState();
      return true;
   }

   public void setConstellation(int level) {
      this.data.setConstellation(level);
      this.syncRealtimeState();
   }

   public int getAppearance() {
      return this.data.getAppearance();
   }

   public void setAppearance(int mask) {
      this.data.setAppearance(mask);
      this.syncRealtimeState();
   }

   @Generated
   public int getCharacterUUID() {
      return this.characterUUID;
   }

   @Generated
   public void setCharacterUUID(int characterUUID) {
      this.characterUUID = characterUUID;
   }

   @Generated
   public int getStarRating() {
      return this.starRating;
   }

   @Generated
   public void setStarRating(int starRating) {
      this.starRating = starRating;
   }

   @Generated
   public Component getName() {
      return this.name;
   }

   @Generated
   public void setName(Component name) {
      this.name = name;
   }

   @Generated
   public CharacterAscendAttribute getAscendAttribute() {
      return this.ascendAttribute;
   }

   @Generated
   public void setAscendAttribute(CharacterAscendAttribute ascendAttribute) {
      this.ascendAttribute = ascendAttribute;
   }

   @Generated
   public String getTextureId() {
      return this.textureId;
   }

   @Generated
   public void setTextureId(String textureId) {
      this.textureId = textureId;
   }

   @Generated
   public PGCharacterData getData() {
      return this.data;
   }

   @Generated
   public void setData(PGCharacterData data) {
      this.data = data;
   }
}
