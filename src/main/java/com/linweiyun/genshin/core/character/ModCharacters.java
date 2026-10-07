// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.Linweiyun;
import com.linweiyun.genshin.core.character.catalyst.columbina.Columbina;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.Vodyanitsa;
import com.linweiyun.genshin.core.character.polearm.arlecchino.Arlecchino;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.RaidenShogun;
import com.linweiyun.genshin.core.character.polearm.shenhe.Shenhe;
import com.linweiyun.genshin.core.character.claymore.sandrone.SandroneCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCharacters {
   public static final DeferredRegister<PGCharacter> CHARACTERS = ModRegistries.CHARACTERS;
   private static final Map<Integer, Supplier<PGCharacter>> FACTORIES = new LinkedHashMap<>();

   private static final Map<ResourceLocation, Supplier<PGCharacter>> FACTORIES_BY_ID = new LinkedHashMap<>();
   public static final DeferredHolder<PGCharacter, Shenhe> SHENHE = register("shenhe", 135001, Shenhe::new);
   public static final DeferredHolder<PGCharacter, Arlecchino> ARLECCHINO = register("arlecchino", 135002, Arlecchino::new);
   public static final DeferredHolder<PGCharacter, Columbina> COLUMBINA = register("columbina", 145001, Columbina::new);
   public static final DeferredHolder<PGCharacter, RaidenShogun> RAIDEN_SHOGUN = register("raiden_shogun", 135003, RaidenShogun::new);
   public static final DeferredHolder<PGCharacter, Vesna> VESNA = register("vesna", 115001, Vesna::new);
   public static final DeferredHolder<PGCharacter, Vodyanitsa> VODYANITSA = register("vodyanitsa", 145002, Vodyanitsa::new);
    public static final DeferredHolder<PGCharacter, Linweiyun> LINWEIYUN = register("linweiyun", 105001, Linweiyun::new);
    /** 测试用长柄角色（135009）—— 数值 / 资源 / 技能全部照抄林薇云，见 {@link SandroneCharacter}。 */
    public static final DeferredHolder<PGCharacter, SandroneCharacter> TEST = register(SandroneCharacter.ID, SandroneCharacter.UID, SandroneCharacter::new);

    private static <T extends PGCharacter> DeferredHolder<PGCharacter, T> register(String name, int uuid, Supplier<T> factory) {
      DeferredHolder<PGCharacter, T> holder = CHARACTERS.register(name, factory);
      FACTORIES.put(uuid, () -> factory.get());
      FACTORIES_BY_ID.put(Minegenshin.id(name), () -> factory.get());
      return holder;
   }

   public static PGCharacter getByUUID(int uuid) {
      Supplier<PGCharacter> factory = FACTORIES.get(uuid);
      return factory != null ? createWithBaseStats(factory) : null;
   }

   public static PGCharacter getById(ResourceLocation id) {
      Supplier<PGCharacter> factory = FACTORIES_BY_ID.get(id);
      return factory != null ? createWithBaseStats(factory) : null;
   }

   private static PGCharacter createWithBaseStats(Supplier<PGCharacter> factory) {
      PGCharacter instance = factory.get();
      Double baseHP = null;
      Double baseATK = null;
      Double baseDEF = null;

      for (Entry<ResourceLocation, Supplier<List<? extends Integer>>> entry : instance.getStatGrowthMap().entrySet()) {
         List<? extends Integer> list = entry.getValue().get();
         if (list != null && !list.isEmpty()) {
            double first = list.get(0).intValue();
            ResourceLocation key = entry.getKey();
            if (key.equals(ModAttributes.MAX_HP.getId())) {
               baseHP = first;
            } else if (key.equals(ModAttributes.ATK.getId())) {
               baseATK = first;
            } else if (key.equals(ModAttributes.DEF.getId())) {
               baseDEF = first;
            }
         }
      }

      if (baseHP != null && baseATK != null && baseDEF != null) {
         instance.getData().initBaseStats(baseHP, baseATK, baseDEF);
      }

      return instance;
   }

   public static Collection<PGCharacter> getAllCharacters() {
      return ((Registry)CHARACTERS.getRegistry().get()).stream().toList();
   }

   public static void register(IEventBus bus) {
      CHARACTERS.register(bus);
   }
}