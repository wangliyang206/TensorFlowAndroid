package com.cj.mobile.myapplication.listener;

import com.cj.mobile.myapplication.model.Recognition;

import java.util.List;

/**
 * @ProjectName: MyApplication
 * @Package: com.cj.mobile.myapplication
 * @ClassName: RecognitionListener
 * @Description:
 * @Author: WLY
 * @CreateDate: 2025/1/9 10:34
 */
public interface RecognitionListener {
    void onRecognition(List<Recognition> recognition);
}
