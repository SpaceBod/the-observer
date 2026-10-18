# Features

Everything The Observer does, in detail. Back to the [README](../README.md).

1. [Written by AI](#written-by-ai)
2. [The newspaper](#the-newspaper)
3. [The news](#the-news)
4. [Your world's history, on a shelf](#your-worlds-history-on-a-shelf)
5. [The Exchange](#the-exchange)
6. [The newsstand](#the-newsstand)
7. [The Pawstman](#the-pawstman)
8. [The Editor's Desk](#the-editors-desk)
9. [How it plays](#how-it-plays)
10. [How often](#how-often)

## Written by AI

Every edition is written by a small language model running on your own computer: an editor that plans the paper from
a file on every player in the news, reporters that draft each story several times over, a sub-editor that checks every
draft against the facts, and townsfolk who write letters and opinion columns for the space that's left. The model is
downloaded once from inside the game and runs on the GPU or the CPU; nothing is sent anywhere.

The paper has three voices, set on the Editor's Desk or with `/observer tone`:

| Voice | |
|---|---|
| **The Broadsheet** (default) | Straight news, told with a proud small-town paper's dry wit. |
| **The Roast** | No resident is safe: every story is a joke at somebody's expense. Cheeky, never cruel. |
| **The Chronicle** | Every deed a legend, every resident a hero of the age. |

The voice changes the model's style and examples, the house templates and the masthead's motto. The checks are the
same in every voice.

Without a model, The Observer writes every edition from its built-in house templates: the same paper and the same
news, in a fixed set of phrasings. [The press](press.md) covers the newsroom, the models and tuning.

## The newspaper

Right-click the paper to open it: a stapled newspaper with page-turn arrows, like a book.

1. **The news.** The masthead with the paper's motto, the lead story across the front page, more stories in columns,
   letters and opinion columns, and a **Did You Know?** column of real Minecraft trivia. The sidebar has the
   obituaries, the town's numbers, the weather and the small ads. Nothing is ever cut off: a story only goes in if it
   fits whole. The news takes two spreads.
2. **The Exchange.** Your own notices from the townsfolk (see [The Exchange](#the-exchange)).
3. **The Leaderboard and the Hall of Fame.** Eight rankings of who led the town at what since the last edition, and
   eight all-time records, picked fresh each edition from over 30 boards plus a hunting board for every creature
   anyone killed. Busy boards and close races come first, and boards that ran last time get a rest.

Players' names are printed in blue so readers spot themselves.

<p>
<img src="images/articles-page-1.jpg" alt="The front page" width="49%">
<img src="images/articles-page-2.jpg" alt="The second news spread: more stories, letters, opinion columns and Did You Know?" width="49%">
</p>
<p>
<img src="images/exchange-page.jpg" alt="The Exchange" width="49%">
<img src="images/leaderboard-page.jpg" alt="The Leaderboard and the Hall of Fame" width="49%">
</p>

## The news

Everything comes from the statistics the game keeps anyway, compared edition by edition, plus deaths and
advancements as they happen:

- **Stories** for the big things: boss kills, deaths (in the game's own words), advancements, mining hauls, building,
  redstone machinery, travel, farming, trading, raids, monster hunts, and a few sillier ones for slow weeks.
- **The biggest moment leads.** Every candidate story is scored, and a defeated Ender Dragon, Wither or Warden always
  takes the front page. No one player or kind of story takes over the rest of the paper.
- **Firsts.** About 60 milestones, spotted the edition they happen: the first diamond pickaxe, first steps in the
  Nether, the first enchantment, an elytra picked up.
- **Habits and records.** Twenty-odd pursuits (farming, forestry, redstone, cooking, fishing, sailing, music...)
  measured against each player's own usual week: "took up redstone", or "a record week: 1,400 crops planted, usually
  about 160".
- **Connections.** A death that follows from something else the player did is told as one story: crashing while
  gliding the week they found elytra, a swim in lava the week they found diamonds, being killed by the very creatures
  they spent the week hunting.
- **Every player who has ever played**, online or not, read from the world's statistics files, so the first paper on
  an existing world covers everyone.

## Your world's history, on a shelf

Old editions keep. A chiseled bookshelf holds papers like books, every story, letter and Exchange notice intact, so a
shelf of back issues becomes your world's archive: take one down any time and reread the week it happened.

## The Exchange

- **Your own notices.** Six per reader: four deliveries for townsfolk of different trades ("WANTED: 128 wheat") and
  two jobs for the Watch ("Deal with 12 zombies"). They're worked out from the edition and the reader's player ID and
  pitched to how far they've got: nobody is asked for blaze rods before they've been to the Nether. A player who joins
  after an edition is printed gets theirs when their copy reaches them.
- **Townsfolk with names.** Each notice is posted by someone with a trade (Farmer, Mason, Fisherman, Cleric,
  Librarian, Armourer, Shepherd, Fletcher, Leatherworker, Butcher, Cartographer, Toolsmith, Forester, Baker) from a
  town that ranges from Little Ashcombe to Gloamwood. Now and then a famous face turns up in a job that suits them.
- **Fair pay.** Priced from vanilla villager trade rates, at the generous end, in amounts sized to the time the
  notices stay up. Some pay in goods, enchanted books included. Players starting out always have one notice that pays
  in emeralds.
- **Checked by the server.** Progress shows live, with **Hand in** or **Claim** when it's done. Deliveries are taken
  from your inventory, and Watch jobs count kills since the paper was printed. Finished work makes the next edition's
  news.

## The newsstand

<img src="images/multicolor-options-dog.jpg" alt="Newsstands painted in every dye colour" width="100%">

A painted vending box on a cast-iron post.

- **Every player sees their own.** Its window shows a fresh paper while your copy is waiting and is empty once you've
  had it. Its lamps (the coin slot and the bulb on the lid) glow green while your copy waits, red once you have it and
  orange while the next edition is at the press.
- **When a new edition arrives**, every stand rings its bell and sparkles.
- **Any colour.** It comes in red; use any dye on it to repaint it.

<p>
<img src="images/state-delivered.jpg" alt="Your copy is waiting: a paper in the window, lamps green" width="32%">
<img src="images/state-collected.jpg" alt="You have your copy: the window empty, lamps red" width="32%">
<img src="images/state-printing.jpg" alt="The next edition is at the press: lamps orange" width="32%">
</p>
<p><em>Waiting for you, collected, at the press.</em></p>

### Crafting

Emeralds for the coin works, iron, glass panes (any colour), paper and a wooden chest (a trapped chest works too).

<img src="images/crafting-recipe.jpg" alt="The newsstand's crafting recipe: emerald, iron ingot, emerald; glass pane, paper, glass pane; iron ingot, chest, iron ingot" width="60%">

### The counter

Right-click the stand for the status, a live countdown to the next edition, your copy (bought through the coin slot;
spares too), and your choice of delivery: the Pawstman or collecting here. Operators also get the
[Editor's Desk](#the-editors-desk) beside it.

## The Pawstman

<p>
<img src="images/pawstman-approach.jpg" alt="The Pawstman arriving with the paper rolled in his mouth" width="49%">
<img src="images/pawstman-gui.jpg" alt="The Pawstman's counter: the price in the coin slot, pay and take, or no thanks" width="49%">
</p>

The delivery dog, in a red collar, appears somewhere you can't see him and trots over with the paper rolled up in his
mouth.

- **Paying.** Every copy costs the paper's price (one emerald by default), at the door or at a stand. He follows you,
  barking, until you right-click him: his counter takes the price in its coin slot, and you **pay and take** the
  paper or say **no thanks**. A free copy (your first, or any on a free server) comes the same way: his counter says
  it's free and you **take it**.
- **Saying no.** Turn him away (there, by hitting him, or by keeping him waiting a minute) and he takes the paper home;
  you buy your copy at the nearest newsstand instead, and he doesn't come round again for that edition. If your
  pockets are empty he doesn't pester you: your copy waits at the newsstands.
- **Always finds you.** Like a tamed wolf, he teleports to your side when there's no way through or he falls far
  behind, and keeps trying while there's nowhere near you to stand (flying, swimming, on a pillar). If he still can't
  reach you after five minutes, the delivery has failed and you collect your copy at the nearest newsstand.
- He can't be tamed and isn't saved with the world. Players who were offline get theirs when they next join.

## The Editor's Desk

Operators run the paper from the Editor's Desk, folded beside every newsstand's counter (closed by default): the
paper's voice, how often it comes out, whether it publishes on its own, the Exchange's rewards (low, balanced or
high), the Pawstman's duty and the paper's price, whether the first copy is free, and an early edition. Everything on
it is also a command; see [Configuration](configuration.md).

<img src="images/newsstand-gui-op.jpg" alt="The newsstand's counter, with the Editor's Desk open beside it" width="100%">

## How it plays

1. **Day one.** The paper starts by itself; nothing needs building. The Pawstman brings each player their first copy
   free; his counter welcomes them and says what copies cost after that.
2. **Early game.** After that a copy costs the paper's price. Every player starting out has an Exchange notice that
   pays in emeralds, so the paper pays for itself.
3. **Mid game.** Once you're trading with villagers, build a newsstand: collect whenever you like, see at a glance
   whether your copy is in, buy spares, and (for operators) run the Editor's Desk.
4. **Every week after.** File each edition on a bookshelf, and the shelf becomes your world's history.

## How often

The paper comes out **daily**, **weekly** (the default) or **fortnightly**, and everything follows the choice: the
stories say "today" or "this fortnight", players are compared with their usual day or fortnight (earlier editions are
scaled to match after a change), the Exchange's asks and rewards shrink for a daily paper and grow for a fortnightly
one, and the next edition moves to the start of the next period. Issues are numbered in order, one per edition;
publishing again within the same period reprints that issue under the same number. Operators can also pause the
schedule and publish by hand.

## Notes

- Editions follow the overworld's day count, so with the daylight cycle switched off no new editions come out on their
  own (operators can still publish).
- The house content is data, so resource packs can restyle the paper: `copy.json` (story templates, opinion pieces,
  small ads), `insights.json` (firsts, pursuits, leaderboards), `links.json` (connections), `trivia.json`,
  `exchange.json` (trades, requests, prices), `names.json` (people and towns) and `press.json` (the default press
  profile), all in `assets/spacebod_observer/observer/`.
