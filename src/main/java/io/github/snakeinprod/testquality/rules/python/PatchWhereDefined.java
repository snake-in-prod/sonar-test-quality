package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Patching the module that defines a name has no effect on code that imported the name with from-import. */
public class PatchWhereDefined implements TestRule {

  private static final Pattern TARGET = Pattern.compile("[rRuU]?(['\"])([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)+)\\1");

  @Override
  public void check(RuleContext ctx) {
    ParsedFile file = ctx.file();
    PyModule m = file.get(PyModule.class);
    // importlib.reload re-runs the from-imports under the patch
    if (m == null || ctx.tests().tests().isEmpty() || file.tokens().stream().anyMatch(t -> t.isIdent("reload"))) {
      return;
    }
    Map<String, String> modules = importedModules(ctx, m);
    if (modules.isEmpty()) {
      return;
    }
    for (TestCase t : ctx.tests().tests()) {
      if (!(t.body() instanceof PyStatement def)) {
        continue;
      }
      List<PyStatement> body = MockAssertionTypo.statements(t);
      if (body.stream().anyMatch(s -> s.keyword().equals("import") || s.keyword().equals("from"))) {
        // a module imported inside the test may first be imported while the patch is active
        continue;
      }
      List<Chain> chains = new ArrayList<>();
      def.decorators().forEach(d -> chains.addAll(Chains.direct(d.group(), MockNeverUsed.KEYWORDS)));
      body.forEach(s -> chains.addAll(Chains.in(s.group(), MockNeverUsed.KEYWORDS)));
      Map<Token, String> targets = new LinkedHashMap<>();
      for (Chain c : chains) {
        Chain.Call call = "patch".equals(MockNeverUsed.patchKind(m, c)) ? c.calls().get(0) : null;
        List<Node> first = call == null || call.items().isEmpty() ? List.of() : call.items().get(0);
        Token s = first.size() == 1 ? Nodes.leaf(first.get(0)) : null;
        Matcher x = s != null && s.kind() == TokenKind.STRING ? TARGET.matcher(s.text()) : null;
        if (x != null && x.matches()) {
          targets.put(s, x.group(2));
        }
      }
      Set<String> patched = new HashSet<>(targets.values());
      targets.forEach((token, target) -> check(ctx, m, modules, patched, token, target));
    }
  }

  private static void check(RuleContext ctx, PyModule m, Map<String, String> modules, Set<String> patched, Token token, String target) {
    String defining = target.substring(0, target.lastIndexOf('.'));
    if (m.imports().values().stream().anyMatch(v -> v.equals(defining) || v.startsWith(defining + "."))) {
      // the test uses the defining module itself, whose own functions do see the patch
      return;
    }
    String fix = null;
    String user = null;
    for (Map.Entry<String, String> e : modules.entrySet()) {
      ParsedFile code = ctx.project(e.getValue());
      PyModule pm = code == null ? null : code.get(PyModule.class);
      if (pm == null) {
        continue;
      }
      if (pm.imports().containsValue(defining)) {
        // this module reaches the name through its module, so the patch works for it
        return;
      }
      for (Map.Entry<String, String> i : pm.imports().entrySet()) {
        String local = i.getKey();
        if (fix == null && i.getValue().equals(target) && !local.contains(".") && references(code, local) > 1) {
          fix = e.getKey() + "." + local;
          user = e.getKey();
        }
      }
    }
    if (fix != null && !patched.contains(fix)) {
      ctx.report(token, "\"" + target + "\" is patched where it is defined, but " + user
        + " imports it by name and keeps the original; patch \"" + fix + "\" instead.");
    }
  }

  /** The project modules the test file imports: dotted name to path. */
  private static Map<String, String> importedModules(RuleContext ctx, PyModule m) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String v : m.imports().values()) {
      for (String d = v; !d.isEmpty() && !d.startsWith("."); d = d.contains(".") ? d.substring(0, d.lastIndexOf('.')) : "") {
        String path = modulePath(ctx, d);
        if (path != null) {
          if (!path.equals(ctx.file().path())) {
            out.put(d, path);
          }
          break;
        }
      }
    }
    return out;
  }

  /** The one project file of a dotted module name ({@code a/b.py} or {@code a/b/__init__.py}, under any source root), or null. */
  private static String modulePath(RuleContext ctx, String dotted) {
    String rel = dotted.replace('.', '/');
    Map<String, List<String>> bySuffix = suffixIndex(ctx);
    List<String> found = new ArrayList<>(bySuffix.getOrDefault(rel + ".py", List.of()));
    found.addAll(bySuffix.getOrDefault(rel + "/__init__.py", List.of()));
    return found.size() == 1 ? found.get(0) : null;
  }

  /** Every project path under each of its "/"-separated suffixes ({@code a/b/c.py} under itself, {@code b/c.py} and {@code c.py}). */
  private static Map<String, List<String>> suffixIndex(RuleContext ctx) {
    return ctx.shared("PatchWhereDefined.suffixes", k -> {
      Map<String, List<String>> out = new HashMap<>();
      for (String p : ctx.projectPaths()) {
        out.computeIfAbsent(p, s -> new ArrayList<>()).add(p);
        for (int i = p.indexOf('/'); i >= 0; i = p.indexOf('/', i + 1)) {
          out.computeIfAbsent(p.substring(i + 1), s -> new ArrayList<>()).add(p);
        }
      }
      return out;
    });
  }

  /** How often a name appears in a file, attributes ({@code x.name}) left out; its import counts once. */
  private static int references(ParsedFile file, String name) {
    List<Token> tokens = file.tokens();
    int n = 0;
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).isIdent(name) && (i == 0 || !tokens.get(i - 1).is("."))) {
        n++;
      }
    }
    return n;
  }
}
