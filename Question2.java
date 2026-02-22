import java.util.*;

public class Question2{

    static int maxSum;

    public static int maxPathSum(Integer[] arr) {

        if (arr == null || arr.length == 0) {
            return 0;
        }

        maxSum = Integer.MIN_VALUE;

        dfs(arr, 0);

        return maxSum;
    }

    private static int dfs(Integer[] arr, int i) {


        if (i >= arr.length || arr[i] == null) {
            return 0;
        }
        int leftGain = Math.max(dfs(arr, 2 * i + 1), 0);
        int rightGain = Math.max(dfs(arr, 2 * i + 2), 0);
        int currentTotal = arr[i] + leftGain + rightGain;

        maxSum = Math.max(maxSum, currentTotal);

        return arr[i] + Math.max(leftGain, rightGain);
    }

    public static void main(String[] args) {

        // Example 1
        Integer[] arr1 = {1, 2, 3};
        System.out.println("Input: " + Arrays.toString(arr1));
        System.out.println("Maximum Cascade Power: " + maxPathSum(arr1));
        System.out.println();

        // Example 2
        Integer[] arr2 = {10, -90, 20, 6, null, 15, 7};
        System.out.println("Input: " + Arrays.toString(arr2));
        System.out.println("Maximum Cascade Power: " + maxPathSum(arr2));
    }
}