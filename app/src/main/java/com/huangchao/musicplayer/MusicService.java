package com.huangchao.musicplayer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.huangchao.musicplayer.data.MusicList;

public class MusicService extends Service {

    // 播放控制命令，标识操作
    public static final int COMMAND_UNKNOWN = -1;
    public static final int COMMAND_PLAY = 0;
    public static final int COMMAND_PAUSE = 1;
    public static final int COMMAND_STOP = 2;
    public static final int COMMAND_RESUME = 3;
    public static final int COMMAND_PREVIOUS = 4;
    public static final int COMMAND_NEXT = 5;
    public static final int COMMAND_CHECK_IS_PLAYING = 6;
    public static final int COMMAND_SEEK_TO = 7;

    // 播放器状态̬
    public static final int STATUS_PLAYING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_COMPLETED = 3;

    // 广播标志
    public static final String BROADCAST_MUSICSERVICE_CONTROL = "MusicService.ACTION_CONTROL";
    public static final String BROADCAST_MUSICSERVICE_UPDATE_STATUS = "MusicService.ACTION_UPDATE";

    //歌曲序号，从0开始
    private int number = 0;
    private int status;
    // 媒体播放类
    private MediaPlayer player = new MediaPlayer();

    // 广播接收器
    private CommandReceiver receiver;
    private boolean phone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // 绑定广播接收器，可以接收广播
        bindCommandReceiver();
        status = MusicService.STATUS_STOPPED;

        // 来电音乐暂停
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new MyPhoneListener(), PhoneStateListener.LISTEN_CALL_STATE);
    }

    private final class MyPhoneListener extends PhoneStateListener {
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (status == MusicService.STATUS_PLAYING) {
                        pause();
                        phone = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (phone == true) {
                        resume();
                        phone = false;
                    }
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
        }
        /*if (status ==MusicService.STATUS_STOPPED){
            stopService(new Intent(this,MusicService.class));
        }*/
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * 绑定广播接收器
     */
    private void bindCommandReceiver() {
        receiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter(BROADCAST_MUSICSERVICE_CONTROL);
        registerReceiver(receiver, filter);
    }

    /**
     * 内部类，接收广播命令，并执行操作
     */
    class CommandReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            //获取命令
            int command = intent.getIntExtra("command", COMMAND_UNKNOWN);
            //执行命令
            switch (command) {
                case COMMAND_SEEK_TO:
                    seekTo(intent.getIntExtra("time", 0));
                    break;
                case COMMAND_PLAY:
                    number = intent.getIntExtra("number", 0);
                    play(number);
                    break;
                case COMMAND_PREVIOUS:
                    moveNumberToPrevious();
                    break;
                case COMMAND_NEXT:
                    moveNumberToNext();
                    break;
                case COMMAND_PAUSE:
                    pause();
                    break;
                case COMMAND_STOP:
                    stop();
                    break;
                case COMMAND_RESUME:
                    resume();
                    break;
                case COMMAND_CHECK_IS_PLAYING:
                    if (player != null && player.isPlaying()) {
                        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
                    }
                    break;
                case COMMAND_UNKNOWN:
                default:
                    break;
            }
        }
    }

    /**
     * 发送广播，提醒状态改变了
     */
    private void sendBroadcastOnStatusChanged(int status) {
        Intent intent = new Intent(BROADCAST_MUSICSERVICE_UPDATE_STATUS);
        intent.putExtra("status", status);
        if (status != STATUS_STOPPED) {
            intent.putExtra("time", player.getCurrentPosition());
            intent.putExtra("duration", player.getDuration());
            intent.putExtra("number", number);
            intent.putExtra("musicName", MusicList.getMusicList().get(number).getmusicName());
            intent.putExtra("musicArtist", MusicList.getMusicList().get(number).getmusicArtist());
        }
        sendBroadcast(intent);
    }

    /**
     * 读取音乐文件
     */
    private void load(int number) {
        try {
            player.reset();
            player.setDataSource(MusicList.getMusicList().get(number).getmusicPath());
            player.prepare();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //注册监听器
        player.setOnCompletionListener(completionListener);
    }

    //播放结束监听器
    OnCompletionListener completionListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer player) {
            if (player.isLooping()) {
                replay();
            } else {
                sendBroadcastOnStatusChanged(MusicService.STATUS_COMPLETED);
            }
        }
    };

    /**
     * 选择下一首
     */
    private void moveNumberToNext() {
        // 判断是否到达列表底端
        if ((number) == MusicList.getMusicList().size() - 1) {
            Toast.makeText(MusicService.this, "已达到列表底端", Toast.LENGTH_SHORT).show();
        } else {
            ++number;
            play(number);
        }
    }

    /**
     * 选择上一首
     */
    private void moveNumberToPrevious() {
        // 判断是否到达列表顶端
        if (number == 0) {
            Toast.makeText(MusicService.this, "已达到列表顶端", Toast.LENGTH_SHORT).show();
        } else {
            --number;
            play(number);
        }
    }

    /**
     * 播放音乐
     */
    private void play(int number) {
        // ֹֹͣͣ停止当前播放
        if (player != null && player.isPlaying()) {
            player.stop();
        }
        load(number);
        player.start();
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
    }

    /**
     * 暂停音乐
     */
    private void pause() {
        if (player.isPlaying()) {
            player.pause();
            status = MusicService.STATUS_PAUSED;
            sendBroadcastOnStatusChanged(MusicService.STATUS_PAUSED);
        }
    }

    /**
     * 停止播放
     */
    private void stop() {
        if (status != MusicService.STATUS_STOPPED) {
            player.stop();
            status = MusicService.STATUS_STOPPED;
            sendBroadcastOnStatusChanged(MusicService.STATUS_STOPPED);
        }
    }

    /**
     * 恢复播放（暂停之后）
     */
    private void resume() {
        player.start();
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
    }

    /**
     * 重新播放（播放完成之后）
     */
    private void replay() {
        player.start();
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
    }

    private void seekTo(int time) {
        player.seekTo(time);
    }
}
