package com.gianlu.dnshero.NetIO;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ZoneVisionAPI {
    private static final String BASE_URL = "https://api.zone.vision/";
    private static ZoneVisionAPI instance;
    private final ExecutorService executorService;
    private final OkHttpClient client;
    private final Handler handler;

    private ZoneVisionAPI() {
        executorService = Executors.newSingleThreadExecutor();
        client = new OkHttpClient();
        handler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    public static ZoneVisionAPI get() {
        if (instance == null) instance = new ZoneVisionAPI();
        return instance;
    }

    public void search(@NonNull final String query, final OnSearch listener) {
        executorService.execute(() -> {
            try (final Response resp = client.newCall(new Request.Builder()
                    .get().url(BASE_URL + query.trim()).build()).execute()) {

                JSONObject json = null;
                ResponseBody body = resp.body();
                if (body != null) json = new JSONObject(body.string());

                if (json != null && resp.code() >= 200 && resp.code() < 300) {
                    final Domain domain = new Domain(json);

                    handler.post(() -> {
                        if (listener != null) listener.onDone(domain);
                    });
                } else if (json != null && (resp.code() == 400 || resp.code() == 500)) {
                    String error = json.getString("error");
                    throw new ApiException(error);
                } else {
                    handler.post(() -> {
                        if (listener != null)
                            listener.onException(new StatusCodeException(resp));
                    });
                }
            } catch (IOException | JSONException ex) {
                handler.post(() -> {
                    if (listener != null) listener.onException(ex);
                });
            } catch (final ApiException ex) {
                handler.post(() -> {
                    if (listener != null) listener.onApiException(ex);
                });
            }
        });
    }

    public interface OnSearch {
        @UiThread
        void onDone(@NonNull Domain domain);

        @UiThread
        void onException(@NonNull Exception ex);

        @UiThread
        void onApiException(@NonNull ApiException ex);
    }

    public static class StatusCodeException extends Exception {
        StatusCodeException(Response resp) {
            super(resp.code() + ": " + resp.message());
        }
    }

    public static class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
