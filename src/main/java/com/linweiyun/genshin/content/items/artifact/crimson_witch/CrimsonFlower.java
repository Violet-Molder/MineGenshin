package com.linweiyun.genshin.content.items.artifact.crimson_witch;

import com.linweiyun.genshin.content.items.artifact.type.FlowerArtifact;
import com.linweiyun.genshin.core.system.registry.register.ArtifactSets;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;

public class CrimsonFlower extends FlowerArtifact {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);
    public CrimsonFlower(Properties properties) {
        super(properties);
        this.set = ArtifactSets.CRIMSON_WITCH;
        this.star = 5;
    }
}
