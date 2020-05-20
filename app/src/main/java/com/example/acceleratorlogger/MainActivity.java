package com.example.acceleratorlogger;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener {

    private Sensor accSensor;
    private TextView mX;
    private TextView mY;
    private TextView mZ;
    private TextView HzperS;
    private TextView issamplingratetextView;
    //現在出ているサンプリング周波数
    private EditText editText;
    private Button button_get;
    private TextView edittextView;
    //ユーザが希望するサンプリング周波数の格納場所
    private EditText editText1;
    private Button button_get1;
    private TextView edittextView1;
    private SensorManager mManager;
    public int button_flag = 0;

    long eventOccuredTimeMilli = -1;
    long eventOccuredTimeNano = -1;

    //sensorChangedで更新される情報(加速度,ジャイロ)
    ArrayList<String> accX = new ArrayList<String>();
    ArrayList<String> accY = new ArrayList<String>();
    ArrayList<String> accZ = new ArrayList<String>();
    
    ArrayList<String> timeStamp_Time_forAcc = new ArrayList<String>();//時間

    //書き込み用の情報
    ArrayList<String> accX_w = new ArrayList<String>();
    ArrayList<String> accY_w = new ArrayList<String>();
    ArrayList<String> accZ_w = new ArrayList<String>();
    ArrayList<Float>  accXYZ = new ArrayList<Float>(); //合成加速度

    ArrayList<String> timeStamp_Time_forAcc_w = new ArrayList<String>();//時間

    //時間(年.月.日_時:分:秒)
    SimpleDateFormat sdf = new SimpleDateFormat("yy.MM.dd_HH:mm:ss");

    //時間(年.月.日_時:分:秒:ミリ秒)
    SimpleDateFormat sdf1 = new SimpleDateFormat("yy.MM.dd_HH:mm:ss:SSS");

    //計測開始時間に使用する変数
    String start_time;

    //ファイル名の確認時に使用
    String last_fileName;

    String tmp_fileName;//評価対象のファイルを保存するときのやつ

    //サンプリング周波数求めるための時間格納変数
    private double prevtime = 0;
    private double sampling_rate;

    //入力されたサンプリング周波数
    private double input_sampling_rate=100;
    //入力されたときに立つフラグ
    private boolean isInputSamplingRate=false;

    //変換したいサンプリング周波数
    private double wantConverting_sampling_rate=100;
    //入力されたときに立つフラグ
    private boolean isWantConvertingSamplingRate=false;

    //記録中か表示するやつ
    private TextView isRecording;

    //以下ラジオ体操対応箇所定義
    private MediaPlayer mediaPlayer;
    private Button button_radio;
    //音楽を流す準備のブール値
    private boolean readyPlayRadio=false;

    private boolean audioSetup(){
        boolean fileCheck = false;

        // インタンスを生成
        mediaPlayer = new MediaPlayer();

        //音楽ファイル名, あるいはパス
        String filePath = "radio-taiso.mp3";

        // assetsから mp3 ファイルを読み込み
        try(AssetFileDescriptor afdescripter = getAssets().openFd(filePath);)
        {
            // MediaPlayerに読み込んだ音楽ファイルを指定
            mediaPlayer.setDataSource(afdescripter.getFileDescriptor(),
                    afdescripter.getStartOffset(),
                    afdescripter.getLength());
            // 音量調整を端末のボタンに任せる
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepare();
            fileCheck = true;
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return fileCheck;
    }

    private void audioPlay() {

        if (mediaPlayer == null) {
            // audio ファイルを読出し
            if (audioSetup()){
                Toast.makeText(getApplication(), "Rread audio file", Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(getApplication(), "Error: read audio file", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        else{
            // 繰り返し再生する場合
            mediaPlayer.stop();
            mediaPlayer.reset();
            // リソースの解放
            mediaPlayer.release();
        }

        // 再生する
        mediaPlayer.start();

        // 終了を検知するリスナー
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("debug","end of audio");
                audioStop();
            }
        });
    }

    private void audioStop() {
        // 再生終了
        mediaPlayer.stop();
        // リセット
        mediaPlayer.reset();
        // リソースの解放
        mediaPlayer.release();

        mediaPlayer = null;
    }

    public void onAccuracyChanged(Sensor sensor, int n) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mManager = (SensorManager)this.getSystemService(SENSOR_SERVICE);
        accSensor = this.mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Button start_btn = (Button) findViewById(R.id.button_start);
        Button stop_btn = (Button) findViewById(R.id.button_stop);
        editText = (EditText) findViewById(R.id.editText);
        button_get = (Button) findViewById(R.id.button_get);
        edittextView = (TextView) findViewById(R.id.edittextView);
        editText1 = (EditText) findViewById(R.id.editText1);
        button_get1 = (Button) findViewById(R.id.button_get1);
        edittextView1 = (TextView) findViewById(R.id.edittextView1);
        issamplingratetextView = (TextView) findViewById(R.id.wehterSettingSamplingRate);
        button_radio = (Button) findViewById(R.id.button_radio);
        isRecording = (TextView) findViewById(R.id.isRecoreing);

        button_get.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = editText.getText().toString();    //EditText(テキストボックス)から文字列を取得
                //検証結果を格納する変数
                boolean isInt = true;
                //一文字ずつ先頭から確認する。for文は文字数分繰り返す
                for(int i = 0; i < text.length(); i++) {
                    //i文字めの文字についてCharacter.isDigitメソッドで判定する
                    if (Character.isDigit(text.charAt(i))) {
                        //数字の場合は次の文字の判定へ
                        continue;
                    } else {
                        //数字でない場合は検証結果をfalseに上書きする
                        isInt = false;
                        break;
                    }
                }
                if (!text.equals("") && isInt){
                    edittextView.setText(text);    //TextViewに文字列をセット
                    editText.setText("");
                    input_sampling_rate = Integer.parseInt(text);
                    isInputSamplingRate = true;
                }else{
                    edittextView.setText("数字のみを入力してね");    //TextViewに文字列をセット
                    editText.setText("");
                    isInputSamplingRate = false;
                }

            }
        });
        button_get1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text1 = editText1.getText().toString();    //EditText(テキストボックス)から文字列を取得
                //検証結果を格納する変数
                boolean isInt1 = true;
                //一文字ずつ先頭から確認する。for文は文字数分繰り返す
                for (int i = 0; i < text1.length(); i++) {
                    //i文字めの文字についてCharacter.isDigitメソッドで判定する
                    if (Character.isDigit(text1.charAt(i))) {
                        //数字の場合は次の文字の判定へ
                        continue;
                    } else {
                        //数字でない場合は検証結果をfalseに上書きする
                        isInt1 = false;
                        break;
                    }
                }
                if (!text1.equals("") && isInt1) {
                    edittextView1.setText(text1);    //TextView1に文字列をセット
                    editText1.setText("");
                    wantConverting_sampling_rate = Integer.parseInt(text1);
                    isWantConvertingSamplingRate = true;
                } else {
                    edittextView1.setText("数字のみを入力してね");    //TextView1に文字列をセット
                    editText1.setText("");
                    isWantConvertingSamplingRate = false;
                }
            }
        });

        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sdf.applyPattern("yy.MM.dd_HH:mm:ss");
                start_time = sdf.format(System.currentTimeMillis());

                if(!readyPlayRadio){
                    button_flag = 1;

                    initialize();
                    last_fileName = "";
                    isRecording.setText("recording...");
                }
                else{
                    isRecording.setText("recording...");
                    button_flag = 1;
                    audioPlay();
                    Log.d("radio", "run");
                    initialize();
                    last_fileName = "";


                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("record", "finish!");
                            button_flag = 0;
                            audioStop();

                            if(isInputSamplingRate && isWantConvertingSamplingRate){
                                changeandcopy_information();
                                save_route();
                                initialize();
                                isRecording.setText("not recording");
                            }else {
                                copy_information();
                                save_route();
                                initialize();
                                isRecording.setText("not recording");
                            }
                        }
                    }, 193800);   //任意秒計測する 191000

                }

            }

        });

        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                button_flag = 0;
                if (mediaPlayer != null) {
                    // 音楽停止
                    audioStop();
                }
                if(isInputSamplingRate && isWantConvertingSamplingRate){
                    changeandcopy_information();
                    save_route();
                    initialize();
                    Log.d("record", "finish!");
                }else {
                    copy_information();
                    save_route();
                    initialize();
                    Log.d("record", "finish!");
                }
                isRecording.setText("not recording");
            }
        });

        mX = (TextView)this.findViewById(R.id.textViewX);
        mY = (TextView)this.findViewById(R.id.textViewY);
        mZ = (TextView)this.findViewById(R.id.textViewZ);
        HzperS= (TextView)this.findViewById(R.id.textView_sampling_rate);

        // リスナーをラジオ体操ボタンに登録
        button_radio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 音楽再生準備
                if(!readyPlayRadio){
                    readyPlayRadio=true;
                    button_radio.setText("ラジオ体操モード：ON");
                }
                else{
                    readyPlayRadio=false;
                    button_radio.setText("ラジオ体操モード：OFF");
                }

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.mManager.unregisterListener((SensorEventListener)this, this.accSensor);
    }

    protected void onResume() {
        super.onResume();
        this.mManager.registerListener((SensorEventListener) this, this.accSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void onSensorChanged(SensorEvent sensorEvent) {

        //UNIX時間
        if (eventOccuredTimeMilli < 0){
            eventOccuredTimeMilli = System.currentTimeMillis();
            eventOccuredTimeNano = sensorEvent.timestamp;
        }

        long unixtime = eventOccuredTimeMilli
                + (sensorEvent.timestamp - eventOccuredTimeNano) / 1000000;

        mX.setText("加速度センサーX:" + String.valueOf(sensorEvent.values[0]));
        mY.setText("加速度センサーY:" + String.valueOf(sensorEvent.values[1]));
        mZ.setText("加速度センサーZ:" + String.valueOf(sensorEvent.values[2]));

        sampling_rate = 1/((sensorEvent.timestamp - prevtime)/1000000000); //ns -> s & (t) -> (Hz) （∴ T = 1/f）
        //Log.d("sampling_rate", ""+sampling_rate+"(Hz)");

        //sampling_rate = (sensorEvent.timestamp - prevtime);//(ns)
        //Log.d("sampling_rate", ""+sampling_rate+"(ns)");

        HzperS.setText("サンプリング周波数　：　"+String.valueOf(sampling_rate)+"Hz");
        prevtime = sensorEvent.timestamp;

        if (sensorEvent.sensor.getType() ==  Sensor.TYPE_ACCELEROMETER) {
            if(button_flag==1){

                accX.add(String.format("%3.2f", sensorEvent.values[0]));
                accY.add(String.format("%3.2f", sensorEvent.values[1]));
                accZ.add(String.format("%3.2f", sensorEvent.values[2]));

                timeStamp_Time_forAcc.add(sdf1.format(unixtime));

            }
            if(isInputSamplingRate&&isWantConvertingSamplingRate){
                issamplingratetextView.setText("Ready");
            }else{
                issamplingratetextView.setText("Not Ready");
            }
        }
    }

    public void copy_information() {

        accX_w = new ArrayList(accX);
        accY_w = new ArrayList(accY);
        accZ_w = new ArrayList(accZ);

        timeStamp_Time_forAcc_w = new ArrayList(timeStamp_Time_forAcc);

    }
    public void changeandcopy_information() {

        accX_w = new ArrayList();
        accY_w = new ArrayList();
        accZ_w = new ArrayList();
        timeStamp_Time_forAcc_w = new ArrayList(timeStamp_Time_forAcc);
        int temp_size = timeStamp_Time_forAcc_w.size();
        boolean _once = false;

        //配列を希望するサンプリングレートになるように間引くための変数
        int AdjustI = (int)(input_sampling_rate/wantConverting_sampling_rate);

        for (int i = 0; AdjustI*i < temp_size; i++) {
            if(!_once){
                timeStamp_Time_forAcc_w.clear();
                _once=true;
            }
            accX_w.add(accX.get(AdjustI*i));
            accY_w.add(accY.get(AdjustI*i));
            accZ_w.add(accZ.get(AdjustI*i));
            timeStamp_Time_forAcc_w.add(timeStamp_Time_forAcc.get(AdjustI*i));
            //Log.d("size", ""+ accX_w);
        }
/*
        for (int i =0;i<timeStamp_Time_forAcc_w.size();++i){
            Log.d("accX_W", ""+accX_w.get(i));
        }
*/

    }

    //ArrayListを初期化
    public void initialize() {

        accX.clear();
        accY.clear();
        accZ.clear();

        timeStamp_Time_forAcc.clear();

        eventOccuredTimeMilli = -1;
        eventOccuredTimeNano = -1;
    }

    /*書き込み関係*/
    public void save_route() {
        String txt_name = "test";
        String filePath;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            filePath = Environment.getExternalStorageDirectory().getPath();
        }else{
            filePath = Environment.getExternalStorageDirectory().getPath();
        }

        File file_route_acc = new File(filePath + "/acc_log/" + txt_name);
        file_route_acc.getParentFile().mkdir();

        String str;
        sdf.applyPattern("yy.MM.dd_HH:mm:ss");
        str = sdf.format(System.currentTimeMillis());

        txt_name = start_time + "-" + str + ".csv";


        //初めての書き込みなら、そのままtxt_nameのファイル名で書き込み。それ以外ならリネーム後書き込み。
        if(last_fileName != ""){
            ArrayList<String> list = new ArrayList<String>();
            list.add("/acc_log/");
            //Log.d("hoge", "hoge");
            for(int i=0; i < list.size(); i++){
                File file = new File( filePath + list.get(i) + last_fileName );
                if (file.exists()) {
                    //リネーム
                    File file2 = new File(filePath + list.get(i) + txt_name);
                }
            }
        }
        last_fileName = txt_name;

        if(!start_time.equals(str)) {

            tmp_fileName = txt_name;

            // 別スレッドで時間のかかる処理を実行
            new Thread(new Runnable() {
                @Override

                public void run() {
                    //ここではセンサデータを書き込み

                    if(readyPlayRadio){
                        writeCSV_routeForRadio();
                    }else{
                        writeCSV_route();
                    }

                }
            }).start();

        }
    }

    //    センサデータ書き込み
    public void writeCSV_route() {

        String str;
        String filePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            filePath = Environment.getExternalStorageDirectory().toString();
            //Log.d("hoge", filePath);
        }else{
            filePath = Environment.getDataDirectory().toString();
        }

        //acc書き込み
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/acc_log/" + tmp_fileName, true), "UTF-8"));
            for (int i = 0; i < timeStamp_Time_forAcc_w.size(); i++) {
                str = timeStamp_Time_forAcc_w.get(i) + "," + accX_w.get(i) + "," + accY_w.get(i) + "," + accZ_w.get(i);
                bw.write(str);
                bw.newLine();
                //Log.d("accX_W", str);
            }
            bw.close();
        }catch (Exception e) {
        }

        timeStamp_Time_forAcc_w.clear();

        accX_w.clear();
        accY_w.clear();
        accZ_w.clear();

    }
    public void writeCSV_routeForRadio() {

        String filePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            filePath = Environment.getExternalStorageDirectory().toString();
            //Log.d("hoge", filePath);
        }else{
            filePath = Environment.getDataDirectory().toString();
        }

        int all_data_length= timeStamp_Time_forAcc_w.size();

        labeling(1-1,739-1,1,filePath);
        labeling(740-1,2217-1,2,filePath);
        labeling(2218-1,3801-1,3,filePath);
        labeling(3802-1,5491-1,4,filePath);
        labeling(5492-1,7075-1,5,filePath);
        labeling(7076-1,8553-1,6,filePath);
        labeling(8554-1,10031-1,7,filePath);
        labeling(10032-1,11615-1,8,filePath);
        labeling(11616-1,13357-1,9,filePath);
        labeling(13358-1,14994-1,10,filePath);
        labeling(14995-1,16103-1,11,filePath);
        labeling(16104-1,17687-1,12,filePath);
        labeling(17688-1,19256-1,13,filePath);

        timeStamp_Time_forAcc_w.clear();

        accX_w.clear();
        accY_w.clear();
        accZ_w.clear();

    }
    public void labeling(int start_num,int end_num,int label,String filePath){

        String str;
        double sum_for_ave=0.0;
        double sum_for_var=0.0;
        double sum_for_waido=0.0;
        double sum_for_sendo=0.0;
        double sum_for_signal=0.0;
        double sum_accel=0.0;
        double ave=0.0;
        double bunsan=0.0;
        double hensa=0.0;
        double waido=0.0;
        double sendo=0.0;
        double signal_power=0.0;
        double length = 0.0;

        length = end_num - start_num + 1;

        //Log.d("hoge", accX_w.get(500));
        //合成加速度を計算
        for(int i = start_num;i < end_num-1;++i){
            /*accXYZ.add((float) sqrt(Double.valueOf(accX_w.get(i))*Double.valueOf(accX_w.get(i))
                    + Double.valueOf(accY_w.get(i))*Double.valueOf(accY_w.get(i))
                    + Double.valueOf(accZ_w.get(i))*Double.valueOf(accZ_w.get(i))));
            */
            sum_accel=sqrt(Double.valueOf(accX_w.get(i))*Double.valueOf(accX_w.get(i))
                    + Double.valueOf(accY_w.get(i))*Double.valueOf(accY_w.get(i))
                    + Double.valueOf(accZ_w.get(i))*Double.valueOf(accZ_w.get(i)));
            accXYZ.add((float) sum_accel);

            sum_for_ave += accXYZ.get(i-start_num);
        }

        for(int i = 0;i < length-2;++i){
            Log.d("hoge", String.valueOf(length));
            sum_for_var += (accXYZ.get(i) - sum_for_ave)*(accXYZ.get(i) - sum_for_ave);

        }

        //平均
        ave = sum_for_ave / length;
        //分散
        bunsan = sum_for_var / length;
        //標準偏差
        hensa = Math.sqrt(bunsan);

        for(int i = 0;i < length-2;++i){
            sum_for_waido += ((accXYZ.get(i) - sum_for_ave)/hensa)*((accXYZ.get(i) - sum_for_ave)/hensa)
                    *((accXYZ.get(i) - sum_for_ave)/hensa);
            sum_for_sendo += ((accXYZ.get(i) - sum_for_ave)/hensa)*((accXYZ.get(i) - sum_for_ave)/hensa)
                    *((accXYZ.get(i) - sum_for_ave)/hensa)*((accXYZ.get(i) - sum_for_ave)/hensa);
            sum_for_signal += accXYZ.get(i)*accXYZ.get(i);
        }
        //歪度
        waido = sum_for_waido / length;
        //尖度
        sendo = sum_for_sendo / length;
        //信号パワー
        signal_power = sum_for_signal / length;

        //acc書き込み
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/acc_log/" + tmp_fileName, true), "UTF-8"));
            //体操は全13種類なので，1行目のデータ名と各体操で合計14行になる
            if(label==1){
                str = "average,bunsan,waido,sendo,signal power,label";
                bw.write(str);
                bw.newLine();
            }

            str = ave + "," + bunsan + "," + waido + "," + sendo + "," + signal_power + "," + label;
            bw.write(str);
            bw.newLine();
            //Log.d("accX_W", str);
            bw.close();
            accXYZ.clear();
        }catch (Exception e) {
        }

    }
}