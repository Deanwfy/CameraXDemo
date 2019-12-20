package com.dean.demo.cameraxdemo;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    @ViewById(R.id.tv_goods_info)
    public static TextView goodsInfoTextView;
    // TODO

    @Click(R.id.button_start_camera)
    void startCamera() {
        CameraActivity_.intent(this).start();
    }

}
