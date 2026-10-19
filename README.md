# AEGIS: A Safer Way to Match Text Patterns

> **CS200 (Programming Languages), Group 3.** Two small regular-expression engines written in plain Java, built to be compared side by side.

---

## Table of Contents

1. [What is this project?](#what-is-this-project)
2. [Key terms in plain English](#key-terms-in-plain-english)
3. [What you need before running](#what-you-need-before-running)
4. [Quick start (3 steps)](#quick-start-3-steps)
5. [Which file do I run?](#which-file-do-i-run)
6. [Try your own pattern](#try-your-own-pattern)
7. [How to read the output](#how-to-read-the-output)
8. [Troubleshooting](#troubleshooting)
9. [What patterns are supported?](#what-patterns-are-supported)
10. [How the code is organized](#how-the-code-is-organized)
11. [How AEGIS works (the short version)](#how-aegis-works-the-short-version)
12. [Honest notes and limitations](#honest-notes-and-limitations)

---

## What is this project?

Programs often need to check whether some text follows a certain **pattern**. For example: *"Is this a valid email address?"* or *"Does this input contain a suspicious word?"* The tool used for this is called a **regular expression** (or **regex**).

Most programming languages (Java, Python, Perl, and others) check patterns using a technique called **backtracking**. It works well most of the time, but certain patterns combined with certain inputs can make it take an **extremely long time**. Each extra character can *double* the work. A single malicious input can freeze a server. This is a real security problem known as **ReDoS** (Regular expression Denial of Service).

**This project builds two engines and compares them:**

| Engine | How it works | Behavior on dangerous input |
|---|---|---|
| **AEGIS** | Keeps track of *every place it could currently be* in the pattern and advances them all together, one character at a time. It never backs up. | Always fast. Work is bounded by (pattern size × input length). |
| **Baseline** | The conventional backtracking approach: try one path, and if it fails, go back and try another. | Can blow up exponentially, so it has a built-in step limit to avoid freezing your computer. |

Both engines share the exact same front end (the part that reads and understands the pattern). **The only difference is the matching strategy**, which keeps the comparison fair.

**A simple analogy:** Imagine finding your way through a maze.
- *Backtracking* is like walking down one hallway, hitting a dead end, walking back, and trying the next hallway. In a nasty maze, you might retrace your steps millions of times.
- *AEGIS* is like sending a copy of yourself down every hallway at once. You move all the copies forward together, so you never retrace anything.

---

## Key terms in plain English

| Term | Meaning |
|---|---|
| **Regular expression (regex)** | A short pattern that describes a set of text. For example, `[a-z]+@\d{2,3}` means "one or more lowercase letters, then `@`, then 2 or 3 digits". |
| **Backtracking** | Trying one option, and if it fails, going back to the last decision and trying another. |
| **ReDoS** | An attack that sends text crafted to make a backtracking regex run for a very long time. |
| **Lexer** | Splits the pattern text into small pieces called *tokens* (like splitting a sentence into words). |
| **Parser / AST** | Arranges the tokens into a tree that shows the structure of the pattern. *AST* stands for Abstract Syntax Tree. |
| **NFA** | *Nondeterministic Finite Automaton*. A small map of states (`q0`, `q1`, ...) that the engine moves through while reading text. |
| **Step** | One unit of work done by an engine. Used to measure how much effort a match took. |

---

## What you need before running

You need **Java 17 or newer**, and it must be the **JDK** (Java Development Kit), not just the JRE. The JDK includes `javac`, the compiler. No other libraries are needed.

Check what you have by opening a terminal (Mac/Linux) or Command Prompt (Windows) and typing:

```
java -version
javac -version
```

Both commands should print a version number of **17 or higher**. If either one fails or shows an older version, install a JDK from [Adoptium](https://adoptium.net/) (free) and try again.

---

## Quick start (3 steps)

### Step 1: Get the project

Either clone it:

```
git clone https://github.com/Tantthew/Aegis_Project.git
cd aegis-prototype
```

Or click the green **Code** button on GitHub, choose **Download ZIP**, unzip it, and open a terminal inside the unzipped `aegis-prototype` folder.

> **Important:** Run all the commands below from the project's main folder (the one that contains the `src` folder).

### Step 2: Compile the code

**Mac or Linux:**

```
javac -d out $(find src -name "*.java")
```

**Windows (Command Prompt, not PowerShell):**

```
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
```

This creates a new folder called `out` containing the compiled program. If nothing is printed, it worked.

### Step 3: Run the main demo

```
java -cp out demo.AegisDemo
```

You should see a step-by-step walkthrough of how AEGIS processes a pattern. That is the whole setup.

> **Prefer an IDE?** In IntelliJ IDEA, VS Code, or Eclipse, open the project, mark the `src` folder as the *source root*, then open any file inside `src/demo/` and click **Run** on its `main` method.

---

## Which file do I run?

All the runnable programs are in `src/demo/`. After compiling, run them with `java -cp out demo.<Name>`.

| Command | What it does | Time |
|---|---|---|
| `java -cp out demo.AegisDemo` | **Start here.** Shows every stage of AEGIS for two example patterns: tokens, tree, NFA states, and a step-by-step trace. Ends with the dangerous `(a+)+` case. | instant |
| `java -cp out demo.BaselineDemo` | Runs the backtracking engine on the same patterns, then shows its work **doubling** as the input grows until the step limit stops it. | 1-2 seconds |
| `java -cp out demo.Benchmark` | Compares AEGIS, the baseline, and Java's built-in `java.util.regex` on dangerous and ordinary input. | about 10 seconds |
| `java -cp out demo.FilterDemo` | A small input filter (username, email, SQL-injection scan, and one deliberately vulnerable rule) checked by both engines. Shows why this matters for security. | 1-2 seconds |
| `java -cp out demo.SelfTest` | Correctness check. Compares both engines against Java's built-in regex on hand-written cases, syntax errors, and 300,000 random checks. Ends with `RESULT: PASS` if everything agrees. | 2-5 seconds |

**Suggested order for first-timers:** `AegisDemo`, then `BaselineDemo`, then `Benchmark`, then `FilterDemo`, and `SelfTest` last.

---

## Try your own pattern

`AegisDemo` and `BaselineDemo` accept your own pattern followed by **one or more** test inputs:

```
java -cp out demo.AegisDemo "pattern" input1 input2 input3
```

Examples:

```
java -cp out demo.AegisDemo "ab*c" abbbc ac
java -cp out demo.AegisDemo "[a-z]+@\d{2,3}" tantan@123 tantan@1
java -cp out demo.BaselineDemo "(a|b)*abb" abb babb
```

**Tips:**
- Put the pattern in **double quotes** so your terminal does not misread characters like `|`, `*`, `(`, and `\`.
- Windows Command Prompt only understands **double** quotes. Single quotes will not work there.
- You must give **at least one input** after the pattern. See [Troubleshooting](#troubleshooting) if the built-in demo runs instead.
- `matches` checks whether the **whole** input fits the pattern.

---

## How to read the output

Here is part of what `AegisDemo` prints for the pattern `(a|b)*abb` with the input `aabb`:

```
[1] Lexer tokens:
    [LPAREN, CHAR('a'), PIPE, CHAR('b'), RPAREN, STAR, CHAR('a'), CHAR('b'), CHAR('b'), END]
[2] Parser, abstract syntax tree:
    Concat
      Star (zero or more)
        Alternation
          Lit 'a'
          Lit 'b'
      Lit 'a'
      Lit 'b'
      Lit 'b'
[3] NFA (8 states, Thompson construction):
  q0: ACCEPT
  ...
[4] Simulating input "aabb":
    start  ->  active {q7, q5, q3}
    read 'a'  ->  active {q7, q5, q3, q2}
    read 'a'  ->  active {q7, q5, q3, q2}
    read 'b'  ->  active {q7, q5, q3, q1}
    read 'b'  ->  active {q7, q5, q3, q0}   <- contains the ACCEPT state
    => MATCH  (steps=37, peak active states=4)
```

| Part | What it tells you |
|---|---|
| **[1] Lexer tokens** | The pattern chopped into pieces. |
| **[2] Syntax tree** | The structure of the pattern, drawn as an indented tree. |
| **[3] NFA** | The "map" of states built from the tree. `q0` is the **ACCEPT** state (a match). |
| **[4] Simulating** | After each character, which states are still "alive". If an alive state is the ACCEPT state, the text **matches**. |
| **steps** | How much work was done. Lower is better. |
| **peak active states** | The most states alive at once. For AEGIS this stays small. |

### The key result

For the dangerous pattern `(a+)+` with 40 letters `a` followed by `b`, AEGIS finishes in about **200 steps**. The baseline engine on a much shorter input (22 letters) already hits its **20,000,000-step limit** and prints:

```
n=22   ABORTED after 20000001 steps (step limit of 20000000 exceeded)
```

`ABORTED` means the baseline engine **gave up** to avoid freezing your computer. This is the problem AEGIS avoids.

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| `javac: command not found` (Mac/Linux) or `'javac' is not recognized` (Windows) | Java is not installed, or only the JRE is installed (no compiler), or it is not on your PATH. | Install a **JDK 17+** from [adoptium.net](https://adoptium.net/). On Windows, tick the option to add Java to PATH, then **close and reopen** your terminal. |
| Compile errors mentioning `record`, `sealed`, or an unsupported release | Your Java version is older than 17. | Run `javac -version`. If it is below 17, install a newer JDK. |
| `Error: Could not find or load main class AegisDemo` | You left out the `demo.` prefix. | Use the full name: `java -cp out demo.AegisDemo`. |
| `Error: Could not find or load main class demo.AegisDemo` | The code has not been compiled yet, or you are in the wrong folder. | Do [Step 2](#step-2-compile-the-code) first, and make sure your terminal is in the folder that contains `src` and `out`. |
| `dir` or `find` errors during compile on Windows | You are using PowerShell instead of Command Prompt, or the Mac/Linux command (`$(find ...)`) on Windows. | Open **Command Prompt** (`cmd`) and use the two-line Windows version. |
| `AegisDemo "pattern"` just shows the built-in demo | The custom mode needs the pattern **and at least one input**. | Add an input: `java -cp out demo.AegisDemo "ab*c" abbbc`. |
| `Syntax error: ... (at index N)` | Your pattern is malformed or uses an unsupported feature. | This is expected behavior, not a crash. Read the message, which says what is wrong and where. See [supported patterns](#what-patterns-are-supported). |
| Output shows `ABORTED` for the baseline | The backtracking engine hit its safety limit. | **Not a bug.** This is the demonstration of the problem. |
| The terminal seems stuck during `Benchmark` | It is running many tests. | Wait about 10 seconds (a slower computer may need longer). It has built-in time limits and will finish by itself. |
| Benchmark timings differ from someone else's | Timings depend on your computer. | Normal. Compare how each column *grows*, not the exact numbers. |
| Old files cause strange results after editing code | Stale compiled files. | Delete the `out` folder and compile again. |

---

## What patterns are supported?

**Supported:**

| Feature | Example | Meaning |
|---|---|---|
| Plain characters | `abc` | Exactly `abc` |
| Any character | `.` | Any character except line breaks |
| Escapes | `\d \D \w \W \s \S \n \t \r \f` | Digit, non-digit, word character, non-word character, whitespace, non-whitespace, newline, tab, and so on |
| Escaped symbols | `\.` `\*` `\(` | The literal symbol |
| Character classes | `[abc]` `[a-z]` `[^0-9]` | One of those characters / a range / anything except those |
| Groups | `(ab)` | Treat `ab` as one unit |
| Alternation | `a\|b` | `a` **or** `b` |
| Zero or more | `a*` | Any number of `a`, including none |
| One or more | `a+` | At least one `a` |
| Optional | `a?` | Zero or one `a` |
| Exact counts | `a{3}` `a{2,}` `a{2,5}` | Exactly 3 / 2 or more / between 2 and 5 (maximum 1000) |

**Deliberately not supported** (rejected with a clear error instead of behaving differently from other engines):

- Anchors (`^`, `$`)
- Backreferences (`\1`)
- Look-around (`(?=...)`, `(?!...)`)
- Lazy or possessive quantifiers (`*?`, `++`) and stacked quantifiers such as `a**`
- Flags such as case-insensitive matching
- Returning the matched text or capture groups
- Special Unicode characters that take more than one Java character (such as some emoji)

Both engines answer only **true or false**. They do not report where a match was found.

- `matches(input)` is true if the **whole** input fits the pattern.
- `find(input)` is true if **any part** of the input fits the pattern.

---

## How the code is organized

```
aegis-prototype/
├── README.md
└── src/
    ├── common/                  Shared by both engines (reads the pattern)
    │   ├── Lexer.java             Splits the pattern into tokens
    │   ├── Token.java             One token
    │   ├── Parser.java            Turns tokens into a syntax tree
    │   ├── Node.java              The syntax tree's node types
    │   ├── CharSet.java           A set of characters, like [a-z] or \d
    │   ├── AstPrinter.java        Draws the tree as indented text
    │   └── RegexSyntaxException.java   The error for bad patterns
    │
    ├── aegis/                   The AEGIS engine
    │   ├── NfaBuilder.java        Builds the NFA from the tree (Thompson construction)
    │   ├── Nfa.java               The NFA and its states
    │   └── AegisEngine.java       Runs the matching (no backtracking) and the trace
    │
    ├── baseline/                The conventional engine
    │   └── BacktrackingEngine.java   Backtracking matcher with a step limit
    │
    └── demo/                    Programs you can run
        ├── AegisDemo.java
        ├── BaselineDemo.java
        ├── Benchmark.java
        ├── FilterDemo.java
        └── SelfTest.java
```

---

## How AEGIS works (the short version)

```
 pattern text  ->  Lexer  ->  Parser  ->  Syntax tree  ->  NFA  ->  Simulation  ->  true / false
                   tokens     structure                    states    all paths at once
```

1. **Lexer** (`common/Lexer.java`) splits the pattern into tokens.
2. **Parser** (`common/Parser.java`) builds a syntax tree (`common/Node.java`).
3. **NFA builder** (`aegis/NfaBuilder.java`) converts the tree into a small state machine using *Thompson's construction*.
4. **Simulation** (`aegis/AegisEngine.java`) reads the input one character at a time, keeping the *set* of all states it could be in. The set can never be larger than the NFA, so total work is at most **(NFA size) × (input length)**, no matter how nasty the pattern is.

The baseline engine (`baseline/BacktrackingEngine.java`) uses steps 1 and 2 too, but then walks the tree directly and backtracks.

| Concept | Where it lives in the code |
|---|---|
| Lexical analysis | `common/Lexer.java` |
| Parsing and syntax tree | `common/Parser.java`, `common/Node.java` |
| Thompson construction | `aegis/NfaBuilder.java` |
| Linear-time simulation | `aegis/AegisEngine.java` |
| Backtracking comparison | `baseline/BacktrackingEngine.java` |
| Security application | `demo/FilterDemo.java` |

---

## Honest notes and limitations

Please keep these in mind when reading the results:

1. **Modern Java is not slow on every textbook pattern.** Java's built-in regex has extra optimizations, so patterns like `(a+)+` do not explode there. Our baseline is the plain textbook version *without* those optimizations. The benchmark also includes `((a+)+)+b` and `(.*a){12}`, which still hurt Java's real engine.
2. **On ordinary patterns, AEGIS is not faster.** All three engines finish in milliseconds. AEGIS's advantage is in the **worst case**, not average speed.
3. **"Steps" are counted differently.** AEGIS counts NFA state operations. The baseline counts matcher calls. Compare how each *grows* with input length, not the raw numbers between engines.
4. **Timings are rough.** They come from one run in one Java session, not from a professional benchmarking tool.
5. **The baseline has a step limit** (20 million by default) so it reports `ABORTED` instead of hanging. A real backtracking engine has no such limit.
6. **Deep recursion.** The baseline calls itself once per repeated item, so the demos run it on a thread with a large stack. This also mirrors how real backtracking engines struggle on very long input.
