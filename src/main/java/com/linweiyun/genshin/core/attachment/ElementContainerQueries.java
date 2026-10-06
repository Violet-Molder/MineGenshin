package com.linweiyun.genshin.core.attachment;

import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.status.StatusInstance;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.CharacterKeys;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 状态容器上的角色查询 —— elementlib 只保留元素本身的容器能力，角色溯源放在本模组。
 */
public final class ElementContainerQueries {

    private ElementContainerQueries() {
    }

    /** 所有尚有附着量的角色（按元素过滤；不传元素则返回全部）。 */
    public static Set<PGCharacter> getActiveContributors(StatusContainer container, ServerLevel level,
                                                         long currentTick, GenshinElement... elements) {
        Set<PGCharacter> result = new LinkedHashSet<>();
        if (container == null || level == null) {
            return result;
        }
        Set<GenshinElement> filter = elements.length == 0 ? null : Set.of(elements);
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getDecayEndTick() <= currentTick) continue;
            GenshinElement el = ea.getElement();
            if (el == null) continue;
            if (filter != null && !filter.contains(el)) continue;
            addKey(result, level, ea.getSourceCharacterKey());
            if (ea.getFrozenCyroSourceKeys() != null) {
                for (String k : ea.getFrozenCyroSourceKeys()) addKey(result, level, k);
            }
            if (ea.getFrozenHydroSourceKeys() != null) {
                for (String k : ea.getFrozenHydroSourceKeys()) addKey(result, level, k);
            }
        }
        return result;
    }

    /** 最后一个附着指定元素的角色（用于伤害源）。 */
    @Nullable
    public static PGCharacter getLastAttacher(StatusContainer container, ServerLevel level,
                                              long currentTick, GenshinElement... elements) {
        if (container == null || level == null) {
            return null;
        }
        PGCharacter last = null;
        long lastTick = 0;
        Set<GenshinElement> filter = elements.length == 0 ? null : Set.of(elements);
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getDecayEndTick() <= currentTick) continue;
            if (filter != null && !filter.contains(ea.getElement())) continue;
            if (ea.getAttachTick() > lastTick) {
                last = CharacterKeys.resolve(level, ea.getSourceCharacterKey());
                lastTick = ea.getAttachTick();
            }
        }
        return last;
    }

    private static void addKey(Set<PGCharacter> result, ServerLevel level, @Nullable String key) {
        PGCharacter character = CharacterKeys.resolve(level, key);
        if (character != null) {
            result.add(character);
        }
    }

    /** 冻结源键（冻元素记着「谁冻的」）—— 反应里要把贡献者算进去。 */
    public static List<String> sourceKeysOf(ElementalAttachmentInstance instance) {
        return instance.getSourceCharacterKey() == null
                ? List.of()
                : List.of(instance.getSourceCharacterKey());
    }
}
