package vazkii.botania.fabric.xplat;

import com.google.gson.JsonObject;
import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;

import dev.emi.stepheightentityattribute.StepHeightEntityAttributeMain;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.impl.screenhandler.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.HashCache;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

import org.apache.commons.lang3.function.TriFunction;

import vazkii.botania.api.BotaniaFabricCapabilities;
import vazkii.botania.api.block.*;
import vazkii.botania.api.brew.Brew;
import vazkii.botania.api.corporea.CorporeaIndexRequestCallback;
import vazkii.botania.api.corporea.CorporeaRequestCallback;
import vazkii.botania.api.corporea.ICorporeaRequestMatcher;
import vazkii.botania.api.corporea.ICorporeaSpark;
import vazkii.botania.api.item.IAvatarWieldable;
import vazkii.botania.api.item.IBlockProvider;
import vazkii.botania.api.item.ICoordBoundItem;
import vazkii.botania.api.item.IRelic;
import vazkii.botania.api.mana.*;
import vazkii.botania.api.recipe.ElvenPortalUpdateCallback;
import vazkii.botania.common.block.tile.string.TileRedStringContainer;
import vazkii.botania.common.handler.EquipmentHandler;
import vazkii.botania.common.internal_caps.*;
import vazkii.botania.common.item.equipment.ICustomDamageItem;
import vazkii.botania.common.lib.LibMisc;
import vazkii.botania.fabric.FabricBotaniaCreativeTab;
import vazkii.botania.fabric.integration.tr_energy.FluxfieldTRStorage;
import vazkii.botania.fabric.integration.trinkets.TrinketsIntegration;
import vazkii.botania.fabric.internal_caps.CCAInternalEntityComponents;
import vazkii.botania.fabric.mixin.FabricAccessorAbstractFurnaceBlockEntity;
import vazkii.botania.fabric.mixin.FabricAccessorBucketItem;
import vazkii.botania.fabric.tile.FabricTileRedStringContainer;
import vazkii.botania.network.IPacket;
import vazkii.botania.xplat.IXplatAbstractions;

import javax.annotation.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

public class FabricXplatImpl implements IXplatAbstractions {
	@Override
	public boolean isFabric() {
		return true;
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public boolean isDevEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public boolean isPhysicalClient() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
	}

	@Override
	public String getBotaniaVersion() {
		return FabricLoader.getInstance().getModContainer(LibMisc.MOD_ID).get()
				.getMetadata().getVersion().getFriendlyString();
	}

	@Nullable
	@Override
	public IAvatarWieldable findAvatarWieldable(ItemStack stack) {
		return BotaniaFabricCapabilities.AVATAR_WIELDABLE.find(stack, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IBlockProvider findBlockProvider(ItemStack stack) {
		return BotaniaFabricCapabilities.BLOCK_PROVIDER.find(stack, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public ICoordBoundItem findCoordBoundItem(ItemStack stack) {
		return BotaniaFabricCapabilities.COORD_BOUND_ITEM.find(stack, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IManaItem findManaItem(ItemStack stack) {
		return BotaniaFabricCapabilities.MANA_ITEM.find(stack, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IRelic findRelic(ItemStack stack) {
		return BotaniaFabricCapabilities.RELIC.find(stack, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IExoflameHeatable findExoflameHeatable(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.EXOFLAME_HEATABLE.find(level, pos, state, be, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IHornHarvestable findHornHarvestable(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.HORN_HARVEST.find(level, pos, state, be, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IHourglassTrigger findHourglassTrigger(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.HOURGLASS_TRIGGER.find(level, pos, state, be, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IManaCollisionGhost findManaGhost(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.MANA_GHOST.find(level, pos, state, be, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IManaReceiver findManaReceiver(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Direction direction) {
		return BotaniaFabricCapabilities.MANA_RECEIVER.find(level, pos, state, be, direction);
	}

	@Nullable
	@Override
	public IManaTrigger findManaTrigger(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.MANA_TRIGGER.find(level, pos, state, be, Unit.INSTANCE);
	}

	@Nullable
	@Override
	public IWandable findWandable(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		return BotaniaFabricCapabilities.WANDABLE.find(level, pos, state, be, Unit.INSTANCE);
	}

	private static class SingleStackEntityStorage extends SingleStackStorage {
		private final ItemEntity entity;

		private SingleStackEntityStorage(ItemEntity entity) {
			this.entity = entity;
		}

		@Override
		protected ItemStack getStack() {
			return entity.getItem();
		}

		@Override
		protected void setStack(ItemStack stack) {
			entity.setItem(stack);
		}
	}

	@Override
	public boolean isFluidContainer(ItemEntity item) {
		return ContainerItemContext.ofSingleSlot(new SingleStackEntityStorage(item)).find(FluidStorage.ITEM) != null;
	}

	@Override
	public boolean extractFluidFromItemEntity(ItemEntity item, Fluid fluid) {
		var fluidStorage = ContainerItemContext.ofSingleSlot(new SingleStackEntityStorage(item)).find(FluidStorage.ITEM);
		if (fluidStorage == null) {
			return false;
		}
		try (Transaction txn = Transaction.openOuter()) {
			long extracted = fluidStorage.extract(FluidVariant.of(fluid), FluidConstants.BLOCK, txn);
			if (extracted == FluidConstants.BLOCK) {
				txn.commit();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean extractFluidFromPlayerItem(Player player, InteractionHand hand, Fluid fluid) {
		var fluidStorage = ContainerItemContext.ofPlayerHand(player, hand).find(FluidStorage.ITEM);
		if (fluidStorage == null) {
			return false;
		}
		try (Transaction txn = Transaction.openOuter()) {
			long extracted = fluidStorage.extract(FluidVariant.of(fluid), FluidConstants.BUCKET, txn);
			if (extracted == FluidConstants.BUCKET) {
				if (!player.getAbilities().instabuild) {
					// Only perform inventory side effects in survival
					txn.commit();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean insertFluidIntoPlayerItem(Player player, InteractionHand hand, Fluid fluid) {
		var fluidStorage = ContainerItemContext.ofPlayerHand(player, hand).find(FluidStorage.ITEM);

		if (fluidStorage == null) {
			return false;
		}

		try (Transaction txn = Transaction.openOuter()) {
			long inserted = fluidStorage.insert(FluidVariant.of(fluid), FluidConstants.BUCKET, txn);
			if (inserted == FluidConstants.BUCKET) {
				if (!player.getAbilities().instabuild) {
					// Only perform inventory side effects in survival
					txn.commit();
				}
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean hasInventory(Level level, BlockPos pos, Direction sideOfPos) {
		var state = level.getBlockState(pos);
		var be = level.getBlockEntity(pos);
		return ItemStorage.SIDED.find(level, pos, state, be, sideOfPos) != null;
	}

	@Override
	public ItemStack insertToInventory(Level level, BlockPos pos, Direction sideOfPos, ItemStack toInsert, boolean simulate) {
		var state = level.getBlockState(pos);
		var be = level.getBlockEntity(pos);
		var storage = ItemStorage.SIDED.find(level, pos, state, be, sideOfPos);
		if (storage == null) {
			return toInsert;
		}

		var itemVariant = ItemVariant.of(toInsert);
		try (Transaction txn = Transaction.openOuter()) {
			// Truncation to int ok since the value passed in was an int
			// and that value should only decrease or stay same
			int inserted = (int) storage.insert(itemVariant, toInsert.getCount(), txn);
			if (!simulate) {
				txn.commit();
			}

			if (inserted == toInsert.getCount()) {
				return ItemStack.EMPTY;
			} else {
				var ret = toInsert.copy();
				ret.setCount(toInsert.getCount() - inserted);
				return ret;
			}
		}
	}

	@Override
	public EthicalComponent ethicalComponent(PrimedTnt tnt) {
		return CCAInternalEntityComponents.TNT_ETHICAL.get(tnt);
	}

	@Override
	public GhostRailComponent ghostRailComponent(AbstractMinecart cart) {
		return CCAInternalEntityComponents.GHOST_RAIL.get(cart);
	}

	@Override
	public ItemFlagsComponent itemFlagsComponent(ItemEntity item) {
		return CCAInternalEntityComponents.INTERNAL_ITEM.get(item);
	}

	@Override
	public KeptItemsComponent keptItemsComponent(Player player) {
		return CCAInternalEntityComponents.KEPT_ITEMS.get(player);
	}

	@Nullable
	@Override
	public LooniumComponent looniumComponent(LivingEntity entity) {
		return CCAInternalEntityComponents.LOONIUM_DROP.getNullable(entity);
	}

	@Override
	public NarslimmusComponent narslimmusComponent(Slime slime) {
		return CCAInternalEntityComponents.NARSLIMMUS.get(slime);
	}

	@Override
	public TigerseyeComponent tigersEyeComponent(Creeper creeper) {
		return CCAInternalEntityComponents.TIGERSEYE.get(creeper);
	}

	@Override
	public boolean fireCorporeaRequestEvent(ICorporeaRequestMatcher matcher, int itemCount, ICorporeaSpark spark, boolean dryRun) {
		return CorporeaRequestCallback.EVENT.invoker().onRequest(matcher, itemCount, spark, dryRun);
	}

	@Override
	public boolean fireCorporeaIndexRequestEvent(ServerPlayer player, ICorporeaRequestMatcher request, int count, ICorporeaSpark spark) {
		return CorporeaIndexRequestCallback.EVENT.invoker().onIndexRequest(player, request, count, spark);
	}

	@Override
	public void fireManaItemEvent(Player player, List<ItemStack> toReturn) {
		ManaItemsCallback.EVENT.invoker().getManaItems(player, toReturn);
	}

	@Override
	public float fireManaDiscountEvent(Player player, float discount, ItemStack tool) {
		return ManaDiscountCallback.EVENT.invoker().getManaDiscount(player, discount, tool);
	}

	@Override
	public boolean fireManaProficiencyEvent(Player player, ItemStack tool, boolean proficient) {
		return ManaProficiencyCallback.EVENT.invoker().getProficient(player, tool, proficient);
	}

	@Override
	public void fireElvenPortalUpdateEvent(BlockEntity portal, AABB bounds, boolean open, List<ItemStack> stacksInside) {
		ElvenPortalUpdateCallback.EVENT.invoker().onElvenPortalTick(portal, bounds, open, stacksInside);
	}

	@Override
	public void fireManaNetworkEvent(IManaReceiver thing, ManaBlockType type, ManaNetworkAction action) {
		ManaNetworkCallback.EVENT.invoker().onNetworkChange(thing, type, action);
	}

	@Override
	public Packet<?> toVanillaClientboundPacket(IPacket packet) {
		return ServerPlayNetworking.createS2CPacket(packet.getFabricId(), packet.toBuf());
	}

	@Override
	public void sendToPlayer(Player player, IPacket packet) {
		ServerPlayNetworking.send((ServerPlayer) player, packet.getFabricId(), packet.toBuf());
	}

	@Override
	public void sendToNear(Level level, BlockPos pos, IPacket packet) {
		var pkt = ServerPlayNetworking.createS2CPacket(packet.getFabricId(), packet.toBuf());
		PlayerLookup.tracking((ServerLevel) level, pos).stream()
				.filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64)
				.forEach(p -> p.connection.send(pkt));
	}

	@Override
	public void sendToTracking(Entity e, IPacket packet) {
		var pkt = ServerPlayNetworking.createS2CPacket(packet.getFabricId(), packet.toBuf());
		PlayerLookup.tracking(e).forEach(p -> p.connection.send(pkt));
		if (e instanceof ServerPlayer) {
			((ServerPlayer) e).connection.send(pkt);
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func, Block... blocks) {
		return FabricBlockEntityTypeBuilder.create(func::apply, blocks).build();
	}

	@Override
	public void registerReloadListener(PackType type, ResourceLocation id, PreparableReloadListener listener) {
		ResourceManagerHelper.get(type).registerReloadListener(new IdentifiableResourceReloadListener() {
			@Override
			public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepProfiler,
					ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
				return listener.reload(barrier, manager, prepProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
			}

			@Override
			public ResourceLocation getFabricId() {
				return id;
			}
		});
	}

	@Override
	public FabricItemSettings defaultItemBuilder() {
		return new FabricItemSettings().group(FabricBotaniaCreativeTab.INSTANCE);
	}

	@Override
	public Item.Properties defaultItemBuilderWithCustomDamageOnFabric() {
		return defaultItemBuilder().customDamage((stack, amount, entity, breakCallback) -> {
			var item = stack.getItem();
			if (item instanceof ICustomDamageItem cd) {
				return cd.damageItem(stack, amount, entity, breakCallback);
			}
			return amount;
		});
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> createMenuType(TriFunction<Integer, Inventory, FriendlyByteBuf, T> constructor) {
		return new ExtendedScreenHandlerType<>(constructor::apply);
	}

	@Override
	public Registry<Brew> createBrewRegistry() {
		return FabricRegistryBuilder.createDefaulted(Brew.class, prefix("brews"), prefix("fallback")).buildAndRegister();
	}

	@Nullable
	@Override
	public EquipmentHandler tryCreateEquipmentHandler() {
		if (isModLoaded("trinkets")) {
			TrinketsIntegration.init();
			return new TrinketsIntegration();
		}
		return null;
	}

	@Override
	public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> writeInitialData) {
		var menuProvider = new ExtendedScreenHandlerFactory() {
			@Nullable
			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
				return menu.createMenu(id, inventory, player);
			}

			@Override
			public Component getDisplayName() {
				return menu.getDisplayName();
			}

			@Override
			public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
				writeInitialData.accept(buf);
			}
		};
		player.openMenu(menuProvider);
	}

	@Override
	public Attribute getReachDistanceAttribute() {
		return ReachEntityAttributes.REACH;
	}

	@Override
	public Attribute getStepHeightAttribute() {
		return StepHeightEntityAttributeMain.STEP_HEIGHT;
	}

	private final TagKey<Block> oreTag = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("c", "ores"));

	@Override
	public TagKey<Block> getOreTag() {
		return oreTag;
	}

	// No standard so we have to check both :wacko:
	private final TagKey<Block> cGlass = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("c", "glass"));
	private final TagKey<Block> cGlassBlocks = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("c", "glass_blocks"));

	@Override
	public boolean isInGlassTag(BlockState state) {
		return state.is(cGlass) || state.is(cGlassBlocks);
	}

	@Override
	public boolean canFurnaceBurn(AbstractFurnaceBlockEntity furnace, @Nullable Recipe<?> recipe, NonNullList<ItemStack> items, int maxStackSize) {
		return FabricAccessorAbstractFurnaceBlockEntity.callCanBurn(recipe, items, maxStackSize);
	}

	@Override
	public void saveRecipeAdvancement(DataGenerator generator, HashCache cache, JsonObject json, Path path) {
		RecipeProvider.saveAdvancement(cache, json, path);
	}

	@Override
	public Fluid getBucketFluid(BucketItem item) {
		return ((FabricAccessorBucketItem) item).getContent();
	}

	@Override
	public int getSmeltingBurnTime(ItemStack stack) {
		Integer v = FuelRegistry.INSTANCE.get(stack.getItem());
		return v == null ? 0 : v;
	}

	@Override
	public boolean preventsRemoteMovement(ItemEntity entity) {
		return false;
	}

	public static final Map<Block, Block> CUSTOM_STRIPPING = new LinkedHashMap<>();

	@Override
	public void addAxeStripping(Block input, Block output) {
		if (input.getStateDefinition().getProperties().contains(BlockStateProperties.AXIS)
				&& output.getStateDefinition().getProperties().contains(BlockStateProperties.AXIS)) {
			StrippableBlockRegistry.register(input, output);
		} else {
			CUSTOM_STRIPPING.put(input, output);
		}
	}

	@Override
	public int transferEnergyToNeighbors(Level level, BlockPos pos, int energy) {
		if (isModLoaded("team_reborn_energy")) {
			return FluxfieldTRStorage.transferEnergyToNeighbors(level, pos, energy);
		}
		return energy;
	}

	@Override
	public boolean isRedStringContainerTarget(BlockEntity be) {
		if (be.getLevel().isClientSide) {
			return false;
		}
		for (Direction value : Direction.values()) {
			if (ItemStorage.SIDED.find(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, value) != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public TileRedStringContainer newRedStringContainer(BlockPos pos, BlockState state) {
		return new FabricTileRedStringContainer(pos, state);
	}
}
