package com.cyl.musiclake.ui.localmusic.presenter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.Log;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.cyl.musiclake.R;
import com.cyl.musiclake.RxBus;
import com.cyl.musiclake.api.GlideApp;
import com.cyl.musiclake.api.qq.QQApiServiceImpl;
import com.cyl.musiclake.api.xiami.XiamiServiceImpl;
import com.cyl.musiclake.data.model.MetaChangedEvent;
import com.cyl.musiclake.data.model.Music;
import com.cyl.musiclake.data.source.AppRepository;
import com.cyl.musiclake.service.PlayManager;
import com.cyl.musiclake.ui.localmusic.contract.PlayControlsContract;
import com.cyl.musiclake.utils.CoverLoader;
import com.cyl.musiclake.utils.FileUtils;
import com.cyl.musiclake.utils.ImageUtils;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by hefuyi on 2016/11/8.
 */

public class PlayControlsPresenter implements PlayControlsContract.Presenter {

    private static final String TAG = "PlayControlsPresenter";
    private PlayControlsContract.View mView;

    private boolean isPlayPauseClick = false;
    private int mProgress;

    private Handler mHandler;
    private Context mContext;
    private Activity activity;

    public PlayControlsPresenter(Context mContext) {
        this.mContext = mContext;
        this.activity = (Activity) mContext;
    }

    @Override
    public void attachView(PlayControlsContract.View view) {
        mView = view;
        mHandler = new Handler();
    }

    @Override
    public void subscribe() {
        RxBus.getInstance().register(MetaChangedEvent.class)
                .subscribe(metaChangedEvent -> {
                    if (!activity.isFinishing()) {
                        updateNowPlayingCard();
                    }
                });
    }

    @Override
    public void unsubscribe() {
        mHandler.removeCallbacks(updateProgress);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onPlayPauseClick() {
        isPlayPauseClick = true;
        PlayManager.playPause();
        if (PlayManager.getPlayingMusic() == null) {
            mView.setErrorInfo("请选择需要播放的音乐");
        } else {
            boolean isPlaying = PlayManager.isPlaying();
            mView.setPlayPauseButton(isPlaying);
        }
    }

    @Override
    public void onPreviousClick() {
        PlayManager.prev();
    }

    @Override
    public void loadLyric() {
        Music music = PlayManager.getPlayingMusic();
        if (music == null) {
            return;
        }
        if (isPlayPauseClick)
            return;
        String localLyricInfo = null;
        String lrcPath = FileUtils.getLrcDir() + music.getTitle() + "-" + music.getArtist() + ".lrc";
        if (FileUtils.exists(lrcPath)) {
            mView.showLyric(lrcPath, true);
        } else if (music.getType() == Music.Type.QQ) {
            QQApiServiceImpl.getQQLyric(music)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<String>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(String lyricInfo) {
                            Log.e(TAG, lyricInfo);
                            mView.showLyric(lyricInfo, false);
                        }

                        @Override
                        public void onError(Throwable e) {
                            mView.showLyric(null, false);
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else if (music.getType() == Music.Type.XIAMI || music.getType() == Music.Type.BAIDU) {
            Log.e(TAG, music.getLrcPath());

            XiamiServiceImpl.getXimaiLyric(music)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<String>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(String lyricInfo) {
                            Log.e(TAG, lyricInfo);
                            mView.showLyric(lyricInfo, false);
                        }

                        @Override
                        public void onError(Throwable e) {
                            mView.showLyric(null, false);
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            mView.showLyric(null, false);
        }
    }

    @Override
    public void onNextClick() {
        PlayManager.next();
    }


    @Override
    public void updateNowPlayingCard() {
        Log.d(TAG, "updateNowPlayingCard" + mProgress);
        Music music = PlayManager.getPlayingMusic();
        if (music == null || PlayManager.getPlayList().size() == 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.default_cover);
            mView.setAlbumArt(bitmap);
            mView.setTitle(mContext.getResources().getString(R.string.app_name));
            mView.setArtist("");
            mView.updatePanelLayout(false);
            return;
        } else {
            mView.updatePanelLayout(true);
        }
        if (PlayManager.isPlaying()) {
            if (!mView.getPlayPauseStatus()) {//true表示按钮为待暂停状态
                mView.setPlayPauseButton(true);
            }
        } else {
            if (mView.getPlayPauseStatus()) {
                mView.setPlayPauseButton(false);
            }
        }

        final String title = PlayManager.getSongName();
        final String artist = PlayManager.getSongArtist();
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(artist)) {
            mView.setTitle(mContext.getResources().getString(R.string.app_name));
            mView.setArtist("");
        } else {
            mView.setTitle(title);
            mView.setArtist(artist);
        }
        String picUrl = CoverLoader.getInstance().getCoverUriByMusic(music);
        //设置音乐来源
        mView.setOtherInfo(music.getTypeName());
        //获取当前歌曲状态
        AppRepository.getMusicInfo(mContext, music)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(music1 -> mView.updateFavorite(music1.isLove()));

        if (!isPlayPauseClick && !activity.isFinishing()) {
            loadLyric();
            GlideApp.with(mContext)
                    .asBitmap()
                    .load(picUrl != null ? picUrl : CoverLoader.getInstance().getCoverUriByRandom())
                    .error(CoverLoader.getInstance().getCoverUriByRandom())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            mView.setAlbumArt(resource);
                            mView.setAlbumArt(ImageUtils.createBlurredImageFromBitmap(resource, mContext, 12));
                            new Palette.Builder(resource).generate(palette -> mView.setPalette(palette));
                        }
                    });
        }
        isPlayPauseClick = false;
        mView.setProgressMax(PlayManager.getDuration());
        mHandler.post(updateProgress);
    }

    private Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            mProgress = PlayManager.getCurrentPosition();
            mView.updateProgress(mProgress);
            mHandler.postDelayed(updateProgress, 500);
        }
    };

    public void updateFavoriteSong() {
        Music music = PlayManager.getPlayingMusic();
        if (music == null)
            return;
        PlayManager.updateFavorite(music);
        mView.updateFavorite(!PlayManager.getPlayingMusic().isLove());
    }
}