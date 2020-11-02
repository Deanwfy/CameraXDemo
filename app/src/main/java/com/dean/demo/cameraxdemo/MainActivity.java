package com.dean.demo.cameraxdemo;

import android.content.DialogInterface;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    @ViewById(R.id.tv_goods_info)
    public static TextView goodsInfoTextView;

    private BiometricPrompt biometricPrompt;

    @Click(R.id.button_start_camera)
    void startCamera() {
        CameraActivity_.intent(this).start();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBiometricRecognize();
    }

    private void initBiometricRecognize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            biometricPrompt = new BiometricPrompt
                    .Builder(this)
                    .setTitle("请认证指纹以继续")
                    .setNegativeButton("取消", this.getMainExecutor(), (dialog, which) -> finish())
                    .build();
            biometricPrompt.authenticate(new CancellationSignal(), this.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    finish();
                }

                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                    super.onAuthenticationHelp(helpCode, helpString);
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                }

                @Override
                public void onAuthenticationFailed() {
                    // do nothing
                }
            });
        }
    }
}
