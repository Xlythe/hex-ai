# Hex AI

`GameAI` and `BeeGameAI` are the legacy bots. `TreeGameAI` is a separate
Monte Carlo tree search bot. It uses bounded simulations, legal pie-rule swap,
immediate win/block checks, and connection-path move ordering. Its pure
`choose` method accepts a board snapshot and seed, so decisions can be tested
without Android.

On Windows, set `JAVA_HOME` to a JDK and run `./build.ps1`. The script runs
deterministic tactics and 20 games against random play, then creates
`build/hex-ai.jar`. To use it in Android, copy that JAR to
`../Hex/app/libs/hex-ai.jar`. The script packages only `com/hex/ai`; it must
not bundle `hex-core` classes.

Android exposes Tree as an experimental fourth difficulty alongside Bee.
The current head-to-head sample was two wins out of four against Bee on 5×5
with 12,000 simulations per move. That small sample is a smoke benchmark, not
a strength rating.
