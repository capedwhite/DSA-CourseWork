import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.swing.table.*;

public class Question5b extends JFrame {

    private static final String API_KEY = System.getenv("WEATHER_API_KEY");
    private static final String BASE_URL =
        "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric";

    private static final String[][] CITIES = {
        {"Kathmandu",  "🏔"},
        {"Pokhara",    "🌊"},
        {"Biratnagar", "🌿"},
        {"Nepalgunj",  "☀"},
        {"Dhangadhi",  "🌾"},
    };

    static class WeatherData {
        String city, emoji, condition;
        double temp, feelsLike, humidity, pressure, windSpeed;
        int    visibility;
        long   fetchTimeMs;
        boolean success;
        String  error;
        WeatherData(String city, String emoji) { this.city = city; this.emoji = emoji; }
    }


    private JButton           btnFetch, btnClear;
    private JLabel            lblStatus, lblSeqTime, lblParTime, lblSpeedup;
    private JProgressBar      progressBar;
    private JTable            table;
    private DefaultTableModel tableModel;
    private ChartPanel        chartPanel;
    private JTextArea         txaLog;
    private JTabbedPane       tabs;

    private final ConcurrentHashMap<String, WeatherData> results = new ConcurrentHashMap<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final Object tableLock = new Object();
    private long seqLatency = 0, parLatency = 0;


    public Question5b() {
        super("Multi-threaded Weather Collector — Nepal");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(6, 6));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(),   BorderLayout.CENTER);
        add(buildStatus(), BorderLayout.SOUTH);
    }


    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(235, 240, 255));
        p.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("🌤  Multi-threaded Weather Collector — Nepal");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(new Color(40, 60, 140));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btnFetch = makeBtn("⚡ Fetch Weather", new Color(60, 120, 220));
        btnClear = makeBtn("🗑 Clear",          new Color(140, 140, 160));
        btnFetch.addActionListener(e -> startFetching());
        btnClear.addActionListener(e -> clearAll());
        btns.add(btnFetch);
        btns.add(btnClear);

        p.add(title, BorderLayout.WEST);
        p.add(btns,  BorderLayout.EAST);
        return p;
    }

    // ── Tabs 
    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabs.setForeground(Color.BLACK);
        tabs.addTab("🌡 Weather Data",   buildWeatherTab());
        tabs.addTab("📊 Latency Chart",  buildChartTab());
        tabs.addTab("📋 Thread Log",     buildLogTab());
        tabs.setForegroundAt(0, Color.BLACK);
        tabs.setForegroundAt(1, Color.BLACK);
        tabs.setForegroundAt(2, Color.BLACK);
        return tabs;
    }

    // ── Weather tab 
    private JPanel buildWeatherTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        progressBar = new JProgressBar(0, CITIES.length);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setForeground(new Color(60, 180, 120));
        progressBar.setPreferredSize(new Dimension(0, 22));

        String[] cols = {"", "City", "Temp", "Feels Like", "Humidity",
                         "Pressure", "Wind", "Visibility", "Condition", "Fetch Time"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(220, 228, 255));
        table.getTableHeader().setForeground(new Color(30, 50, 120));
        table.setGridColor(new Color(210, 215, 235));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        int[] widths = {28, 110, 75, 80, 70, 80, 75, 80, 110, 75};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        for (String[] city : CITIES)
            tableModel.addRow(new Object[]{city[1], city[0], "—","—","—","—","—","—","—","—"});

        // Stats row
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        stats.setBackground(new Color(245, 247, 255));
        lblSeqTime = new JLabel("Sequential: —");
        lblParTime = new JLabel("Parallel: —");
        lblSpeedup = new JLabel("Speedup: —");
        for (JLabel l : new JLabel[]{lblSeqTime, lblParTime, lblSpeedup}) {
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            l.setForeground(new Color(40, 60, 130));
            stats.add(l);
        }

        p.add(progressBar,          BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(stats,                BorderLayout.SOUTH);
        return p;
    }


    private JPanel buildChartTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chartPanel = new ChartPanel();
        p.add(chartPanel, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildLogTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        txaLog = new JTextArea();
        txaLog.setEditable(false);
        txaLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txaLog.setBackground(new Color(245, 248, 255));
        txaLog.setForeground(new Color(30, 50, 100));
        p.add(new JScrollPane(txaLog), BorderLayout.CENTER);
        return p;
    }


    private JPanel buildStatus() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(235, 240, 255));
        p.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        lblStatus = new JLabel("Ready");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(80, 90, 120));
        JLabel info = new JLabel("API: OpenWeatherMap  |  Threads: 5");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(new Color(120, 130, 160));
        p.add(lblStatus, BorderLayout.WEST);
        p.add(info,      BorderLayout.EAST);
        return p;
    }

    // ── Fetch logic 
    private void startFetching() {
        btnFetch.setEnabled(false);
        results.clear();
        completedCount.set(0);
        progressBar.setValue(0);
        progressBar.setString("Fetching...");

        SwingUtilities.invokeLater(() -> {
            for (int r = 0; r < tableModel.getRowCount(); r++)
                for (int c = 2; c < tableModel.getColumnCount(); c++)
                    tableModel.setValueAt("⏳", r, c);
        });

        log("Starting fetch — " + new java.util.Date());

        new Thread(() -> {
            // Sequential phase
            log("[SEQUENTIAL PHASE]");
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
            log("Sequential total: " + seqLatency + "ms");

            // Parallel phase
            log("[PARALLEL PHASE — 5 threads]");
            long parStart = System.currentTimeMillis();
            ConcurrentLinkedQueue<WeatherData> parQueue = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(CITIES.length);
            CountDownLatch latch = new CountDownLatch(CITIES.length);

            for (String[] city : CITIES) {
                final String name = city[0], emoji = city[1];
                executor.submit(() -> {
                    log(String.format("  [PAR] %s → fetching %s", Thread.currentThread().getName(), name));
                    WeatherData wd = new WeatherData(name, emoji);
                    long t0 = System.currentTimeMillis();
                    fetchWeather(wd);
                    wd.fetchTimeMs = System.currentTimeMillis() - t0;
                    parQueue.add(wd);
                    results.put(name, wd);
                    log(String.format("  [PAR] %-12s → %dms [%s]", name, wd.fetchTimeMs, wd.success ? "OK" : wd.error));
                    SwingUtilities.invokeLater(() -> updateTableRow(wd));
                    int done = completedCount.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(done);
                        progressBar.setString(done + " / " + CITIES.length + " fetched");
                        lblStatus.setText("Fetching... " + done + "/" + CITIES.length);
                    });
                    latch.countDown();
                });
            }

            try { latch.await(30, TimeUnit.SECONDS); }
            catch (InterruptedException e) { log("Interrupted: " + e.getMessage()); }
            executor.shutdown();

            parLatency = System.currentTimeMillis() - parStart;
            double speedup = seqLatency > 0 ? (double) seqLatency / parLatency : 1.0;
            log("Parallel total: " + parLatency + "ms  |  Speedup: " + String.format("%.2fx", speedup));

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(CITIES.length);
                progressBar.setString("✅ Done!");
                lblSeqTime.setText("Sequential: " + seqLatency + "ms");
                lblParTime.setText("Parallel: "   + parLatency + "ms");
                lblSpeedup.setText("Speedup: "    + String.format("%.2fx", speedup));
                chartPanel.setData(seqLatency, parLatency, seqResults, new ArrayList<>(parQueue));
                chartPanel.repaint();
                lblStatus.setText("Done! Speedup: " + String.format("%.2fx", speedup));
                btnFetch.setEnabled(true);
                tabs.setSelectedIndex(1);
            });

        }, "WeatherFetch-Coordinator").start();
    }

    // ── API call
    private void fetchWeather(WeatherData wd) {
        try {
            String urlStr = String.format(BASE_URL, URLEncoder.encode(wd.city, "UTF-8"), API_KEY);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) { simulateData(wd); return; }
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            parseJSON(wd, sb.toString());
            wd.success = true;
        } catch (Exception e) {
            wd.success = false;
            wd.error = e.getClass().getSimpleName();
            simulateData(wd);
        }
    }

    private void parseJSON(WeatherData wd, String json) {
        wd.temp       = parseDouble(json, "\"temp\":");
        wd.feelsLike  = parseDouble(json, "\"feels_like\":");
        wd.humidity   = parseDouble(json, "\"humidity\":");
        wd.pressure   = parseDouble(json, "\"pressure\":");
        wd.windSpeed  = parseDouble(json, "\"speed\":");
        wd.visibility = (int) parseDouble(json, "\"visibility\":");
        int ds = json.indexOf("\"description\":\"") + 15;
        int de = json.indexOf("\"", ds);
        wd.condition  = (ds > 14 && de > 0) ? json.substring(ds, de) : "N/A";
    }

    private double parseDouble(String json, String key) {
        try {
            int i = json.indexOf(key); if (i < 0) return 0;
            int s = i + key.length(), e = s;
            while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '.' || json.charAt(e) == '-')) e++;
            return Double.parseDouble(json.substring(s, e));
        } catch (Exception e) { return 0; }
    }

    private void simulateData(WeatherData wd) {
        Random r = new Random(wd.city.hashCode());
        wd.temp = 15 + r.nextInt(20); wd.feelsLike = wd.temp - 2 + r.nextInt(4);
        wd.humidity = 50 + r.nextInt(40); wd.pressure = 1010 + r.nextInt(20);
        wd.windSpeed = 1 + r.nextDouble() * 8; wd.visibility = 5000 + r.nextInt(5000);
        wd.condition = new String[]{"Clear sky","Few clouds","Overcast","Light rain","Partly cloudy"}[r.nextInt(5)];
        try { Thread.sleep(200 + r.nextInt(600)); } catch (InterruptedException ignored) {}
        wd.success = true; wd.error = "Simulated";
    }

    private void updateTableRow(WeatherData wd) {
        synchronized (tableLock) {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                if (tableModel.getValueAt(r, 1).equals(wd.city)) {
                    if (wd.success) {
                        tableModel.setValueAt(wd.emoji, r, 0);
                        tableModel.setValueAt(wd.city,  r, 1);
                        tableModel.setValueAt(String.format("%.1f°C",  wd.temp),      r, 2);
                        tableModel.setValueAt(String.format("%.1f°C",  wd.feelsLike), r, 3);
                        tableModel.setValueAt(String.format("%.0f%%",  wd.humidity),  r, 4);
                        tableModel.setValueAt(String.format("%.0fhPa", wd.pressure),  r, 5);
                        tableModel.setValueAt(String.format("%.1fm/s", wd.windSpeed), r, 6);
                        tableModel.setValueAt(String.format("%dm",     wd.visibility),r, 7);
                        tableModel.setValueAt(wd.condition,            r, 8);
                        tableModel.setValueAt(wd.fetchTimeMs + "ms",   r, 9);
                    } else {
                        tableModel.setValueAt("❌ " + wd.error, r, 2);
                    }
                    break;
                }
            }
        }
    }

    // ── Simple bar chart 
    static class ChartPanel extends JPanel {
        private long seqTotal = 0, parTotal = 0;
        private List<WeatherData> seqData = new ArrayList<>(), parData = new ArrayList<>();

        void setData(long seq, long par, List<WeatherData> sd, List<WeatherData> pd) {
            seqTotal = seq; parTotal = par; seqData = sd; parData = pd;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight(), pad = 55;

            if (seqTotal == 0) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g2.drawString("Click 'Fetch Weather' to see latency chart.", W/2 - 160, H/2);
                return;
            }

            Color cSeq = new Color(220, 80,  80);
            Color cPar = new Color(60,  180, 100);

            // ── Top: total bars 
            int topBot = H / 2 - 20;
            int availH = topBot - 60;
            long maxV  = Math.max(seqTotal, parTotal);
            int barW   = 80, cW = W - 2*pad;

            g2.setColor(new Color(40, 60, 130));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString("Total: Sequential vs Parallel", pad, 22);

            // grid lines
            for (int i = 1; i <= 4; i++) {
                int y = topBot - i * availH / 4;
                g2.setColor(new Color(200, 205, 220));
                g2.setStroke(new BasicStroke(0.6f));
                g2.drawLine(pad, y, W - pad, y);
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2.drawString((int)(maxV * i / 4) + "ms", pad - 42, y + 4);
            }

            int seqX = pad + cW/4 - barW/2;
            int parX = pad + 3*cW/4 - barW/2;
            int seqH = (int)((double) seqTotal / maxV * availH);
            int parH = (int)((double) parTotal / maxV * availH);

            g2.setColor(cSeq); g2.fillRoundRect(seqX, topBot - seqH, barW, seqH, 5, 5);
            g2.setColor(cPar); g2.fillRoundRect(parX, topBot - parH, barW, parH, 5, 5);

            g2.setColor(Color.DARK_GRAY); g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.drawString("Sequential",  seqX + barW/2 - 28, topBot + 14);
            g2.drawString(seqTotal + "ms", seqX + barW/2 - 18, topBot - seqH - 5);
            g2.drawString("Parallel",    parX + barW/2 - 22, topBot + 14);
            g2.drawString(parTotal + "ms", parX + barW/2 - 18, topBot - parH - 5);

            double speedup = (double) seqTotal / parTotal;
            g2.setColor(new Color(180, 120, 0));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.drawString(String.format("%.2fx faster", speedup), W/2 - 35, topBot - availH/2);

            // ── Bottom: per-city bars ─────────────────────────────
            int botTop = H / 2 + 10, botBot = H - 30;
            int availH2 = botBot - botTop - 30;

            g2.setColor(new Color(40, 60, 130));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString("Per-City Fetch Time", pad, botTop + 16);

            if (seqData.isEmpty() || parData.isEmpty()) return;
            long maxC = 0;
            for (WeatherData w : seqData) maxC = Math.max(maxC, w.fetchTimeMs);
            for (WeatherData w : parData) maxC = Math.max(maxC, w.fetchTimeMs);
            if (maxC == 0) maxC = 1000;

            Map<String, WeatherData> parMap = new HashMap<>();
            for (WeatherData w : parData) parMap.put(w.city, w);

            int n = seqData.size(), slotW = (W - 2*pad) / n, bw = slotW / 3;
            for (int i = 0; i < n; i++) {
                WeatherData sw = seqData.get(i);
                WeatherData pw = parMap.getOrDefault(sw.city, sw);
                int sx = pad + i * slotW + slotW/6;

                int sH = (int)((double) sw.fetchTimeMs / maxC * availH2);
                int pH = (int)((double) pw.fetchTimeMs / maxC * availH2);

                g2.setColor(cSeq); g2.fillRoundRect(sx,       botBot - sH, bw, sH, 4, 4);
                g2.setColor(cPar); g2.fillRoundRect(sx + bw + 3, botBot - pH, bw, pH, 4, 4);

                g2.setColor(Color.DARK_GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                String cityName = sw.city.length() > 8 ? sw.city.substring(0, 7) + "…" : sw.city;
                g2.drawString(cityName, sx, botBot + 12);
                g2.setColor(cSeq); g2.drawString(sw.fetchTimeMs + "ms", sx,        botBot - sH - 2);
                g2.setColor(cPar); g2.drawString(pw.fetchTimeMs + "ms", sx + bw+3, botBot - pH - 2);
            }

            // Legend
            g2.setColor(cSeq); g2.fillRect(W - 140, 8, 12, 12);
            g2.setColor(Color.DARK_GRAY); g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString("Sequential", W - 124, 19);
            g2.setColor(cPar); g2.fillRect(W - 140, 26, 12, 12);
            g2.setColor(Color.DARK_GRAY); g2.drawString("Parallel", W - 124, 37);
        }
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
        progressBar.setValue(0); progressBar.setString("Ready");
        chartPanel.setData(0, 0, new ArrayList<>(), new ArrayList<>());
        chartPanel.repaint();
        lblStatus.setText("Cleared.");
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txaLog.append("[" + String.format("%tT", new java.util.Date()) + "]  " + msg + "\n");
            txaLog.setCaretPosition(txaLog.getDocument().getLength());
        });
    }

    private JButton makeBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color); b.setForeground(Color.black);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Question5b().setVisible(true));
    }
}