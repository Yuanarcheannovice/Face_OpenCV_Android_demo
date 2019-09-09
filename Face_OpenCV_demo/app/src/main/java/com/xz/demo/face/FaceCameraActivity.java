package com.xz.demo.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.archeanx.lib.util.DpToUtil;
import com.archeanx.lib.util.Util;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;


/**
 * @author DEV
 */
public class FaceCameraActivity extends AppCompatActivity {

    // 手动装载openCV库文件，以保证手机无需安装OpenCV Manager
    static {
        System.loadLibrary("opencv_java3");
    }

    private final String TIP_TESTING_FACE = "正在检测人脸中...";
    private final String TIP_SHOT_READY = "已检测到人脸，准备拍摄照片,请保持脸部位置";
    private final String TIP_GENERATE_PHOTO = "正在生成图片,请稍后";
    private String mTipShotEnd = "拍摄完成, %d 秒后重新检测人脸";
    private final String TIP_UPLOAD_ERROR = "上传照片错误,请检查网络!";
    private final String TIP_GENERATE_ERROR = "生成图片错误!";
    private final String TIP_UPLOAD_SUCCESS = "上传照片成功!";
    /**
     * 正在检测
     */
    private final int TIP_STATUS_TESTING_FACE = 0;
    /**
     * 已检测到，准备拍摄
     */
    private final int TIP_STATUS_SHOT_READY = 1;
    /**
     * 准备生成图片
     */
    private final int TIP_STATUS_GENERATE_PHOTO = 2;
    /**
     * 正在上传
     */
    private final int TIP_STATUS_UPLOAD_PHOTO = 3;
    /**
     * 拍摄完成,准备下一个拍摄
     */
    private final int TIP_STATUS_SHOT_END = 4;


    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    private final float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private int mDetectorType = JAVA_DETECTOR;
    private Mat mGray;
    private Mat mRgba;
    private JavaCameraView mJavaCameraView;
    private CascadeClassifier mJavaDetector;
    private int mLine;


    /**
     * 提示状态[0-正在检测,1-已检测到，准备拍摄,2-正在生成图片，3-正在上传，4-拍摄完成]
     */
    private int mTipStatus = TIP_STATUS_TESTING_FACE;

    private TextView mTipTv;


    /**
     * 是否检测到了人脸
     */
    private boolean isShowFace = false;
    /**
     *
     */

    private SoundPool soundPool;
    private int music;
    private Disposable mTestingFaceDis;
    private Disposable mTime5Dis;
    private Disposable mReadyShotDis;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_camera);
        mTipTv = findViewById(R.id.afc_tv);
        mJavaCameraView = findViewById(R.id.afc_cv);
        mJavaCameraView.setCvCameraViewListener(cvCameraViewListener);
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                initClassifier();
                //选择摄像头
                mJavaCameraView.setCameraIndex(openCamera());
                mJavaCameraView.enableView();
                mLine = DpToUtil.dip2px(FaceCameraActivity.this, 200);
                mTipTv.setText(TIP_TESTING_FACE);
                //initMedia
                //第一个参数为同时播放数据流的最大个数，第二数据流类型，第三为声音质量
                soundPool = new SoundPool(10, AudioManager.STREAM_SYSTEM, 5);
                //把你的声音素材放到res/raw里，第2个参数即为资源文件，第3个为音乐的优先级
                music = soundPool.load(FaceCameraActivity.this, R.raw.beep, 1);

                return true;
            }
        });
    }

    CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width, int height) {
            mGray = new Mat();
            mRgba = new Mat();
        }

        @Override
        public void onCameraViewStopped() {
            mGray.release();
            mRgba.release();
        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            mRgba = inputFrame.rgba();
            mGray = inputFrame.gray();
            //处理前置后镜像的摄像头
            //倒转镜像的摄像头
            Core.flip(mRgba, mRgba, 1);
            Core.flip(mGray, mGray, 1);

            if (mAbsoluteFaceSize == 0) {
                int height = mGray.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (mDetectorType == JAVA_DETECTOR) {
                if (mJavaDetector != null) {
                    // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2,
                            new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
                }
            }

            /**
             * 获取到有几个人脸
             */
            final Rect[] facesArray = faces.toArray();
            if (facesArray.length > 0) {
                //只允许出现一个框,只允许显示中间的扫描框
                if (facesArray[0].tl().x > mRgba.width() / 2 - mLine && facesArray[0].br().x < mRgba.width() / 2 + mLine && facesArray[0].tl().y > mRgba.height() / 2 - mLine && facesArray[0].br().y < mRgba.height() / 2 + mLine) {
                    testingFace();
                    if (mTipStatus == TIP_STATUS_TESTING_FACE) {
                        timeReadyShot();
                    } else if (mTipStatus == TIP_STATUS_GENERATE_PHOTO) {
                        //10秒内，不允许重复使用此方法, 根据矩阵和脸部大小裁剪成图片
                        if (Util.isNoDoubleClick(6000)) {
                            mTipStatus = TIP_STATUS_UPLOAD_PHOTO;
                            //     把识别区域扩大
                            facesArray[0].width = facesArray[0].width + 200;
                            facesArray[0].height = facesArray[0].height + 200;
                            facesArray[0].x = facesArray[0].x - 100;
                            facesArray[0].y = facesArray[0].y - 100;
                            uploadImage(facesArray[0]);
                        }
                    }
                    Imgproc.rectangle(mRgba, facesArray[0].tl(), facesArray[0].br(), FACE_RECT_COLOR, 3);
                }
            }
            return mRgba;
        }
    };

    /**
     * 生成并上传图片
     */
    private void uploadImage(final Rect rect) {
        //生成图片并上传
        Bitmap bmpCanny = Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(new Mat(mRgba, rect), bmpCanny);
//        File imagePath = Util.saveBitmapFile(bmpCanny);

    }


    /**
     * 处理人脸离开时，取消所有操作
     */
    private void testingFace() {
        isShowFace = true;
        if (Util.isNoDoubleClick(500)) {
            timeTest();
        }
    }

    /**
     * 是否在两秒内检测不到人脸
     */
    private void timeTest() {
        if (mTestingFaceDis != null) {
            mTestingFaceDis.dispose();
            mTestingFaceDis = null;
        }
        mTestingFaceDis = Flowable.intervalRange(0, 2, 0, 1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) {

                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() {
                        isShowFace = false;
                        if (mTipStatus != TIP_STATUS_SHOT_END && mTipStatus != TIP_STATUS_UPLOAD_PHOTO) {
                            mTipStatus = TIP_STATUS_TESTING_FACE;
                            mTipTv.setText(TIP_TESTING_FACE);
                            if (mReadyShotDis != null) {
                                mReadyShotDis.dispose();
                                mReadyShotDis = null;
                            }
                            if (mTime5Dis != null) {
                                mTime5Dis.dispose();
                                mTime5Dis = null;
                            }
                        }
                    }
                })
                .subscribe();
    }

    /**
     * 5秒后，重新录制
     */
    private void time5() {
        if (mTime5Dis != null) {
            if (!mTime5Dis.isDisposed()) {
                return;
            }
        }
        mTime5Dis = Flowable.intervalRange(0, 6, 4, 1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong)  {
                        mTipTv.setText(String.format(mTipShotEnd, 5 - aLong));
                        mTipStatus = TIP_STATUS_SHOT_END;
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() {
                        mTipTv.setText(TIP_TESTING_FACE);
                        mTipStatus = TIP_STATUS_TESTING_FACE;
                        isShowFace = false;
                    }
                })
                .subscribe();
    }

    /**
     * 准备拍摄照片
     */
    private void timeReadyShot() {
        if (mReadyShotDis != null) {
            if (!mReadyShotDis.isDisposed()) {
                return;
            }
        }
        //从0开始发射11个数字为：0-10依次输出，延时0s执行，每1s发射一次。
        mReadyShotDis = Flowable.intervalRange(0, 4, 0, 1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong)  {
                        mTipTv.setText(TIP_SHOT_READY + "  " + (3 - aLong));
                        mTipStatus = TIP_STATUS_SHOT_READY;
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        mTipTv.setText(TIP_GENERATE_PHOTO);
                        mTipStatus = TIP_STATUS_GENERATE_PHOTO;
                    }
                })
                .subscribe();

    }


    // 初始化人脸级联分类器，必须先初始化
    private void initClassifier() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            mJavaDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (mJavaDetector.empty()) {
                mJavaDetector = null;
            }
            cascadeDir.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 哪里要调用就执行这行代码
     **/
    public void playVoice() {
        if (soundPool != null) {
            soundPool.play(music, 1, 1, 0, 0, 1);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mJavaCameraView != null) {
            mJavaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReadyShotDis != null) {
            mReadyShotDis.dispose();
        }
        if (mTime5Dis != null) {
            mTime5Dis.dispose();
        }
        mJavaCameraView.disableView();
    }

    public static int openCamera() {
        int mCamId = -1;
        if (mCamId < 0) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            int frontCamId = -1;
            int backCamId = -1;
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    backCamId = i;
                } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCamId = i;
                    break;
                }
            }
            if (frontCamId != -1) {
                mCamId = frontCamId;
            } else if (backCamId != -1) {
                mCamId = backCamId;
            } else {
                mCamId = 0;
            }
        }
        return mCamId;
    }

}
