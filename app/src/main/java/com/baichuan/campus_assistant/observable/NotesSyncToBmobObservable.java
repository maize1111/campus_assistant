package com.baichuan.campus_assistant.observable;

import android.text.TextUtils;

import com.baichuan.campus_assistant.CloudNoteApp;
import com.baichuan.campus_assistant.db.NoteUtils;
import com.baichuan.campus_assistant.db.entity.Note;
import com.baichuan.campus_assistant.db.entity.NoteEntity;
import com.baichuan.campus_assistant.utils.NetUtils;
import com.orhanobut.logger.Logger;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by baichuan on 2017/2/19.
 * 用于同步本地数据到bmob云
 * 两种情况，本地有云端无（基于isSync标记位），云端有本地无。
 *
 */
public class NotesSyncToBmobObservable {
    public static Observable syncToBmob() {
        return Observable.from(NoteUtils.listNotSync())
                .map(new Func1<NoteEntity, Note>() {
                    @Override
                    public Note call(final NoteEntity noteEntity) {
                        //同步本地到云端
                        boolean isConnected = NetUtils.isConnected(CloudNoteApp.getContext());
                        //封装对象
                        Note note = noteEntity.toBmob();
                        if (TextUtils.isEmpty(noteEntity.getObjId())) {
                            //为空说明这个云端对象不存早或者没有创建
                            if (isConnected) {
                                note.save(new SaveListener<String>() {
                                    @Override
                                    public void done(String objectId, BmobException e) {
                                        if (e == null) {
                                            //网络数据保存成功 保存objId 到数据库
                                            noteEntity.setObjId(objectId);
                                            noteEntity.setSync(true);
                                        } else {
                                            noteEntity.setSync(false);  //同步不成功
                                            Logger.d("bmob保存失败：" + e.getMessage() + "," + e.getErrorCode());
                                        }
                                        //需要更新本地数据状态
                                        CloudNoteApp.getNoteEntityDao().update(noteEntity);
                                    }
                                });
                            }
                        } else {
                            //说明这个是一个已经上传过一次的云端对象，需要更新
                            note.update(noteEntity.getObjId(), new UpdateListener() {
                                @Override
                                public void done(BmobException e) {
                                    if (e == null) {
                                        //数据更新成功
                                        noteEntity.setSync(true);
                                    } else {
                                        noteEntity.setSync(false);  //同步不成功
                                        Logger.d("bmob更新失败：" + e.getMessage() + "," + e.getErrorCode());
                                    }
                                    CloudNoteApp.getNoteEntityDao().update(noteEntity);
                                }
                            });
                        }

                        return note;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}


