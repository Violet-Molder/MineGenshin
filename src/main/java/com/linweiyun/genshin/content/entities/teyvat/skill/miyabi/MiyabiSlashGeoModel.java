package com.linweiyun.genshin.content.entities.teyvat.skill.miyabi;

import com.linweiyun.genshin.client.render.geo.GenshinGeoModel;
import com.linweiyun.genshin.core.character.ib.IBLink;

/**
 * 斩击的模型 / 贴图 / 动画全在联动模组（IB）里，本 MOD 只登记路径：
 * {@code imaginary_branch:geo/miyabi_slash}、
 * {@code imaginary_branch:animations/miyabi_slash}、
 * {@code imaginary_branch:textures/entities/bullet/miyabi_slash.png}。
 *
 * <p>那条动画只有一段空循环，挥砍的动感来自贴图本身（20 帧的动画贴图，自带 {@code .mcmeta}）。
 * 对方不在时这三个路径读不到，模型不会画出来。
 */
public class MiyabiSlashGeoModel extends GenshinGeoModel<MiyabiSlashEffect> {

    private static final String IB_NAME = "miyabi_slash";

    public MiyabiSlashGeoModel() {
        setPaths(
                IBLink.asset("geo/" + IB_NAME),
                IBLink.asset("textures/entities/bullet/" + IB_NAME + ".png"),
                IBLink.asset("animations/" + IB_NAME)
        );
    }
}
