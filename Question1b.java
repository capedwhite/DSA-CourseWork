import java.util.*;
public class Question1b {
        static List<String> segment(String query, Set<String> dict) {
        List<String> results = new ArrayList<>();
        backtrack(query, 0, dict, new ArrayList<>(), results);
        return results;
    }
    
    static void backtrack(String query, int start, Set<String> dict, List<String> current, List<String> results) {
        if (start == query.length()) {
            results.add(String.join(" ", current));
            return;
        }
        for (int end = start + 1; end <= query.length(); end++) {
            String word = query.substring(start, end);
            if (dict.contains(word)) {
                current.add(word);
                backtrack(query, end, dict, current, results);
                current.remove(current.size() - 1);
            }
        }
    }
    
    public static void main(String[] args) {
        // Example 1
        Set<String> dict1 = new HashSet<>(Arrays.asList("nepal", "trekking", "guide", "nepaltrekking"));
        System.out.println(segment("nepaltrekkingguide", dict1));
        
        // Example 2
        Set<String> dict2 = new HashSet<>(Arrays.asList("visit", "kathmandu", "nepal", "visitkathmandu", "kathmandunepal"));
        System.out.println(segment("visitkathmandunepal", dict2));
        
        // Example 3
        Set<String> dict3 = new HashSet<>(Arrays.asList("everest", "hiking", "trek"));
        System.out.println(segment("everesthikingtrail", dict3));
    }
}
