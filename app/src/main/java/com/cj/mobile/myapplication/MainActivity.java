package com.cj.mobile.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 请求相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }

        findViewById(R.id.btn_main_location).setOnClickListener(this);
        findViewById(R.id.btn_main_objectdetector).setOnClickListener(this);
        findViewById(R.id.btn_main_imageclassifier).setOnClickListener(this);
        findViewById(R.id.btn_main_humanjoints).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_main_location:                                                            // 维智定位功能
                startActivity(new Intent(this, LocationActivity.class));
                break;
            case R.id.btn_main_objectdetector:                                                      // 物体探测器功能
                startActivity(new Intent(this, ObjectDetectorActivity.class));
                break;
            case R.id.btn_main_imageclassifier:                                                     // 图像分类功能
                startActivity(new Intent(this, ImageClassifierActivity.class));
                break;
            case R.id.btn_main_humanjoints:                                                         // 人脸关节功能
                startActivity(new Intent(this, HumanJointsActivity.class));
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

        }
    }
}