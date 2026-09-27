package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.attachment.CharacterAttachment;
import com.linweiyun.genshin.core.character.attachment.CharacterAttachmentType;
import com.linweiyun.genshin.core.character.attachment.ModCharacterAttachmentTypes;
import com.linweiyun.genshin.core.character.catalyst.columbina.Columbina;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.Linweiyun;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.Vodyanitsa;
import com.linweiyun.genshin.core.character.polearm.arlecchino.Arlecchino;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.RaidenShogun;
import com.linweiyun.genshin.core.character.polearm.shenhe.Shenhe;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ModCharacters {

    public static final DeferredRegister<PGCharacter> CHARACTERS = ModRegistries.CHARACTERS;

    private static final Map<Integer, Supplier<PGCharacter>> FACTORIES = new LinkedHashMap<>();
    private static final Map<Identifier, Supplier<PGCharacter>> FACTORIES_BY_ID = new LinkedHashMap<>();

    public static final DeferredHolder<PGCharacter, Shenhe> SHENHE = register("shenhe", 135001, Shenhe::new);
    public static final DeferredHolder<PGCharacter, Arlecchino> ARLECCHINO = register("arlecchino", 135002, Arlecchino::new);
    public static final DeferredHolder<PGCharacter, Columbina> COLUMBINA = register("columbina", 145001, Columbina::new);
    public static final DeferredHolder<PGCharacter, RaidenShogun> RAIDEN_SHOGUN = register("raiden_shogun", 135003, RaidenShogun::new);
    public static final DeferredHolder<PGCharacter, Vesna> VESNA = register(Vesna.ID, Vesna.UID, Vesna::new);
    /** 沃雅妮莎（水系五星法器，突破属性生命值）。 */
    public static final DeferredHolder<PGCharacter, Vodyanitsa> VODYANITSA =
            register(Vodyanitsa.ID, Vodyanitsa.UID, Vodyanitsa::new);
    /**
     * 林薇云 —— 全武器类，同时是<b>兜底角色</b>：别的角色缺自己的资源 / 配置页 / 天赋时回退到她。
     */
    public static final DeferredHolder<PGCharacter, Linweiyun> LINWEIYUN =
            register(Linweiyun.ID, Linweiyun.UID, Linweiyun::new);

    @SuppressWarnings("unchecked")
    private static <T extends PGCharacter> DeferredHolder<PGCharacter, T> register(
            String name, int uuid, Supplier<T> factory) {
        DeferredHolder<PGCharacter, T> holder = CHARACTERS.register(name, factory);
        FACTORIES.put(uuid, (Supplier<PGCharacter>) factory);
        FACTORIES_BY_ID.put(Minegenshin.id(name), (Supplier<PGCharacter>) factory);
        return holder;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T extends PGCharacter> DeferredHolder<PGCharacter, T> register(
            String name, int uuid, Supplier<T> factory,
            Class<? extends CharacterAttachment>... attachmentClasses) {
        DeferredHolder<PGCharacter, T> holder = register(name, uuid, factory);
        for (Class<? extends CharacterAttachment> clazz : attachmentClasses) {
            try {
                String typeId = (String) clazz.getField("TYPE_ID").get(null);
                Supplier<CharacterAttachment> attachmentFactory = () -> {
                    try {
                        return clazz.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to instantiate attachment: " + clazz.getName(), e);
                    }
                };
                ModCharacterAttachmentTypes.CHARACTER_ATTACHMENT_TYPES_REGISTER.register(
                        typeId, () -> new CharacterAttachmentType<>(typeId, attachmentFactory));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Auto-register attachment failed: " + clazz.getName(), e);
            }
        }
        return holder;
    }

    public static PGCharacter getByUUID(int uuid) {
        Supplier<PGCharacter> factory = FACTORIES.get(uuid);
        return factory != null ? createWithBaseStats(factory) : null;
    }

    public static PGCharacter getById(Identifier id) {
        Supplier<PGCharacter> factory = FACTORIES_BY_ID.get(id);
        return factory != null ? createWithBaseStats(factory) : null;
    }

    private static PGCharacter createWithBaseStats(Supplier<PGCharacter> factory) {
        PGCharacter instance = factory.get();
        Double baseHP = null, baseATK = null, baseDEF = null;
        for (Map.Entry<Identifier, Supplier<List<? extends Integer>>> entry : instance.getStatGrowthMap().entrySet()) {
            List<? extends Integer> list = entry.getValue().get();
            if (list == null || list.isEmpty()) continue;
            double first = list.get(0);
            Identifier key = entry.getKey();
            if (key.equals(ModAttributes.MAX_HP.getId())) baseHP = first;
            else if (key.equals(ModAttributes.ATK.getId())) baseATK = first;
            else if (key.equals(ModAttributes.DEF.getId())) baseDEF = first;
        }
        if (baseHP != null && baseATK != null && baseDEF != null) {
            instance.getData().initBaseStats(baseHP, baseATK, baseDEF);
        }
        return instance;
    }

    public static Collection<PGCharacter> getAllCharacters() {
        return CHARACTERS.getRegistry().get().stream().toList();
    }

    public static void register(IEventBus bus) {
        CHARACTERS.register(bus);
    }
}
