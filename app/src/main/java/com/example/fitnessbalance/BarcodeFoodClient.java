package com.example.fitnessbalance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BarcodeFoodClient {

    private static final String FIELDS =
            "code,product_name,product_name_en,generic_name,"
                    + "nutriments,nutrition_data_per,nutrition_data,"
                    + "no_nutrition_data";

    private static final Map<String, Cached> CACHE =
            new LinkedHashMap<>();

    private static long lastRequestNanos;
    private static boolean hasRequested;

    public static final class RateLimited extends IOException {}

    private static final class Cached {
        final Map<String, Object> food;
        final long time = System.nanoTime();

        Cached(Map<String, Object> food) {
            this.food = food;
        }
    }

    private BarcodeFoodClient() {}

    // Call this from a worker thread, not Android's main thread.
    public static Map<String, Object> lookup(
            String barcode,
            String userAgent
    ) throws Exception {

        if (!BarcodeFood.validBarcode(barcode)) {
            throw new IllegalArgumentException(
                    "Invalid product barcode"
            );
        }

        synchronized (CACHE) {
            Cached cached = CACHE.get(barcode);

            // Reuse a cached response for up to one hour.
            if (cached != null
                    && System.nanoTime() - cached.time
                    < 3_600_000_000_000L) {
                return cached.food;
            }

            long now = System.nanoTime();

            if (hasRequested
                    && now - lastRequestNanos
                    < 5_000_000_000L) {
                throw new RateLimited();
            }

            lastRequestNanos = now;
            hasRequested = true;
        }

        URL url = new URL(
                "https://world.openfoodfacts.org/api/v2/product/"
                        + barcode
                        + ".json?fields="
                        + FIELDS
        );

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");

        try {
            int status = connection.getResponseCode();

            if (status == 429) {
                throw new RateLimited();
            }

            if (status != 200 && status != 404) {
                throw new IOException(
                        "Product service HTTP " + status
                );
            }

            InputStream response = status == 404
                    ? connection.getErrorStream()
                    : connection.getInputStream();

            if (response == null) {
                throw new IOException("Empty product response");
            }

            byte[] bytes;

            try (
                    InputStream input = response;
                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream()
            ) {
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException();
                    }

                    if (output.size() + count > 1_048_576) {
                        throw new IOException(
                                "Product response too large"
                        );
                    }

                    output.write(buffer, 0, count);
                }

                bytes = output.toByteArray();
            }

            Map<String, Object> food = BarcodeFood.parse(
                    new String(bytes, StandardCharsets.UTF_8),
                    barcode
            );

            synchronized (CACHE) {
                CACHE.put(barcode, new Cached(food));

                if (CACHE.size() > 24) {
                    Iterator<String> keys =
                            CACHE.keySet().iterator();

                    keys.next();
                    keys.remove();
                }
            }

            return food;

        } finally {
            connection.disconnect();
        }
    }
}