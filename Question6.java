import java.util.*;

public class Question6 {
//finding safest path using dijastra
    static class Edge {
        String to;
        double p;
        Edge(String to, double p) { this.to = to; this.p = p; }
    }

    public static Map<String, Double> safestFrom(String source, Map<String, List<Edge>> g) {
        // dist = sum of -log(p)
        Map<String, Double> dist = new HashMap<>();
        for (String v : g.keySet()) dist.put(v, Double.POSITIVE_INFINITY);
        dist.put(source, 0.0);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(source);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            double du = dist.get(u);

            for (Edge e : g.getOrDefault(u, List.of())) {
                double w = -Math.log(e.p);
                double cand = du + w;
                if (cand < dist.getOrDefault(e.to, Double.POSITIVE_INFINITY)) {
                    dist.put(e.to, cand);
                    pq.add(e.to);
                }
            }
        }

        // convert back to probability: safety = exp(-dist)
        Map<String, Double> safety = new HashMap<>();
        for (Map.Entry<String, Double> en : dist.entrySet()) {
            double d = en.getValue();
            safety.put(en.getKey(), Double.isInfinite(d) ? 0.0 : Math.exp(-d));
        }
        return safety;
    }

    public static void main(String[] args) {
        Map<String, List<Edge>> g = new HashMap<>();
        add(g,"KTM","JA",0.90);
        add(g,"KTM","JB",0.80);
        add(g,"JA","KTM",0.90);
        add(g,"JA","PH",0.95);
        add(g,"JA","BS",0.70);
        add(g,"JB","KTM",0.80);
        add(g,"JB","JA",0.60);
        add(g,"JB","BS",0.90);
        add(g,"PH","JA",0.95);
        add(g,"PH","BS",0.85);
        add(g,"BS","JA",0.70);
        add(g,"BS","JB",0.90);
        add(g,"BS","PH",0.85);

        System.out.println(safestFrom("KTM", g));
    }

    private static void add(Map<String, List<Edge>> g, String u, String v, double p) {
        g.computeIfAbsent(u, k -> new ArrayList<>()).add(new Edge(v, p));
        g.putIfAbsent(v, new ArrayList<>());
    }
} 