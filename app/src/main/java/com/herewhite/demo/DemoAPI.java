package com.herewhite.demo;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by buhe on 2018/8/16.
 */

public class DemoAPI {
    private static final String TAG = DemoAPI.class.getSimpleName();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    Context contextRef;
    private static final String host = "https://cloudcapiv4.herewhite.com";

    private final String sdkToken;
    private final String appId;
    private final String demoUUID;
    private final String demoRoomToken;

    public DemoAPI() {
        contextRef = MainApplication.sContext;
        appId = contextRef.getString(R.string.sdk_app_id);
        sdkToken = contextRef.getString(R.string.sdk_app_token);

        demoUUID = contextRef.getString(R.string.room_uuid);
        demoRoomToken = contextRef.getString(R.string.room_token);
    }

    public String getAppId() {
        return appId;
    }

    public String getDemoUUID() {
        return demoUUID;
    }

    public String getSdkToken() {
        return sdkToken;
    }

    private OkHttpClient client = new OkHttpClient();
    private Gson gson = new Gson();

    boolean hasDemoInfo() {
        return demoUUID.length() > 0 && demoRoomToken.length() > 0;
    }

    public boolean validateToken() {
        return hasDemoInfo() || sdkToken.length() > 50;
    }

    public interface Result {
        void success(String uuid, String token);

        void fail(String message);
    }

    public void getNewRoom(final Result result) {
        if (hasDemoInfo()) {
            result.success(demoUUID, demoRoomToken);
            return;
        }

        createRoom(100, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                result.fail("网络请求错误：" + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.code() == 200) {
                        JsonObject room = gson.fromJson(response.body().string(), JsonObject.class);
                        String uuid = room.getAsJsonObject("msg").getAsJsonObject("room").get("uuid").getAsString();
                        String roomToken = room.getAsJsonObject("msg").get("roomToken").getAsString();
                        result.success(uuid, roomToken);
                    } else {
                        assert response.body() != null;
                        result.fail("创建房间失败：" + response.body().string());
                    }
                } catch (Throwable e) {
                    result.fail("网络请求错误：" + e.toString());
                }
            }
        });
    }

    private void createRoom(int limit, Callback callback) {
        Map<String, Object> roomSpec = new HashMap<>();
        roomSpec.put("name", "Android test room");
        roomSpec.put("limit", limit);
        roomSpec.put("mode", "historied");

        RequestBody body = RequestBody.create(JSON, gson.toJson(roomSpec));
        Request request = new Request.Builder()
                .url(host + "/room")
                .addHeader("token", sdkToken)
                .post(body)
                .build();

        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public void getRoomToken(final String uuid, final Result result) {
        if (uuid.equals(demoUUID)) {
            result.success(demoUUID, demoRoomToken);
            return;
        }

        Map<String, Object> roomSpec = new HashMap<>();
        RequestBody body = RequestBody.create(JSON, gson.toJson(roomSpec));
        Request request = new Request.Builder()
                .url(host + "/room/join?uuid=" + uuid)
                .addHeader("token", sdkToken)
                .post(body)
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                result.fail("网络请求错误：" + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    assert response.body() != null;
                    if (response.code() == 200) {
                        JsonObject room = gson.fromJson(response.body().string(), JsonObject.class);
                        String roomToken = room.getAsJsonObject("msg").get("roomToken").getAsString();
                        result.success(uuid, roomToken);
                    } else {
                        result.fail("获取房间 token 失败：" + response.body().string());
                    }
                } catch (Throwable e) {
                    result.fail("网络请求错误：" + e.toString());
                }
            }
        });
    }

    public void downloadZip(String zipUrl, String des) {
        Request request = new Request.Builder().url(zipUrl).build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "download error: " + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException(("下载失败: " + response));
                }
                String path = des + "/convertcdn.netless.link/dynamicConvert";
                File file = new File(path);
                if (!file.exists()) {
                    boolean success = file.mkdirs();
                    Log.i("LocalFile", "success: " + success + " path: " + path);
                } else {
                    Log.i("LocalFile", path + " is exist");
                }

                FileOutputStream fos = new FileOutputStream(path + "/1.zip", false);
                fos.write(response.body().bytes());
                fos.close();
                unzip(new File(path + "/1.zip"), new File(path));
                Log.i("LocalFile", "unzip");
            }
        });
    }

    private static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    int count;
                    byte[] buffer = new byte[8192];
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            }
        } finally {
            zis.close();
        }
    }

}
