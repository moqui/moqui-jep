# Moqui JEP - Java Embedded Python
Jep embeds CPython in Java through JNI.

Moqui tool component that adds Python script execution through Jep.

What this component contains:

- The JepToolFactory
- The PythonScriptRunner
- The PythonServiceRunner
- Python package requirements
- Root Gradle helpers to prepare `runtime/python_venv`

Default behavior:

- Moqui starts even if Python/Jep is not installed
- Python scripts become available only when the venv contains Jep and its native library
- Use `./gradlew moquiJepSetup` to prepare the local Python environment

Typical service definition:

<service verb="run" noun="Something" type="python" location="component://component-name/script/RunSomething.py"/>

## Concurrency and the GIL

Both `SharedInterpreter` and `SubInterpreter` (in the default `legacy` mode) share the same CPython process and serialize through the Global Interpreter Lock. Concurrent Moqui threads calling Python services will therefore queue at the GIL — which is fine for training, simulation, or batch inference, but becomes a bottleneck under high concurrent HTTP load.

`SubInterpreterOptions.isolated()` (enabled via `-Djep_sub_opts=isolated`, requires Python 3.12+ with experimental per-interpreter GIL) can eventually lift this constraint, but it is still experimental upstream. Select the interpreter mode with the `-Djep_sandboxed_interpreter=true` system property (default: `false`, i.e. `SharedInterpreter`).

For latency-sensitive concurrent inference workloads, consider delegating to an out-of-process runtime (ONNX Runtime, TorchServe, Triton) and calling it from a standard Moqui service rather than routing through moqui-jep.

## Examples and tests:

- `src/test/groovy/JepServiceTests.groovy` covers Moqui service/script integration
- `src/test/groovy/JepDocsExamplesTests.groovy` contains small examples inspired by the Jep wiki:
  - How Jep Works
  - Numpy Usage
  - Interactive Console
- Run them with `./gradlew :runtime:component:moqui-jep:test`
