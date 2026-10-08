package xtr.keymapper.floatingkeys;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import xtr.keymapper.databinding.FloatingKeyBinding;

public class MovableFloatingActionKey implements View.OnTouchListener {

    private float dX, dY;
    public final TextView textView;
    public final FrameLayout frameView;
    private OnXyChangeListener xyChangeListener;
    public final MaterialButton closeButton;
    public final FloatingKeyBinding binding;
    private OnKeyRemoved callback;
    boolean isSwipeKey = false;

    public interface OnKeyRemoved {

        void onKeyRemoved(MovableFloatingActionKey key);
    }
    public MovableFloatingActionKey(Context context, @Nullable android.view.ViewGroup parent) {

        binding = FloatingKeyBinding.inflate(LayoutInflater.from(context), parent, true);
        frameView = binding.getRoot();
        frameView.setOnTouchListener(this);

        closeButton = binding.closeButton;
        textView = binding.textView;

        closeButton.setOnClickListener(v -> {
            if (!isSwipeKey) {
                frameView.removeAllViews();
                frameView.invalidate();
                if (callback != null) callback.onKeyRemoved(this);
            } else {
                setText(" ");
            }
        });
    }

    public MovableFloatingActionKey(Context context, OnKeyRemoved callback, ViewGroup parent) {
        this(context, parent);
        this.callback = callback;
    }
    public MovableFloatingActionKey(Context context, boolean isSwipeKey, ViewGroup parent) {
        this(context, parent);
        this.isSwipeKey = isSwipeKey;
    }

    public String getData(){
        float pivot = frameView.getPivotX();
        if (pivot <= 0 && frameView.getWidth() > 0) {
            pivot = frameView.getWidth() / 2f;
        }
        if (pivot <= 0) {
            pivot = 35f * frameView.getContext().getResources().getDisplayMetrics().density;
        }
        return "KEY_" + getText() + " " + frameView.getX() + " " + frameView.getY() + " " + pivot;
    }

    public void setText(CharSequence s) {
        textView.setText(s);
    }

    public void setText(@StringRes int resId) {
        textView.setText(resId);
    }

    public String getText(){
        return textView.getText().toString().toUpperCase();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent){
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                dX = view.getX() - motionEvent.getRawX();
                dY = view.getY() - motionEvent.getRawY();
                return true; // Consumed
            }
            case MotionEvent.ACTION_UP: {
                return frameView.performClick();
            }
            case MotionEvent.ACTION_MOVE: {

                View viewParent = (View)view.getParent();
                int parentWidth = viewParent != null ? viewParent.getWidth() : 0;
                int parentHeight = viewParent != null ? viewParent.getHeight() : 0;
                int viewWidth = view.getWidth() > 0 ? view.getWidth() : (int) (70 * view.getContext().getResources().getDisplayMetrics().density);
                int viewHeight = view.getHeight() > 0 ? view.getHeight() : (int) (70 * view.getContext().getResources().getDisplayMetrics().density);

                float newX = motionEvent.getRawX() + dX;
                if (parentWidth > 0) {
                    newX = Math.max(layoutParams.leftMargin, newX);
                    newX = Math.min(parentWidth - viewWidth - layoutParams.rightMargin, newX);
                }

                float newY = motionEvent.getRawY() + dY;
                if (parentHeight > 0) {
                    newY = Math.max(layoutParams.topMargin, newY);
                    newY = Math.min(parentHeight - viewHeight - layoutParams.bottomMargin, newY);
                }

                view.setX(newX);
                view.setY(newY);

                if (xyChangeListener != null)
                    xyChangeListener.onNewXY(newX, newY);

                return true; // Consumed
            }
            default:
                return frameView.onTouchEvent(motionEvent);
        }
    }
    public void setXyChangeListener(OnXyChangeListener xyChangeListener) {
        this.xyChangeListener = xyChangeListener;
        xyChangeListener.onNewXY(frameView.getX(), frameView.getY());
    }

    public interface OnXyChangeListener {
        void onNewXY(float x, float y);
    }

    public void setX(float x) {
        frameView.setX(x);
    }

    public void setY(float y) {
        frameView.setY(y);
    }

    public float getX() {
        return frameView.getX();
    }

    public float getY() {
        return frameView.getY();
    }

    public void setOnClickListener(View.OnClickListener listener) {
        frameView.setOnClickListener(listener);
    }
}