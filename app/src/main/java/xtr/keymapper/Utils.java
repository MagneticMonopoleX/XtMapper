package xtr.keymapper;

import android.graphics.PixelFormat;
import android.os.Build;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {
    public static final String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * @param key input key code KEY_X
     * @return the index of X in alphabet
     */
    public static int obtainIndex(String key) {
        return alphabet.indexOf(key.substring(4));
    }

    public static BufferedReader geteventStream(String nativeLibraryDir) throws IOException {
        Process sh = Runtime.getRuntime().exec("sh");
        DataOutputStream outputStream = new DataOutputStream(sh.getOutputStream());

        outputStream.writeBytes("exec env LD_PRELOAD=" + nativeLibraryDir + "/libgetevent.so getevent -ql\n");
        outputStream.flush();

        return new BufferedReader(new InputStreamReader(sh.getInputStream()));
    }

    public static WindowManager.LayoutParams getPointerLayoutParams(int type) {
        WindowManager.LayoutParams mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                // Make the underlying application window visible through the cursor
                PixelFormat.TRANSLUCENT);
        mParams.alpha = 0.5f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return mParams;
    }

    public static void applyEmptyTouchableRegion(android.view.View view) {
        if (view == null) return;
        android.view.View.OnAttachStateChangeListener listener = new android.view.View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(android.view.View v) {
                registerEmptyTouchableRegion(v);
            }

            @Override
            public void onViewDetachedFromWindow(android.view.View v) {}
        };
        view.addOnAttachStateChangeListener(listener);
        if (view.isAttachedToWindow()) {
            registerEmptyTouchableRegion(view);
        }
    }

    private static void registerEmptyTouchableRegion(android.view.View view) {
        try {
            Class<?> listenerClass = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            Class<?> infoClass = Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            java.lang.reflect.Field touchableRegionField = infoClass.getField("touchableRegion");
            java.lang.reflect.Method setTouchableInsetsMethod = infoClass.getMethod("setTouchableInsets", int.class);
            java.lang.reflect.Field touchableInsetsRegionField = infoClass.getField("TOUCHABLE_INSETS_REGION");
            int touchableInsetsRegion = touchableInsetsRegionField.getInt(null);

            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onComputeInternalInsets".equals(method.getName()) && args != null && args.length > 0) {
                            Object info = args[0];
                            android.graphics.Region region = (android.graphics.Region) touchableRegionField.get(info);
                            if (region != null) {
                                region.setEmpty();
                            }
                            setTouchableInsetsMethod.invoke(info, touchableInsetsRegion);
                        }
                        return null;
                    }
            );

            java.lang.reflect.Method addListenerMethod = android.view.ViewTreeObserver.class.getMethod("addOnComputeInternalInsetsListener", listenerClass);
            addListenerMethod.invoke(view.getViewTreeObserver(), listener);
        } catch (Throwable t) {
            android.util.Log.w("Utils", "Failed to register empty touchable region", t);
        }
    }


}
