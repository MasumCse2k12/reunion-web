package bd.sammalani.alumni.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

/** Utility for loading avatar images: photo URL via Glide, or initials drawn on a colored circle. */
public class AvatarView {

    private static final int[] PALETTE = {
        0xFF1f6b4a, // brand green
        0xFF2d6a9f, // blue
        0xFF7b4f9e, // purple
        0xFF9e4f4f, // red
        0xFF4f7b9e, // steel
        0xFF9e7b4f, // brown
        0xFF4f9e7b, // teal
        0xFF9e4f7b, // pink
    };

    public static void load(Context ctx, ImageView iv, String photoUrl, String name) {
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(ctx)
                .load(photoUrl)
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .placeholder(initialsDrawable(ctx, name, iv.getLayoutParams() != null ? iv.getLayoutParams().width : 40))
                .error(initialsDrawable(ctx, name, iv.getLayoutParams() != null ? iv.getLayoutParams().width : 40))
                .into(iv);
        } else {
            iv.setImageDrawable(initialsDrawable(ctx, name, iv.getLayoutParams() != null ? iv.getLayoutParams().width : 40));
        }
    }

    private static BitmapDrawable initialsDrawable(Context ctx, String name, int sizePx) {
        int size = sizePx > 0 ? sizePx : (int) (40 * ctx.getResources().getDisplayMetrics().density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int color = PALETTE[Math.abs((name != null ? name : "").hashCode()) % PALETTE.length];

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(color);
        canvas.drawOval(new RectF(0, 0, size, size), bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(size * 0.38f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        String initials = initials(name);
        Rect bounds = new Rect();
        textPaint.getTextBounds(initials, 0, initials.length(), bounds);
        float y = size / 2f - bounds.exactCenterY();
        canvas.drawText(initials, size / 2f, y, textPaint);

        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }
}
