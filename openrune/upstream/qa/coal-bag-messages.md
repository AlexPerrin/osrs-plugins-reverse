# QA: fix/75-coal-bag-messages

- Branch: `fix/75-coal-bag-messages` (`refs/qa/fix/75-coal-bag-messages`, read-only fetch from server dev #22's clone)
- Commit: `08f089ee699ffbf9f8c94058a6e1e61595dba0eb` (base: upstream `main` `c2492318d`)
- Date: 2026-10-18
- Texts: `batch-3-texts/coal-bag-messages.{title,body}`

## Verdict: **incomplete** (stopped for a fresh start)

What ran:
- Fresh clone at the commit. No build was started, so there are no build, test or in-game results.
- Code and PR text review (section 4, partial):
  - The diff matches the body. Check and Fill: "The coal bag contains N pieces of coal." / "... one piece of
    coal." / "The coal bag is empty.". Empty: "The coal bag is now empty.", or "The coal bag still contains ...".
    "Your coal bag is full." and the no-coal Fill message are unchanged, as the Caveats say.
  - These match the Plugin Hub Coal Bag plugin's regexes (`re/hub-src/coal-bag/.../CoalBag.java`:
    `^The coal bag is (?:now\s)?empty\.` and `^The coal bag (?:still\s)?contains ([\d]+|one) pieces? of coal\.`)
    and its tests.
  - Behaviour change to note: Empty when the bag is already empty now says "The coal bag is empty." (main: "Your
    coal bag is empty."). It fits the same pattern.

Still to do: section 1, then section 3 in game: check, fill and empty with 0, 1 and N coal, plus empty into a full
inventory. Then install the Plugin Hub `coal-bag` plugin in the isolated profile (`runelite.externalPlugins`,
C:\osrs-test only), check that its count follows the bag, and remove it afterwards.
