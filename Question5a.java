import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;


public class Question5a extends JFrame {

    // ─────────────────────────────────────────────────────────────
    // TASK 2 — TOURIST SPOT DATASET
    // ─────────────────────────────────────────────────────────────
    static class Spot {
        String   name;
        double   lat, lon;
        int      entryFee;          // Rs.
        int      openHour, closeHour;
        String[] tags;
        int      visitDuration;     // hours (estimated)
        int      rating;            // 1-5 interest score

        Spot(String name, double lat, double lon, int fee,
             int openH, int closeH, String[] tags, int dur, int rating) {
            this.name        = name;
            this.lat         = lat;
            this.lon         = lon;
            this.entryFee    = fee;
            this.openHour    = openH;
            this.closeHour   = closeH;
            this.tags        = tags;
            this.visitDuration = dur;
            this.rating      = rating;
        }

        boolean hasTag(String interest) {
            if (interest.equalsIgnoreCase("all")) return true;
            for (String t : tags)
                if (t.equalsIgnoreCase(interest)) return true;
            return false;
        }

        @Override public String toString() { return name; }
    }

    // Full dataset — 10 Kathmandu spots
    static final List<Spot> ALL_SPOTS = new ArrayList<>(Arrays.asList(
        new Spot("Pashupatinath Temple",   27.7104, 85.3488, 100,  6, 18, new String[]{"culture","religious"}, 2, 5),
        new Spot("Swayambhunath Stupa",    27.7149, 85.2906, 200,  7, 17, new String[]{"culture","heritage"},  2, 5),
        new Spot("Garden of Dreams",       27.7125, 85.3170, 150,  9, 21, new String[]{"nature","relaxation"}, 1, 4),
        new Spot("Chandragiri Hills",      27.6616, 85.2458, 700,  9, 17, new String[]{"nature","adventure"},  3, 4),
        new Spot("Kathmandu Durbar Square",27.7048, 85.3076, 100, 10, 17, new String[]{"culture","heritage"},  2, 5),
        new Spot("Boudhanath Stupa",       27.7215, 85.3620, 400,  7, 18, new String[]{"culture","religious"}, 2, 5),
        new Spot("Narayanhiti Palace",     27.7155, 85.3175, 500, 10, 17, new String[]{"culture","heritage"},  1, 3),
        new Spot("Shivapuri Nagarjun Park",27.8050, 85.3700, 200,  7, 17, new String[]{"nature","adventure"},  3, 4),
        new Spot("Patan Durbar Square",    27.6726, 85.3248, 1000,10, 18, new String[]{"culture","heritage"},  2, 5),
        new Spot("Kopan Monastery",        27.7383, 85.3636, 0,   8, 17, new String[]{"culture","religious"}, 1, 3)
    ));

//GUI components
    // Input panel
    private JSpinner    spnHours, spnBudget, spnStartHour;
    private JComboBox<String> cmbInterest;
    private JButton     btnOptimize, btnBruteForce, btnClear;

    // Output panels
    private JTextArea   txaItinerary, txaExplanation, txaBrute;
    private MapPanel    mapPanel;
    private JLabel      lblStatus;
    private JTabbedPane tabs;

    // Colors
    static final Color COL_BG      = new Color(15, 20, 40);
    static final Color COL_PANEL   = new Color(25, 32, 58);
    static final Color COL_CARD    = new Color(35, 44, 75);
    static final Color COL_ACCENT  = new Color(99, 179, 237);
    static final Color COL_GREEN   = new Color(72, 199, 142);
    static final Color COL_ORANGE  = new Color(246, 173, 85);
    static final Color COL_RED     = new Color(252, 129, 129);
    static final Color COL_TEXT    = new Color(226, 232, 240);
    static final Color COL_MUTED   = new Color(113, 128, 150);

    // State
    private List<Spot> lastHeuristicResult = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────
    // CONSTRUCTOR — BUILD UI
    // ─────────────────────────────────────────────────────────────
    public Question5a() {
        super("Tourist Spot Optimizer — Kathmandu, Nepal");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 820);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COL_BG);
        setLayout(new BorderLayout(10, 10));

        add(buildHeader(),     BorderLayout.NORTH);
        add(buildLeftPanel(),  BorderLayout.WEST);
        add(buildMainPanel(),  BorderLayout.CENTER);
        add(buildStatusBar(),  BorderLayout.SOUTH);
    }

    // ── Header 
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COL_CARD);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("Tourist Spot Optimizer  —  Kathmandu, Nepal");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(COL_ACCENT);

        JLabel sub = new JLabel("Heuristic-based itinerary planner using Greedy + Simulated Annealing");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(COL_MUTED);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        p.add(text, BorderLayout.WEST);
        return p;
    }

    // ── Left Input Panel (TASK 1) 
    private JPanel buildLeftPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setPreferredSize(new Dimension(270, 0));
        outer.setBackground(COL_BG);
        outer.setBorder(new EmptyBorder(8, 10, 8, 5));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(COL_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(COL_CARD, 1, true),
            new EmptyBorder(18, 16, 18, 16)));

        card.add(sectionLabel("⚙  Your Preferences"));
        card.add(Box.createVerticalStrut(12));

        // Start hour
        spnStartHour = new JSpinner(new SpinnerNumberModel(9, 6, 12, 1));
        card.add(inputRow("Start Hour (06–12):", spnStartHour));
        card.add(Box.createVerticalStrut(10));

        // Hours available
        spnHours = new JSpinner(new SpinnerNumberModel(6, 1, 12, 1));
        card.add(inputRow("Time Available (hrs):", spnHours));
        card.add(Box.createVerticalStrut(10));

        // Budget
        spnBudget = new JSpinner(new SpinnerNumberModel(2000, 100, 10000, 100));
        card.add(inputRow("Max Budget (Rs.):", spnBudget));
        card.add(Box.createVerticalStrut(10));

        // Interest
        cmbInterest = new JComboBox<>(new String[]{
            "all", "culture", "nature", "adventure", "religious", "heritage", "relaxation"});
        styleCombo(cmbInterest);
        card.add(inputRow("Interest:", cmbInterest));
        card.add(Box.createVerticalStrut(24));

        // Buttons
        btnOptimize   = makeButton("Optimize Itinerary", COL_ACCENT);
        btnBruteForce = makeButton("Brute-Force Compare", COL_ORANGE);
        btnClear      = makeButton("Clear Results", COL_MUTED);

        btnOptimize  .addActionListener(e -> runHeuristic());
        btnBruteForce.addActionListener(e -> runBruteForce());
        btnClear     .addActionListener(e -> clearAll());

        card.add(fullWidth(btnOptimize));
        card.add(Box.createVerticalStrut(8));
        card.add(fullWidth(btnBruteForce));
        card.add(Box.createVerticalStrut(8));
        card.add(fullWidth(btnClear));
        card.add(Box.createVerticalStrut(24));

        // Dataset info
        card.add(sectionLabel("📍  Dataset (10 Spots)"));
        card.add(Box.createVerticalStrut(8));
        for (Spot s : ALL_SPOTS) {
            JLabel lbl = new JLabel("• " + s.name);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lbl.setForeground(COL_MUTED);
            lbl.setAlignmentX(LEFT_ALIGNMENT);
            card.add(lbl);
            card.add(Box.createVerticalStrut(3));
        }

        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    // ── Main Tabbed Panel ────────────────────────────────────────
    private JTabbedPane buildMainPanel() {
        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(COL_PANEL);
        tabs.setForeground(COL_TEXT);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.setBorder(new EmptyBorder(8, 5, 8, 10));

        // Tab 1 — Itinerary
        txaItinerary = makeTextArea();
        tabs.addTab("📋  Itinerary", scrollPane(txaItinerary));

        // Tab 2 — Map
        mapPanel = new MapPanel();
        JScrollPane mapScroll = new JScrollPane(mapPanel);
        mapScroll.setBackground(COL_PANEL);
        tabs.addTab("🗺  Map View", mapScroll);

        // Tab 3 — Explanation
        txaExplanation = makeTextArea();
        tabs.addTab("🧠  Algorithm Explanation", scrollPane(txaExplanation));

        // Tab 4 — Brute Force
        txaBrute = makeTextArea();
        tabs.addTab("🔍  Brute-Force Comparison", scrollPane(txaBrute));

        return tabs;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COL_CARD);
        p.setBorder(new EmptyBorder(6, 16, 6, 16));
        lblStatus = new JLabel("Ready — set your preferences and click Optimize.");
        lblStatus.setForeground(COL_MUTED);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(lblStatus, BorderLayout.WEST);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 3 — HEURISTIC OPTIMIZATION (Greedy + Simulated Annealing)
    // ─────────────────────────────────────────────────────────────
    private void runHeuristic() {
        int maxHours  = (int) spnHours.getValue();
        int maxBudget = (int) spnBudget.getValue();
        int startHour = (int) spnStartHour.getValue();
        String interest = (String) cmbInterest.getSelectedItem();

        setStatus("Running Greedy + Simulated Annealing...", COL_ACCENT);

        // Step 1 — GREEDY PHASE: score and filter spots
        List<Spot> candidates = new ArrayList<>();
        for (Spot s : ALL_SPOTS) {
            if (s.entryFee <= maxBudget && s.hasTag(interest) && s.openHour <= startHour + maxHours)
                candidates.add(s);
        }

        // Score = rating / (entry_fee + 1) * interest_match_bonus
        candidates.sort((a, b) -> Double.compare(score(b, interest), score(a, interest)));

        // Greedy: pick spots until time or budget runs out
        List<Spot> greedySolution = new ArrayList<>();
        int usedBudget = 0, usedTime = 0, currentHour = startHour;

        StringBuilder explanation = new StringBuilder();
        explanation.append("═══════════════════════════════════════════════\n");
        explanation.append("  ALGORITHM EXPLANATION — GREEDY + SIMULATED ANNEALING\n");
        explanation.append("═══════════════════════════════════════════════\n\n");
        explanation.append("PHASE 1: GREEDY SELECTION\n");
        explanation.append("  Spots are scored by:  rating / (fee+1) × tag_bonus\n");
        explanation.append("  Greedily pick highest-scoring feasible spot each step.\n\n");
        explanation.append(String.format("  Start Hour: %02d:00 | Budget: Rs.%d | Max Hours: %d\n\n",
            startHour, maxBudget, maxHours));

        for (Spot s : candidates) {
            if (usedTime + s.visitDuration > maxHours) {
                explanation.append(String.format("  ✗ SKIP  %-28s (time limit)\n", s.name));
                continue;
            }
            if (usedBudget + s.entryFee > maxBudget) {
                explanation.append(String.format("  ✗ SKIP  %-28s (over budget)\n", s.name));
                continue;
            }
            if (currentHour < s.openHour || currentHour + s.visitDuration > s.closeHour) {
                explanation.append(String.format("  ✗ SKIP  %-28s (not open at %02d:00)\n", s.name, currentHour));
                continue;
            }
            greedySolution.add(s);
            usedBudget   += s.entryFee;
            usedTime     += s.visitDuration;
            currentHour  += s.visitDuration;
            explanation.append(String.format("  ✔ ADD   %-28s score=%.3f  fee=Rs.%-5d  dur=%dhr\n",
                s.name, score(s, interest), s.entryFee, s.visitDuration));
        }

        explanation.append(String.format("\n  Greedy result: %d spots | Rs.%d | %d hrs\n",
            greedySolution.size(), usedBudget, usedTime));

        // Step 2 — SIMULATED ANNEALING PHASE: try to improve order (minimize travel distance)
        explanation.append("\nPHASE 2: SIMULATED ANNEALING (path ordering)\n");
        explanation.append("  Minimizes total Euclidean travel distance.\n");
        explanation.append("  Parameters: T=1.0, cooling=0.995, iterations=5000\n\n");

        List<Spot> saResult = simulatedAnnealing(greedySolution, explanation);

        lastHeuristicResult = saResult;

        // Display itinerary (TASK 4)
        displayItinerary(saResult, startHour, "🚀 HEURISTIC RESULT (Greedy + Simulated Annealing)", interest);
        txaExplanation.setText(explanation.toString());
        mapPanel.setSpots(saResult, ALL_SPOTS);
        tabs.setSelectedIndex(0);
        setStatus("Heuristic complete: " + saResult.size() + " spots selected.", COL_GREEN);
    }

    // Simulated Annealing to optimize visit ORDER within the greedy selection
    private List<Spot> simulatedAnnealing(List<Spot> input, StringBuilder log) {
        if (input.size() <= 2) {
            log.append("  (SA skipped — fewer than 3 spots)\n");
            return new ArrayList<>(input);
        }

        List<Spot> current = new ArrayList<>(input);
        List<Spot> best    = new ArrayList<>(input);
        double bestDist    = totalDistance(best);
        double T           = 1.0;
        double cooling     = 0.995;
        int    iterations  = 5000;
        int    improvements = 0;
        Random rand        = new Random(42);

        for (int i = 0; i < iterations; i++) {
            // Swap two random spots
            List<Spot> neighbour = new ArrayList<>(current);
            int a = rand.nextInt(neighbour.size());
            int b = rand.nextInt(neighbour.size());
            Collections.swap(neighbour, a, b);

            double dCurrent   = totalDistance(current);
            double dNeighbour = totalDistance(neighbour);
            double delta      = dNeighbour - dCurrent;

            if (delta < 0 || Math.random() < Math.exp(-delta / T)) {
                current = neighbour;
                if (dNeighbour < bestDist) {
                    best     = new ArrayList<>(neighbour);
                    bestDist = dNeighbour;
                    improvements++;
                }
            }
            T *= cooling;
        }

        log.append(String.format("  Improvements found: %d / %d iterations\n", improvements, iterations));
        log.append(String.format("  Original distance: %.4f units\n", totalDistance(input)));
        log.append(String.format("  SA optimized distance: %.4f units\n", bestDist));
        log.append("\n  SA reordered path:\n");
        for (int i = 0; i < best.size(); i++)
            log.append(String.format("    %d. %s\n", i + 1, best.get(i).name));

        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 5 — BRUTE-FORCE COMPARISON (on first 6 spots)
    // ─────────────────────────────────────────────────────────────
    private void runBruteForce() {
        int maxHours  = (int) spnHours.getValue();
        int maxBudget = (int) spnBudget.getValue();
        int startHour = (int) spnStartHour.getValue();
        String interest = (String) cmbInterest.getSelectedItem();

        // Use first 6 spots for brute-force (manageable: 6! = 720 permutations)
        List<Spot> smallSet = ALL_SPOTS.subList(0, 6);

        setStatus("Running brute-force on 6 spots (720 permutations)...", COL_ORANGE);

        long startTime = System.nanoTime();

        List<Spot> bestBrute   = new ArrayList<>();
        int        bestScore   = -1;

        // Generate all permutations via indices
        List<List<Integer>> perms = permutations(6);

        for (List<Integer> perm : perms) {
            List<Spot> candidate = new ArrayList<>();
            int budget = 0, time = 0, hour = startHour;

            for (int idx : perm) {
                Spot s = smallSet.get(idx);
                if (!s.hasTag(interest))               continue;
                if (budget + s.entryFee   > maxBudget) continue;
                if (time   + s.visitDuration > maxHours)break;
                if (hour < s.openHour || hour + s.visitDuration > s.closeHour) continue;
                candidate.add(s);
                budget += s.entryFee;
                time   += s.visitDuration;
                hour   += s.visitDuration;
            }

            int sc = candidate.size() * 1000 - totalCost(candidate);
            if (sc > bestScore) {
                bestScore  = sc;
                bestBrute  = new ArrayList<>(candidate);
            }
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;

        // Compare
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  BRUTE-FORCE vs HEURISTIC COMPARISON\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");
        sb.append("  Dataset used: First 6 Kathmandu spots\n");
        sb.append("  Permutations checked: 720  (6!)\n");
        sb.append(String.format("  Brute-force time: %d ms\n\n", elapsed));

        sb.append("┌─────────────────────────┬───────────┬───────────┐\n");
        sb.append("│ Metric                  │ Heuristic │  Brute    │\n");
        sb.append("├─────────────────────────┼───────────┼───────────┤\n");

        int hSpots = lastHeuristicResult.size();
        int bSpots = bestBrute.size();
        int hCost  = totalCost(lastHeuristicResult);
        int bCost  = totalCost(bestBrute);
        int hTime  = totalTime(lastHeuristicResult);
        int bTime  = totalTime(bestBrute);

        sb.append(String.format("│ Spots Visited           │ %-9d │ %-9d │\n", hSpots, bSpots));
        sb.append(String.format("│ Total Cost (Rs.)        │ %-9d │ %-9d │\n", hCost,  bCost));
        sb.append(String.format("│ Total Time (hrs)        │ %-9d │ %-9d │\n", hTime,  bTime));
        sb.append(String.format("│ Permutations Checked    │ %-9s │ %-9s │\n", "~10", "720"));
        sb.append(String.format("│ Compute Time (ms)       │ %-9s │ %-9d │\n", "<1", elapsed));
        sb.append("└─────────────────────────┴───────────┴───────────┘\n\n");

        sb.append("  BRUTE-FORCE OPTIMAL PATH:\n");
        if (bestBrute.isEmpty()) {
            sb.append("  (No valid path found with current constraints)\n");
        } else {
            for (int i = 0; i < bestBrute.size(); i++)
                sb.append(String.format("  %d. %-30s  Rs.%d\n",
                    i+1, bestBrute.get(i).name, bestBrute.get(i).entryFee));
        }

        sb.append("\n  DISCUSSION — ACCURACY vs PERFORMANCE:\n");
        sb.append("  ┌──────────────────────────────────────────────────────┐\n");
        sb.append("  │ • Brute-force GUARANTEES the optimal solution.       │\n");
        sb.append("  │ • However, complexity is O(n!) — with 10 spots that  │\n");
        sb.append("  │   is 3.6 million permutations; with 15 it's ~1.3T.  │\n");
        sb.append("  │ • Heuristic (Greedy+SA) runs in milliseconds at any  │\n");
        sb.append("  │   scale, giving a near-optimal 'good enough' answer. │\n");
        sb.append("  │ • SA helps escape local optima that pure greedy falls │\n");
        sb.append("  │   into, improving path ordering significantly.       │\n");
        sb.append("  │ • Trade-off: heuristic may miss 1–2 spots vs brute  │\n");
        sb.append("  │   force, but is the only practical choice at scale.  │\n");
        sb.append("  └──────────────────────────────────────────────────────┘\n");

        txaBrute.setText(sb.toString());
        tabs.setSelectedIndex(3);
        setStatus("Brute-force complete. " + elapsed + " ms for 720 permutations.", COL_ORANGE);
    }

    // ─────────────────────────────────────────────────────────────
    // TASK 4 — DISPLAY ITINERARY
    // ─────────────────────────────────────────────────────────────
    private void displayItinerary(List<Spot> spots, int startHour, String title, String interest) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  " + title + "\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");

        if (spots.isEmpty()) {
            sb.append("  ⚠  No spots could be selected with current constraints.\n");
            sb.append("  Try increasing budget, time, or changing interest filter.\n");
            txaItinerary.setText(sb.toString());
            return;
        }

        int hour = startHour, totalFee = 0, totalT = 0;

        sb.append(String.format("  %-4s  %-30s  %-10s  %-10s  %-10s  %-30s%n",
            "No.", "Spot Name", "Arrive", "Depart", "Entry Fee", "Tags"));
        sb.append("  " + "─".repeat(100) + "\n");

        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            int  depart = hour + s.visitDuration;
            sb.append(String.format("  %-4d  %-30s  %02d:00       %02d:00       Rs. %-6d  %s%n",
                i+1, s.name, hour, depart, s.entryFee, String.join(", ", s.tags)));
            sb.append(String.format("       📍 Lat:%.4f  Lon:%.4f  | Visit: %d hr | Rating: %s%n",
                s.lat, s.lon, s.visitDuration, "★".repeat(s.rating)));
            sb.append(String.format("       💡 Selected: high interest match (score=%.3f) within budget & time%n",
                score(s, interest)));
            if (i < spots.size() - 1) {
                double dist = euclidean(s, spots.get(i + 1));
                sb.append(String.format("       ↓  Travel to next: ~%.2f km (%.0f min)%n", dist * 111, dist * 111 * 3));
            }
            sb.append("\n");
            totalFee += s.entryFee;
            totalT   += s.visitDuration;
            hour     += s.visitDuration;
        }

        sb.append("  " + "─".repeat(100) + "\n");
        sb.append(String.format("  Total Spots : %d%n", spots.size()));
        sb.append(String.format("  Total Cost  : Rs. %d%n", totalFee));
        sb.append(String.format("  Total Time  : %d hours  (%02d:00 → %02d:00)%n",
            totalT, startHour, startHour + totalT));
        sb.append(String.format("  📏 Total Dist  : %.2f km (Euclidean)%n", totalDistance(spots) * 111));

        txaItinerary.setText(sb.toString());
    }

//map panel
    static class MapPanel extends JPanel {
        List<Spot> selected = new ArrayList<>();
        List<Spot> all      = new ArrayList<>();

        MapPanel() {
            setBackground(new Color(10, 18, 38));
            setPreferredSize(new Dimension(700, 550));
        }

        void setSpots(List<Spot> selected, List<Spot> all) {
            this.selected = selected;
            this.all      = all;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W = getWidth(), H = getHeight();
            int pad = 60;

            // Find lat/lon bounds from ALL spots
            double minLat = all.stream().mapToDouble(s -> s.lat).min().orElse(27.6);
            double maxLat = all.stream().mapToDouble(s -> s.lat).max().orElse(27.85);
            double minLon = all.stream().mapToDouble(s -> s.lon).min().orElse(85.2);
            double maxLon = all.stream().mapToDouble(s -> s.lon).max().orElse(85.4);

            // Expand bounds slightly
            double latRange = (maxLat - minLat) == 0 ? 0.1 : (maxLat - minLat) * 1.3;
            double lonRange = (maxLon - minLon) == 0 ? 0.1 : (maxLon - minLon) * 1.3;
            double midLat   = (minLat + maxLat) / 2;
            double midLon   = (minLon + maxLon) / 2;
            minLat = midLat - latRange / 2;
            maxLat = midLat + latRange / 2;
            minLon = midLon - lonRange / 2;
            maxLon = midLon + lonRange / 2;

            // Grid
            g2.setColor(new Color(30, 45, 70));
            g2.setStroke(new BasicStroke(0.5f));
            for (int gx = 0; gx <= 8; gx++) {
                int x = pad + gx * (W - 2*pad) / 8;
                g2.drawLine(x, pad, x, H - pad);
            }
            for (int gy = 0; gy <= 6; gy++) {
                int y = pad + gy * (H - 2*pad) / 6;
                g2.drawLine(pad, y, W - pad, y);
            }

            // Title
            g2.setColor(COL_ACCENT);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.drawString("Map View — Kathmandu Tourist Spots", pad, 30);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(COL_MUTED);
            g2.drawString("● All spots    ● Selected route", pad, 48);

            // Convert lat/lon to screen coords
            final double fMinLat = minLat, fMaxLat = maxLat, fMinLon = minLon, fMaxLon = maxLon;
            java.util.function.Function<Spot, Point> toScreen = s -> {
                int sx = pad + (int) ((s.lon - fMinLon) / (fMaxLon - fMinLon) * (W - 2*pad));
                int sy = (H - pad) - (int) ((s.lat - fMinLat) / (fMaxLat - fMinLat) * (H - 2*pad));
                return new Point(sx, sy);
            };

            // Draw all unselected spots (dim)
            for (Spot s : all) {
                Point p = toScreen.apply(s);
                g2.setColor(new Color(70, 90, 130));
                g2.fillOval(p.x - 5, p.y - 5, 10, 10);
            }

            // Draw path between selected spots
            if (!selected.isEmpty()) {
                g2.setColor(new Color(99, 179, 237, 180));
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1.0f, new float[]{8, 4}, 0));
                for (int i = 0; i < selected.size() - 1; i++) {
                    Point a = toScreen.apply(selected.get(i));
                    Point b = toScreen.apply(selected.get(i + 1));
                    g2.drawLine(a.x, a.y, b.x, b.y);
                    // Arrow head
                    drawArrow(g2, a, b);
                }

                // Draw selected spots (highlighted)
                g2.setStroke(new BasicStroke(1.5f));
                for (int i = 0; i < selected.size(); i++) {
                    Spot s = selected.get(i);
                    Point p = toScreen.apply(s);
                    // Glow
                    g2.setColor(new Color(99, 179, 237, 60));
                    g2.fillOval(p.x - 12, p.y - 12, 24, 24);
                    // Pin
                    g2.setColor(COL_GREEN);
                    g2.fillOval(p.x - 7, p.y - 7, 14, 14);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    g2.drawString(String.valueOf(i + 1), p.x - 3, p.y + 4);
                    // Label
                    g2.setColor(COL_TEXT);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    String short_name = s.name.length() > 20 ? s.name.substring(0, 18) + "…" : s.name;
                    g2.drawString(short_name, p.x + 10, p.y + 4);
                }
            }

            // Legend
            int lx = W - 180, ly = pad + 10;
            g2.setColor(new Color(25, 35, 60, 200));
            g2.fillRoundRect(lx - 10, ly - 10, 165, 60, 10, 10);
            g2.setColor(new Color(70, 90, 130));
            g2.fillOval(lx, ly + 5, 10, 10);
            g2.setColor(COL_MUTED);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString("Unvisited spot", lx + 16, ly + 15);
            g2.setColor(COL_GREEN);
            g2.fillOval(lx, ly + 25, 10, 10);
            g2.setColor(COL_MUTED);
            g2.drawString("Selected route", lx + 16, ly + 35);
        }

        void drawArrow(Graphics2D g2, Point from, Point to) {
            double angle = Math.atan2(to.y - from.y, to.x - from.x);
            int mx = (from.x + to.x) / 2, my = (from.y + to.y) / 2;
            int size = 8;
            int x1 = (int) (mx - size * Math.cos(angle - 0.4));
            int y1 = (int) (my - size * Math.sin(angle - 0.4));
            int x2 = (int) (mx - size * Math.cos(angle + 0.4));
            int y2 = (int) (my - size * Math.sin(angle + 0.4));
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(COL_ACCENT);
            g2.drawLine(mx, my, x1, y1);
            g2.drawLine(mx, my, x2, y2);
        }
    }

//helpers
    static double score(Spot s, String interest) {
        double tagBonus = s.hasTag(interest) && !interest.equals("all") ? 2.0 : 1.0;
        return (double) s.rating / (s.entryFee + 1) * tagBonus * 1000;
    }

    static double euclidean(Spot a, Spot b) {
        double dLat = a.lat - b.lat, dLon = a.lon - b.lon;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    static double totalDistance(List<Spot> spots) {
        double d = 0;
        for (int i = 0; i < spots.size() - 1; i++)
            d += euclidean(spots.get(i), spots.get(i + 1));
        return d;
    }

    static int totalCost(List<Spot> spots) {
        return spots.stream().mapToInt(s -> s.entryFee).sum();
    }

    static int totalTime(List<Spot> spots) {
        return spots.stream().mapToInt(s -> s.visitDuration).sum();
    }

    static List<List<Integer>> permutations(int n) {
        List<List<Integer>> result = new ArrayList<>();
        permHelper(new ArrayList<>(), new boolean[n], n, result);
        return result;
    }

    static void permHelper(List<Integer> current, boolean[] used, int n, List<List<Integer>> result) {
        if (current.size() == n) { result.add(new ArrayList<>(current)); return; }
        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                used[i] = true;
                current.add(i);
                permHelper(current, used, n, result);
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }

    void setStatus(String msg, Color color) {
        lblStatus.setText(msg);
        lblStatus.setForeground(color);
    }

    void clearAll() {
        txaItinerary.setText("");
        txaExplanation.setText("");
        txaBrute.setText("");
        mapPanel.setSpots(new ArrayList<>(), ALL_SPOTS);
        lastHeuristicResult.clear();
        setStatus("Cleared. Ready.", COL_MUTED);
    }

    // ── UI builder helpers ──────────────────────────────────────
    JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(COL_ACCENT);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    JPanel inputRow(String label, JComponent comp) {
        JPanel row = new JPanel(new GridLayout(2, 1, 0, 2));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setForeground(COL_MUTED);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (comp instanceof JSpinner) {
            comp.setBackground(COL_CARD);
            comp.setForeground(COL_TEXT);
            ((JSpinner)comp).setBorder(BorderFactory.createLineBorder(COL_CARD));
        }
        row.add(lbl);
        row.add(comp);
        return row;
    }

    void styleCombo(JComboBox<?> c) {
        c.setBackground(COL_CARD);
        c.setForeground(Color.BLACK);
        c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }

    JButton makeButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.black);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 12, 10, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setBackground(color.brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setBackground(color);
            }
        });
        return b;
    }

    JPanel fullWidth(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    JTextArea makeTextArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        ta.setBackground(new Color(15, 22, 42));
        ta.setForeground(COL_TEXT);
        ta.setCaretColor(COL_ACCENT);
        ta.setBorder(new EmptyBorder(12, 16, 12, 16));
        return ta;
    }

    JScrollPane scrollPane(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBackground(COL_PANEL);
        sp.getViewport().setBackground(COL_PANEL);
        sp.setBorder(BorderFactory.createLineBorder(COL_CARD));
        return sp;
    }

//main run function
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            Question5a app = new Question5a();
            app.setVisible(true);

            // Show welcome text
            app.txaItinerary.setText(
                "═══════════════════════════════════════════════════════\n" +
                "  Welcome to Tourist Spot Optimizer — Kathmandu, Nepal\n" +
                "═══════════════════════════════════════════════════════\n\n" +
                "  HOW TO USE:\n" +
                "  1. Set your Start Hour (e.g. 9 = 09:00 AM)\n" +
                "  2. Set Time Available (e.g. 6 = 6 hours of sightseeing)\n" +
                "  3. Set your Max Budget in Rs.\n" +
                "  4. Choose an Interest filter (or 'all')\n" +
                "  5. Click ' Optimize Itinerary' for heuristic result\n" +
                "  6. Click ' Brute-Force Compare' to compare with optimal\n\n" +
                "  ALGORITHM:\n" +
                "  • Phase 1 — Greedy: ranks spots by score = rating/fee × tag_bonus\n" +
                "    and greedily picks the best feasible spots within constraints.\n" +
                "  • Phase 2 — Simulated Annealing: reorders selected spots to\n" +
                "    minimize total travel distance (Euclidean).\n\n" +
                "  DATASET: 10 real Kathmandu tourist spots with coordinates,\n" +
                "  entry fees, opening hours, and interest tags.\n"
            );
        });
    }
}