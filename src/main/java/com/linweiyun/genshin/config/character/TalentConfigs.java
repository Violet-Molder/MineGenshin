package com.linweiyun.genshin.config.character;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「角色 id → 技能倍率表」的登记处，给通用配置页（{@code CharacterConfigUI}）用。
 *
 * <p>通用页拿到的是一个角色实例，它只知道自己的资源 id（{@code "shenhe"} /
 * {@code "linweiyun"}…），所以这里按 id 登记：角色注册时（{@code ModCharacters}）把各自的
 * {@code SOURCE} 挂进来，页面就按 id 取。新增一个角色时只要在这里补一行，
 * 配置页的「技能倍率」页签会自动出现。
 *
 * <p>写值走 {@link #setByKeyGlobal}：key 全局唯一，服务端收到同步包时不需要知道是哪个角色。
 */
public final class TalentConfigs {

    /** 登记顺序 = 页面/日志里的查找顺序（{@link LinkedHashMap}）。 */
    private static final Map<String, TalentConfigSource> BY_CHARACTER = new LinkedHashMap<>();

    private TalentConfigs() {
    }

    /** 登记一个角色的倍率表；重复登记以最后一次为准。 */
    public static void register(String characterId, TalentConfigSource source) {
        if (characterId == null || characterId.isEmpty() || source == null) {
            return;
        }
        BY_CHARACTER.put(characterId, source);
    }

    /** 取某个角色的倍率表；没登记过返回 {@code null}（页面据此决定要不要出「技能倍率」页签）。 */
    @Nullable
    public static TalentConfigSource of(@Nullable String characterId) {
        return characterId == null ? null : BY_CHARACTER.get(characterId);
    }

    /**
     * 按 key 写值 —— 依次问每一张表，第一个认下这条 key 的生效。
     *
     * <p>服务端同步包（{@code NetworkManager.talentMultiplierRPCPacket}）用这个，
     * 免得每加一个角色都要去改网络层。
     *
     * @return 有没有表认下这条 key
     */
    public static boolean setByKeyGlobal(String key, double value) {
        if (key == null) {
            return false;
        }
        for (TalentConfigSource source : BY_CHARACTER.values()) {
            if (source.setByKey(key, value)) {
                return true;
            }
        }
        return false;
    }
}
