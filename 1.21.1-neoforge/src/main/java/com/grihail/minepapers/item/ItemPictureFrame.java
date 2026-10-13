package com.grihail.minepapers.item;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Предмет картины: вешает картину-сущность, ПКМ в воздух — окно её настройки; в подсказке видно, какие настройки в нём сохранены. */
public class ItemPictureFrame extends Item {

    public ItemPictureFrame(Properties properties) {
        super(properties);
    }

    /** Настройки из предмета или null, если предмет не настраивали. */
    public static CompoundTag settings(ItemStack stack) {
        return stack.get(MinePapers.FRAME_SETTINGS.get());
    }

    /** Картина запривачена (в настройках есть владелец). */
    public static boolean isLocked(CompoundTag settings) {
        return settings != null && settings.hasUUID("Owner");
    }

    /** Заприваченная картина блестит, как зачарованный предмет. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isLocked(settings(stack)) || super.isFoil(stack);
    }

    /** Предмет картины; настройки сохраняются, если картину настраивали. */
    public static ItemStack makeStack(PictureFrame frame) {
        ItemStack stack = new ItemStack(MinePapers.FRAME.get());
        if (frame != null) store(stack, frame);
        return stack;
    }

    /** Настройки из предмета (не настраивали — как у новой картины). */
    public static PictureFrame read(ItemStack stack) {
        PictureFrame frame = new PictureFrame();
        CompoundTag settings = settings(stack);
        if (settings != null) frame.readSettings(settings);
        return frame;
    }

    /** Записать настройки в предмет; ненастроенная картина остаётся без них и складывается с новыми. */
    public static void store(ItemStack stack, PictureFrame frame) {
        if (!frame.isCustomized()) {
            stack.remove(MinePapers.FRAME_SETTINGS.get());
            return;
        }
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        stack.set(MinePapers.FRAME_SETTINGS.get(), settings);
    }

    /** ПКМ не по блоку — окно настройки этой картины, как у висящей. Окно с инвентарём открывает сервер. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            FrameHolder holder = FrameHolder.of(sp, FrameHolder.targetOf(sp, hand));
            if (holder != null) ContainerPictureFrame.open(sp, holder);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** Будущая картина при клике по грани side блока pos, с настройками из предмета. Лежит ровно на этой грани — на настоящей поверхности блока (полублок, ковёр, боковина забора), а не на границе клетки. На полу и потолке верх картинки — в ту сторону, куда смотрит игрок (yaw). */
    public static PictureFrame ghostAt(Level level, BlockPos pos, Direction side, float yaw, ItemStack stack) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        AABB bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        double[] min = { bounds.minX, bounds.minY, bounds.minZ };
        double[] max = { bounds.maxX, bounds.maxY, bounds.maxZ };
        int s = side.get3DDataValue();
        int axis = s < 2 ? 1 : s < 4 ? 2 : 0;
        boolean positive = (s & 1) == 1;

        BlockPos at = pos.relative(side);
        PictureFrame ghost = new PictureFrame();
        ghost.x = at.getX();
        ghost.y = at.getY();
        ghost.z = at.getZ();
        ghost.facing = s;
        ghost.rotation = Mth.floor(yaw * 4f / 360f + 0.5) & 3;
        // Грань блока может не доходить до границы клетки: прижимаем картину к ней.
        ghost.surface = (float) Mth.clamp(positive ? max[axis] - 1 : -min[axis], -1, 0);
        CompoundTag settings = settings(stack);
        if (settings != null) ghost.readSettings(settings);
        return ghost;
    }

    /** Установка картины-сущности. Клетки она не занимает, поэтому рядом и в той же клетке может быть что угодно; нельзя только провести холст сквозь блоки и другие картины. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Direction side = context.getClickedFace();
        PictureFrame ghost = ghostAt(level, context.getClickedPos(), side, player != null ? player.getYRot() : 0, stack);
        BlockPos at = new BlockPos(ghost.x, ghost.y, ghost.z);
        if (player != null && !player.mayUseItemAt(at, side, stack)) return InteractionResult.FAIL;
        if (FrameGeometry.isBlocked(level, ghost, null)) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            EntityPicture picture = new EntityPicture(level, ghost);
            level.addFreshEntity(picture);
            SoundType sound = SoundType.WOOD;
            level.playSound(null, picture.getX(), picture.getY(), picture.getZ(), sound.getPlaceSound(),
                SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, picture.position());
        }
        stack.consume(1, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        CompoundTag settings = settings(stack);
        if (settings == null) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(settings);

        lines.add(Component.translatable("minepapers.tooltip.configured").withStyle(ChatFormatting.GOLD));
        if (!s.title.isEmpty()) lines.add(Component.literal(s.title).withStyle(ChatFormatting.WHITE));
        for (String line : PictureFrame.wrap(s.description, 40)) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        String path = s.getSourcePath();
        if (path.length() > 40) path = "..." + path.substring(path.length() - 37);
        Component source = Component.translatable("minepapers.tooltip.source." + s.source).withStyle(ChatFormatting.GRAY);
        lines.add(path.isEmpty() ? source : Component.empty().append(source)
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(path).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.translatable("minepapers.tooltip.size", Numbers.blocks(s.getWidthBlocks()), Numbers.blocks(s.getHeightBlocks()))
            .append(", ").append(Component.translatable("minepapers.gui.mode." + s.mode))
            .withStyle(ChatFormatting.GRAY));
        material(lines, s.border, "minepapers.tooltip.border");
        material(lines, s.canvas, "minepapers.tooltip.canvas");
        int glow = s.glowLevel();
        if (PictureFrame.isGlowDust(s.glow.stack())) {
            lines.add(Component.translatable("minepapers.tooltip.glow.self").withStyle(ChatFormatting.GRAY));
        } else if (glow > 0) {
            lines.add(Component.translatable("minepapers.tooltip.glow", glow).withStyle(ChatFormatting.GRAY));
        }
        if (s.isLocked()) {
            lines.add(Component.translatable("minepapers.tooltip.owner", s.ownerName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static void material(List<Component> lines, PictureFrame.Material m, String key) {
        ItemStack stack = borderStack(m.item);
        if (!stack.isEmpty()) lines.add(Component.translatable(key, stack.getHoverName()).withStyle(ChatFormatting.GRAY));
    }

    /** Блоки из слотов материалов (рамка, холст), лежащие в картине-предмете. */
    public static List<ItemStack> storedMaterials(ItemStack stack) {
        List<ItemStack> stored = new ArrayList<ItemStack>();
        CompoundTag settings = stack.is(MinePapers.FRAME.get()) ? settings(stack) : null;
        if (settings == null) return stored;
        for (PictureFrame.Material m : new PictureFrame().materials()) {
            m.read(settings);
            if (!m.stack().isEmpty()) stored.add(m.stack().copy());
        }
        return stored;
    }

    /** Предмет, с которого взята текстура материала (рамки или холста); пустой — обычный материал. */
    public static ItemStack borderStack(String itemId) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
    }

}
