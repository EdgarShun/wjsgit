package com.example.administrator.study;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static String TAG = MainActivity.class.getSimpleName();


    //录音权限
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private Toast mToast;


    private EditText mNlpText;


    private AIUIAgent mAIUIAgent = null;


    //交互状态
    private int     mAIUIState = AIUIConstant.STATE_IDLE;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //初始化界面
        initLayout();
        //用来消除打开APP的弹出来的窗口
        closeAndroidPDialog();

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        //申请录音权限
        requestPermission();

    }


    /**
     * 初始化界面
     */
    private void initLayout() {
        findViewById(R.id.nlp_start).setOnClickListener(this);

        mNlpText = (EditText)findViewById(R.id.nlp_text);
    }






    //申请录音权限
    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int i = ContextCompat.checkSelfPermission(this, permissions[0]);
            if (i != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 321);
            }
        }



    }


        //点击开始录音 点击开始录音的时候，检测有没有AIUIAgent，没有就创建它。@Override
        public void onClick(View v) {
            if (!checkAIUIAgent()){
                return;
            }

            switch (v.getId()) {
                // 开始语音理解
                case R.id.nlp_start:
                    startVoiceNlp();
                    break;
                default:
                    break;
            }

           

        }

    private void startVoiceNlp() {

        Log.i( TAG, "start voice nlp" );
        mNlpText.setText("");

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot 模式，即一次唤醒后就进入休眠，如果语音唤醒后，需要进行文本语义，请将改段逻辑copy至startTextNlp()开头处
        if( AIUIConstant.STATE_WORKING != 	this.mAIUIState ){
            AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeupMsg);
        }

        // 打开AIUI内部录音机，开始录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage writeMsg = new AIUIMessage( AIUIConstant.CMD_START_RECORD, 0, 0, params, null );
        mAIUIAgent.sendMessage(writeMsg);
    }

    //用来检测AIUIAgent的
    private boolean checkAIUIAgent() {
            if (null==mAIUIAgent){
                Log.i(TAG,"赶快创建AIUIAgent");


                //创建AIUIAgent
                mAIUIAgent=AIUIAgent.createAgent(this,getAIUIParams(),mAIUIListener);

            }

        if( null == mAIUIAgent ){
            final String strErrorTip = "创建 AIUI Agent 失败！";
            showTip( strErrorTip );
            this.mNlpText.setText( strErrorTip );
        } else{
            showTip("AIUIAgent创建成功！");
        }
        return null != mAIUIAgent;
    }

    private String getAIUIParams() {
        String params = "";

        AssetManager assetManager = getResources().getAssets();
        try {
            InputStream ins = assetManager.open( "cfg/aiui_phone.cfg" );
            byte[] buffer = new byte[ins.available()];
            ins.read(buffer);
            ins.close();

            params = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }


    private AIUIListener mAIUIListener=new AIUIListener() {
        @Override
        public void onEvent(AIUIEvent event) {


            switch (event.eventType) {

                case AIUIConstant.EVENT_WAKEUP: {
                    //唤醒事件
                    Log.i( TAG,  "on event: "+ event.eventType );
                    showTip("进入识别状态");

                    break;
                }

                case AIUIConstant.EVENT_RESULT:
                {
                    //结果事件（包含听写，语义，离线语法结果）
                    Log.i( TAG,  "on event: "+ event.eventType );
                    try {
                        JSONObject bizParamJson = new JSONObject(event.info);
                        JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
                        JSONObject params = data.getJSONObject("params");
                        JSONObject content = data.getJSONArray("content").getJSONObject(0);

                        if (content.has("cnt_id")) {
                            String cnt_id = content.getString("cnt_id");
                            JSONObject cntJson = new JSONObject(new String(event.data.getByteArray(cnt_id), "utf-8"));

                            String sub = params.optString("sub");
                            JSONObject result = cntJson.optJSONObject("intent");
                            if ("nlp".equals(sub) && result.length() > 2) {
                                // 解析得到语义结果
                                String str = "";
                                //在线语义结果
                                if(result.optInt("rc") == 0){
                                    JSONObject answer = result.optJSONObject("answer");
                                    if(answer != null){
                                        str = answer.optString("text");
                                    }
                                }else{
                                    str = "rc4，无法识别";
                                }
                                if (!TextUtils.isEmpty(str)){
                                    mNlpText.append( "\n" );
                                    mNlpText.append(str);
                                }

                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        mNlpText.append( "\n" );
                        mNlpText.append( e.getLocalizedMessage() );
                    }

                    mNlpText.append( "\n" );
                } break;

                //休眠事件
                case AIUIConstant.EVENT_SLEEP:
                {
                    break;
                }



                case AIUIConstant.EVENT_ERROR: {
                    //错误事件
                    Log.i( TAG,  "on event: "+ event.eventType );
                    mNlpText.append( "\n" );
                    mNlpText.append( "错误: "+event.arg1+"\n"+event.info );
                } break;

                case AIUIConstant.EVENT_VAD: {
                    //vad事件
                    if (AIUIConstant.VAD_BOS == event.arg1) {
                        //找到语音前端点
                        showTip("找到vad_bos");
                    } else if (AIUIConstant.VAD_EOS == event.arg1) {
                        //找到语音后端点
                        showTip("找到vad_eos");
                    } else {
                        showTip("" + event.arg2);
                    }
                } break;

                case AIUIConstant.EVENT_START_RECORD: {
                    //开始录音事件
                    Log.i( TAG,  "on event: "+ event.eventType );
                    showTip("开始录音");
                } break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    //停止录音事件
                    Log.i( TAG,  "on event: "+ event.eventType );
                    showTip("停止录音");
                } break;

                case AIUIConstant.EVENT_STATE: {	// 状态事件
                    mAIUIState = event.arg1;

                    if (AIUIConstant.STATE_IDLE == mAIUIState) {
                        // 闲置状态，AIUI未开启
                        showTip("STATE_IDLE");
                    } else if (AIUIConstant.STATE_READY == mAIUIState) {
                        // AIUI已就绪，等待唤醒
                        showTip("STATE_READY");
                    } else if (AIUIConstant.STATE_WORKING == mAIUIState) {
                        // AIUI工作中，可进行交互
                        showTip("STATE_WORKING");
                    }
                } break;


                default:
                    break;
            }

        }
    };


    private void showTip(final String str)
    {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 321) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PERMISSION_GRANTED) {
                    this.finish();
                }
            }
        }
    }

    //用来消除打开APP的弹出来的窗口
    private void closeAndroidPDialog(){
        try {
            Class aClass = Class.forName("android.content.pm.PackageParser$Package");
            Constructor declaredConstructor = aClass.getDeclaredConstructor(String.class);
            declaredConstructor.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Class cls = Class.forName("android.app.ActivityThread");
            Method declaredMethod = cls.getDeclaredMethod("currentActivityThread");
            declaredMethod.setAccessible(true);
            Object activityThread = declaredMethod.invoke(null);
            Field mHiddenApiWarningShown = cls.getDeclaredField("mHiddenApiWarningShown");
            mHiddenApiWarningShown.setAccessible(true);
            mHiddenApiWarningShown.setBoolean(activityThread, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
