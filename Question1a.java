import java.util.*;

public class Question1a {

    public static int maxCustomersOnLine(int[][] customerLocations) {
        if (customerLocations.length <= 2) {
            return customerLocations.length;
        }

        int maxPoints = 0;

        for (int i = 0; i < customerLocations.length; i++) {
            Map<String, Integer> slopes = new HashMap<>();
            int samePoint = 1;
            int x1 = customerLocations[i][0];
            int y1 = customerLocations[i][1];

            for (int j = i + 1; j < customerLocations.length; j++) {
                int x2 = customerLocations[j][0];
                int y2 = customerLocations[j][1];

                int dx = x2 - x1;
                int dy = y2 - y1;

                // Duplicate point
                if (dx == 0 && dy == 0) {
                    samePoint++;
                    continue;
                }

                int g = gcd(dx, dy);
                dx /= g;
                dy /= g;

               
                if (dx < 0) {
                    dx *= -1;
                    dy *= -1;
                }

                String slopeKey = dy + "/" + dx;
                slopes.put(slopeKey, slopes.getOrDefault(slopeKey, 0) + 1);
            }

            int currentMax = samePoint;

            for (int count : slopes.values()) {
                currentMax = Math.max(currentMax, count + samePoint);
            }

            maxPoints = Math.max(maxPoints, currentMax);
        }

        return maxPoints;
    }


    private static int gcd(int a, int b) {
        if (b == 0) {
            return Math.abs(a);
        }
        return gcd(b, a % b);
    }

    public static void main(String[] args) {
        // Example 1
        int[][] customerLocations1 = {{1, 1}, {2, 2}, {3, 3}};
        int result1 = maxCustomersOnLine(customerLocations1);
        System.out.println("Maximum customers covered (Example 1): " + result1);

        // Example 2
        int[][] customerLocations2 = {{1,4}, {2,3}, {3,2}, {4,1}, {5,3}};
        int result2 = maxCustomersOnLine(customerLocations2);
        System.out.println("Maximum customers covered (Example 2): " + result2);
    }
}