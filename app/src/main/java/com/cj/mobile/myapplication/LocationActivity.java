package com.cj.mobile.myapplication;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.wayz.location.WzException;
import com.wayz.location.WzLocation;
import com.wayz.location.WzLocationClient;
import com.wayz.location.WzLocationClientOption;
import com.wayz.location.WzLocationListener;

/**
 * @ProjectName: TensorFlowAndroid
 * @Package: com.cj.mobile.myapplication
 * @ClassName: LocationActivity
 * @Description: 维智科技定位
 * @Author: WLY
 * @CreateDate: 2025/1/16 9:52
 */
public class LocationActivity extends AppCompatActivity{
    private WzLocationClientOption option;
    private TextView txviLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        txviLocation = findViewById(R.id.txvi_location_text);

        // #以下是定位的逻辑
        option = new WzLocationClientOption();
        option.setInterval(30000);
        // 设置间隔秒定位
        option.setNeedPosition(true);

        WzLocationClient client = new WzLocationClient(getApplicationContext(), option);
        client.startLocation(new WzLocationListener() {
            @Override
            public void onLocationReceived(WzLocation wzLocation) {
                System.out.println("定位成功：" + wzLocation.getLatitude() + "," + wzLocation.getLongitude());
                txviLocation.setText("定位成功：" + wzLocation.getLatitude() + "," + wzLocation.getLongitude());
            }

            @Override
            public void onLocationError(WzException e) {
                System.out.println("定位失败：" + e.getErrorMessage());
                txviLocation.setText("定位失败：" + e.getErrorMessage());
            }
        });
    }
}
