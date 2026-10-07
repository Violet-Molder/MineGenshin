package com.linweiyun.genshin.data.generators;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.items.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class ModEntityLootSubProvider extends EntityLootSubProvider {
  private BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output;

  protected ModEntityLootSubProvider(HolderLookup.Provider registries) {
    super(FeatureFlags.DEFAULT_FLAGS, registries);
  }

  @Override
  public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
    this.output = output;
    generate();
  }

  @Override
  protected @NotNull Stream<EntityType<?>> getKnownEntityTypes() {
    return BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
        .filter(entry -> entry.getValue().getCategory() == MobCategory.MONSTER)
        .map(Map.Entry::getValue);
  }

  @Override
  protected void add(EntityType<?> entityType, LootTable.Builder builder) {
    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    ResourceLocation lootTableId =
            ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "entities/" + entityId.getPath());
    output.accept(ResourceKey.create(Registries.LOOT_TABLE, lootTableId), builder);
  }

  public void generate() {
    getKnownEntityTypes()
        .forEach(
            entityType -> {
              LootTable.Builder lootTable =
                  LootTable.lootTable()
                      .withPool(
                          LootPool.lootPool()
                              .setRolls(ConstantValue.exactly(1))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(60)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(0))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(17)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(1))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(13)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(2))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(5)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(3))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(3)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(4))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(2)
                                      .apply(
                                          SetItemCountFunction.setCount(ConstantValue.exactly(5))))
                              .add(
                                  LootItem.lootTableItem(ModItems.PRIMOGEM.get())
                                      .setWeight(1)
                                      .apply(
                                          SetItemCountFunction.setCount(
                                              ConstantValue.exactly(6)))));
              add(entityType, lootTable);
            });
  }
}
