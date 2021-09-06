package com.huangchao.musicplayer.data;

/**
 * Music类，包括歌曲名，艺术家，路径，时长等属性， 以及相关的获取方法
 */

public class Music {

    private String musicName;
    private String musicArtist;
    private String musicPath;
    private String musicDuration;
    private String musicAlbum;

    public long musicId;
    public long musicArtistId;
    public long musicAlbumId;

    public Music(String musicName, String musicArtist, String musicPath, String musicDuration, String musicAlbum) {
        this.musicName = musicName;
        this.musicArtist = musicArtist;
        this.musicPath = musicPath;
        this.musicDuration = musicDuration;
        this.musicAlbum = musicAlbum;
    }

    public String getmusicName() {
        return this.musicName;
    }

    public String getmusicArtist() {
        return this.musicArtist;
    }

    public String getmusicPath() {
        return this.musicPath;
    }

    public String getmusicDuration() {
        return this.musicDuration;
    }

    public String getMusicAlbum() {
        return this.musicAlbum;
    }
}
