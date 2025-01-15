package com.cj.mobile.myapplication.model;

/**
 * @ProjectName: MyApplication
 * @Package: com.cj.mobile.myapplication.model
 * @ClassName: Recognition
 * @Description:
 * @Author: WLY
 * @CreateDate: 2025/1/9 10:37
 */
public class Recognition {
    public Recognition() {
    }

    public Recognition(String id, String title, float confidence) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
    }

    private String id = "";
    private String title = "";
    private float confidence = 0F;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "Title = " + title + ", Confidence = " + confidence;
    }
}
