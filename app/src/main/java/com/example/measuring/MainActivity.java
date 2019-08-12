package com.example.measuring;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
////////////////////////////////////////////////////////////////////////////////////////////////////

    // 자이로/가속도 센서 사용
    private SensorManager mSensorManager = null;
    private SensorEventListener mAccLis, mGyroLis;
    private UserSensorListener userSensorListener;
    private Sensor mAccelometerSensor = null;
    private Sensor mGyroSensor = null;

    // 센서 변수들
    private float[] mGyroValues = new float[3];
    private float[] mAccValues = new float[3];
    private double mAccPitch, mAccRoll, mAccYaw;

    /*
    private double accX, accY, accZ, angleXZ, angleYZ;
    private double pitch, roll, yaw;
    private double timestamp, dt;
    */

    // 보수 필터에 사용
    private float a = 0.2f;
    private double RAD2DGR = 180 / Math.PI;
    private static final float NS2S = 1.0f/1000000000.0f;
    private double pitch = 0, roll = 0, yaw = 0;
    private double timestamp, dt, temp, runing;
    private boolean gyroRunning, accRunning;

//////////////////////////////////////////////////////////////////////////////////////////////////

    private static String TAG = "MainActivity";
    JavaCameraView javaCameraView;
    MyCameraActivity camActivity;
    Button EstimateButton;
    Mat img, imgGray, imgCanny, imgHSV, threshold;
    int counter = 0, object_num = 3;
    boolean estimate_flag = false;

    BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    javaCameraView.enableView();
                    break;
                }
                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    static { }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EstimateButton = (Button) this.findViewById(R.id.BtnEstimate);
        //CaptureButton = (Button) this.findViewById(R.id.BtnCapture);

        EstimateButton.setOnClickListener(click);
        //CaptureButton.setOnClickListener(click);

/////////////////////////////////////////////////////////////////////////////////////////////

        // 가속도/자이로 센서 사용
        //mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Application.SENSOR_SERVICE);
        userSensorListener  = new UserSensorListener();
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        /*
        mAccLis = new AccelometerListener();
        mGyroLis = new GyroscopeListener();
        */

////////////////////////////////////////////////////////////////////////////////////////////////////

        javaCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {
                //퍼미션 허가 안되어있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }
    }

    private View.OnClickListener click = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.BtnEstimate:
                    estimate_flag = !estimate_flag;
                    break;
                    /*
                case R.id.BtnCapture:
                    MediaActionSound sound = new MediaActionSound();
                    sound.play(MediaActionSound.SHUTTER_CLICK);
                    Log.i(TAG, "on Button click");
                    Date now = new Date();
                    String currentDateAndTime = now.toString();
                    String fileName = Environment.getExternalStorageDirectory().getPath()
                             + "/sample_picture_" + currentDateAndTime + ".jpeg";
                    camActivity.takePicture(fileName);
                    break;
                    */
                default :
                    break;
            }

        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = (Bitmap) data.getExtras().get("data");

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        /*
        mSensorManager.unregisterListener(mAccLis);
        mSensorManager.unregisterListener(mGyroLis);
        */
        mSensorManager.unregisterListener(userSensorListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        /*
        mSensorManager.unregisterListener(mAccLis);
        mSensorManager.unregisterListener(mGyroLis);
        */
        mSensorManager.unregisterListener(userSensorListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            Log.i(TAG, "OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallBack);
        }
        /*
        mSensorManager.registerListener(mGyroLis, mGyroSensor, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mAccLis, mAccelometerSensor, SensorManager.SENSOR_DELAY_UI);
        */
        mSensorManager.registerListener(userSensorListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(userSensorListener, mAccelometerSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        img = new Mat(height, width, CvType.CV_8UC4);
        imgGray = new Mat(height, width, CvType.CV_8UC1);
        imgCanny = new Mat(height, width, CvType.CV_8UC1);
        imgHSV = new Mat(height, width, CvType.CV_8UC4);
        threshold = new Mat(height, width, CvType.CV_8SC4);
    }

    @Override
    public void onCameraViewStopped() {
        img.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        double reference = 0, dimA = 0, dimB = 0;
        int object_counter = 0;
        Point rollPoint = new Point();
        Point pitchPoint = new Point();

////////////////////////////////////////////////////////////////////////////////////////////////////

        //mSensorManager.registerListener(mGyroLis, mGyroSensor, SensorManager.SENSOR_DELAY_UI);
        //mSensorManager.registerListener(mAccLis, mAccelometerSensor, SensorManager.SENSOR_DELAY_UI);

        pitchPoint.x = 500;
        pitchPoint.y = 40;

        rollPoint.x = 500;
        rollPoint.y = 80;

        String accxStr = String.format("%.1f", mAccValues[0]);
        String accyStr = String.format("%.1f", mAccValues[1]);
        String acczStr = String.format("%.1f", mAccValues[2]);
        //String angleXZStr = String.format("%.4f", angleXZ);
        //String angleYZStr = String.format("%.4f", angleYZ);

        String pitchStr = String.format("%.1f", pitch + 89.2);
        String rollStr = String.format("%.1f", roll + 0.8);
        //String yawStr = String.format("%.1f", yaw*RAD2DGR);

////////////////////////////////////////////////////////////////////////////////////////////////////
        img = inputFrame.rgba();
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(img, imgHSV, Imgproc.COLOR_BGR2HSV);
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGBA);

        // Best : (0,30,0) (180,255,255)
        // Second: (40,20,0) (180,255,255)
        // Hue : 색상(색의 질)
        // Saturation : 채도(높아질수록 잡티 심해짐)
        // Value : 명도(밝기)
        Core.inRange(imgHSV, new Scalar(0,30,0), new Scalar(180,255,255), threshold);

        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5));

        Imgproc.erode(threshold, threshold, erodeElement);
        Imgproc.dilate(threshold, threshold, dilateElement);

        Imgproc.erode(threshold, threshold, erodeElement);
        Imgproc.dilate(threshold, threshold, dilateElement);


        Imgproc.dilate(threshold, threshold, dilateElement);
        Imgproc.erode(threshold, threshold, erodeElement);

        Imgproc.dilate(threshold, threshold, dilateElement);
        Imgproc.erode(threshold, threshold, erodeElement);


        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(threshold, cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        //Imgproc.drawContours(img, cnts, -1, new Scalar(255,255,255));
        for (MatOfPoint c : cnts)
            Imgproc.fillPoly(threshold, Arrays.asList(c), new Scalar(255,255,255));
        //List<Point> box = new ArrayList<Point>();

        if (estimate_flag) {
            for (int i = 0; i < cnts.size(); i++) {
                if (Imgproc.contourArea(cnts.get(i)) > 200) {
                    MatOfPoint maxMatOfPoint = cnts.get(i);
                    MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                    RotatedRect rect = Imgproc.minAreaRect(maxMatOfPoint2f);

                    Point points[] = new Point[4];
                    rect.points(points);
                    for (int j = 0; j < 4; j++) {
                        Imgproc.line(img, points[j], points[(j + 1) % 4], new Scalar(0, 255, 0), 8);
                        Imgproc.circle(img, new Point(points[j].x, points[j].y), 5, new Scalar(0, 0, 255), 8);
                        Imgproc.circle(img, midPoint(points[j], points[(j + 1) % 4]), 5, new Scalar(255, 0, 0), 8);
                    }
                    for (int j = 0; j < 2; j++)
                        Imgproc.line(img, midPoint(points[j % 4], points[(j + 1) % 4]), midPoint(points[(j + 2) % 4], points[(j + 3) % 4]), new Scalar(255, 0, 255), 8);

                    double dA = euclidean(midPoint(points[0], points[1]), midPoint(points[2], points[3]));
                    double dB = euclidean(midPoint(points[0], points[3]), midPoint(points[1], points[2]));

                    if (i == 0 || reference == 0)
                        reference = dB / 2.4;

                    dimA = dA / reference;
                    dimB = dB / reference;

                    Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimA)) + "cm", new Point(midPoint(points[0], points[1]).x - 15, midPoint(points[0], points[0]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimB)) + "cm", new Point(midPoint(points[1], points[2]).x - 15, midPoint(points[1], points[2]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                }
            }
        }
        Imgproc.putText(img, "Front/Back : " + pitchStr + "`", pitchPoint, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);
        Imgproc.putText(img, "Right/Left : " + rollStr + "`", rollPoint, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);

        return img;
        //return threshold;
    }

    public Point midPoint(Point a, Point b) {
        return new Point((a.x + b.x) / 2, (a.y + b.y) / 2);
    }

    public double euclidean(Point a, Point b) {
        return Math.sqrt(square(a.x - b.x) + square(a.y - b.y));
    }

    public double square(double x) {
        return x * x;
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    //여기서부턴 퍼미션 관련 메소드
    static final int PERMISSIONS_REQUEST_CODE = 1000;
    String[] PERMISSIONS  = {"android.permission.CAMERA"};

    private boolean hasPermissions(String[] permissions) {
        int result;
        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions){
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED){
                //허가 안된 퍼미션 발견
                return false;
            }
        }
        //모든 퍼미션이 허가되었음
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){
            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

                    if (!cameraPermissionAccepted)
                        showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

    // 퍼미션 관련 메소드 끝

////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
    private class AccelometerListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {  // 센서값 바뀔때마다 호출됨
                accX = event.values[0];
                accY = event.values[1];
                accZ = event.values[2];

                angleXZ = Math.atan2(accX, accZ) * 180 / Math.PI;
                angleYZ = Math.atan2(accY, accZ) * 180 / Math.PI;

                Log.e("LOG", "ACCELOMETER           [X]:" + String.format("%.4f", event.values[0])
                        + "           [Y]:" + String.format("%.4f", event.values[1])
                        + "           [Z]:" + String.format("%.4f", event.values[2])
                        + "           [angleXZ]: " + String.format("%.4f", angleXZ)
                        + "           [angleYZ]: " + String.format("%.4f", angleYZ));

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }

    private class GyroscopeListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
                // 각 축의 각속도 성분을 받는다.
                double gyroX = event.values[0];
                double gyroY = event.values[1];
                double gyroZ = event.values[2];

                // 각속도를 적분하여 회전각을 추출하기 위해 적분 간격(dt)을 구한다.
                // dt : 센서가 현재 상태를 감지하는 시간 간격
                // NS2S : nano second -> second
                dt = (event.timestamp - timestamp) * NS2S;
                timestamp = event.timestamp;

                // 맨 센서 인식을 활성화 하여 처음 timestamp가 0일때는 dt값이 올바르지 않으므로 넘어간다.
                if (dt - timestamp * NS2S != 0) {

                    // 각속도 성분을 적분 -> 회전각(pitch, roll)으로 변환.
                    // 여기까지의 pitch, roll의 단위는 '라디안'이다.
                    // SO 아래 로그 출력부분에서 멤버변수 'RAD2DGR'를 곱해주어 degree로 변환해줌.
                    pitch = pitch + gyroY * dt;
                    roll = roll + gyroX * dt;
                    yaw = yaw + gyroZ * dt;

                    Log.e("LOG", "GYROSCOPE           [X]:" + String.format("%.4f", event.values[0])
                            + "           [Y]:" + String.format("%.4f", event.values[1])
                            + "           [Z]:" + String.format("%.4f", event.values[2])
                            + "           [Pitch]: " + String.format("%.1f", pitch * RAD2DGR)
                            + "           [Roll]: " + String.format("%.1f", roll * RAD2DGR)
                            + "           [Yaw]: " + String.format("%.1f", yaw * RAD2DGR)
                            + "           [dt]: " + String.format("%.4f", dt));

            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }
    */

    // 1차 상보필터 적용
    private void complementaty(double new_ts){
        /* 자이로랑 가속 해제 */
        gyroRunning = false;
        accRunning = false;

        /*센서 값 첫 출력시 dt(=timestamp - event.timestamp)에 오차가 생기므로 처음엔 break */
        if(timestamp == 0){
            timestamp = new_ts;
            return;
        }
        dt = (new_ts - timestamp) * NS2S; // ns->s 변환
        timestamp = new_ts;

        /* degree measure for accelerometer */
        mAccPitch = -Math.atan2(mAccValues[0], mAccValues[2]) * 180.0 / Math.PI; // Y 축 기준
        mAccRoll= Math.atan2(mAccValues[1], mAccValues[2]) * 180.0 / Math.PI; // X 축 기준

        /**
         * 1st complementary filter.
         *  mGyroValuess : 각속도 성분.
         *  mAccPitch : 가속도계를 통해 얻어낸 회전각.
         */
        temp = (1/a) * (mAccPitch - pitch) + mGyroValues[1];
        pitch = pitch + (temp*dt);

        temp = (1/a) * (mAccRoll - roll) + mGyroValues[0];
        roll = roll + (temp*dt);
    }

    public class UserSensorListener implements SensorEventListener{
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()){
                /** GYROSCOPE */
                case Sensor.TYPE_GYROSCOPE:
                    /*센서 값을 mGyroValues에 저장*/
                    mGyroValues = event.values;
                    if(!gyroRunning)
                        gyroRunning = true;
                    break;

                /** ACCELEROMETER */
                case Sensor.TYPE_ACCELEROMETER:
                    /*센서 값을 mAccValues에 저장*/
                    mAccValues = event.values;
                    if(!accRunning)
                        accRunning = true;
                    break;
            }

            /**두 센서 새로운 값을 받으면 상보필터 적용*/
            if(gyroRunning && accRunning){
                complementaty(event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }
}