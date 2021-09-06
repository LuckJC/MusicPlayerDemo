package com.huangchao.musicplayer.data;

import java.util.ArrayList;

/*
 * MusicList类，采用单一实例，
 * 只能通过getMusicList方法获取
 * 共享，唯一的ArrayList<Music>对象
 * */
public class MusicList {

    private static ArrayList<Music> musicarray = new ArrayList<Music>();

    private MusicList() {
    }

    public static ArrayList<Music> getMusicList() {
        return musicarray;
    }
}
