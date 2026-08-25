package com.wpzenora.storemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String CHANNEL_ID = "new_orders";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private static final long POLL_MS = 60_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Order> orders = new ArrayList<>();

    private SecureStore secureStore;
    private String siteUrl;
    private String consumerKey;
    private String consumerSecret;
    private LinearLayout orderContainer;
    private TextView summaryText;
    private TextView statusText;
    private EditText searchBox;
    private Spinner statusSpinner;
    private boolean fetchInFlight = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasConnection()) fetchOrders(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secureStore = new SecureStore(this);
        loadConnection();
        createNotificationChannel();
        if (hasConnection()) {
            showDashboard();
            fetchOrders(true);
        } else {
            showSetup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(pollRunnable);
        if (hasConnection()) handler.postDelayed(pollRunnable, POLL_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadConnection() {
        siteUrl = secureStore.get("site_url");
        consumerKey = secureStore.get("consumer_key");
        consumerSecret = secureStore.get("consumer_secret");
    }

    private boolean hasConnection() {
        return siteUrl != null && !siteUrl.isEmpty()
                && consumerKey != null && !consumerKey.isEmpty()
                && consumerSecret != null && !consumerSecret.isEmpty();
    }

    private void showSetup() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical(20);
        scroll.addView(root);

        TextView title = text("Store Manager", 30, true);
        TextView subtitle = text("Connect your WooCommerce website securely", 16, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(title);
        root.addView(space(8));
        root.addView(subtitle);
        root.addView(space(24));

        TextView note = text("Use HTTPS. Create a Read/Write REST API key in WooCommerce > Settings > Advanced > REST API.", 14, false);
        note.setPadding(dp(14), dp(14), dp(14), dp(14));
        note.setBackground(rounded(0xFFF1F5F9, 12));
        root.addView(note);
        root.addView(space(20));

        EditText urlInput = input("https://yourstore.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText keyInput = input("Consumer Key (ck_...)", InputType.TYPE_CLASS_TEXT);
        EditText secretInput = input("Consumer Secret (cs_...)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(label("Website URL")); root.addView(urlInput);
        root.addView(space(12));
        root.addView(label("Consumer Key")); root.addView(keyInput);
        root.addView(space(12));
        root.addView(label("Consumer Secret")); root.addView(secretInput);
        root.addView(space(20));

        Button connect = button("Test & Connect");
        root.addView(connect);
        TextView setupStatus = text("", 14, false);
        setupStatus.setPadding(0, dp(12), 0, 0);
        root.addView(setupStatus);

        connect.setOnClickListener(v -> {
            String url = normalizeUrl(urlInput.getText().toString());
            String key = keyInput.getText().toString().trim();
            String secret = secretInput.getText().toString().trim();
            if (!url.startsWith("https://")) {
                setupStatus.setText("For security, the website must use HTTPS.");
                return;
            }
            if (key.isEmpty() || secret.isEmpty()) {
                setupStatus.setText("Consumer Key and Consumer Secret are required.");
                return;
            }
            connect.setEnabled(false);
            setupStatus.setText("Testing connection...");
            executor.execute(() -> {
                try {
                    request(url, key, secret, "GET", "/wp-json/wc/v3/orders?per_page=1", null);
                    secureStore.put("site_url", url);
                    secureStore.put("consumer_key", key);
                    secureStore.put("consumer_secret", secret);
                    siteUrl = url;
                    consumerKey = key;
                    consumerSecret = secret;
                    runOnUiThread(() -> {
                        toast("Website connected successfully");
                        showDashboard();
                        fetchOrders(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        setupStatus.setText("Connection failed: " + friendlyError(e));
                    });
                }
            });
        });

        setContentView(scroll);
    }

    private void showDashboard() {
        LinearLayout page = vertical(0);
        page.setBackgroundColor(0xFFF8FAFC);

        LinearLayout header = horizontal(16);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF111827);
        TextView title = text("Store Manager", 23, true);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, grow);
        Button settings = button("Connection");
        settings.setTextSize(12);
        header.addView(settings);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical(16);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        summaryText = text("Loading store summary...", 15, true);
        summaryText.setPadding(dp(14), dp(14), dp(14), dp(14));
        summaryText.setBackground(rounded(Color.WHITE, 14));
        content.addView(summaryText);
        content.addView(space(12));

        LinearLayout controls = vertical(0);
        controls.setPadding(dp(12), dp(12), dp(12), dp(12));
        controls.setBackground(rounded(Color.WHITE, 14));

        searchBox = input("Search order, customer or product", InputType.TYPE_CLASS_TEXT);
        controls.addView(searchBox);
        controls.addView(space(10));

        String[] statuses = {"All", "Pending", "Processing", "On-hold", "Completed", "Cancelled", "Refunded", "Failed"};
        statusSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses);
        statusSpinner.setAdapter(adapter);
        controls.addView(statusSpinner);
        controls.addView(space(10));

        Button refresh = button("Refresh Orders");
        controls.addView(refresh);
        content.addView(controls);
        content.addView(space(12));

        statusText = text("", 14, false);
        content.addView(statusText);
        content.addView(space(8));

        orderContainer = vertical(0);
        content.addView(orderContainer);

        refresh.setOnClickListener(v -> fetchOrders(true));
        settings.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Website connection")
                .setMessage(siteUrl + "\n\nDisconnecting removes the saved API credentials from this app.")
                .setPositiveButton("Disconnect", (d, which) -> {
                    secureStore.clear();
                    siteUrl = consumerKey = consumerSecret = null;
                    handler.removeCallbacks(pollRunnable);
                    showSetup();
                })
                .setNegativeButton("Cancel", null)
                .show());

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderOrders(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderOrders(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        requestNotificationPermission();
        setContentView(page);
    }

    private void fetchOrders(boolean showLoading) {
        if (fetchInFlight || !hasConnection()) return;
        fetchInFlight = true;
        if (showLoading && statusText != null) statusText.setText("Refreshing orders...");
        executor.execute(() -> {
            try {
                String json = request(siteUrl, consumerKey, consumerSecret, "GET",
                        "/wp-json/wc/v3/orders?per_page=50&orderby=date&order=desc", null);
                JSONArray array = new JSONArray(json);
                List<Order> fresh = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) fresh.add(parseOrder(array.getJSONObject(i)));
                detectNewOrder(fresh);
                runOnUiThread(() -> {
                    orders.clear();
                    orders.addAll(fresh);
                    updateSummary();
                    renderOrders();
                    if (statusText != null) statusText.setText("Last refreshed just now • auto-check every 60 seconds while app is open");
                    fetchInFlight = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (statusText != null) statusText.setText("Refresh failed: " + friendlyError(e));
                    fetchInFlight = false;
                });
            }
        });
    }

    private void updateSummary() {
        int pending = 0, processing = 0;
        double revenue = 0;
        String currency = "";
        for (Order order : orders) {
            if ("pending".equals(order.status)) pending++;
            if ("processing".equals(order.status)) processing++;
            try { revenue += Double.parseDouble(order.total); } catch (Exception ignored) {}
            if (currency.isEmpty()) currency = order.currency;
        }
        summaryText.setText(String.format(Locale.getDefault(),
                "%d recent orders   •   %d pending   •   %d processing\nRecent total: %s %.2f",
                orders.size(), pending, processing, currency, revenue));
    }

    private void renderOrders() {
        if (orderContainer == null) return;
        orderContainer.removeAllViews();
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        String selected = statusSpinner == null ? "All" : String.valueOf(statusSpinner.getSelectedItem());
        String wantedStatus = selected.equals("All") ? "" : selected.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Order order : orders) {
            if (!wantedStatus.isEmpty() && !order.status.equals(wantedStatus)) continue;
            String haystack = (order.number + " " + order.customer + " " + order.email + " " + order.items).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            orderContainer.addView(orderCard(order));
            orderContainer.addView(space(10));
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("No matching orders found.", 15, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            orderContainer.addView(empty);
        }
    }

    private View orderCard(Order order) {
        LinearLayout card = vertical(14);
        card.setBackground(rounded(Color.WHITE, 14));
        card.setClickable(true);

        LinearLayout top = horizontal(0);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView number = text("Order #" + order.number, 17, true);
        TextView badge = text(prettyStatus(order.status), 12, true);
        badge.setTextColor(statusColor(order.status));
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(rounded(0xFFF1F5F9, 20));
        top.addView(number, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(badge);
        card.addView(top);
        card.addView(space(8));

        TextView customer = text(order.customer.isEmpty() ? "Guest customer" : order.customer, 15, true);
        card.addView(customer);
        TextView items = text(order.items, 14, false);
        items.setTextColor(Color.DKGRAY);
        card.addView(items);
        card.addView(space(8));

        TextView footer = text(order.currency + " " + order.total + "   •   " + order.date, 14, true);
        card.addView(footer);
        card.setOnClickListener(v -> showOrderDetails(order));
        return card;
    }

    private void showOrderDetails(Order order) {
        StringBuilder details = new StringBuilder();
        details.append("Status: ").append(prettyStatus(order.status)).append("\n")
                .append("Customer: ").append(order.customer).append("\n")
                .append("Email: ").append(order.email).append("\n")
                .append("Phone: ").append(order.phone).append("\n")
                .append("Total: ").append(order.currency).append(" ").append(order.total).append("\n")
                .append("Payment: ").append(order.payment).append("\n")
                .append("Date: ").append(order.date).append("\n\nItems:\n").append(order.items);
        new AlertDialog.Builder(this)
                .setTitle("Order #" + order.number)
                .setMessage(details.toString())
                .setPositiveButton("Change Status", (d, which) -> showStatusPicker(order))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showStatusPicker(Order order) {
        String[] labels = {"Pending", "Processing", "On-hold", "Completed", "Cancelled", "Refunded"};
        String[] values = {"pending", "processing", "on-hold", "completed", "cancelled", "refunded"};
        new AlertDialog.Builder(this)
                .setTitle("Set order status")
                .setItems(labels, (dialog, which) -> updateOrderStatus(order, values[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateOrderStatus(Order order, String newStatus) {
        toast("Updating order #" + order.number + "...");
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("status", newStatus);
                request(siteUrl, consumerKey, consumerSecret, "PUT",
                        "/wp-json/wc/v3/orders/" + order.id, payload.toString());
                runOnUiThread(() -> {
                    toast("Order #" + order.number + " updated to " + prettyStatus(newStatus));
                    fetchInFlight = false;
                    fetchOrders(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Update failed: " + friendlyError(e)));
            }
        });
    }

    private Order parseOrder(JSONObject obj) {
        Order order = new Order();
        order.id = obj.optLong("id");
        order.number = obj.optString("number", String.valueOf(order.id));
        order.status = obj.optString("status", "pending");
        order.currency = obj.optString("currency", "");
        order.total = obj.optString("total", "0");
        order.payment = obj.optString("payment_method_title", "");
        String rawDate = obj.optString("date_created", "");
        order.date = rawDate.length() >= 16 ? rawDate.substring(0, 16).replace('T', ' ') : rawDate;

        JSONObject billing = obj.optJSONObject("billing");
        if (billing != null) {
            order.customer = (billing.optString("first_name") + " " + billing.optString("last_name")).trim();
            order.email = billing.optString("email");
            order.phone = billing.optString("phone");
        }
        JSONArray lines = obj.optJSONArray("line_items");
        StringBuilder items = new StringBuilder();
        if (lines != null) {
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.optJSONObject(i);
                if (line == null) continue;
                if (items.length() > 0) items.append("\n");
                items.append("• ").append(line.optString("name", "Product"))
                        .append(" × ").append(line.optInt("quantity", 1));
            }
        }
        order.items = items.length() == 0 ? "No product details" : items.toString();
        return order;
    }

    private String request(String baseUrl, String key, String secret, String method, String path, String body) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/json");
        String auth = Base64.encodeToString((key + ":" + secret).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        connection.setRequestProperty("Authorization", "Basic " + auth);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder result = new StringBuilder();
        if (input != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
            }
        }
        connection.disconnect();
        if (code < 200 || code >= 300) {
            String message = "HTTP " + code;
            try {
                JSONObject error = new JSONObject(result.toString());
                message = error.optString("message", message);
            } catch (Exception ignored) {}
            throw new Exception(message);
        }
        return result.toString();
    }

    private void detectNewOrder(List<Order> fresh) {
        if (fresh.isEmpty()) return;
        long maxId = 0;
        Order newest = null;
        for (Order order : fresh) {
            if (order.id > maxId) { maxId = order.id; newest = order; }
        }
        long previous = 0;
        try {
            String stored = secureStore.get("last_order_id");
            if (stored != null) previous = Long.parseLong(stored);
        } catch (Exception ignored) {}
        if (previous > 0 && maxId > previous && newest != null) notifyNewOrder(newest);
        try { secureStore.put("last_order_id", String.valueOf(maxId)); } catch (Exception ignored) {}
    }

    private void notifyNewOrder(Order order) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("New order #" + order.number)
                .setContentText(order.customer + " • " + order.currency + " " + order.total)
                .setAutoCancel(true);
        manager.notify((int) (order.id % Integer.MAX_VALUE), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "New Orders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications when the app detects a new WooCommerce order");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private String normalizeUrl(String value) {
        String url = value.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String prettyStatus(String status) {
        if (status == null || status.isEmpty()) return "Unknown";
        if ("on-hold".equals(status)) return "On-hold";
        return status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1);
    }

    private int statusColor(String status) {
        if ("completed".equals(status)) return 0xFF15803D;
        if ("processing".equals(status)) return 0xFF1D4ED8;
        if ("pending".equals(status) || "on-hold".equals(status)) return 0xFFB45309;
        if ("cancelled".equals(status) || "failed".equals(status)) return 0xFFB91C1C;
        return 0xFF475569;
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isEmpty() ? "Unknown error" : msg;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private LinearLayout horizontal(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(0xFF111827);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14, true);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(rounded(Color.WHITE, 10));
        return input;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), 0xFFE2E8F0);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class Order {
        long id;
        String number = "";
        String status = "";
        String customer = "";
        String email = "";
        String phone = "";
        String currency = "";
        String total = "0";
        String payment = "";
        String date = "";
        String items = "";
    }
}
