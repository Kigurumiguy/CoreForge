package com.torque.voice;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class MemoryDB extends SQLiteOpenHelper {
    MemoryDB(Context c) { super(c, "torque_memories.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, fact TEXT NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, heard TEXT NOT NULL, said TEXT NOT NULL, created INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    void remember(String fact) {
        if (fact == null || fact.trim().isEmpty()) return;
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("fact", fact.trim().substring(0, Math.min(fact.trim().length(), 500)));
        v.put("created", System.currentTimeMillis());
        getWritableDatabase().insert("memories", null, v);
    }

    List<String> facts() {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT fact FROM memories ORDER BY id DESC LIMIT 12", null)) {
            while (c.moveToNext()) list.add(c.getString(0));
        }
        return list;
    }

    String lastConversation() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT heard FROM history ORDER BY id DESC LIMIT 1", null)) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return "";
    }

    void log(String heard, String said) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("heard", heard); v.put("said", said); v.put("created", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, v);
        getWritableDatabase().execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 500)");
    }

    void forgetLatest() {
        getWritableDatabase().execSQL("DELETE FROM memories WHERE id = (SELECT MAX(id) FROM memories)");
    }
    void clearAll() {
        getWritableDatabase().delete("history", null, null);
        getWritableDatabase().delete("memories", null, null);
    }
}
