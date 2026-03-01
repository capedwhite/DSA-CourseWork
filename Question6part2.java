import java.util.*;
//edmons karp max flow 
public class Question6part2 {

    static class Edge {
        int to, rev, cap;
        Edge(int to, int rev, int cap) { this.to = to; this.rev = rev; this.cap = cap; }
    }

    static class MaxFlow {
        List<Edge>[] g;
        MaxFlow(int n) {
            g = new ArrayList[n];
            for (int i = 0; i < n; i++) g[i] = new ArrayList<>();
        }
        void addEdge(int u, int v, int cap) {
            Edge a = new Edge(v, g[v].size(), cap);
            Edge b = new Edge(u, g[u].size(), 0);
            g[u].add(a);
            g[v].add(b);
        }
        int maxFlow(int s, int t) {
            int flow = 0;
            int n = g.length;

            while (true) {
                int[] parentV = new int[n];
                int[] parentE = new int[n];
                Arrays.fill(parentV, -1);
                Queue<Integer> q = new ArrayDeque<>();
                q.add(s);
                parentV[s] = s;

                while (!q.isEmpty() && parentV[t] == -1) {
                    int u = q.poll();
                    for (int i = 0; i < g[u].size(); i++) {
                        Edge e = g[u].get(i);
                        if (parentV[e.to] == -1 && e.cap > 0) {
                            parentV[e.to] = u;
                            parentE[e.to] = i;
                            q.add(e.to);
                        }
                    }
                }

                if (parentV[t] == -1) break; // no augmenting path

                int add = Integer.MAX_VALUE;
                for (int v = t; v != s; v = parentV[v]) {
                    Edge e = g[parentV[v]].get(parentE[v]);
                    add = Math.min(add, e.cap);
                }

                for (int v = t; v != s; v = parentV[v]) {
                    Edge e = g[parentV[v]].get(parentE[v]);
                    e.cap -= add;
                    g[v].get(e.rev).cap += add;
                }

                flow += add;
            }

            return flow;
        }
    }

    public static void main(String[] args) {
        MaxFlow mf = new MaxFlow(5);

        mf.addEdge(0,1,10); // KTM->JA
        mf.addEdge(0,2,15); // KTM->JB
        mf.addEdge(1,0,10); // JA->KTM
        mf.addEdge(1,3,8);  // JA->PH
        mf.addEdge(1,4,5);  // JA->BS
        mf.addEdge(2,0,15); // JB->KTM
        mf.addEdge(2,1,4);  // JB->JA
        mf.addEdge(2,4,12); // JB->BS
        mf.addEdge(3,1,8);  // PH->JA
        mf.addEdge(3,4,6);  // PH->BS
        mf.addEdge(4,1,5);  // BS->JA
        mf.addEdge(4,2,12); // BS->JB
        mf.addEdge(4,3,6);  // BS->PH

        System.out.println(mf.maxFlow(0,4)); // 23
    }
} 
