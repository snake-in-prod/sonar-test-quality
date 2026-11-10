package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A getBy or queryBy right after an action that renders later fails or checks the old screen. The action is a
 * user-event v14 call ({@code user = userEvent.setup()}; {@code user.click(...)}) that is not awaited: it dispatches its
 * events only after the current statement, so the query on the next statement sees the screen from before the action.
 * fireEvent and the v13 {@code userEvent.click} are synchronous and stay silent.
 */
public class SyncQueryForAsyncUi implements TestRule {

  private static final Set<String> ACTIONS = Set.of("click", "dblClick", "tripleClick", "type", "keyboard", "clear",
    "selectOptions", "deselectOptions", "upload", "hover", "unhover", "tab", "paste", "pointer");
  private static final Pattern SYNC_QUERY = Pattern.compile("(get|query)(All)?By[A-Z]\\w*");

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    Set<String> users = users(ctx.file().chains());
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      Nodes.groups(body, g -> {
        List<Chain> direct = Chains.direct(g, keywords);
        for (int i = 0; i + 1 < direct.size(); i++) {
          Chain action = direct.get(i);
          if (unawaitedAction(action, users)) {
            Chain next = direct.get(i + 1);
            if (next.segments().size() == 1 && Nodes.isLeaf(next.after(), "=") && i + 2 < direct.size()) {
              next = direct.get(i + 2); // const x = <query>: the declared name is a chain of its own
            }
            Chain query = query(next, action, keywords);
            if (query != null) {
              String q = query.lastName();
              ctx.report(nameOf(query, q), query.last(), "\"" + q + "\" runs before the unawaited \"" + action.path()
                + "\" has changed the screen; await the action or use \"" + q.replaceFirst("^(get|query)", "find") + "\".");
            }
          }
        }
      });
    }
  }

  /** Variables assigned {@code userEvent.setup(...)} anywhere in the file. */
  private static Set<String> users(List<Chain> chains) {
    Set<String> out = new HashSet<>();
    for (Chain c : chains) {
      if (c.names().equals(List.of("userEvent", "setup")) && c.segments().size() == 3 && c.precededBy("=") && c.from() >= 2) {
        Token name = Nodes.leaf(c.container().children().get(c.from() - 2));
        if (name != null && name.kind() == TokenKind.IDENT) {
          out.add(name.text());
        }
      }
    }
    return out;
  }

  /** {@code user.click(...)} or {@code userEvent.setup().click(...)} as a whole statement, not awaited, returned or kept. */
  private static boolean unawaitedAction(Chain c, Set<String> users) {
    List<String> names = c.names();
    List<Chain.Segment> segs = c.segments();
    boolean onUser = names.size() == 2 && users.contains(names.get(0)) && segs.size() == 3;
    boolean onSetup = names.size() == 3 && names.get(0).equals("userEvent") && names.get(1).equals("setup") && segs.size() == 5;
    if (!(onUser || onSetup) || !ACTIONS.contains(c.lastName()) || !c.endsWithCall()) {
      return false;
    }
    Node before = c.before();
    Node after = c.after();
    return (before == null || Nodes.isLeaf(before, ";") || before instanceof Node.Group)
      && (after == null || Nodes.isLeaf(after, ";") || after.first().line() > c.last().line());
  }

  /**
   * The getBy / queryBy call of the statement right after the action: the statement itself
   * ({@code screen.getByText(...)}), an {@code expect(...)} around it, or a {@code const x = ...} of it.
   */
  private static Chain query(Chain next, Chain action, Set<String> keywords) {
    List<Node> kids = action.container().children();
    int i = action.to();
    while (i < next.from() && Nodes.isLeaf(kids.get(i), ";")) {
      i++;
    }
    if (i < next.from()) {
      boolean declaration = next.from() - i == 3 && (Nodes.isIdent(kids.get(i), "const") || Nodes.isIdent(kids.get(i), "let"))
        && Nodes.isLeaf(kids.get(i + 2), "=");
      if (!declaration) {
        return null;
      }
    }
    if (isQuery(next)) {
      return next;
    }
    if (!next.firstName().equals("expect") || next.callAfter(0) == null) {
      return null;
    }
    List<List<Node>> items = next.callAfter(0).items();
    if (items.size() != 1) {
      return null;
    }
    List<Chain> inner = Chains.direct(new Node.Group(null, null, items.get(0)), keywords);
    return inner.size() == 1 && inner.get(0).from() == 0 && inner.get(0).to() == items.get(0).size() && isQuery(inner.get(0))
      ? inner.get(0) : null;
  }

  /** {@code getByText(...)}, {@code screen.queryByRole(...)}, {@code within(x).getAllByRole(...)}: the query is the last call. */
  private static boolean isQuery(Chain c) {
    return c.endsWithCall() && SYNC_QUERY.matcher(c.lastName()).matches()
      && c.segments().get(c.segments().size() - 2) instanceof Chain.Name;
  }

  private static Token nameOf(Chain c, String name) {
    Token out = c.first();
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Name n && n.token().is(name)) {
        out = n.token();
      }
    }
    return out;
  }
}
