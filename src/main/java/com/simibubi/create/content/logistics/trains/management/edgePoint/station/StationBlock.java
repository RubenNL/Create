package com.simibubi.create.content.logistics.trains.management.edgePoint.station;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.AllTileEntities;
import com.simibubi.create.content.contraptions.wrench.IWrenchable;
import com.simibubi.create.content.logistics.block.depot.SharedDepotBlockMethods;
import com.simibubi.create.content.logistics.trains.entity.Train;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.block.ITE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.gui.ScreenOpener;

import com.simibubi.create.foundation.utility.Iterate;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

import java.util.Random;
import java.util.UUID;

public class StationBlock extends Block implements ITE<StationTileEntity>, IWrenchable, ProperWaterloggedBlock {

	public static final BooleanProperty ASSEMBLING = BooleanProperty.create("assembling");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public StationBlock(Properties p_54120_) {
		super(p_54120_);
		registerDefaultState(defaultBlockState().setValue(ASSEMBLING, false)
			.setValue(WATERLOGGED, false)
			.setValue(POWERED, false));
	}

	private boolean isPowered(Level worldIn, BlockPos pos) {
		int power = 0;
		for (Direction direction : Iterate.directions) {
			if(worldIn.getSignal(pos.relative(direction), direction)>0) {
				return true;
			}
		}
		return false;
	}
	@Override
	public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		if (worldIn.isClientSide)
			return;

		if (!worldIn.getBlockTicks()
				.willTickThisTick(pos, this))
			worldIn.scheduleTick(pos, this, 0);
	}
	@Override
	public void tick(BlockState state, ServerLevel worldIn, BlockPos pos, Random pRandom) {
		if (worldIn.isClientSide)
			return;
		boolean isPowered = isPowered(worldIn, pos);

		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != isPowered) {
			worldIn.setBlock(pos, state.cycle(POWERED), 2);
			if(isPowered) withTileEntityDo(worldIn, pos, te -> {
				if(te.trainCanDisassemble) {
					GlobalStation station = te.getStation();
					if(station==null)
						return;
					Train train = station.getPresentTrain();
					if(train==null)
						return;
					train.disassemble(te.getAssemblyDirection(), te.edgePoint.getGlobalPosition().above());
				} else {
					te.assemble(UUID.randomUUID());
				}
			});
		}

	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(ASSEMBLING, WATERLOGGED, POWERED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return withWater(super.getStateForPlacement(pContext), pContext);
	}

	@Override
	public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState,
		LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pNeighborPos) {
		updateWater(pLevel, pState, pCurrentPos);
		return pState;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		AdvancementBehaviour.setPlacedBy(pLevel, pPos, pPlacer);
	}

	@Override
	public FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos) {
		return getTileEntityOptional(pLevel, pPos).map(ste -> ste.trainPresent ? 15 : 0)
			.orElse(0);
	}

	@Override
	public void fillItemCategory(CreativeModeTab pTab, NonNullList<ItemStack> pItems) {
		pItems.add(AllItems.SCHEDULE.asStack());
		super.fillItemCategory(pTab, pItems);
	}

	@Override
	public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
		ITE.onRemove(state, worldIn, pos, newState);
	}

	@Override
	public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
		super.updateEntityAfterFallOn(worldIn, entityIn);
		SharedDepotBlockMethods.onLanded(worldIn, entityIn);
	}

	@Override
	public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
		BlockHitResult pHit) {

		if (pPlayer == null || pPlayer.isSteppingCarefully())
			return InteractionResult.PASS;
		ItemStack itemInHand = pPlayer.getItemInHand(pHand);
		if (AllItems.WRENCH.isIn(itemInHand))
			return InteractionResult.PASS;

		if (itemInHand.getItem() == Items.FILLED_MAP) {
			return onTileEntityUse(pLevel, pPos, station -> {
				if (pLevel.isClientSide)
					return InteractionResult.SUCCESS;

				if (station.getStation() == null || station.getStation().getId() == null)
					return InteractionResult.FAIL;

				MapItemSavedData savedData = MapItem.getSavedData(itemInHand, pLevel);
				if (!(savedData instanceof StationMapData stationMapData))
					return InteractionResult.FAIL;

				if (!stationMapData.toggleStation(pLevel, pPos, station))
					return InteractionResult.FAIL;

				return InteractionResult.SUCCESS;
			});
		}

		InteractionResult result = onTileEntityUse(pLevel, pPos, station -> {
			ItemStack autoSchedule = station.getAutoSchedule();
			if (autoSchedule.isEmpty())
				return InteractionResult.PASS;
			if (pLevel.isClientSide)
				return InteractionResult.SUCCESS;
			pPlayer.getInventory()
				.placeItemBackInInventory(autoSchedule.copy());
			station.depotBehaviour.removeHeldItem();
			station.notifyUpdate();
			AllSoundEvents.playItemPickup(pPlayer);
			return InteractionResult.SUCCESS;
		});

		if (result == InteractionResult.PASS)
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> withTileEntityDo(pLevel, pPos, te -> this.displayScreen(te, pPlayer)));
		return InteractionResult.SUCCESS;
	}

	@OnlyIn(value = Dist.CLIENT)
	protected void displayScreen(StationTileEntity te, Player player) {
		if (!(player instanceof LocalPlayer))
			return;
		GlobalStation station = te.getStation();
		BlockState blockState = te.getBlockState();
		if (station == null || blockState == null)
			return;
		boolean assembling = blockState.getBlock() == this && blockState.getValue(ASSEMBLING);
		ScreenOpener.open(assembling ? new AssemblyScreen(te, station) : new StationScreen(te, station));
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return AllShapes.STATION;
	}

	@Override
	public Class<StationTileEntity> getTileEntityClass() {
		return StationTileEntity.class;
	}

	@Override
	public BlockEntityType<? extends StationTileEntity> getTileEntityType() {
		return AllTileEntities.TRACK_STATION.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

}
