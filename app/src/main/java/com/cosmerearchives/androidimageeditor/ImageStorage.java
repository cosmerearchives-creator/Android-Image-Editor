package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

/** Copies one user-selected image into private app storage for offline VR viewing.
 * Original files are never modified. A later milestone will add project management.
 */
public final class ImageStorage {
    private static final String FILE_NAME = "vr_import.png";
    private static final int MAX_EDGE = 2048;
    private ImageStorage() {}

    private static File imageFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean hasImage(Context context) {
        return imageFile(context).isFile();
    }

    public static void importImage(Context context, Uri uri) throws IOException {
        // Decode with bounds first, downsample before allocation, then cap to 2048.
        final BitmapFactory.Options dimensions = new BitmapFactory.Options();
        dimensions.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Selected image is unavailable");
            BitmapFactory.decodeStream(in, null, dimensions);
        }
        if (dimensions.outWidth < 1 || dimensions.outHeight < 1)
            throw new IOException("Unsupported or invalid image");
        BitmapFactory.Options opts = new BitmapFactory.Options();
        int sample = 1;
        while (dimensions.outWidth / sample > MAX_EDGE * 2
                || dimensions.outHeight / sample > MAX_EDGE * 2) sample *= 2;
        opts.inSampleSize = sample;
        Bitmap image;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Selected image is unavailable");
            image = BitmapFactory.decodeStream(in, null, opts);
        }
        if (image == null) throw new IOException("Unsupported image format");
        Bitmap output = image;
        try {
            if (image.getWidth() > MAX_EDGE || image.getHeight() > MAX_EDGE) {
                float scale = Math.min((float)MAX_EDGE / image.getWidth(),
                                       (float)MAX_EDGE / image.getHeight());
                output = Bitmap.createScaledBitmap(image,
                        Math.max(1,Math.round(image.getWidth()*scale)),
                        Math.max(1,Math.round(image.getHeight()*scale)), true);
            }
            File dest = imageFile(context);
            File staging = new File(context.getFilesDir(), FILE_NAME + ".partial");
            try (FileOutputStream stream = new FileOutputStream(staging)) {
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IOException("Could not save imported image");
                stream.getFD().sync();
            } catch (IOException error) {
                staging.delete();
                throw error;
            }
            if (!staging.renameTo(dest)) {
                staging.delete();
                throw new IOException("Could not replace stored image");
            }
        } finally {
            if (output != image) output.recycle();
            image.recycle();
        }
    }

    /** Called on the native XR thread, returning tightly packed Java ARGB pixels. */
    public static int[] loadPixels(Context context) {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile(context).getPath());
        if (bitmap == null) return null;
        try {
            int[] data = new int[bitmap.getWidth()*bitmap.getHeight() + 2];
            data[0] = bitmap.getWidth();
            data[1] = bitmap.getHeight();
            bitmap.getPixels(data, 2, bitmap.getWidth(), 0, 0,
                    bitmap.getWidth(), bitmap.getHeight());
            return data;
        } finally {
            bitmap.recycle();
        }
    }
}
