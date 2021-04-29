package com.cxd.av.views;

import android.graphics.PointF;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class RotationGestureDetector {
    private final String TAG = RotationGestureDetector.class.getSimpleName();
    private static final int INVALID_POINTER_ID = -1;
    private float mFirstX, mFirstY, mSecondX, mSecondY;
    private int mPtrID1, mPtrID2;
    private float mAngle;
    private float mDeltaAngle;
    private float mPreviousAngle;
    private OnRotationGestureListener mListener;
    private PointF mFirstPoint = new PointF();
    private PointF mSecPoint = new PointF();
    private View mView;
    private boolean mFirstTouch;

    public RotationGestureDetector(OnRotationGestureListener listener, View view){
        mListener = listener;
        mView = view;
        mPtrID1 = INVALID_POINTER_ID;
        mPtrID2 = INVALID_POINTER_ID;
    }

    public float getAngle() {
        return mAngle;
    }

    public float getDeltaAngle() {
        return mDeltaAngle;
    }

    public boolean onTouchEvent(MotionEvent event){
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
                Log.d(TAG, ",ACTION_OUTSIDE");
                break;
            case MotionEvent.ACTION_DOWN:
                mPtrID1 = event.getPointerId(event.getActionIndex());
                mFirstTouch = true;
                mAngle = 0;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mPtrID2 = event.getPointerId(event.getActionIndex());
                mFirstTouch = true;
                mAngle = 0;
                if (mView != null) {
                    getRawPoint(event, mPtrID1, mSecPoint);
                    getRawPoint(event, mPtrID2, mFirstPoint);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.d(TAG, ",ACTION_POINTER_DOWN");
                        mSecondX = event.getRawX(event.findPointerIndex(mPtrID1));
                        mSecondY = event.getRawY(event.findPointerIndex(mPtrID1));
                        mFirstX = event.getRawX(event.findPointerIndex(mPtrID2));
                        mFirstY = event.getRawY(event.findPointerIndex(mPtrID2));
                    } else {
                        mSecondX = event.getX(event.findPointerIndex(mPtrID1));
                        mSecondY = event.getY(event.findPointerIndex(mPtrID1));
                        mFirstX = event.getX(event.findPointerIndex(mPtrID2));
                        mFirstY = event.getY(event.findPointerIndex(mPtrID2));
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(mPtrID1 != INVALID_POINTER_ID && mPtrID2 != INVALID_POINTER_ID){
                    if (mFirstTouch) {
                        mAngle = 0;
                        mFirstTouch = false;
                    }
                    if (mView != null) {
                        PointF tmpFstPoint = new PointF();
                        PointF tmpSecPoint = new PointF();
                        mAngle = angleBetweenRawXY(mFirstPoint, mSecPoint, tmpFstPoint, tmpSecPoint);
                    } else {
                        float tmpFstX, tmpFstY, tmpSecX, tmpSecY;
                        Log.d(TAG, ",ACTION_MOVE,mPtrID1="+mPtrID1+",mPtrID2="+mPtrID2);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Log.d(TAG, ",ACTION_MOVE");
                            tmpSecX = event.getRawX(event.findPointerIndex(mPtrID1));
                            tmpSecY = event.getRawY(event.findPointerIndex(mPtrID1));
                            tmpFstX = event.getRawX(event.findPointerIndex(mPtrID2));
                            tmpFstY = event.getRawY(event.findPointerIndex(mPtrID2));
                        } else {
                            tmpSecX = event.getX(event.findPointerIndex(mPtrID1));
                            tmpSecY = event.getY(event.findPointerIndex(mPtrID1));
                            tmpFstX = event.getX(event.findPointerIndex(mPtrID2));
                            tmpFstY = event.getY(event.findPointerIndex(mPtrID2));
                        }
                        mAngle = angleBetweenLines(mFirstX, mFirstY, mSecondX, mSecondY, tmpFstX, tmpFstY, tmpSecX, tmpSecY);
                    }

                    if (mListener != null) {
                        mListener.OnRotation(this);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mPtrID1 = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mPtrID2 = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_CANCEL:
                mPtrID1 = INVALID_POINTER_ID;
                mPtrID2 = INVALID_POINTER_ID;
                break;
        }
        return true;
    }

    //触摸点相对于屏幕左上角
    void getRawPoint(MotionEvent ev, int index, PointF point) {
        final int[] location = { 0, 0 };
        mView.getLocationOnScreen(location);

        float x = ev.getX(index);
        float y = ev.getY(index);

        double angle = Math.toDegrees(Math.atan2(y, x));
        angle += mView.getRotation();

        final float length = PointF.length(x, y);

        x = (float) (length * Math.cos(Math.toRadians(angle))) + location[0];
        y = (float) (length * Math.sin(Math.toRadians(angle))) + location[1];

        point.set(x, y);
    }

    private float angleBetweenRawXY(PointF fPoint, PointF sPoint, PointF nFpoint, PointF nSpoint) {
        float angle1 = (float) Math.atan2((fPoint.y - sPoint.y), (fPoint.x - sPoint.x));
        float angle2 = (float) Math.atan2((nFpoint.y - nSpoint.y), (nFpoint.x - nSpoint.x));

        float angle = ((float) Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return -angle;
    }

    private float angleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY)
    {
        float angle1 = (float) Math.atan2( (fY - sY), (fX - sX) );
        float angle2 = (float) Math.atan2( (nfY - nsY), (nfX - nsX) );

        float angle = ((float) Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return angle;
    }

    public static interface OnRotationGestureListener {
        public void OnRotation(RotationGestureDetector rotationDetector);
    }
}
