package com.linweiyun.genshin.core.system.about.host;

import com.linweiyun.elementlib.core.module.ElibModuleContainer;
import com.linweiyun.elementlib.core.module.ElibModuleData;
import com.linweiyun.elementlib.core.module.ElibModuleHost;
import com.linweiyun.elementlib.core.module.ElibModuleTargetKind;
import com.linweiyun.elementlib.core.module.ElibModuleType;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.module.MinegenshinModuleKinds;
import org.jetbrains.annotations.Nullable;

/** 角色宿主：容器挂在 PGCharacterData 上。 */
public final class CharacterModuleHost implements ElibModuleHost {

    private final PGCharacter character;

    public CharacterModuleHost(PGCharacter character) {
        this.character = character;
    }

    @Override
    public boolean isValid() {
        return character.getData() != null;
    }

    @Override
    public ElibModuleTargetKind kind() {
        return MinegenshinModuleKinds.CHARACTER;
    }

    @Override
    public String hostKey() {
        return "character:" + character.getCharacterUUID() + "::" + character.getClass().getSimpleName();
    }

    @Override
    @Nullable
    public ElibModuleContainer container() {
        PGCharacterData data = character.getData();
        return data == null ? null : data.getModuleContainer();
    }

    /** @Persisted 字段：原地改即随角色数据保存。 */
    @Override
    public void commit(ElibModuleContainer container) {
    }

    @Override
    @Nullable
    public <T extends ElibModuleData> T get(ElibModuleType<T> type) {
        ElibModuleContainer container = container();
        return container == null ? null : container.get(type);
    }

    @Override
    @Nullable
    public <T extends ElibModuleData> T ensure(ElibModuleType<T> type) {
        if (!type.supports(kind())) {
            return null;
        }
        ElibModuleContainer container = container();
        return container == null ? null : container.ensure(type);
    }

    @Override
    public String toString() {
        return hostKey();
    }
}
