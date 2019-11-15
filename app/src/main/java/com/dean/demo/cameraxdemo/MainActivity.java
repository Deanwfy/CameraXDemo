package com.dean.demo.cameraxdemo;

import androidx.appcompat.app.AppCompatActivity;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    @Click(R.id.button_start_camera)
    void startCamera() {
        CameraActivity_.intent(this).start();
    }

}
