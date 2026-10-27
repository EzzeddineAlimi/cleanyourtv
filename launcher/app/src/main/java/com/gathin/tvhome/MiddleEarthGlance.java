package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Real, read-only on-Home weather and news glance panels for the separate Middle-earth
 * test launcher. Fetches only when Home is foregrounded and a cached value is stale.
 * Never talks to Google Home or touches system settings.
 */
final class MiddleEarthGlance {
    private static final String TEST_PACKAGE = "com.ezzeddine.middleearth";
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=45.5017&longitude=-73.5673"
                    + "&current=temperature_2m,weather_code"
                    + "&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=America%2FToronto&forecast_days=1";
    private static final String NEWS_URL = "https://feeds.bbci.co.uk/news/world/rss.xml";
    private static final long WEATHER_AGE_MS = 30L * 60L * 1000L;
    private static final long NEWS_AGE_MS = 15L * 60L * 1000L;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "middle-earth-glance");
        thread.setDaemon(true);
        return thread;
    });
    private final TextView weatherText;
    private final TextView newsText;
    private Future<?> pending;
    private boolean active;
    private boolean closed;

    static MiddleEarthGlance attach(Activity activity, FrameLayout root) {
        if (!TEST_PACKAGE.equals(activity.getPackageName())) return null;
        return new MiddleEarthGlance(activity, root);
    }

    private MiddleEarthGlance(Activity activity, FrameLayout root) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("middle_earth_glance", Context.MODE_PRIVATE);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setFocusable(false);

        LinearLayout weatherCard = card("WEATHER · MONTRÉAL");
        weatherText = valueText("Forecast loading...");
        weatherCard.addView(weatherText);
        column.addView(weatherCard);

        LinearLayout newsCard = card("WORLD NEWS · BBC");
        newsText = valueText("Headlines loading...");
        newsText.setMaxLines(3);
        newsCard.addView(newsText);
        LinearLayout.LayoutParams newsLp = new LinearLayout.LayoutParams(-1, -2);
        newsLp.topMargin = dp(10);
        column.addView(newsCard, newsLp);

        FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(
                dp(310), -2, Gravity.TOP | Gravity.END);
        panel.topMargin = dp(119);
        panel.rightMargin = dp(57);
        root.addView(column, panel);

        weatherText.setText(prefs.getString("weather", "Forecast loading..."));
        newsText.setText(prefs.getString("news", "Headlines loading..."));
    }

    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(11), dp(16), dp(13));
        box.setFocusable(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(214, 8, 22, 17));
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), Color.rgb(165, 136, 77));
        box.setBackground(background);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(Color.rgb(219, 187, 118));
        heading.setTextSize(12f);
        heading.setLetterSpacing(0.13f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(heading);
        return box;
    }

    private TextView valueText(String value) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(Color.rgb(251, 240, 218));
        text.setTextSize(17f);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        text.setLayoutParams(lp);
        return text;
    }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    void resume() {
        if (closed) return;
        active = true;
        if (pending != null && !pending.isDone()) return;
        final long now = System.currentTimeMillis();
        final boolean fetchWeather = now - prefs.getLong("weather_at", 0) >= WEATHER_AGE_MS;
        final boolean fetchNews = now - prefs.getLong("news_at", 0) >= NEWS_AGE_MS;
        if (!fetchWeather && !fetchNews) return;
        pending = worker.submit(() -> {
            if (fetchWeather && active) loadWeather();
            if (fetchNews && active) loadNews();
        });
    }

    void pause() {
        active = false;
        if (pending != null) pending.cancel(true);
    }

    void close() {
        pause();
        closed = true;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private void loadWeather() {
        HttpURLConnection conn = null;
        try {
            conn = connection(WEATHER_URL);
            try (InputStream input = conn.getInputStream()) {
                byte[] bytes = readLimited(input, 80 * 1024);
                JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                JSONObject current = data.getJSONObject("current");
                int degrees = (int) Math.round(current.getDouble("temperature_2m"));
                int code = current.getInt("weather_code");
                JSONObject daily = data.getJSONObject("daily");
                JSONArray highs = daily.getJSONArray("temperature_2m_max");
                JSONArray lows = daily.getJSONArray("temperature_2m_min");
                String display = degrees + "°C · " + condition(code)
                        + "\nHigh " + Math.round(highs.getDouble(0)) + "° · Low "
                        + Math.round(lows.getDouble(0)) + "°";
                saveResult("weather", "weather_at", display, weatherText);
            }
        } catch (Exception e) {
            showErrorIfEmpty("weather", "Weather unavailable · open Weather app", weatherText);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void loadNews() {
        HttpURLConnection conn = null;
        try {
            conn = connection(NEWS_URL);
            try (InputStream input = conn.getInputStream()) {
                // Android's bundled pull parser, no WebView, browser or third-party dependency.
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(input, "UTF-8");
                boolean inItem = false;
                String headline = null;
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("item".equalsIgnoreCase(name)) inItem = true;
                        else if (inItem && "title".equalsIgnoreCase(name)) {
                            headline = parser.nextText().trim();
                            break;
                        }
                    }
                }
                if (headline == null || headline.isEmpty()) throw new IllegalStateException("No headline");
                if (headline.length() > 150) headline = headline.substring(0, 150) + "…";
                saveResult("news", "news_at", headline, newsText);
            }
        } catch (Exception e) {
            showErrorIfEmpty("news", "News unavailable · open News app", newsText);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private HttpURLConnection connection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "MiddleEarthTV/1.0");
        connection.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml");
        return connection;
    }

    private byte[] readLimited(InputStream in, int limit) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int count;
        while ((count = in.read(buf)) != -1) {
            if (out.size() + count > limit) throw new IllegalStateException("Response too large");
            out.write(buf, 0, count);
        }
        return out.toByteArray();
    }

    private void saveResult(String name, String when, String result, TextView view) {
        prefs.edit().putString(name, result).putLong(when, System.currentTimeMillis()).apply();
        main.post(() -> { if (active && !closed) view.setText(result); });
    }

    private void showErrorIfEmpty(String name, String fallback, TextView view) {
        if (!prefs.contains(name)) {
            main.post(() -> { if (active && !closed) view.setText(fallback); });
        }
    }

    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Cloudy";
    }
}
