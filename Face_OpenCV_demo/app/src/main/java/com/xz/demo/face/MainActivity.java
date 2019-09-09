package com.xz.demo.face;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import cn.caratel.dj.rc.rl.BaseApp;
import cn.caratel.dj.rc.rl.utils.ToastUtil;
import cn.caratel.dj.rc.rl.widget.AppProgressDialog;

/**
 * Created by xz on 2018/4/18.
 * 党建宣传
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppProgressDialog.show(this,"正在打开人脸识别");
        BaseApp.sAppHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initData();
            }
        }, 500);
    }

    private void initView() {

    }

    @Override
    protected void onPause() {
        AppProgressDialog.onDismiss();
        super.onPause();
    }

    private void initData() {
        startActivity(new Intent(this, FaceCameraActivity.class));
        finish();
    }

    private void rightData() {

    }


    @Override
    public void onClick(View v) {

    }


    private long d = 0;
    private int showIndex = 1;

    public void show(long date) {
        Log.e("showtime", date + "-" + showIndex);
        if (date - d < 1500) {
            showIndex++;
            if (showIndex == 6) {
                ToastUtil.show(BuildConfig.VERSION_NAME);
            }
        } else {
            d = date;
            showIndex = 1;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_0) {
            show(System.currentTimeMillis());
        }
        return super.onKeyDown(keyCode, event);

    }
}
