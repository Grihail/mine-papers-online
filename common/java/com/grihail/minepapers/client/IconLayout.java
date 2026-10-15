package com.grihail.minepapers.client;

import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.PictureSettings;

/** Раскладка иконки настроенной картины на сетке 16x16, в пикселях иконки (x вправо, y вниз): холст вписан с сохранением пропорций, рамка — кольцом вокруг него (если она есть). Рисует версия. */
public final class IconLayout {

    public static final int ICON = 16;
    /** Ширина рамки на иконке в пикселях. */
    public static final int RING = 2;

    /** Ширина кольца рамки (0 — без рамки) и сторона квадрата, в который вписан холст. */
    public final int ring, inner;
    /** Размер холста. */
    public final int iw, ih;
    /** Внешний край рамки и сам холст. */
    public final int x0, y0, x1, y1, cx0, cy0, cx1, cy1;
    public final boolean canvasBlock, plate;
    /** Картина NSFW, скрытая настройкой hideNsfw: картинка не загружается, на заглушке — значок "18+". */
    public final boolean hidden;
    private final PictureSettings s;
    /** Размер картины в блоках. */
    private final double w, h;
    /** Насколько багет в мире заходит на холст (на иконке его кольцо и есть эта часть). */
    private final double overlap;

    public IconLayout(PictureSettings s) {
        this.s = s;
        hidden = ModConfig.hides(s.nsfw);
        canvasBlock = s.getCanvasKind() == PictureSettings.CANVAS_BLOCK;
        plate = s.getCanvasKind() == PictureSettings.CANVAS_PICTURE;
        ring = s.getFrameShape() != PictureSettings.FRAME_NONE ? RING : 0;
        inner = ICON - 2 * ring;
        w = s.getWidthBlocks();
        h = s.getHeightBlocks();
        iw = w >= h ? inner : Math.max(1, (int) Math.round(inner * w / h));
        ih = h >= w ? inner : Math.max(1, (int) Math.round(inner * h / w));
        x0 = (ICON - iw - 2 * ring) / 2;
        y0 = (ICON - ih - 2 * ring) / 2;
        x1 = x0 + iw + 2 * ring;
        y1 = y0 + ih + 2 * ring;
        cx0 = x0 + ring;
        cy0 = y0 + ring;
        cx1 = x1 - ring;
        cy1 = y1 - ring;
        double ov = s.getFrameShape() == PictureSettings.FRAME_MOLDING ? Math.min(s.getMoldingBlocks(), Math.min(w, h) / 2) : 0;
        overlap = w - 2 * ov < 1e-6 || h - 2 * ov < 1e-6 ? 0 : ov;
    }

    /** Миниатюра картинки на холст (null — пока не готова, нет источника или картина скрыта). */
    public PictureManager.Variant thumbnail() {
        String key = hidden ? null : s.getSourceKey();
        return key == null ? null : PictureManager.thumbnail(key, ImageStylizer.Layout.icon(s, inner));
    }

    /** Где на иконке видна миниатюра thumb (внутри — только то, что видно из-под багета): {x0, y0, x1, y1} на иконке и {u0, v0, u1, v1} в текстуре миниатюры (0..1); null — ничего не видно. */
    public double[] picture(PictureManager.Variant thumb) {
        double ov = overlap, ww = w - 2 * ov, wh = h - 2 * ov;
        double px0 = Math.max(thumb.x0, ov), px1 = Math.min(thumb.x1, w - ov);
        double py0 = Math.max(thumb.y0, ov), py1 = Math.min(thumb.y1, h - ov);
        if (px1 - px0 <= 1e-6 || py1 - py0 <= 1e-6) return null;
        return new double[] {
            cx0 + (px0 - ov) / ww * iw, cy0 + (h - ov - py1) / wh * ih,
            cx0 + (px1 - ov) / ww * iw, cy0 + (h - ov - py0) / wh * ih,
            (px0 - thumb.x0) / (thumb.x1 - thumb.x0), (thumb.y1 - py1) / (thumb.y1 - thumb.y0),
            (px1 - thumb.x0) / (thumb.x1 - thumb.x0), (thumb.y1 - py0) / (thumb.y1 - thumb.y0) };
    }

    /** Значок "18+" скрытой картины NSFW: квадрат по меньшей стороне холста, по центру — {x0, y0, x1, y1}. */
    public double[] nsfwMark() {
        int size = Math.min(cx1 - cx0, cy1 - cy0);
        double mx = (cx0 + cx1 - size) / 2.0, my = (cy0 + cy1 - size) / 2.0;
        return new double[] { mx, my, mx + size, my + size };
    }
}
