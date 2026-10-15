package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.grihail.minepapers.ModConfig;

/**
 * Предметы в настраиваемой картине (FrameHolder версии) — на сервере: клики по слотам материалов окна (см. MaterialStack), Shift+клик из инвентаря, вставка материалов из конфига (PacketPasteMaterials) и сохранение настроек от клиента (PacketSaveFrame). У стопки картин-предметов в слоте по блоку на картину; изменились не все картины стопки — изменённые отделяются от неё и уходят в инвентарь.
 * Стопка предметов S — класс версии (ItemStack); версия даёт адаптер: что за стопка, слоты материалов, картина в руке, инвентарь игрока. Пустая стопка — empty() (null в 1.7.10, ItemStack.EMPTY в новых версиях).
 * Права (может ли игрок менять картину, разрешает ли сервер менять стопку) проверяет версия до вызова.
 */
public abstract class MaterialSlots<S> {

    /** Номера материалов — по порядку PictureFrame.materials() и PictureConfig.MATERIALS. */
    public static final int BORDER = 0, CANVAS = 1, GLOW = 2;

    // ---------- Стопка предметов ----------

    protected abstract S empty();

    protected abstract boolean isEmpty(S stack);

    protected abstract int count(S stack);

    protected abstract int maxStack(S stack);

    /** Копия стопки с другим числом предметов. */
    protected abstract S copy(S stack, int count);

    /** Убрать n предметов из самой стопки (у пустой не вызывается). */
    protected abstract void shrink(S stack, int n);

    /** Тот же предмет с теми же данными. */
    protected abstract boolean same(S a, S b);

    /** Предмет id (и в 1.7.10 метаданные meta; meta < 0 — любые). */
    protected abstract boolean matches(S stack, PictureConfig.Item item);

    /** Предмет id из реестра, один (пусто — такого нет); для творческого режима. */
    protected abstract S create(PictureConfig.Item item);

    // ---------- Картина и игрок ----------

    /** Настройки картины (сами материалы в них — у версии). */
    protected abstract PictureSettings frame();

    /** Сколько картин настраивается: вся стопка картин-предметов или одна висящая. */
    protected abstract int pictures();

    /** Предмет в слоте материала index. */
    protected abstract S material(int index);

    /** Положить предмет в слот материала index (по одному на картину; пусто — вынуть) — только в настройки окна, без записи в картину. */
    protected abstract void setMaterial(int index, S stack);

    /** Можно ли положить предмет в слот index: саму рамку для картин нельзя (картина в картине), в слот свечения — только светящиеся блоки. */
    protected abstract boolean accepts(int index, S stack);

    /** Настройки изменились: висящая картина рассылает их игрокам, картина-предмет записывает в себя. */
    protected abstract void settingsChanged();

    /** Стопка картин-предметов (null — висящая картина). */
    protected abstract S held();

    /** Записать в картину-предмет stack её настройки, где у материала index — material. */
    protected abstract void storeMaterial(S stack, int index, S material);

    /** Перечитать настройки окна из картины-предмета: её поменяли мимо окна. */
    protected abstract void reload();

    /** Предмет в руке мыши. */
    protected abstract S carried();

    protected abstract void setCarried(S stack);

    /** В инвентарь игрока, а что не влезло — на землю (стопка не больше максимальной). */
    protected abstract void addToInventory(S stack);

    /** Основной инвентарь игрока (с хотбаром): число слотов, предмет в слоте и забрать из слота n предметов. */
    protected abstract int inventorySize();

    protected abstract S inventoryItem(int slot);

    protected abstract S removeFromInventory(int slot, int n);

    protected abstract boolean creative();

    // ---------- Окно ----------

    /** Клик по слоту материала: пустой рукой — забрать блоки (картины остаются без материала), с блоками — положить или заменить; right — правой кнопкой (забрать половину, положить один). */
    public final void click(int index, boolean right) {
        S current = material(index);
        S carried = carried();
        int pictures = pictures();
        if (isEmpty(carried)) {
            if (isEmpty(current)) return;
            setCarried(change(index, empty(), MaterialStack.takeCount(pictures, right, maxStack(current))));
            return;
        }
        if (!accepts(index, carried) || !isEmpty(current) && same(current, carried)) return;
        int count = MaterialStack.putCount(pictures, count(carried), right);
        S old = change(index, carried, count);
        shrink(carried, count);
        if (count(carried) > 0) {
            give(old);
        } else if (isEmpty(old) || count(old) <= maxStack(old)) {
            setCarried(old);
        } else {
            setCarried(empty());
            give(old);
        }
    }

    /** Shift+клик по слоту материала: все блоки — в инвентарь. */
    public final void takeToInventory(int index) {
        if (isEmpty(material(index))) return;
        give(change(index, empty(), pictures()));
    }

    /** Shift+клик по стопке stack в инвентаре: блоки — в первый пустой подходящий слот материала, по блоку на картину; из stack они убираются. @return переложено ли что-нибудь */
    public final boolean quickMove(S stack) {
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            if (!isEmpty(material(i)) || !accepts(i, stack)) continue;
            int count = MaterialStack.putCount(pictures(), count(stack), false);
            change(i, stack, count);
            shrink(stack, count);
            return true;
        }
        return false;
    }

    /** У count картин материал index становится material (пусто — снять). Изменились не все картины стопки — изменённые отделяются от неё и уходят в инвентарь. @return блоки прежнего материала, по одному с каждой изменённой картины */
    private S change(int index, S material, int count) {
        S old = material(index);
        S held = held();
        if (held == null) {
            // Висящая картина — одна.
            setMaterial(index, material);
            settingsChanged();
        } else {
            if (count >= count(held)) {
                storeMaterial(held, index, material);
            } else {
                S split = copy(held, count);
                storeMaterial(split, index, material);
                shrink(held, count);
                give(split);
            }
            reload();
        }
        return isEmpty(old) ? empty() : copy(old, count);
    }

    /** В инвентарь пачками не больше стопки, а что не влезло — на землю. */
    private void give(S stack) {
        if (isEmpty(stack)) return;
        int left = count(stack);
        while (left > 0) {
            int n = Math.min(left, maxStack(stack));
            addToInventory(copy(stack, n));
            left -= n;
        }
    }

    // ---------- Вставка конфига ----------

    /** Вставка материалов из конфига: has[i] — материал i указан, items[i] — какой (null — вынуть). Предметы берутся из инвентаря игрока, по одному на каждую картину стопки (прежние возвращаются), в творческом режиме недостающие создаются. Только если сервер разрешает менять эту стопку (ModConfig.stackMaterials). @return каких предметов не хватило (пусто — всё положено) */
    public final List<PictureConfig.Item> paste(boolean[] has, PictureConfig.Item[] items) {
        List<PictureConfig.Item> missing = new ArrayList<>();
        int count = pictures();
        if (!MaterialStack.editable(ModConfig.stackMaterials, count)) return missing;
        for (int i = 0; i < PictureConfig.MATERIALS.length && i < has.length; i++) {
            if (!has[i]) continue;
            PictureConfig.Item item = items[i];
            S current = material(i);
            if (item == null) {
                returnMaterial(i);
                continue;
            }
            if (!isEmpty(current) && matches(current, item)) continue;
            S taken = take(i, item, count);
            if (isEmpty(taken) && creative()) {
                taken = create(item);
                if (!isEmpty(taken) && !accepts(i, taken)) taken = empty();
            }
            if (isEmpty(taken)) {
                missing.add(item);
                continue;
            }
            returnMaterial(i);
            setMaterial(i, taken);
        }
        settingsChanged();
        return missing;
    }

    /** count подходящих предметов из инвентаря игрока; возвращает один из них. Если столько нет — ничего не забирает и возвращает пусто. */
    private S take(int index, PictureConfig.Item item, int count) {
        int found = 0;
        for (int s = 0; s < inventorySize(); s++) {
            S stack = inventoryItem(s);
            if (fits(stack, index, item)) found += count(stack);
        }
        if (found < count) return empty();
        S taken = empty();
        for (int s = 0; s < inventorySize() && count > 0; s++) {
            S stack = inventoryItem(s);
            if (!fits(stack, index, item)) continue;
            S part = removeFromInventory(s, Math.min(count, count(stack)));
            if (isEmpty(taken)) taken = copy(part, 1);
            count -= count(part);
        }
        return taken;
    }

    /** Картину в картину не кладут (и настраиваемую картину-предмет так не забрать — см. accepts). */
    private boolean fits(S stack, int index, PictureConfig.Item item) {
        return !isEmpty(stack) && matches(stack, item) && accepts(index, stack);
    }

    /** Блоки материала index — игроку, по одному с каждой картины; слот пустеет. */
    private void returnMaterial(int index) {
        S current = material(index);
        if (isEmpty(current)) return;
        give(copy(current, pictures()));
        setMaterial(index, empty());
    }

    // ---------- Сохранение настроек ----------

    /** Новые настройки от клиента (PacketSaveFrame, при закрытии окна). readSettings читает их в frame(), не трогая материалов: материалы — настоящие предметы, их меняют только слоты окна. То, что клиент сам менять не может, остаётся (PictureSettings.keepServerFields). Блоки рамки и холста, которые выбранному виду не нужны, возвращаются игроку. */
    public final void save(Runnable readSettings, UUID player, String playerName) {
        PictureSettings frame = frame();
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        UUID owner = frame.owner;
        String ownerName = frame.ownerName;
        UUID signer = frame.signer;
        String signerName = frame.signerName;
        readSettings.run();
        frame.keepServerFields(origin, files, owner, ownerName, signer, signerName, player, playerName);
        // Подпись уникальна: стопку картин-предметов не подписывают (подпись размножилась бы).
        if (signer == null && pictures() > 1) {
            frame.signer = null;
            frame.signerName = "";
        }
        if (frame.mayEdit(player) && MaterialStack.editable(ModConfig.stackMaterials, pictures())) {
            if (!frame.usesBorder()) returnMaterial(BORDER);
            if (!frame.usesCanvas()) returnMaterial(CANVAS);
        }
        settingsChanged();
    }
}
