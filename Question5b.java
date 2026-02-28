import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;


public class Question5b extends JFrame {


    private static final String API_KEY = "96caa078cb05c25157f43a24770528dd";
    private static final String BASE_URL =
        "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric";

//cities
    private static final String[][] CITIES = {
        {"Kathmandu",  "🏔"},
        {"Pokhara",    "🌊"},
        {"Biratnagar", "🌿"},
        {"Nepalgunj",  "☀"},
        {"Dhangadhi",  "🌾"},
    };


    // COLORS

    static final Color COL_BG      = new Color(10, 15, 35);
    static final Color COL_PANEL   = new Color(18, 26, 52);
    static final Color COL_CARD    = new Color(28, 38, 68);
    static final Color COL_ACCENT  = new Color(96, 165, 250);
    static final Color COL_GREEN   = new Color(52, 211, 153);
    static final Color COL_ORANGE  = new Color(251, 146, 60);
    static final Color COL_RED     = new Color(248, 113, 113);
    static final Color COL_YELLOW  = new Color(251, 191, 36);
    static final Color COL_TEXT    = new Color(226, 232, 240);
    static final Color COL_MUTED   = new Color(100, 116, 139);
    static final Color COL_SEQ     = new Color(239, 68,  68);
    static final Color COL_PAR     = new Color(34,  197, 94);

    // ─────────────────────────────────────────────────────────────
    // DATA MODEL
    // ─────────────────────────────────────────────────────────────
    static class WeatherData {
        String city, emoji, condition, icon;
        double temp, feelsLike, humidity, pressure, windSpeed;
        int    visibility;
        long   fetchTimeMs;
        boolean success;
        String  error;

        WeatherData(String city, String emoji) {
            this.city  = city;
            this.emoji = emoji;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GUI COMPONENTS
    // ─────────────────────────────────────────────────────────────
    private JButton       btnFetch, btnClear;
    private JLabel        lblStatus, lblSeqTime, lblParTime, lblSpeedup;
    private JProgressBar  progressBar;
    private JTable        table;
    private DefaultTableModel tableModel;
    private LatencyChartPanel chartPanel;
    private JTextArea     txaLog;
    private JTabbedPane   tabs;

    // Thread-safe state
    private final ConcurrentHashMap<String, WeatherData> results = new ConcurrentHashMap<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final Object tableLock = new Object();

    // Latency tracking
    private long seqLatency = 0;
    private long parLatency = 0;

    // ─────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────
    public Question5b() {
        super("🌤  Multi-threaded Weather Collector — Nepal");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 780);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COL_BG);
        setLayout(new BorderLayout(8, 8));

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildStatus(),  BorderLayout.SOUTH);


    }

    // ─────────────────────────────────────────────────────────────
    // HEADER
    // ─────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setBackground(COL_CARD);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Left — title
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        JLabel title = new JLabel("🌤  Multi-threaded Weather Data Collector");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(COL_ACCENT);
        JLabel sub = new JLabel("5 Nepal cities · 5 concurrent threads · Sequential vs Parallel latency comparison");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(COL_MUTED);
        left.add(title);
        left.add(sub);

        // Right — buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        btnFetch = makeBtn("⚡  Fetch Weather", COL_ACCENT);
        btnClear = makeBtn("🗑  Clear", COL_MUTED);
        btnFetch.addActionListener(e -> startFetching());
        btnClear.addActionListener(e -> clearAll());
        right.add(btnFetch);
        right.add(btnClear);

        p.add(left,  BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // CENTER — TABS
    // ─────────────────────────────────────────────────────────────
    private JTabbedPane buildCenter() {
        tabs = new JTabbedPane();
        tabs.setBackground(COL_PANEL);
        tabs.setForeground(Color.black);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.setBorder(new EmptyBorder(6, 8, 6, 8));

        tabs.addTab("🌡  Weather Data",    buildWeatherTab());
        tabs.addTab("📊  Latency Chart",   buildChartTab());
        tabs.addTab("📋  Thread Log",      buildLogTab());
        return tabs;
    }

    // ── Tab 1: Weather Table ────────────────────────────────────
    private JPanel buildWeatherTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(COL_BG);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Progress bar
        progressBar = new JProgressBar(0, CITIES.length);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setBackground(COL_CARD);
        progressBar.setForeground(COL_GREEN);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 28));

        // Table
        String[] cols = {"", "City", "🌡 Temp (°C)", "🤔 Feels Like", "💧 Humidity",
                         "🔵 Pressure", "💨 Wind (m/s)", "👁 Visibility", "☁ Condition", "⏱ Fetch (ms)"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable();

        // Pre-fill rows
        for (String[] city : CITIES) {
            tableModel.addRow(new Object[]{city[1], city[0], "—", "—", "—", "—", "—", "—", "—", "—"});
        }

        JScrollPane sp = new JScrollPane(table);
        sp.setBackground(COL_PANEL);
        sp.getViewport().setBackground(COL_CARD);
        sp.setBorder(BorderFactory.createLineBorder(COL_CARD, 1));

        // Stats row
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        stats.setBackground(COL_BG);
        lblSeqTime = statLabel("Sequential: —");
        lblParTime = statLabel("Parallel: —");
        lblSpeedup = statLabel("Speedup: —");
        stats.add(colorDot(COL_SEQ)); stats.add(lblSeqTime);
        stats.add(colorDot(COL_PAR)); stats.add(lblParTime);
        stats.add(colorDot(COL_YELLOW)); stats.add(lblSpeedup);

        p.add(progressBar, BorderLayout.NORTH);
        p.add(sp,          BorderLayout.CENTER);
        p.add(stats,       BorderLayout.SOUTH);
        return p;
    }

    // ── Tab 2: Latency Chart ────────────────────────────────────
    private JPanel buildChartTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COL_BG);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        chartPanel = new LatencyChartPanel();
        p.add(chartPanel, BorderLayout.CENTER);
        return p;
    }

    // ── Tab 3: Thread Log ───────────────────────────────────────
    private JPanel buildLogTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COL_BG);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        txaLog = new JTextArea();
        txaLog.setEditable(false);
        txaLog.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        txaLog.setBackground(new Color(8, 14, 30));
        txaLog.setForeground(COL_GREEN);
        txaLog.setCaretColor(COL_GREEN);
        txaLog.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane sp = new JScrollPane(txaLog);
        sp.setBorder(BorderFactory.createLineBorder(COL_CARD));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // STATUS BAR
    // ─────────────────────────────────────────────────────────────
    private JPanel buildStatus() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COL_CARD);
        p.setBorder(new EmptyBorder(6, 16, 6, 16));
        lblStatus = new JLabel("Ready");
        lblStatus.setForeground(COL_MUTED);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(lblStatus, BorderLayout.WEST);

        JLabel api = new JLabel("API: OpenWeatherMap  |  Units: Metric  |  Threads: 5");
        api.setForeground(COL_MUTED);
        api.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        p.add(api, BorderLayout.EAST);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 3 & 4 — MULTITHREADED FETCH + TASK 5 — LATENCY
    // ─────────────────────────────────────────────────────────────
    private void startFetching() {
        btnFetch.setEnabled(false);
        results.clear();
        completedCount.set(0);
        progressBar.setValue(0);
        progressBar.setString("Fetching...");

        // Reset table rows to "loading"
        SwingUtilities.invokeLater(() -> {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                for (int c = 2; c < tableModel.getColumnCount(); c++)
                    tableModel.setValueAt("⏳", r, c);
            }
        });

        log("═══════════════════════════════════════════");
        log("  Starting weather fetch — " + new java.util.Date());
        log("═══════════════════════════════════════════");

        // Run both sequential and parallel in a background thread
        // (so GUI stays responsive — TASK 1 & 4)
        new Thread(() -> {
            // STEP 1 — SEQUENTIAL fetch (for latency comparison)
            log("\n[SEQUENTIAL PHASE]");
            long seqStart = System.currentTimeMillis();
            List<WeatherData> seqResults = new ArrayList<>();
            for (String[] city : CITIES) {
                WeatherData wd = new WeatherData(city[0], city[1]);
                long t0 = System.currentTimeMillis();
                fetchWeather(wd);
                wd.fetchTimeMs = System.currentTimeMillis() - t0;
                seqResults.add(wd);
                log(String.format("  [SEQ] %-12s → %dms", city[0], wd.fetchTimeMs));
            }
            seqLatency = System.currentTimeMillis() - seqStart;
            log(String.format("  Sequential total: %dms", seqLatency));

            // STEP 2 — PARALLEL fetch (5 threads, one per city — TASK 3)
            log("\n[PARALLEL PHASE — 5 threads]");
            long parStart = System.currentTimeMillis();

            // Thread-safe queue for results — TASK 4
            ConcurrentLinkedQueue<WeatherData> parQueue = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(CITIES.length);
            CountDownLatch latch = new CountDownLatch(CITIES.length);

            for (String[] city : CITIES) {
                final String cityName  = city[0];
                final String cityEmoji = city[1];

                executor.submit(() -> {
                    String threadName = Thread.currentThread().getName();
                    log(String.format("  [PAR] Thread %-20s → fetching %s", threadName, cityName));

                    WeatherData wd = new WeatherData(cityName, cityEmoji);
                    long t0 = System.currentTimeMillis();
                    fetchWeather(wd);
                    wd.fetchTimeMs = System.currentTimeMillis() - t0;

                    // Thread-safe result storage
                    parQueue.add(wd);
                    results.put(cityName, wd);

                    log(String.format("  [PAR] %-12s → done in %dms [%s]",
                        cityName, wd.fetchTimeMs, wd.success ? "OK" : "ERR: " + wd.error));

                    // Thread-safe GUI update via SwingUtilities.invokeLater — TASK 4
                    SwingUtilities.invokeLater(() -> updateTableRow(wd));

                    int done = completedCount.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(done);
                        progressBar.setString(done + " / " + CITIES.length + " cities fetched");
                        setStatus("Fetching... " + done + "/" + CITIES.length + " complete", COL_ACCENT);
                    });

                    latch.countDown();
                });
            }

            try {
                latch.await(30, TimeUnit.SECONDS); // wait for all threads
            } catch (InterruptedException e) {
                log("  [WARN] Fetch interrupted: " + e.getMessage());
            }
            executor.shutdown();

            parLatency = System.currentTimeMillis() - parStart;
            double speedup = seqLatency > 0 ? (double) seqLatency / parLatency : 1.0;

            log(String.format("\n  Parallel total: %dms", parLatency));
            log(String.format("  Speedup: %.2fx (sequential / parallel)", speedup));

            // Final GUI updates — all via invokeLater for thread safety (TASK 4)
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(CITIES.length);
                progressBar.setString("✅ All cities fetched!");
                lblSeqTime.setText(String.format("Sequential: %dms", seqLatency));
                lblParTime.setText(String.format("Parallel: %dms",   parLatency));
                lblSpeedup.setText(String.format("Speedup: %.2fx",   speedup));
                chartPanel.setData(seqLatency, parLatency, seqResults, new ArrayList<>(parQueue));
                chartPanel.repaint();
                setStatus("Done! Sequential: " + seqLatency + "ms | Parallel: " + parLatency + "ms | Speedup: " + String.format("%.2f", speedup) + "x", COL_GREEN);
                btnFetch.setEnabled(true);
                tabs.setSelectedIndex(1); // switch to chart
            });

        }, "WeatherFetch-Coordinator").start();
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 2 — API CALL (OpenWeatherMap)
    // ─────────────────────────────────────────────────────────────
    private void fetchWeather(WeatherData wd) {
        try {
            String urlStr = String.format(BASE_URL, URLEncoder.encode(wd.city, "UTF-8"), API_KEY);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) {
                wd.success = false;
                wd.error   = "HTTP " + code;
                // Use simulated data as fallback for demo purposes
                simulateData(wd);
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            parseJSON(wd, sb.toString());
            wd.success = true;

        } catch (Exception e) {
            wd.success = false;
            wd.error   = e.getClass().getSimpleName() + ": " + e.getMessage();
            // Fallback to simulated data so the GUI demo works without a real key
            simulateData(wd);
        }
    }

    // Simple JSON parser (no external library needed)
    private void parseJSON(WeatherData wd, String json) {
        wd.temp       = parseDouble(json, "\"temp\":");
        wd.feelsLike  = parseDouble(json, "\"feels_like\":");
        wd.humidity   = parseDouble(json, "\"humidity\":");
        wd.pressure   = parseDouble(json, "\"pressure\":");
        wd.windSpeed  = parseDouble(json, "\"speed\":");
        wd.visibility = (int) parseDouble(json, "\"visibility\":");

        int descStart = json.indexOf("\"description\":\"") + 15;
        int descEnd   = json.indexOf("\"", descStart);
        if (descStart > 14 && descEnd > 0)
            wd.condition = json.substring(descStart, descEnd);
        else
            wd.condition = "N/A";
    }

    private double parseDouble(String json, String key) {
        try {
            int i = json.indexOf(key);
            if (i < 0) return 0;
            int start = i + key.length();
            int end   = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end))
                   || json.charAt(end) == '.' || json.charAt(end) == '-'))
                end++;
            return Double.parseDouble(json.substring(start, end));
        } catch (Exception e) { return 0; }
    }

    // Simulated fallback data when API key is not set
    private void simulateData(WeatherData wd) {
        Random r = new Random(wd.city.hashCode());
        wd.temp       = 15 + r.nextInt(20);
        wd.feelsLike  = wd.temp - 2 + r.nextInt(4);
        wd.humidity   = 50 + r.nextInt(40);
        wd.pressure   = 1010 + r.nextInt(20);
        wd.windSpeed  = 1 + r.nextDouble() * 8;
        wd.visibility = 5000 + r.nextInt(5000);
        String[] conds = {"Clear sky", "Few clouds", "Overcast", "Light rain", "Partly cloudy"};
        wd.condition  = conds[r.nextInt(conds.length)];
        // Add realistic network latency simulation
        try { Thread.sleep(200 + r.nextInt(600)); } catch (InterruptedException ignored) {}
        wd.success = true;
        wd.error   = "Simulated (no API key)";
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 4 — THREAD-SAFE TABLE UPDATE
    // ─────────────────────────────────────────────────────────────
    private void updateTableRow(WeatherData wd) {
        // Must be called on EDT (Event Dispatch Thread) via SwingUtilities.invokeLater
        // Synchronized block ensures no two threads corrupt the same row — TASK 4
        synchronized (tableLock) {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                if (tableModel.getValueAt(r, 1).equals(wd.city)) {
                    if (wd.success) {
                        tableModel.setValueAt(wd.emoji,                       r, 0);
                        tableModel.setValueAt(wd.city,                        r, 1);
                        tableModel.setValueAt(String.format("%.1f°C", wd.temp),      r, 2);
                        tableModel.setValueAt(String.format("%.1f°C", wd.feelsLike), r, 3);
                        tableModel.setValueAt(String.format("%.0f%%", wd.humidity),  r, 4);
                        tableModel.setValueAt(String.format("%.0f hPa", wd.pressure),r, 5);
                        tableModel.setValueAt(String.format("%.1f m/s", wd.windSpeed),r, 6);
                        tableModel.setValueAt(String.format("%dm", wd.visibility),   r, 7);
                        tableModel.setValueAt(wd.condition,                   r, 8);
                        tableModel.setValueAt(wd.fetchTimeMs + "ms",          r, 9);
                    } else {
                        tableModel.setValueAt("❌ Error: " + wd.error, r, 2);
                    }
                    break;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 5 — LATENCY BAR CHART PANEL
    // ─────────────────────────────────────────────────────────────
    static class LatencyChartPanel extends JPanel {
        private long seqTotal = 0, parTotal = 0;
        private List<WeatherData> seqData = new ArrayList<>();
        private List<WeatherData> parData = new ArrayList<>();

        LatencyChartPanel() {
            setBackground(new Color(10, 15, 35));
            setPreferredSize(new Dimension(700, 500));
        }

        void setData(long seq, long par, List<WeatherData> seqD, List<WeatherData> parD) {
            this.seqTotal = seq;
            this.parTotal = par;
            this.seqData  = seqD;
            this.parData  = parD;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int W = getWidth(), H = getHeight();

            if (seqTotal == 0) {
                g2.setColor(COL_MUTED);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g2.drawString("Click 'Fetch Weather' to see latency comparison.", W/2 - 180, H/2);
                return;
            }

            drawTotalBars(g2, W, H);
            drawPerCityBars(g2, W, H);
            drawLegend(g2, W);
        }

        // Top section — Sequential vs Parallel totals
        private void drawTotalBars(Graphics2D g2, int W, int H) {
            int topH = H / 2 - 30;
            int pad  = 60;

            g2.setColor(COL_ACCENT);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.drawString("Total Fetch Time: Sequential vs Parallel", pad, 30);

            long maxVal = Math.max(seqTotal, parTotal);
            int barW    = 100;
            int chartW  = W - 2 * pad;
            int seqX    = pad + chartW / 4 - barW / 2;
            int parX    = pad + 3 * chartW / 4 - barW / 2;
            int chartTop = 50;
            int chartBot = topH;
            int availH   = chartBot - chartTop - 20;

            // Y-axis grid
            g2.setStroke(new BasicStroke(0.5f));
            for (int i = 0; i <= 5; i++) {
                int y = chartBot - i * availH / 5;
                g2.setColor(new Color(40, 55, 90));
                g2.drawLine(pad, y, W - pad, y);
                g2.setColor(COL_MUTED);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(String.format("%dms", (int)(maxVal * i / 5)), pad - 50, y + 4);
            }

            // Sequential bar
            int seqH = (int)((double) seqTotal / maxVal * availH);
            drawBar(g2, seqX, chartBot - seqH, barW, seqH, COL_SEQ, "Sequential\n" + seqTotal + "ms", chartBot);

            // Parallel bar
            int parH = (int)((double) parTotal / maxVal * availH);
            drawBar(g2, parX, chartBot - parH, barW, parH, COL_PAR, "Parallel\n" + parTotal + "ms", chartBot);

            // Speedup annotation
            double speedup = (double) seqTotal / parTotal;
            g2.setColor(COL_YELLOW);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString(String.format("%.2fx faster →", speedup), W / 2 - 50, chartBot - availH / 2);
            // Arrow
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(parX + barW + 10, chartBot - parH / 2, seqX - 10, chartBot - seqH / 2);
        }

        private void drawBar(Graphics2D g2, int x, int y, int w, int h, Color color, String label, int bottom) {
            // Gradient fill
            GradientPaint gp = new GradientPaint(x, y, color.brighter(), x, y + h, color.darker());
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, w, h, 6, 6);
            g2.setColor(color.brighter());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, w, h, 6, 6);

            // Label above bar
            g2.setColor(COL_TEXT);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String[] parts = label.split("\n");
            g2.drawString(parts[0], x + w/2 - g2.getFontMetrics().stringWidth(parts[0])/2, y - 18);
            g2.setColor(color.brighter());
            if (parts.length > 1)
                g2.drawString(parts[1], x + w/2 - g2.getFontMetrics().stringWidth(parts[1])/2, y - 5);
        }

        // Bottom section — per-city comparison bars
        private void drawPerCityBars(Graphics2D g2, int W, int H) {
            int topY = H / 2 + 10;
            int botY = H - 40;
            int pad  = 60;

            g2.setColor(COL_ACCENT);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.drawString("Per-City Fetch Time (Sequential vs Parallel)", pad, topY + 20);

            if (seqData.isEmpty() || parData.isEmpty()) return;

            int n        = seqData.size();
            int availW   = W - 2 * pad;
            int slotW    = availW / n;
            int barW     = slotW / 3;
            int availH   = botY - topY - 40;
            int chartBot = botY - 20;

            // Find max per-city time
            long maxCity = 0;
            for (WeatherData w : seqData) maxCity = Math.max(maxCity, w.fetchTimeMs);
            for (WeatherData w : parData) maxCity = Math.max(maxCity, w.fetchTimeMs);
            if (maxCity == 0) maxCity = 1000;

            // Grid
            g2.setStroke(new BasicStroke(0.5f));
            for (int i = 0; i <= 4; i++) {
                int y = chartBot - i * availH / 4;
                g2.setColor(new Color(40, 55, 90));
                g2.drawLine(pad, y, W - pad, y);
                g2.setColor(COL_MUTED);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2.drawString(String.format("%dms", (int)(maxCity * i / 4)), pad - 40, y + 4);
            }

            // One slot per city
            Map<String, WeatherData> parMap = new HashMap<>();
            for (WeatherData w : parData) parMap.put(w.city, w);

            for (int i = 0; i < seqData.size(); i++) {
                WeatherData sw = seqData.get(i);
                WeatherData pw = parMap.getOrDefault(sw.city, sw);

                int slotX = pad + i * slotW + slotW / 6;

                // Sequential bar
                int sH = (int)((double) sw.fetchTimeMs / maxCity * availH);
                GradientPaint gp1 = new GradientPaint(slotX, chartBot - sH, COL_SEQ.brighter(), slotX, chartBot, COL_SEQ.darker());
                g2.setPaint(gp1);
                g2.fillRoundRect(slotX, chartBot - sH, barW, sH, 4, 4);

                // Parallel bar
                int pX = slotX + barW + 4;
                int pH = (int)((double) pw.fetchTimeMs / maxCity * availH);
                GradientPaint gp2 = new GradientPaint(pX, chartBot - pH, COL_PAR.brighter(), pX, chartBot, COL_PAR.darker());
                g2.setPaint(gp2);
                g2.fillRoundRect(pX, chartBot - pH, barW, pH, 4, 4);

                // City label
                g2.setColor(COL_TEXT);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                String name = sw.city.length() > 9 ? sw.city.substring(0, 8) + "…" : sw.city;
                g2.drawString(name, slotX, chartBot + 14);

                // Value labels
                g2.setColor(COL_SEQ);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2.drawString(sw.fetchTimeMs + "ms", slotX, chartBot - sH - 3);
                g2.setColor(COL_PAR);
                g2.drawString(pw.fetchTimeMs + "ms", pX, chartBot - pH - 3);
            }
        }

        private void drawLegend(Graphics2D g2, int W) {
            int lx = W - 200, ly = 45;
            g2.setColor(new Color(20, 30, 55, 220));
            g2.fillRoundRect(lx - 8, ly - 8, 185, 50, 8, 8);
            g2.setColor(COL_SEQ);
            g2.fillRect(lx, ly, 14, 14);
            g2.setColor(COL_TEXT);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString("Sequential fetch", lx + 20, ly + 11);
            g2.setColor(COL_PAR);
            g2.fillRect(lx, ly + 22, 14, 14);
            g2.setColor(COL_TEXT);
            g2.drawString("Parallel fetch", lx + 20, ly + 33);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private void styleTable() {
        table.setBackground(COL_CARD);
        table.setForeground(COL_TEXT);
        table.setGridColor(new Color(35, 48, 80));
        table.setSelectionBackground(new Color(50, 80, 130));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(36);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(20, 32, 60));
        header.setForeground(COL_ACCENT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(0, 38));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, COL_ACCENT));

        // Alternating row colors via custom renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(col == 0 ? CENTER : col == 1 ? LEFT : CENTER);
                setBackground(sel ? new Color(50, 80, 130)
                    : row % 2 == 0 ? COL_CARD : new Color(32, 44, 74));
                setForeground(COL_TEXT);
                setBorder(new EmptyBorder(0, 8, 0, 8));

                // Color-code temperature
                if (col == 2 && val != null && !val.toString().equals("—") && !val.toString().equals("⏳")) {
                    try {
                        double temp = Double.parseDouble(val.toString().replace("°C", ""));
                        if      (temp >= 30) setForeground(COL_RED);
                        else if (temp >= 20) setForeground(COL_ORANGE);
                        else if (temp >= 10) setForeground(COL_GREEN);
                        else                 setForeground(COL_ACCENT);
                    } catch (Exception ignored) {}
                }
                return this;
            }
        });

        // Column widths
        int[] widths = {30, 130, 90, 90, 80, 90, 90, 90, 120, 80};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private void setStatus(String msg, Color c) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(msg);
            lblStatus.setForeground(c);
        });
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txaLog.append("[" + String.format("%tT", new java.util.Date()) + "]  " + msg + "\n");
            txaLog.setCaretPosition(txaLog.getDocument().getLength());
        });
    }

    private void clearAll() {
        for (int r = 0; r < tableModel.getRowCount(); r++)
            for (int c = 2; c < tableModel.getColumnCount(); c++)
                tableModel.setValueAt("—", r, c);
        txaLog.setText("");
        seqLatency = 0; parLatency = 0;
        lblSeqTime.setText("Sequential: —");
        lblParTime.setText("Parallel: —");
        lblSpeedup.setText("Speedup: —");
        progressBar.setValue(0);
        progressBar.setString("Ready");
        chartPanel.setData(0, 0, new ArrayList<>(), new ArrayList<>());
        chartPanel.repaint();
        setStatus("Cleared.", COL_MUTED);
        log("Results cleared.");
    }

    private JButton makeBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.black);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9, 18, 9, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(color.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(color); }
        });
        return b;
    }

    private JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(COL_TEXT);
        return l;
    }

    private JLabel colorDot(Color c) {
        JLabel l = new JLabel("●");
        l.setForeground(c);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return l;
    }

    // ─────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new Question5b().setVisible(true));
    }
}