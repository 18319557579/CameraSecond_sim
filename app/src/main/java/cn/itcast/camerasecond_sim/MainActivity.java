package cn.itcast.camerasecond_sim;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.qiniu.pili.droid.streaming.AVCodecType;
import com.qiniu.pili.droid.streaming.StreamingManager;
import com.qiniu.pili.droid.streaming.StreamingProfile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Handler.Callback {

    private static final int MSG_OPEN_CAMERA = 1;
    private static final int MSG_CLOSE_CAMERA = 2;
    private static final int MSG_SET_PREVIEW_SIZE = 3;
    private static final int MSG_SET_PREVIEW_SURFACE = 4;
    private static final int MSG_START_PREVIEW = 5;
    private static final int MSG_STOP_PREVIEW = 6;
    private static final int MSG_SET_PICTURE_SIZE = 7;
    private static final int MSG_TAKE_PICTURE = 8;

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int PREVIEW_FORMAT = ImageFormat.NV21;

    private HandlerThread mCameraThread = null;

    private Handler mCameraHandler = null;

    private Camera.CameraInfo mFrontCameraInfo= null;
    private int mFrontCameraId = -1;

    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;

    private Camera mCamera;
    private int mCameraId = -1;
    private Camera.CameraInfo mCameraInfo;

    private SurfaceHolder mPreviewSurface;
    private int mPreviewSurfaceWidth;
    private int mPreviewSurfaceHeight;

    private DeviceOrientationListener mDeviceOrientationListener;

    private SurfaceView cameraPreview;

    //????????????
    private StreamingManager mStreamingManager;
    private StreamingProfile mProfile;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_OPEN_CAMERA: {
                openCamera(msg.arg1);
                break;
            }
            case MSG_CLOSE_CAMERA: {
                closeCamera();
                break;
            }
            case MSG_SET_PREVIEW_SIZE: {
                int shortSide = msg.arg1;
                int longSide = msg.arg2;
                setPreviewSize(shortSide, longSide);
                break;
            }
            case MSG_SET_PREVIEW_SURFACE: {
                SurfaceHolder previewSurface = ((SurfaceHolder) msg.obj);
                setPreviewSurface(previewSurface);
                break;
            }
            case MSG_START_PREVIEW: {
                startPreview();
                break;
            }
            case MSG_STOP_PREVIEW: {
                stopPreview();
                break;
            }
            /*case MSG_SET_PICTURE_SIZE: {
                int shortSide = msg.arg1;
                int longSide = msg.arg2;
                setPictureSize(shortSide, longSide);
                break;
            }*/
            /*case MSG_TAKE_PICTURE: {
                takePicture();
            }*/
            default:
                throw new IllegalArgumentException("Illegal message: " + msg.what);
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate()??????");

        mDeviceOrientationListener = new DeviceOrientationListener(this);

        startCameraThread();

        initCameraInfo();

        //??????SurfaceView??????????????????
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.getHolder().addCallback(new PreviewSurfaceCallback());
        //????????????????????????????????????????????????
        Button switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setOnClickListener(new OnSwitchCameraButtonClickListener());

        /*Button takePictureButton = findViewById(R.id.take_picture);
        takePictureButton.setOnClickListener(new OnTakePictureButtonClickListener());*/


        //??????demo
        initEncodingProfile();
        initStreamingManager();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()??????");

        DeviceOrientationListener deviceOrientationListener = mDeviceOrientationListener;
        if (deviceOrientationListener != null) {
            deviceOrientationListener.enable();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()??????");

        // ???????????????????????????????????????????????????????????????????????????????????????????????????
        if (!isRequiredPermissionGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS_CODE);
        }else if (mCameraHandler != null) {
            mCameraHandler.obtainMessage(MSG_OPEN_CAMERA, getCameraId(), 0).sendToTarget();
            cameraPreview.getHolder().setFormat(PREVIEW_FORMAT);
        }

        //??????demo
        mStreamingManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()??????");

        if (mCameraHandler != null) {
            mCameraHandler.removeMessages(MSG_OPEN_CAMERA);
            mCameraHandler.sendEmptyMessage(MSG_CLOSE_CAMERA);
        }
        stopPreview();

        //??????demo
        mStreamingManager.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()??????");

        DeviceOrientationListener deviceOrientationListener = mDeviceOrientationListener;
        if (deviceOrientationListener != null) {
            deviceOrientationListener.disable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()??????");

        stopCameraThread();

        //??????demo
        mStreamingManager.destroy();
    }

    /**
     * ???????????????id??????????????????????????????id?????????????????????id???
     * @return
     */
    private int getCameraId() {
        Log.d(TAG, "getCameraId() ???????????????Id");
        if (hasBackCamera()) {
            return mBackCameraId;
        }else if (hasFrontCamera()) {
            return mFrontCameraId;
        }else {
            throw new RuntimeException("No available camera id found.");
        }
    }

    /**
     * ??????????????????????????????????????????????????????id
     * @return ???????????????????????????id
     */
    private int switchCameraId() {
        Log.d(TAG, "switchCameraId() ?????????????????????Id");
        if (mCameraId == mFrontCameraId && hasBackCamera()) {
            return mBackCameraId;
        }else if (mCameraId == mBackCameraId && hasFrontCamera()) {
            return mFrontCameraId;
        }else {
            throw new RuntimeException("No available camera id to switch");
        }
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????? false???
     * @return true ??????????????????, false ????????????????????????
     */
    private boolean isRequiredPermissionGranted() {
        Log.d(TAG, "isRequiredPermissionGranted() ???????????????????????????");
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    /**
     * ????????????HandlerThread??????????????????Handler???????????????
     */
    private void startCameraThread(){
        Log.d(TAG, "startCameraThread() ??????HandlerThread?????????Handler");
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper(), this);
    }

    private void stopCameraThread() {
        Log.d(TAG, "stopCameraThread() ??????HandlerThread");

        if (mCameraThread != null) {
            mCameraThread.quitSafely();
        }
        mCameraThread = null;
        mCameraHandler = null;
    }

    /**
     * ?????????????????????id ??? ??????
     */
    private void initCameraInfo() {
        Log.d(TAG, "initCameraInfo() ?????????????????????id ??? ??????");
        int numberOfCameras = Camera.getNumberOfCameras();//?????????????????????
        for (int cameraId = 0; cameraId < numberOfCameras; cameraId++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            //??????????????????????????????????????????cameraInfo???,cameraId?????????????????????????????????
            Camera.getCameraInfo(cameraId, cameraInfo);
            //?????????????????????cameraInfo???facing()????????????????????????????????????
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // ?????????????????????????????????
                mBackCameraId = cameraId;
                mBackCameraInfo = cameraInfo;
            }else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //?????????????????????????????????
                mFrontCameraId = cameraId;
                mFrontCameraInfo = cameraInfo;
            }


        }
    }

    /**
     * ?????????????????????
     */
    private void openCamera(int cameraId) {
        Log.d(TAG, "openCamera() ?????????????????????");

        Camera camera = mCamera;
        if (camera != null) {
            throw new RuntimeException("You must close previous camera before open a new one.");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            mCamera = Camera.open(cameraId);

            //?????????????????????????????????????????????id??????????????????????????????
            mCameraId = cameraId;
            mCameraInfo = cameraId == mFrontCameraId ? mFrontCameraInfo : mBackCameraInfo;

            Log.d(TAG, "Camera[" + cameraId + "] has been opened");
            assert mCamera != null;
            Log.d(TAG, "aaa");
            mCamera.setDisplayOrientation(getCameraDisplayOrientation(mCameraInfo));
            Log.d(TAG, "bbb");
        }
    }

    /**
     * ???????????????????????????????????????
     */
    private int getCameraDisplayOrientation(Camera.CameraInfo cameraInfo) {
        Log.d(TAG, "getCameraDisplayOrientation() ????????????????????????????????????");

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * ????????????
     */
    private void closeCamera() {
        Log.d(TAG, "closeCamera() ????????????");

        Camera camera = mCamera;
        mCamera = null;
        if (camera != null) {
            camera.release();
            mCameraId = -1;
            mCameraInfo = null;
        }
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void setPreviewSize(int shortSide, int longSide) {
        Log.d(TAG, "setPreviewSize() ??????????????????????????????");

        Camera camera = mCamera;
        if (camera != null && shortSide != 0 && longSide != 0) {
            float aspectRatio = (float) longSide / shortSide;
            Camera.Parameters parameters = camera.getParameters();
            //????????????????????????????????????
            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            for (Camera.Size previewSize : supportedPreviewSizes) {
                if ((float) previewSize.width / previewSize.height == aspectRatio && previewSize.height <= shortSide && previewSize.width <= longSide) {
                    parameters.setPreviewSize(previewSize.width, previewSize.height);
                    Log.d(TAG, "setPreviewSize() called with: width = " + previewSize.width + "; height = " + previewSize.height);

                    if (isPreviewFormatSupported(parameters, PREVIEW_FORMAT)) {
                        //?????????????????????PREVIEW_FORMAT???????????????????????????PREVIEW_FORMAT??????
                        parameters.setPreviewFormat(PREVIEW_FORMAT);

                        //???????????????????????????????????????????????????????????????????????? Bit??????????????????????????????????????????????????????
                        int frameWidth =  previewSize.width;
                        int frameHeight = previewSize.height;
                        int previewFormat = parameters.getPreviewFormat();
                        PixelFormat pixelFormat = new PixelFormat();
                        //(??????)?????????previewFormat?????????????????????pixelFormat????????????
                        PixelFormat.getPixelFormatInfo(previewFormat, pixelFormat);
                        int bufferSize = (frameWidth * frameHeight * pixelFormat.bitsPerPixel) / 8;
                        camera.addCallbackBuffer(new byte[bufferSize]);
                        camera.addCallbackBuffer(new byte[bufferSize]);
                        camera.addCallbackBuffer(new byte[bufferSize]);
                        Log.d(TAG, "Add three callback buffers with size:" + bufferSize);

                    }
                    camera.setParameters(parameters);
                    break;
                }
            }
        }
    }

    /**
     * ????????????????????????????????????????????????
     */
    private boolean isPreviewFormatSupported(Camera.Parameters parameters, int format) {
        Log.d(TAG, "isPreviewFormatSupported() ???????????????????????????????????????");

        List<Integer> supportedPreviewFormats = parameters.getSupportedPreviewFormats();
        return supportedPreviewFormats != null && supportedPreviewFormats.contains(format);
    }

    /**
     * ?????????????????????????????????????????????
     */
//    private void setPictureSize(int shortSide, int longSide) {
//        Camera camera = mCamera;
//        if (camera != null && shortSide != 0 && longSide != 0) {
//            float aspectRatio = (float) longSide / shortSide;
//            Camera.Parameters parameters = camera.getParameters();
//            List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
//            for (Camera.Size pictureSize : supportedPictureSizes) {
//                if ((float) pictureSize.width / pictureSize.height == aspectRatio) {
//                    parameters.setPictureSize(pictureSize.width, pictureSize.height);
//                    camera.setParameters(parameters);
//                    Log.d(TAG, "setPictureSize() called with: width = " + pictureSize.width + "; height = " + pictureSize.height);
//                    break;
//                }
//            }
//        }
//    }

    /**
     * ????????????Surface
     * @param previewSurface
     */
    private void setPreviewSurface(SurfaceHolder previewSurface) {
        Log.d(TAG, "setPreviewSurface() ????????????Surface");

        Camera camera = mCamera;
        if (camera != null && previewSurface != null) {
            try {
                camera.setPreviewDisplay(previewSurface); //????????????????????????????????????surface???
                Log.d(TAG, "setPreviewSurface() called");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ????????????
     */
    private void startPreview() {
        Log.d(TAG, "startPreview() ????????????");

        Camera camera = mCamera;
        SurfaceHolder previewSurface = mPreviewSurface;
        if (camera != null && previewSurface != null) {
            Log.d(TAG, "startPreview(): called");
            camera.setPreviewCallbackWithBuffer(new PreviewCallback());
            camera.startPreview();
            Log.d(TAG, "startPreview(): called2");
        }
    }

    /**
     * ??????
     */
    /*private void takePicture() {
        Camera camera = mCamera;
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.takePicture(new ShutterCallback(), new RawCallback(), new PostviewCallback(), new JpegCallback());
        }
    }*/

    /**
     * ????????????
     */
    private void stopPreview() {
        Log.d(TAG, "stopPreview() ????????????");

        Camera camera = mCamera;
        if (camera != null) {
            camera.stopPreview();
            Log.d(TAG, "stopPreview() called");
        }
    }

    private boolean hasBackCamera() {
        Log.d(TAG, "hasBackCamera() ??????????????????????????????");
        return mBackCameraId != -1;
    }

    private boolean hasFrontCamera() {
        Log.d(TAG, "hasFrontCamera() ??????????????????????????????");
        return mFrontCameraId != -1;
    }


    private class OnSwitchCameraButtonClickListener implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick() ????????????????????????????????????");

            Handler cameraHandler = mCameraHandler;
            SurfaceHolder previewSurface = mPreviewSurface;
            int previewSurfaceWidth = mPreviewSurfaceWidth;
            int previewSurfaceHeight= mPreviewSurfaceHeight;
            if (cameraHandler != null && previewSurface != null) {
                int cameraId = switchCameraId();
                cameraHandler.sendEmptyMessage(MSG_STOP_PREVIEW);//????????????
                cameraHandler.sendEmptyMessage(MSG_CLOSE_CAMERA);//?????????????????????
                cameraHandler.obtainMessage(MSG_OPEN_CAMERA, cameraId, 0).sendToTarget();//?????????????????????
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SIZE, previewSurfaceWidth, previewSurfaceHeight).sendToTarget();//??????????????????
//                cameraHandler.obtainMessage(MSG_SET_PICTURE_SIZE, previewSurfaceWidth, previewSurfaceHeight).sendToTarget();//??????????????????
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SURFACE, previewSurface).sendToTarget();//????????????Surface
                cameraHandler.sendEmptyMessage(MSG_START_PREVIEW);//????????????
            }
        }
    }

    private class PreviewCallback implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d(TAG, "onPreviewFrame() ???????????????");
//            Log.d(TAG, "nv21?????????" + data.toString() + " ,??????:" + data.length);
            // ???????????? Buffer ???????????????????????????

            //????????????
            int previewWidth = camera.getParameters().getPreviewSize().width;
            int previewHeight = camera.getParameters().getPreviewSize().height;
            int orientation = getCameraDisplayOrientation(mCameraInfo);
            mStreamingManager.inputVideoFrame(data, previewWidth, previewHeight, orientation, false, PREVIEW_FORMAT, System.nanoTime());

            camera.addCallbackBuffer(data);

//            int previewWidth = camera.getParameters().getPreviewSize().width;
//            int previewHeight = camera.getParameters().getPreviewSize().height;
//            byte[] nv12 = new byte[data.length];

//            Convert.NV21ToNV12(data, nv12, previewWidth, previewHeight);
//            Log.d(TAG, "nv12?????????" + nv12.toString() + ",??????:" + nv12.length);
        }
    }

//    private class OnTakePictureButtonClickListener implements View.OnClickListener {
//
//        @Override
//        public void onClick(View v) {
//            takePicture();
//        }
//    }

    private class DeviceOrientationListener extends OrientationEventListener {

        public DeviceOrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            Log.d(TAG, "onOrientationChanged() ?????????????????????");
        }
    }

//    private class ShutterCallback implements Camera.ShutterCallback{
//
//        @Override
//        public void onShutter() {
//            Log.d(TAG, "onShuttle() called");
//        }
//    }

    /*private class RawCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "On raw Taken");
        }
    }*/

    /*private class PostviewCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "On postview Taken");
        }
    }*/

    /*private class JpegCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "On jpeg Taken");
        }
    }*/

    private class PreviewSurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated() ?????????surface??????");
        }

        //surface?????????????????????(??????/??????)
        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged() ?????????surface??????");

            mPreviewSurface = holder;
            mPreviewSurfaceWidth = width;
            mPreviewSurfaceHeight = height;
            Handler cameraHandler = mCameraHandler;
            if (cameraHandler != null) {
                //????????????
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SIZE, width, height).sendToTarget();
//                cameraHandler.obtainMessage(MSG_SET_PICTURE_SIZE, width, height).sendToTarget();
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SURFACE, holder).sendToTarget();
                cameraHandler.sendEmptyMessage(MSG_START_PREVIEW);

            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed() ?????????surface??????");

            mPreviewSurface = null;
            mPreviewSurfaceWidth = 0;
            mPreviewSurfaceHeight = 0;
        }
    }



    private void initEncodingProfile() {
        mProfile = new StreamingProfile();
        // ??????????????????
        try {
            mProfile.setPublishUrl("rtmp://180.101.136.120:2045/3nm4x13gzr05p/hfStream");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        mProfile.setQuicEnable(false);
        mProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_MEDIUM1);
    }

    private void initStreamingManager() {
        mStreamingManager = new StreamingManager(this, AVCodecType.HW_VIDEO_YUV_AS_INPUT_WITH_HW_AUDIO_CODEC);
        mStreamingManager.prepare(mProfile);
    }
}