package com.linweiyun.genshin.config.character;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 所有<b>已登记的技能倍率表</b>。
 *
 * <h2>它只服务一件事：服务端同步</h2>
 * 配置页改倍率时客户端只发得出「key + 值」（倍率 key 全局唯一，各角色自带前缀，
 * 如申鹤 {@code nab1} / 林薇云 {@code lwy-nab1}），服务端得知道该写哪张表。
 * 所以每张倍率表在自己的配置类里登记一次（{@code ShenheConfig} / {@code LinweiyunConfig}），
 * 由 {@link #setByKeyGlobal} 依次问。
 *
 * <p><b>页面不查这里</b>：哪个角色显示哪张倍率表由角色自己的配置页子类声明
 * （{@code CharacterConfigScreen#talentConfig()}），不按 id 反查。
 */
public final class TalentConfigs {

    private static final Set<TalentConfigSource> SOURCES = new LinkedHashSet<>();

    private TalentConfigs() {
    }

    /** 登记一张倍率表；重复登记无副作用。 */
    public static void register(TalentConfigSource source) {
        if (source != null) {
            SOURCES.add(source);
        }
    }

    /**
     * 按 key 写值 —— 依次问每一张表，第一个认下这条 key 的生效。
     *
     * @return 有没有表认下这条 key
     */
    public static boolean setByKeyGlobal(String key, double value) {
        if (key == null) {
            return false;
        }
        for (TalentConfigSource source : SOURCES) {
            if (source.setByKey(key, value)) {
                return true;
            }
        }
        return false;
    }
}
