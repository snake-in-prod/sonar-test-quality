package io.github.snakeinprod.testquality.model;

import io.github.snakeinprod.testquality.parse.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a file imports and declares, for the test reach of source files.
 *
 * @param imports   the project files it imports, resolved (paths relative to the base directory)
 * @param functions name tokens of its functions and methods (top level and in classes, not nested ones), in order
 * @param classes   name tokens of its classes (Dart mixins and extensions included)
 */
public record Outline(Set<String> imports, List<Token> functions, List<Token> classes) {

  /** The directory of a path with a trailing "/", "" at the base directory. */
  public static String dir(String path) {
    return path.substring(0, path.lastIndexOf('/') + 1);
  }

  /** {@code dir} + {@code relative} with "." and ".." resolved, or null when it leaves the base directory. */
  public static String join(String dir, String relative) {
    List<String> parts = new ArrayList<>();
    for (String s : (dir + relative).split("/")) {
      if (s.equals("..")) {
        if (parts.isEmpty()) {
          return null;
        }
        parts.remove(parts.size() - 1);
      } else if (!s.isEmpty() && !s.equals(".")) {
        parts.add(s);
      }
    }
    return String.join("/", parts);
  }
}
