package com.linweiyun.genshin.config.character;

import java.util.List;

/**
 * 一个角色的<b>技能倍率表</b>对配置页暴露的取用接口。
 *
 * <p>为什么要有这一层：配置页（{@code CharacterConfigScreen}）是<b>通用</b>的，
 * 而倍率表每个角色一份（申鹤 {@link ShenheTalentConfig}、林薇云 {@link LinweiyunTalentConfig}…）。
 * 页面不认识具体字段名，只按 key 取值 / 写值；写下去的效果和手改 TOML 完全一样
 * （底层就是 {@code ModConfigSpec.ConfigValue}）。
 *
 * <p>key 必须<b>全局唯一</b>（各角色自己加前缀，如申鹤 {@code nab1} / 林薇云 {@code lwy-nab1}）——
 * 配置页改完要用 {@code NetworkManager.setTalentMultiplierToServer(key, value)} 把服务端那份也写掉，
 * 服务端只能拿 key 反查是哪张表（见 {@link TalentConfigs#setByKeyGlobal}）。
 */
public interface TalentConfigSource {

    /** 页面显示顺序下的全部分组 id（对应配置文件里的分组，如 {@code normal-attack}）。 */
    List<String> groups();

    /** 某个分组里的 key（保持定义顺序）。 */
    List<String> keysOf(String group);

    /** 按 key 读倍率；key 不存在返回 {@code null}。 */
    Double getByKey(String key);

    /** 按 key 写倍率；key 不存在返回 {@code false}。 */
    boolean setByKey(String key, double value);
}
