package io.github.snakeinprod.testquality.parse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed test file: its tokens (comments left out), its comments, the token tree, and language-specific structures
 * a language support attaches with {@link #put} (the Python statement tree, for example).
 */
public final class ParsedFile {

  private final String path;
  private final String language;
  private final List<String> lines;
  private final List<Token> tokens;
  private final List<Token> comments;
  private final Node.Group root;
  private final Map<Class<?>, Object> extras = new HashMap<>();

  public ParsedFile(String path, String language, String source, List<Token> tokens, List<Token> comments) {
    this.path = path;
    this.language = language;
    this.lines = List.of(source.split("\r\n|\r|\n", -1));
    this.tokens = List.copyOf(tokens);
    this.comments = List.copyOf(comments);
    this.root = TreeBuilder.build(tokens);
  }

  /** Path relative to the project base directory, "/" separated. */
  public String path() {
    return path;
  }

  /** Language key: "py", "js", "ts" or "dart". */
  public String language() {
    return language;
  }

  public List<String> lines() {
    return lines;
  }

  public List<Token> tokens() {
    return tokens;
  }

  public List<Token> comments() {
    return comments;
  }

  public Node.Group root() {
    return root;
  }

  public <T> void put(Class<T> type, T value) {
    extras.put(type, value);
  }

  public <T> T get(Class<T> type) {
    return type.cast(extras.get(type));
  }

  /** Every chain of the file, see {@link Chains#in}. */
  public List<Chain> chains() {
    @SuppressWarnings("unchecked")
    List<Chain> cached = (List<Chain>) extras.get(Chain.class);
    if (cached == null) {
      cached = Chains.in(root);
      extras.put(Chain.class, cached);
    }
    return cached;
  }
}
