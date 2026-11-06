package com.babushki.block;

import com.babushki.entity.BabushkaEntity;
import com.babushki.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Лавочка: два блока в ширину (как кровать). Левая половина — место Бабы Вали, правая — Бабы Зины.
 * FACING — куда смотрит сидящий.
 */
public class BenchBlock extends HorizontalDirectionalBlock {
    public enum Part implements StringRepresentable {
        LEFT, RIGHT;
        @Override public String getSerializedName() { return name().toLowerCase(); }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Высота сиденья (в пикселях). */
    public static final double SEAT_HEIGHT = 9.0 / 16.0;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // Модель для севера: сиденье z 2..13, спинка z 12..14 (сзади, на юге).
            VoxelShape seat = rotate(d, 0, 0, 2, 16, 9, 13);
            VoxelShape back = rotate(d, 0, 9, 12, 16, 16, 14);
            SHAPES.put(d, Shapes.or(seat, back));
        }
    }

    private static VoxelShape rotate(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public BenchBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** В какую сторону от этой половины лежит вторая половина. */
    public static Direction partnerDirection(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(PART) == Part.LEFT ? f.getClockWise() : f.getCounterClockWise();
    }

    /** Позиция левой половины лавочки (главная). */
    public static BlockPos leftPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LEFT ? pos : pos.relative(partnerDirection(state));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockPos pos = ctx.getClickedPos();
        BlockPos other = pos.relative(facing.getClockWise());
        Level level = ctx.getLevel();
        if (level.getBlockState(other).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(other)) {
            return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LEFT);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos other = pos.relative(partnerDirection(state));
            level.setBlock(other, state.setValue(PART, Part.RIGHT), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    /** Ставит целую лавочку (обе половины) — для автоматического появления. */
    public static void placeWhole(Level level, BlockPos leftPos, Direction facing, Block block) {
        BlockState left = block.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LEFT);
        level.setBlock(leftPos, left, 3);
        level.setBlock(leftPos.relative(facing.getClockWise()), left.setValue(PART, Part.RIGHT), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == partnerDirection(state)) {
            boolean ok = neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART)
                    && neighbor.getValue(FACING) == state.getValue(FACING);
            return ok ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == Part.RIGHT) {
            // В творческом режиме не роняем предмет из левой половины.
            BlockPos other = pos.relative(partnerDirection(state));
            BlockState os = level.getBlockState(other);
            if (os.is(this) && os.getValue(PART) == Part.LEFT) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, other, Block.getId(os));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        AABB box = new AABB(pos).inflate(0.1, 1.0, 0.1);
        List<BabushkaEntity> sitters = level.getEntitiesOfClass(BabushkaEntity.class, box, BabushkaEntity::isSitting);
        if (!sitters.isEmpty()) {
            player.displayClientMessage(Component.literal("Занято! Тут " + sitters.get(0).getRole().displayName + " сидит!"), true);
            return InteractionResult.CONSUME;
        }
        List<SeatEntity> seats = level.getEntitiesOfClass(SeatEntity.class, box);
        if (!seats.isEmpty()) return InteractionResult.CONSUME;
        SeatEntity seat = SeatEntity.create(level, pos, state.getValue(FACING));
        level.addFreshEntity(seat);
        player.startRiding(seat);
        return InteractionResult.CONSUME;
    }
}
