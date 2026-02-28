
public class Question3 {
    

    static int maxProfit(int maxTrades, int[] prices) {
        int n = prices.length;
        if (n == 0 || maxTrades == 0) return 0;

        // If maxTrades >= n/2, we can make all profitable transactions
        if (maxTrades >= n / 2) {
            int profit = 0;
            for (int i = 1; i < n; i++)
                if (prices[i] > prices[i - 1])
                    profit += prices[i] - prices[i - 1];
            return profit;
        }

        int[][] dp = new int[maxTrades + 1][n];
        for (int k = 1; k <= maxTrades; k++) {
            int maxSoFar = -prices[0];
            for (int i = 1; i < n; i++) {
                dp[k][i] = Math.max(dp[k][i - 1], prices[i] + maxSoFar);
                maxSoFar = Math.max(maxSoFar, dp[k - 1][i] - prices[i]);
            }
        }
        return dp[maxTrades][n - 1];
    }

    public static void main(String[] args) {
        System.out.println(maxProfit(2, new int[]{2000, 4000, 1000}));        // 2000
        System.out.println(maxProfit(2, new int[]{3000, 2000, 4000, 1000, 5000})); // test
        System.out.println(maxProfit(1, new int[]{5000, 4000, 3000, 2000}));  // 0 (declining prices)
    }
}
