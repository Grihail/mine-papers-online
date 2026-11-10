package com.grihail.minepapers.item;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.Compat;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <26.1 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Предмет картины: вешает картину-сущность, ПКМ в воздух — окно её настройки; в подсказке видно, какие настройки в нём сохранены. */
public class ItemPictureFrame extends Item {

    public ItemPictureFrame(Properties properties) {
        super(properties);
    }

    //? if <1.20.5 {
    /*// До 1.20.5 настройки лежат в теге предмета под этим ключом (как и в 1.7.10).
    public static final String TAG = "MinePapersFrame";
    *///?}

    /** Настройки из предмета или null, если предмет не настраивали. */
    public static CompoundTag settings(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(MinePapers.FRAME_SETTINGS.get());
        //?} else {
        /*return stack.getTagElement(TAG);
        *///?}
    }

    /** Записать настройки в предмет как есть (null — убрать: картина станет обычной). */
    public static void setSettings(ItemStack stack, CompoundTag settings) {
        //? if >=1.20.5 {
        if (settings == null) {
            stack.remove(MinePapers.FRAME_SETTINGS.get());
        } else {
            stack.set(MinePapers.FRAME_SETTINGS.get(), settings);
        }
        //?} else {
        /*if (settings == null) {
            stack.removeTagKey(TAG);
        } else {
            stack.addTagElement(TAG, settings);
        }
        *///?}
    }

    /** Картина запривачена (в настройках есть владелец). */
    /** Подписанная картина: подпись уникальна и навсегда — её не копируют и не сбрасывают. */
    public static boolean isSigned(CompoundTag settings) {
        return Compat.uuid(settings, "Signer") != null;
    }

    public static boolean isLocked(CompoundTag settings) {
        return Compat.uuid(settings, "Owner") != null;
    }

    /** Заприваченная и подписанная картины блестят, как зачарованный предмет. */
    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag settings = settings(stack);
        return isLocked(settings) || isSigned(settings) || super.isFoil(stack);
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
            setSettings(stack, null);
            return;
        }
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        setSettings(stack, settings);
    }

    //? if forge && <26.1 {
    /*// Forge до 1.21: свой рендерер предмета задаёт сам предмет (настроенная рамка — модель picture_frame_configured, builtin/entity).
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private com.grihail.minepapers.client.RenderPictureItem renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.grihail.minepapers.client.RenderPictureItem();
                return renderer;
            }
        });
    }
    *///?}

    /** ПКМ не по блоку — окно настройки этой картины, как у висящей. Окно с инвентарём открывает сервер. */
    @Override
    //? if >=26.1 {
    /*public InteractionResult use(Level level, Player player, InteractionHand hand) {
    *///?} else {
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    //?}
        if (player instanceof ServerPlayer sp) {
            FrameHolder holder = FrameHolder.of(sp, FrameHolder.targetOf(sp, hand));
            if (holder != null) ContainerPictureFrame.open(sp, holder);
        }
        //? if >=26.1 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        //?}
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
        if (!level.isClientSide()) {
            EntityPicture picture = new EntityPicture(level, ghost);
            level.addFreshEntity(picture);
            level.playSound(null, picture.getX(), picture.getY(), picture.getZ(), SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1f, 1f);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, picture.position());
        }
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        //? if >=26.1 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(level.isClientSide());
        //?}
    }

    @Override
    //? if >=26.1 {
    /*public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> builder, TooltipFlag flag) {
        List<Component> lines = new ArrayList<Component>();
        tooltip(stack, lines);
        lines.forEach(builder);
    }
    *///?} else if >=1.20.5 {
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    //?} else {
    /*public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    *///?}

    /** Подсказка настроенной картины: какие настройки в ней сохранены. */
    private static void tooltip(ItemStack stack, List<Component> lines) {
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
            .withStyle(ChatFormatting.GRAY));
        part(lines, "minepapers.tooltip.border", "minepapers.gui.frameShape." + s.getFrameShape(), s.usesBorder() ? s.border : null);
        part(lines, "minepapers.tooltip.canvas", "minepapers.gui.canvasKind." + s.getCanvasKind(), s.usesCanvas() ? s.canvas : null);
        if (s.glass) part(lines, "minepapers.tooltip.glass", "minepapers.gui.glass.1", s.pane);
        int glow = s.glowLevel();
        if (PictureFrame.isGlowDust(s.glow.stack())) {
            lines.add(Component.translatable("minepapers.tooltip.glow.self").withStyle(ChatFormatting.GRAY));
        } else if (glow > 0) {
            lines.add(Component.translatable("minepapers.tooltip.glow", glow).withStyle(ChatFormatting.GRAY));
        }
        if (s.isSigned()) {
            lines.add(Component.translatable("minepapers.tooltip.signer", s.signerName).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (s.nsfw) lines.add(Component.translatable("minepapers.tooltip.nsfw").withStyle(ChatFormatting.RED));
        if (s.isLocked()) {
            lines.add(Component.translatable("minepapers.tooltip.owner", s.ownerName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Рамка или холст: какие они и, если нужен блок, из какого. */
    private static void part(List<Component> lines, String key, String kindKey, PictureFrame.Material m) {
        MutableComponent text = Component.translatable(kindKey);
        ItemStack stack = m == null || m.item.isEmpty() ? ItemStack.EMPTY : borderStack(m.item);
        if (!stack.isEmpty()) text.append(", ").append(stack.getHoverName());
        lines.add(Component.translatable(key, text).withStyle(ChatFormatting.GRAY));
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
        return new ItemStack(Compat.item(MinePapers.parse(itemId)));
    }

}
