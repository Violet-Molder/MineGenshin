package com.linweiyun.genshin.core.character.util.appearance;

/**
 * 一条腿的「袜子类型」—— <b>三选一</b>：裸腿 / 白丝 / 黑丝。
 *
 * <h2>为什么是枚举而不是三个 boolean</h2>
 * 模型里同一段腿有<b>三套完全独立的网格</b>（{@code _tui} / {@code _baisi} / {@code _heisi}），
 * 它们大小位置一样、默认全渲染，所以必须<b>选一套、藏两套</b>。
 * 用三个 boolean 表达会出现「两个都 true」这种模型里根本不存在的状态。
 *
 * <p>各部位对应的骨骼名后缀<b>不统一</b>（大腿是 {@code tui/baisi/heisi}，
 * 小腿和脚是 {@code tui/bs/hs}），映射集中在 {@link LegBoneRules}，
 * 这个枚举本身只描述语义。
 */
public enum SockType {
    /** 裸腿 —— 骨骼后缀 {@code tui}。 */
    BARE,
    /** 白丝 —— 大腿 {@code baisi}、小腿/脚 {@code bs}。 */
    WHITE,
    /** 黑丝 —— 大腿 {@code heisi}、小腿/脚 {@code hs}。 */
    BLACK;

    public static final SockType[] VALUES = values();

    /** 越界一概当裸腿（老存档 / 坏数据不该让模型崩）。 */
    public static SockType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BARE;
    }
}
