package com.linweiyun.genshin.data.generators;

import com.linweiyun.genshin.Minegenshin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;


@EventBusSubscriber(modid = Minegenshin.MOD_ID)
public class DataGenerators {
  @SubscribeEvent
  public static void gatherData(GatherDataEvent event) {
    // 物品模型 / 物品定义（assets/minegenshin/items + models/item）
    // 别随便注释掉：原版 ModelProvider 会校验「每个物品都得有定义」，
    // 少了它，新加的物品要么没有模型、要么得手写一份。
    event.createProvider(ModModeProvider::new);
    event.createProvider(DamageTypeDataProviderGIM::new);
  }
}
