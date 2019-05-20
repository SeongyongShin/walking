package com.example.shin.walking;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;

import static android.hardware.SensorManager.DATA_X;
import static android.hardware.SensorManager.DATA_Y;
import static android.hardware.SensorManager.DATA_Z;

public class MainActivity extends AppCompatActivity {

    LinearLayout L1, L2, L3;
    ImageView active;

    TextView showText, mRun, t1;
    Button connectBtn;
    Button Button_send;
    Button Button_show;
    EditText ip_EditText;
    EditText port_EditText;
    Handler msghandler;

    SocketClient client; // 서버 접속으 위한 클라이언트 클래스
    ReceiveThread receive; // 서버에서 보내온 데이터 안드로이드에서 보이게 하는 거
    SendThread send; // 안드로이드에서 임의의 문자 보내는거
    Socket socket; // 네트워크

    LinkedList<SocketClient> threadList;
    SensorManager sm;
    SensorEventListener accL; // 자이로
    SensorEventListener oriL; // 가속도
    Sensor oriSensor;
    Sensor accSensor;
    TextView ax, ay, az; //자이로
    TextView ox, oy, oz; //가속도
    TextView mSteps;
    TextView textView10;
    public static int count = 0;

    private long lastTime;
    private long lastTime1;
    private double speed = 0;
    private double slapA = 0;
    private double slapB = 0;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double timestamp;
    private double dt;
    //Roll and Pitch
    private double pitch;
    private double roll;
    private double yaw;
    // for radian -> dgree
    private double RAD2DGR = 180 / Math.PI;
    private static final double NS2S = 1.0f / 1000000000.0f;

    private double x, y, z;
    private double x1, y1, z1;
    private static final int SHAKE_THERESHOLD = 800;
    private static final int RUN = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        Button_show = (Button) findViewById(R.id.show);
        active = (ImageView) findViewById(R.id.image_activity);
        L1 = (LinearLayout) findViewById(R.id.linear1);
        L2 = (LinearLayout) findViewById(R.id.linear2);

//안드로이드 view 소스코드 연동 래이아웃에 정의되어 있는 뷰
        ip_EditText = (EditText) findViewById(R.id.ip_EditText);
        port_EditText = (EditText) findViewById(R.id.port_EditText);
        connectBtn = (Button) findViewById(R.id.connect_Button);
        showText = (TextView) findViewById(R.id.showText_TextView);
        mRun = (TextView) findViewById(R.id.mRun);
        Button_send = (Button) findViewById(R.id.Button_send);
        threadList = new LinkedList<MainActivity.SocketClient>();

        ip_EditText.setText("192.168.1.101");
        port_EditText.setText("9511");

        msghandler = new Handler() {
            @Override
            public void handleMessage(Message hdmsg) {
                if (hdmsg.what == 1111) {
//식별자.
                    showText.setText(hdmsg.obj.toString() + "\n");//보여줄 객체
                }

            }
        };
        // 연결버튼 클릭 이벤트
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
//Client 연결부
                client = new SocketClient(ip_EditText.getText().toString(),
                        port_EditText.getText().toString());
                threadList.add(client);
                client.start();
            }
        });
        //전송 버튼 클릭 이벤트
        Button_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

//SendThread 시작
                if (Button_send.getText().toString() != null) {
                    send = new SendThread(socket, "Request");
                    send.start();
                }
            }
        });

//-------------------------------------------------------------------------------------
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        oriSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE); //자이로 선세
        accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); // 가속도 센서

        oriL = new oriListener();
        accL = new accListener();
        mSteps = (TextView) findViewById(R.id.mDistance);

    }

    public void backTo(View v) {
        String msg = showText.getText().toString().substring(0, 6);
        try {
            if (msg.equals("SERVER")) {
                L2.setVisibility(View.GONE);
                L1.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(getApplicationContext(), "" + msg, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "error", Toast.LENGTH_SHORT).show();
        }
    }

    public void select(View v) {
        String msg = null;
        if (v.getId() == R.id.show) {
            msg = "40";
        } else if (v.getId() == R.id.reset) {
            msg = "50";
            count = 0;
            mSteps.setText(count + "걸음");
        }
        send = new SendThread(socket, msg);
        send.start();
    }
//---------------------------------------------------------------------------------------

    class SocketClient extends Thread {
        boolean threadAlive;
        String ip;
        String port;

        DataOutputStream output = null; //byte 로 보내고 문자열로 읽고

        public SocketClient(String ip, String port) {
            threadAlive = true;
            this.ip = ip;
            this.port = port;
        }

        @Override
        public void run() {

            try {
// 연결후 바로 ReceiveThread 시작
                socket = new Socket(ip, Integer.parseInt(port));
                output = new DataOutputStream(socket.getOutputStream());
                receive = new ReceiveThread(socket);
                receive.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ReceiveThread extends Thread {
        private Socket sock = null;
        DataInputStream input;

        public ReceiveThread(Socket socket) {
            this.sock = socket;
            try {
                input = new DataInputStream(sock.getInputStream());
            } catch (Exception e) {
            }
        }

        // 메세지 수신후 Handler로 전달
        public void run() {
            try {
                while (input != null) {
                    String msg;
                    String ck = null;

                    int count = input.available();
                    byte[] rcv = new byte[count];
                    input.read(rcv);
                    msg = new String(rcv);

                    if (count > 0) {
                        Log.d(ACTIVITY_SERVICE, "test :" + msg);
                        Message hdmsg = msghandler.obtainMessage();
                        hdmsg.what = 1111;
                        hdmsg.obj = msg;
                        msghandler.sendMessage(hdmsg);
                        Log.d(ACTIVITY_SERVICE, hdmsg.obj.toString());

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class SendThread extends Thread {
        Socket socket;
        String sendmsg = Button_send.getText().toString() + "\n";
        DataOutputStream output;

        public SendThread(Socket socket, String sendmsg) {
            this.socket = socket;
            this.sendmsg = sendmsg + "\n";
            try {
                output = new DataOutputStream(socket.getOutputStream());
            } catch (Exception e) {
            }
        }

        public void run() {

            try {
// 메세지 전송부
                Log.d(ACTIVITY_SERVICE, "11111");

                if (output != null) {
                    if (sendmsg != null) {
                        output.write(sendmsg.getBytes());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException npe) {
                npe.printStackTrace();

            }
        }
    }

    /////////////////////////////mmmmmm------------------------------
    @Override
    public void onResume() { //일시 중지된 상태에서 액티비티로 다시 onResume() 실행
        super.onResume();

        //자이로 센서 리스너 오브젝트 등록
        sm.registerListener(accL, accSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // 가속도~
        sm.registerListener(oriL, oriSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() { // 액티비티 일시 중지 시
        super.onPause();

        sm.unregisterListener(oriL);
        sm.unregisterListener(accL);
    }


    private class accListener implements SensorEventListener { // 가속도 센서값이  바뀔때 마다 호출해주는 곳
        public void onSensorChanged(SensorEvent event) {
            String message = "";

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                long currentTime = System.currentTimeMillis();
                long gabOfTime = (currentTime - lastTime);

                if (gabOfTime > 300) {
                    lastTime = currentTime;
                    x = event.values[DATA_X];
                    y = event.values[DATA_Y];
                    z = event.values[DATA_Z];

                    speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gabOfTime * 10000;
                    slapA = Math.abs(x - lastZ);
                    Log.d("[SlapA]", "" + slapA);
                    Log.d("[SlapB]", "" + slapB);
                    if (speed > SHAKE_THERESHOLD) {
                        count++;
                        String str = String.format("%d", count);
                        mSteps.setText(str + "걸음");
                        Log.d("[www]", str + "걸음");
                        send = new SendThread(socket, "10");
                        send.start();
                    }
                    if (speed > RUN) {
                        message = "뛰는중!";
                        active.setImageDrawable(getResources().getDrawable(R.drawable.run));
                        Toast.makeText(getApplicationContext(), "now Running!", Toast.LENGTH_SHORT).show();
                        send = new SendThread(socket, "20");
                        send.start();
                    } else if (speed < RUN && speed > SHAKE_THERESHOLD / 2) {
                        message = "걷는중!";
                        active.setImageDrawable(getResources().getDrawable(R.drawable.walk));
                    } else {
                        message = "쉬는중!";
                        active.setImageDrawable(getResources().getDrawable(R.drawable.rest));
                    }
                    mRun.setText(message);
                    lastX = event.values[DATA_X];
                    lastY = event.values[DATA_Y];
                    lastZ = event.values[DATA_Z];
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }


    }

    private class oriListener implements SensorEventListener {
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                long currentTime1 = System.currentTimeMillis();
                long gabOfTime1 = (currentTime1 - lastTime1);

                x1 = event.values[0];
                y1 = event.values[1];
                z1 = event.values[2];
                /* 각속도를 적분하여 회전각을 추출하기 위해 적분 간격(dt)을 구한다.
                 * dt : 센서가 현재 상태를 감지하는 시간 간격
                 * NS2S : nano second -> second */
                dt = (event.timestamp - timestamp) * NS2S;
                timestamp = event.timestamp;

                if (dt - timestamp * NS2S != 0) {

                    /* 각속도 성분을 적분 -> 회전각(pitch, roll)으로 변환.
                     * 여기까지의 pitch, roll의 단위는 '라디안'이다.
                     * SO 아래 로그 출력부분에서 멤버변수 'RAD2DGR'를 곱해주어 degree로 변환해줌.  */
                    pitch += x1 * dt;
                    roll = roll + y1 * dt;
                    yaw = yaw + z1 * dt;
                }

                lastTime1 = currentTime1;
                slapB = (pitch + roll + yaw);
                if (slapB < 0) slapB = -slapB;

                double slap = slapA + slapB - 9.8;

                if (slap > 45) {
                    Toast.makeText(getApplicationContext(), "넘어짐", Toast.LENGTH_SHORT).show();
                    send = new SendThread(socket, "30");
                    send.start();
                }

            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}
