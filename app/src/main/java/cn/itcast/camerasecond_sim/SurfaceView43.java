package cn.itcast.camerasecond_sim;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

public class SurfaceView43 extends SurfaceView {
    public SurfaceView43(Context context) {
        super(context);
    }

    public SurfaceView43(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SurfaceView43(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

//    public SurfaceView43(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d("MainActivity","onMeasure()");

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width / 3 * 4;
        setMeasuredDimension(width, height);
    }
}
