package me.shouheng.omnilist.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Date;
import java.util.List;

import me.shouheng.omnilist.PalmApp;
import me.shouheng.omnilist.model.Assignment;
import me.shouheng.omnilist.model.Attachment;
import me.shouheng.omnilist.model.TimeLine;
import me.shouheng.omnilist.model.enums.ModelType;
import me.shouheng.omnilist.model.enums.Operation;
import me.shouheng.omnilist.model.enums.Status;
import me.shouheng.omnilist.provider.helper.StoreHelper;
import me.shouheng.omnilist.provider.helper.TimelineHelper;
import me.shouheng.omnilist.provider.schema.AttachmentSchema;
import me.shouheng.omnilist.provider.schema.BaseSchema;
import me.shouheng.omnilist.provider.schema.TimelineSchema;


/**
 * Created by wangshouheng on 2017/4/9.*/
public class AttachmentsStore extends BaseStore<Attachment> {

    private static AttachmentsStore sInstance = null;

    public static AttachmentsStore getInstance() {
        if (sInstance == null){
            synchronized (AttachmentsStore.class) {
                if (sInstance == null) {
                    sInstance = new AttachmentsStore(PalmApp.getContext());
                }
            }
        }
        return sInstance;
    }

    private AttachmentsStore(final Context context){
        super(context);
    }

    @Override
    protected void afterDBCreated(SQLiteDatabase db) {}

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion){}

    @Override
    public void fillModel(Attachment model, Cursor cursor) {
        model.setModelCode(cursor.getLong(cursor.getColumnIndex(AttachmentSchema.MODEL_CODE)));
        model.setModelType(ModelType.getTypeById(cursor.getInt(cursor.getColumnIndex(AttachmentSchema.MODEL_TYPE))));
        String uriStr = cursor.getString(cursor.getColumnIndex(AttachmentSchema.URI));
        model.setUri(TextUtils.isEmpty(uriStr) ? null : Uri.parse(uriStr));
        model.setPath(cursor.getString(cursor.getColumnIndex(AttachmentSchema.PATH)));
        model.setName(cursor.getString(cursor.getColumnIndex(AttachmentSchema.NAME)));
        model.setSize(cursor.getLong(cursor.getColumnIndex(AttachmentSchema.SIZE)));
        model.setLength(cursor.getLong(cursor.getColumnIndex(AttachmentSchema.LENGTH)));
        model.setMineType(cursor.getString(cursor.getColumnIndex(AttachmentSchema.MINE_TYPE)));
        model.setOneDriveSyncTime(new Date(cursor.getLong(cursor.getColumnIndex(AttachmentSchema.ONE_DRIVE_SYNC_TIME))));
        model.setOneDriveItemId(cursor.getString(cursor.getColumnIndex(AttachmentSchema.ONE_DRIVE_ITEM_ID)));
    }

    @Override
    protected void fillContentValues(ContentValues values, Attachment model) {
        values.put(AttachmentSchema.MODEL_CODE, model.getModelCode());
        values.put(AttachmentSchema.MODEL_TYPE, model.getModelType().id);
        values.put(AttachmentSchema.URI, model.getUri() != null ? model.getUri().toString() : null);
        values.put(AttachmentSchema.PATH, model.getPath());
        values.put(AttachmentSchema.NAME, model.getName());
        values.put(AttachmentSchema.SIZE, model.getSize());
        values.put(AttachmentSchema.LENGTH, model.getLength());
        values.put(AttachmentSchema.MINE_TYPE, model.getMineType());
        values.put(AttachmentSchema.ONE_DRIVE_SYNC_TIME, model.getOneDriveSyncTime() == null ? 0 : model.getOneDriveSyncTime().getTime());
        values.put(AttachmentSchema.ONE_DRIVE_ITEM_ID, model.getOneDriveItemId());
    }

    // region OneDrive
    public synchronized List<Attachment> getUploadForOneDrive(int pageCount) {
        Cursor cursor = null;
        List<Attachment> models;
        SQLiteDatabase database = getWritableDatabase();
        try {
            cursor = database.rawQuery(" SELECT * FROM " + tableName + " AS t "
                    + " WHERE t." + AttachmentSchema.USER_ID + " = " + userId
                    + " AND ( t." + AttachmentSchema.ONE_DRIVE_SYNC_TIME + " IS NULL "
                    + " OR t." + AttachmentSchema.ONE_DRIVE_SYNC_TIME + " < t." + AttachmentSchema.LAST_MODIFIED_TIME + " ) "
                    + " ORDER BY " + AttachmentSchema.ADDED_TIME
                    + " LIMIT ?, ?  ", new String[]{String.valueOf(0), String.valueOf(pageCount)});
            models = getList(cursor);
        } finally {
            closeCursor(cursor);
            closeDatabase(database);
        }
        return models;
    }

    @Override
    public synchronized void update(Attachment model) {
        if (model == null) return;
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.update(tableName, getContentValues(model),
                    BaseSchema.CODE + " = ? " + " AND " + BaseSchema.USER_ID + " = ? ",
                    new String[]{String.valueOf(model.getCode()), String.valueOf(userId)});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
            closeDatabase(database);
        }
    }

    public synchronized void clearOneDriveBackupState() {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.execSQL(" UPDATE " + tableName
                            + " SET " + AttachmentSchema.ONE_DRIVE_SYNC_TIME + " = null , " + AttachmentSchema.ONE_DRIVE_ITEM_ID + " = null "
                            + " WHERE " + BaseSchema.USER_ID + " = " + userId, new String[]{});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
            closeDatabase(database);
        }
    }
    // endregion

    public synchronized Attachment getAttachment(ModelType modelType, long modelCode) {
        List<Attachment> list = getAttachments(modelType, modelCode, AttachmentSchema.ADDED_TIME + " DESC ");
        return list.size() > 0 ? list.get(0) : null;
    }

    public synchronized List<Attachment> getAttachments(ModelType modelType, long modelCode, String orderSQL) {
        Cursor cursor = null;
        List<Attachment> models = null;
        SQLiteDatabase database = getWritableDatabase();
        try {
            cursor = database.rawQuery(" SELECT * FROM " + tableName +
                    " WHERE " + AttachmentSchema.USER_ID + " = " + userId
                    + " AND " + AttachmentSchema.MODEL_TYPE + " = " + modelType.id
                    + " AND " + AttachmentSchema.MODEL_CODE + " = " + modelCode
                    + " AND " + AttachmentSchema.STATUS + " = " + Status.NORMAL.id
                    + (TextUtils.isEmpty(orderSQL) ? "" : " ORDER BY " + orderSQL), new String[]{});
            models = getList(cursor);
        } finally {
            closeCursor(cursor);
            closeDatabase(database);
        }
        return models;
    }

    public synchronized void updateAttachments(Assignment assignment, List<Attachment> attachments) {
        final SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(tableName,
                    AttachmentSchema.MODEL_CODE + " = " + assignment.getCode()
                    + " AND " + AttachmentSchema.MODEL_TYPE + " = " + ModelType.ASSIGNMENT.id
                    + " AND " + AttachmentSchema.USER_ID + " = " + userId, new String[]{});
            for (Attachment attachment : attachments){
                if (attachment.isNew()) {
                    // only update last modified time for new added items
                    StoreHelper.setLastModifiedInfo(attachment);
                    TimeLine timeLine = TimelineHelper.getTimeLine(attachment, Operation.ADD);
                    database.insert(TimelineSchema.TABLE_NAME, null, StoreHelper.getContentValues(timeLine));
                }
                database.insert(tableName, null, getContentValues(attachment));
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
            closeDatabase(database);
        }
    }
}