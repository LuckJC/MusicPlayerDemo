/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huangchao.musicplayer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class MusicUtils {
    private static final String TAG = "MusicUtils";
    //cache the artwork to avoid the bitmap is released when the quick view the songs in play panel
    private final static int MAX_ARTWORK_CACHE_SIZE = 40;
    private final static long[] sEmptyList = new long[0];
    private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
    private static final Uri sArtworkUri = Uri
            .parse("content://media/external/audio/albumart");
    private static final LruCache<Long, Drawable> sArtCache = new LruCache<Long, Drawable>(20);
    // get album art for specified file
    private static final String sExternalMediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString();
    // decoding and caching 20 bitmaps to overcome Out of memory exception.
    public static LruCache<String, Bitmap[]> mArtCache =
            new LruCache<String, Bitmap[]>(20);
    public static LruCache<String, Bitmap> mFolderCache = new LruCache<String, Bitmap>(
            20);
    public static LruCache<String, Bitmap[]> mAlbumArtCache =
            new LruCache<String, Bitmap[]>(20);
    public static HashMap<Integer, Cursor> cur = new HashMap<Integer, Cursor>();
    public static boolean mIsScreenOff = false;
    static Bitmap mAlbumArtsArray[];
    static Bitmap mAlbumArtWorkCacheArray[] = new Bitmap[MAX_ARTWORK_CACHE_SIZE];
    private static int counter = 0;
    private static ContentValues[] sContentValuesCache = null;
    /////////////////////////////////////////////////////////////////////////////////////////
    private static int sArtId = -2;
    private static Bitmap sCachedBitAlbum = null;
    private static Bitmap sCachedBitSong = null;
    private static long sLastSong = -1;
    private static long sLastAlbum = -1;
    private static int sArtCacheId = -1;

    static {
        // for the cache,
        // 565 is faster to decode and display
        // and we don't want to dither here because the image will be scaled
        // down later
        sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptionsCache.inDither = false;

        sBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptions.inDither = false;
    }

    public static <K, V> V getFromLruCache(K key, LruCache<K, V> lruCache) {
        if (key == null || lruCache == null) {
            return null;
        }

        return lruCache.get(key);
    }

    public static <K, V> V putIntoLruCache(K key, V value, LruCache<K, V> lruCache) {
        if (key == null || value == null || lruCache == null) {
            return null;
        }

        return lruCache.put(key, value);
    }

    private static void addDateToCache(String artistName,
                                       Bitmap[] mAlbumArtsArray2) {
        MusicUtils.putIntoLruCache(artistName, mAlbumArtsArray2, mArtCache);
    }

    public static void addArtworkToCache(Bitmap bitmap) {
        if (counter == MAX_ARTWORK_CACHE_SIZE) {
            counter = 0;
        }
        mAlbumArtWorkCacheArray[counter++] = bitmap;
    }

    public static long[] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        int len = cursor.getCount();
        long[] list = new long[len];
        cursor.moveToFirst();
        int colidx = -1;
        try {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (IllegalArgumentException ex) {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(colidx);
            cursor.moveToNext();
        }
        return list;
    }

    public static long[] getSongListForArtist(Context context, long id) {
        final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
        String where = MediaStore.Audio.Media.ARTIST_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1";
        Cursor cursor = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null,
                MediaStore.Audio.Media.ALBUM_KEY + "," + MediaStore.Audio.Media.TRACK);

        if (cursor != null) {
            long[] list = getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return sEmptyList;
    }

    public static long[] getSongListForAlbum(Context context, long id) {
        final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
        String where = MediaStore.Audio.Media.ALBUM_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1";
        Cursor cursor = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null, MediaStore.Audio.Media.TRACK);

        if (cursor != null) {
            long[] list = getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return sEmptyList;
    }

    public static long[] getSongListForPlaylist(Context context, long plid) {
        if (plid == -1) {
            return sEmptyList;
        }
        final String[] ccols = new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID};
        Cursor cursor = query(context, MediaStore.Audio.Playlists.Members.getContentUri("external", plid),
                ccols, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            long[] list = getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return sEmptyList;
    }

    /**
     * @param ids    The source array containing all the ids to be added to the playlist
     * @param offset Where in the 'ids' array we start reading
     * @param len    How many items to copy during this pass
     * @param base   The play order offset to use for this pass
     */
    private static void makeInsertItems(long[] ids, int offset, int len, int base) {
        // adjust 'len' if would extend beyond the end of the source array
        if (offset + len > ids.length) {
            len = ids.length - offset;
        }
        // allocate the ContentValues array, or reallocate if it is the wrong size
        if (sContentValuesCache == null || sContentValuesCache.length != len) {
            sContentValuesCache = new ContentValues[len];
        }
        // fill in the ContentValues array with the right values for this pass
        for (int i = 0; i < len; i++) {
            if (sContentValuesCache[i] == null) {
                sContentValuesCache[i] = new ContentValues();
            }

            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + offset + i);
            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, ids[offset + i]);
        }
    }

    public static Cursor query(Context context, Uri uri, String[] projection,
                               String selection, String[] selectionArgs, String sortOrder, int limit) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            if (limit > 0) {
                uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static Cursor query(Context context, Uri uri, String[] projection,
                               String selection, String[] selectionArgs, String sortOrder) {
        return query(context, uri, projection, selection, selectionArgs, sortOrder, 0);
    }

    /*public static void initAlbumArtCache() {
        try {
            int id = sService.getMediaMountedCount();
            if (id != sArtCacheId) {
                clearAlbumArtCache();
                sArtCacheId = id;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }*/

    public static void clearAlbumArtCache() {
        synchronized (sArtCache) {
            sArtCache.evictAll();
        }
    }

    public static Drawable getsArtCachedDrawable(Context context, long artIndex) {
        synchronized (sArtCache) {
            return sArtCache.get(artIndex);
        }
    }

    public static Drawable getCachedArtwork(Context context, long artIndex,
                                            BitmapDrawable defaultArtwork) {
        Drawable d = null;
        synchronized (sArtCache) {
            d = sArtCache.get(artIndex);
        }
        if (d == null) {
            d = defaultArtwork;
            final Bitmap icon = defaultArtwork.getBitmap();
            int w = icon.getWidth();
            int h = icon.getHeight();
            Bitmap b = MusicUtils.getArtworkQuick(context, artIndex, w, h);
            if (b != null) {
                d = new FastBitmapDrawable(b);
                synchronized (sArtCache) {
                    // the cache may have changed since we checked
                    Drawable value = sArtCache.get(artIndex);
                    if (value == null) {
                        sArtCache.put(artIndex, d);
                    } else {
                        d = value;
                    }
                }
            }
        }
        return d;
    }

    // Get album art for specified album. This method will not try to
    // fall back to getting artwork directly from the file, nor will
    // it attempt to repair the database.
    public static Bitmap getArtworkQuick(Context context, long album_id, int w,
                                         int h) {
        // NOTE: There is in fact a 1 pixel border on the right side in the ImageView
        // used to display this drawable. Take it into account now, so we don't have to
        // scale later.
        w -= 1;
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            ParcelFileDescriptor fd = null;
            try {
                fd = res.openFileDescriptor(uri, "r");
                int sampleSize = 1;

                if (fd != null) {
                    // Compute the closest power-of-two scale factor
                    // and pass that to sBitmapOptionsCache.inSampleSize, which
                    // will result in faster decoding and better quality
                    sBitmapOptionsCache.inJustDecodeBounds = true;
                    BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),
                            null, sBitmapOptionsCache);
                    int nextWidth = sBitmapOptionsCache.outWidth >> 1;
                    int nextHeight = sBitmapOptionsCache.outHeight >> 1;
                    while (nextWidth > w && nextHeight > h) {
                        sampleSize <<= 1;
                        nextWidth >>= 1;
                        nextHeight >>= 1;
                    }

                    sBitmapOptionsCache.inSampleSize = sampleSize;
                    sBitmapOptionsCache.inJustDecodeBounds = false;
                    Bitmap b = BitmapFactory.decodeFileDescriptor(
                            fd.getFileDescriptor(), null, sBitmapOptionsCache);

                    if (b != null) {
                        // finally rescale to exactly the size we need
                        if (sBitmapOptionsCache.outWidth != w
                                || sBitmapOptionsCache.outHeight != h) {
                            Bitmap tmp = Bitmap.createScaledBitmap(b, w, h,
                                    true);
                            // Bitmap.createScaledBitmap() can return the same
                            // bitmap
                            b = tmp;
                        }
                    }

                    return b;
                }
            } catch (FileNotFoundException e) {
            } finally {
                try {
                    if (fd != null)
                        fd.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    /**
     * Get album art for specified album. You should not pass in the album id
     * for the "unknown" album here (use -1 instead)
     * This method always returns the default album art icon when no album art is found.
     */
    public static Bitmap getArtwork(Context context, long song_id, long album_id) {
        return getArtwork(context, song_id, album_id, false);
    }

    /**
     * Get album art for specified album. You should not pass in the album id
     * for the "unknown" album here (use -1 instead)
     */
    public static Bitmap getArtwork(Context context, long song_id,
                                    long album_id, boolean allowdefault) {
        if (context == null) {
            Log.d(TAG, "getArtwork failed because context is null");
            return null;
        }

        if (album_id < 0) {
            // This is something that is not in the database, so get the album
            // art directly from the file.
            Bitmap bm = null;
            if (song_id >= 0) {
                bm = getArtworkFromFile(context, song_id, -1);
                if (bm != null) {
                    addArtworkToCache(bm);
                    return bm;
                }
            }
            if (allowdefault) {
                bm = getDefaultArtwork(context);
                Log.d(TAG, "getArtwork: getDefaultArtwork: " + song_id + ", bm: " + bm);
                if (bm != null && song_id >= 0) {
                    addArtworkToCache(bm);
                }
                return bm;
            }
            return null;
        }

        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                Bitmap b = BitmapFactory.decodeStream(in, null, sBitmapOptions);
                if (b != null && song_id >= 0) {
                    addArtworkToCache(b);
                }
                return b;
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the
                // user deleted it, or
                // maybe it never existed to begin with.
                Bitmap bm = getArtworkFromFile(context, song_id, album_id);
                if (bm != null) {
                    if (bm.getConfig() == null) {
                        bm = bm.copy(Bitmap.Config.RGB_565, false);
                        if (bm == null && allowdefault) {
                            return getDefaultArtwork(context);
                        }
                    }
                } else if (allowdefault) {
                    bm = getDefaultArtwork(context);
                }
                if (bm != null && song_id >= 0) {
                    addArtworkToCache(bm);
                }
                return bm;
            } catch (OutOfMemoryError ex) {
                return null;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        return null;
    }

    private static Bitmap getArtworkFromFile(Context context, long songid, long albumid) {
        Bitmap bm = null;
        byte[] art = null;
        String path = null;
        Uri uri = null;

        if (albumid < 0 && songid < 0) {
            throw new IllegalArgumentException("Must specify an album or a song id");
        }
        ParcelFileDescriptor pfd = null;
        try {
            if (albumid < 0) {
                if (sLastSong == songid) {
                    return sCachedBitSong != null ? sCachedBitSong : getDefaultArtwork(context);
                }
                uri = Uri.parse("content://media/external/audio/media/" + songid + "/albumart");
                try {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                if (pfd != null) {
                    FileDescriptor fd = pfd.getFileDescriptor();
                    bm = BitmapFactory.decodeFileDescriptor(fd);
                }
            } else {
                if (sLastAlbum == albumid) {
                    return sCachedBitAlbum != null ?
                            sCachedBitAlbum : getDefaultArtworkImage(context);
                }
                uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    FileDescriptor fd = pfd.getFileDescriptor();
                    bm = BitmapFactory.decodeFileDescriptor(fd);
                }
            }
        } catch (IllegalStateException ex) {
        } catch (FileNotFoundException ex) {
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "IllegalArgumentException for " + uri);
        } finally {
            try {
                if (pfd != null) {
                    pfd.close();
                }
            } catch (IOException e) {
            }
        }
        if (albumid < 0) {
            sCachedBitSong = bm;
            sLastSong = songid;
        } else {
            sCachedBitAlbum = bm;
            sLastAlbum = albumid;
        }
        return bm;
    }

    public static Bitmap getDefaultArtwork(Context context) {
        /*BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeStream(
                context.getResources().openRawResource(R.drawable.album_cover_background),
                null, opts);*/
        Resources r = context.getResources();
        Bitmap b = BitmapFactory.decodeResource(r, R.drawable.album_cover);
        return b;
    }

    public static Bitmap getDefaultArtworkImage(Context context) {
        Resources r = context.getResources();
        Bitmap b = BitmapFactory.decodeResource(r, R.drawable.album_cover);
        return b;
    }

    static int getIntPref(Context context, String name, int def) {
        SharedPreferences prefs =
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        return prefs.getInt(name, def);
    }

    static void setIntPref(Context context, String name, int value) {
        SharedPreferences prefs =
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putInt(name, value);
        ed.apply();
    }

    public static void startService(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.e(TAG, "context or intent null");
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        return;
    }

    public interface Defs {
        public final static int OPEN_URL = 0;
        public final static int ADD_TO_PLAYLIST = 1;
        public final static int USE_AS_RINGTONE = 2;
        public final static int PLAYLIST_SELECTED = 3;
        public final static int NEW_PLAYLIST = 4;
        public final static int PLAY_SELECTION = 5;
        public final static int GOTO_START = 6;
        public final static int GOTO_PLAYBACK = 7;
        public final static int PARTY_SHUFFLE = 8;
        public final static int SHUFFLE_ALL = 9;
        public final static int DELETE_ITEM = 10;
        public final static int SCAN_DONE = 11;
        public final static int QUEUE = 12;
        public final static int EFFECTS_PANEL = 13;
        public final static int MORE_MUSIC = 14;
        public final static int MORE_VIDEO = 15;
        public final static int USE_AS_RINGTONE_2 = 16;
        public final static int DRM_LICENSE_INFO = 17;
        public final static int CLOSE = 18;
        public final static int CHILD_MENU_BASE = 19;// this should be the last item;
    }

    private static class FastBitmapDrawable extends Drawable {
        private Bitmap mBitmap;

        public FastBitmapDrawable(Bitmap b) {
            mBitmap = b;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }
    }

}
