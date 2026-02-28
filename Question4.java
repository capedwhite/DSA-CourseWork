import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Question4 {
//model data

    static final String[] DISTRICTS = {"District A", "District B", "District C"};


    static final int HOUR_OFFSET = 6;   
    static final int TOTAL_HOURS = 18; 


    static int[][] demand = new int[TOTAL_HOURS][3];

    // Energy Sources
    static final String[] SOURCE_NAMES     = {"Solar", "Hydro", "Diesel"};
    static final double[] COST_PER_KWH     = {1.0,     1.5,     3.0};
    static final int[]    MAX_CAPACITY     = {50,      40,      60};
    static final int[]    AVAIL_START      = {6,       0,       17};
    static final int[]    AVAIL_END        = {18,      24,      23};  // end is exclusive

    // Allocation result per hour per district per source  [hour][district][source]
    static double[][][] allocation;
    // Total cost tracking per hour
    static double[] hourCost;

    public static void main(String[] args) {
        initDemand();
        allocation = new double[TOTAL_HOURS][3][3];
        hourCost   = new double[TOTAL_HOURS];

        System.out.println("=".repeat(100));
        System.out.println("       SMART ENERGY GRID LOAD DISTRIBUTION OPTIMIZATION — NEPAL");
        System.out.println("=".repeat(100));


        double[] dp = new double[TOTAL_HOURS + 1];
        dp[0] = 0;

        for (int h = 0; h < TOTAL_HOURS; h++) {
            int hour = h + HOUR_OFFSET;

            double[] remaining = new double[3];
            for (int s = 0; s < 3; s++) {
                remaining[s] = isAvailable(s, hour) ? MAX_CAPACITY[s] : 0;
            }

            // Sort sources by cost (greedy: cheapest first)
            Integer[] sourceOrder = {0, 1, 2};
            Arrays.sort(sourceOrder, Comparator.comparingDouble(s -> COST_PER_KWH[s]));

            double hourlyTotalCost = 0;

            // Allocate for each district
            for (int d = 0; d < 3; d++) {
                double needed   = demand[h][d];
                double minAllow = needed * 0.9;  // ±10% lower bound
                double fulfilled = 0;

                // Greedy: iterate cheapest sources first
                for (int si = 0; si < 3 && fulfilled < minAllow; si++) {
                    int s = sourceOrder[si];
                    if (remaining[s] <= 0) continue;

                    double take = Math.min(needed - fulfilled, remaining[s]);
                    if (take > 0) {
                        allocation[h][d][s] += take;
                        remaining[s]        -= take;
                        fulfilled           += take;
                        hourlyTotalCost     += take * COST_PER_KWH[s];
                    }
                }
                // Note: if fulfilled < minAllow, demand not satisfiable (edge case)
            }

            hourCost[h] = hourlyTotalCost;
            // DP transition: accumulate minimum cost
            dp[h + 1] = dp[h] + hourlyTotalCost;
        }


        printResults();


        analyzeResults();
    }


    // INITIALIZE DEMAND DATA

    static void initDemand() {
      
        int[][] data = {
            {20, 15, 25},  // 06
            {22, 16, 28},  // 07
            {25, 18, 30},  // 08
            {28, 20, 32},  // 09
            {30, 22, 35},  // 10
            {32, 23, 36},  // 11
            {35, 25, 38},  // 12
            {33, 24, 36},  // 13
            {30, 22, 34},  // 14
            {28, 20, 32},  // 15
            {27, 19, 30},  // 16
            {30, 22, 35},  // 17 — diesel becomes available
            {35, 27, 40},  // 18 — solar ends
            {38, 30, 45},  // 19
            {36, 28, 42},  // 20
            {32, 25, 38},  // 21
            {28, 20, 33},  // 22
            {22, 16, 28},  // 23
        };
        for (int i = 0; i < TOTAL_HOURS; i++) {
            demand[i][0] = data[i][0];
            demand[i][1] = data[i][1];
            demand[i][2] = data[i][2];
        }
    }

//checking resouce
    static boolean isAvailable(int sourceIdx, int hour) {
        return hour >= AVAIL_START[sourceIdx] && hour < AVAIL_END[sourceIdx];
    }
//printing results
    static void printResults() {
        System.out.println("\n📊 HOURLY ALLOCATION RESULTS");
        System.out.println("-".repeat(100));
        System.out.printf("%-5s %-12s %-10s %-10s %-10s %-10s %-10s %-10s %-8s%n",
            "Hour", "District", "Solar(kWh)", "Hydro(kWh)", "Diesel(kWh)",
            "Total Used", "Demand", "% Met", "Cost(Rs.)");
        System.out.println("-".repeat(100));

        for (int h = 0; h < TOTAL_HOURS; h++) {
            int hour = h + HOUR_OFFSET;
            for (int d = 0; d < 3; d++) {
                double solar  = allocation[h][d][0];
                double hydro  = allocation[h][d][1];
                double diesel = allocation[h][d][2];
                double total  = solar + hydro + diesel;
                double dem    = demand[h][d];
                double pct    = (total / dem) * 100;
                double cost   = solar * COST_PER_KWH[0] + hydro * COST_PER_KWH[1] + diesel * COST_PER_KWH[2];

                System.out.printf("%-5s %-12s %-10.1f %-10.1f %-10.1f %-10.1f %-10.0f %-10.1f %-8.2f%n",
                    (d == 0 ? hour + ":00" : ""), DISTRICTS[d],
                    solar, hydro, diesel, total, dem, pct, cost);
            }
            System.out.printf("%-5s %-12s %53s %-8.2f%n", "", "Hour Total:", "", hourCost[h]);
            System.out.println("-".repeat(100));
        }
    }


    static void analyzeResults() {
        System.out.println("\n📈 COST AND RESOURCE USAGE ANALYSIS");
        System.out.println("=".repeat(60));

        double totalCost   = 0;
        double totalEnergy = 0;
        double renewEnergy = 0;   // solar + hydro
        double dieselEnergy = 0;

        Map<String, List<String>> dieselUsage = new LinkedHashMap<>();

        for (int h = 0; h < TOTAL_HOURS; h++) {
            int hour = h + HOUR_OFFSET;
            totalCost += hourCost[h];
            for (int d = 0; d < 3; d++) {
                double solar  = allocation[h][d][0];
                double hydro  = allocation[h][d][1];
                double diesel = allocation[h][d][2];

                totalEnergy  += solar + hydro + diesel;
                renewEnergy  += solar + hydro;
                dieselEnergy += diesel;

                if (diesel > 0) {
                    String key = hour + ":00";
                    dieselUsage.computeIfAbsent(key, k -> new ArrayList<>())
                               .add(DISTRICTS[d] + String.format("(%.1fkWh)", diesel));
                }
            }
        }

        double renewPct  = (renewEnergy  / totalEnergy) * 100;
        double dieselPct = (dieselEnergy / totalEnergy) * 100;

        System.out.printf("  Total Energy Distributed  : %.1f kWh%n", totalEnergy);
        System.out.printf("  Total Cost                : Rs. %.2f%n", totalCost);
        System.out.printf("  Renewable Energy (Solar+Hydro): %.1f kWh (%.1f%%)%n", renewEnergy, renewPct);
        System.out.printf("  Diesel Energy Used        : %.1f kWh (%.1f%%)%n", dieselEnergy, dieselPct);

        System.out.println("\n   HOURS/DISTRICTS WHERE DIESEL WAS USED:");
        if (dieselUsage.isEmpty()) {
            System.out.println("  None — all demand met by renewables!");
        } else {
            for (Map.Entry<String, List<String>> e : dieselUsage.entrySet()) {
                System.out.printf("  Hour %-6s → %s%n", e.getKey(), String.join(", ", e.getValue()));
            }
        }
         System.out.println("Trade-off: Greedy is fast (O(hours*districts*sources)) but may miss global optimum.");


    }
}
