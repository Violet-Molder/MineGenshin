package com.linweiyun.genshin.core.system.module;

import com.linweiyun.elementlib.core.module.ElibModuleTargetKind;
import com.linweiyun.elementlib.core.module.ElibModuleTargetKinds;
import com.linweiyun.genshin.Minegenshin;

/** 本模组额外支持的模块宿主种类。 */
public final class MinegenshinModuleKinds {

    public static final ElibModuleTargetKind CHARACTER =
            ElibModuleTargetKinds.register(Minegenshin.id("character"));

    private MinegenshinModuleKinds() {
    }

    public static void init() {
    }
}
