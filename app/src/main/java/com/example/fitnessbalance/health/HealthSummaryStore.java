package com.example.fitnessbalance.health;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Account-specific summary cache in Android's no-backup directory; never stored in Firestore. */
public final class HealthSummaryStore {
    private final AtomicFile file;
    public HealthSummaryStore(Context context, String uid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder("health-");
            for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            file = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".json"));
        } catch (Exception e) { throw new IllegalStateException("Unable to open health cache"); }
    }
    public Map<String, Object> read() {
        try {
            if (file.getBaseFile().length() > 65536) { clear(); return new HashMap<>(); }
            return decode(new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)));
        } catch (Exception ignored) { return new HashMap<>(); }
    }
    public void write(Map<String, Object> data) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(new JSONObject(data).toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) file.failWrite(stream);
            throw new IOException("Health summary was not saved");
        }
    }
    public void clear() { file.delete(); }
    private Map<String, Object> decode(JSONObject data) throws Exception {
        Map<String, Object> result = new HashMap<>();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = data.get(key);
            result.put(key, value == JSONObject.NULL ? null : value instanceof JSONObject ? decode((JSONObject)value) : value);
        }
        return result;
    }
}
