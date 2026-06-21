# Moqui JEP - Java Embedded Python
Jep embeds CPython in Java through JNI.

Moqui tool component that adds Python script execution through Jep.

What this component contains:

- The JepToolFactory
- The PythonScriptRunner
- The PythonServiceRunner
- Python package requirements
- Root Gradle helpers to prepare `runtime/python_venv`

## System prerequisite: Python installation

Python is a **system-level dependency** for this component, in the same way the JVM is a system-level dependency for Moqui Framework itself. The Gradle setup (`moquiJepSetup`) only creates a virtual environment on top of an existing Python installation — it does not install Python.

A compatible CPython 3.x installation must be present on the host machine before running any Moqui JEP feature or test. [Miniconda](https://docs.conda.io/en/latest/miniconda.html) is the recommended distribution because it provides a self-contained Python with its shared library (`libpython3.x.so`) in a predictable location.

### Required environment variable

Because JEP loads CPython through JNI, the Python shared library (`libpython3.x.so.1.0`) must be visible to the JVM at runtime via `LD_LIBRARY_PATH` (Linux/macOS) or `PATH` (Windows). Without this, the JVM will throw `UnsatisfiedLinkError: libpython3.x.so.1.0: cannot open shared object file` when the first Python interpreter is created.

**Linux / macOS (Miniconda example):**

```bash
export LD_LIBRARY_PATH=/path/to/miniconda3/lib:$LD_LIBRARY_PATH
```

**Windows (Miniconda example):**

Add `C:\path\to\miniconda3\` to the `PATH` environment variable (the directory that contains `python3x.dll`).

### Quick start

```bash
# 1. Install Miniconda (or any CPython 3.x) on the host machine

# 2. Create the virtual environment and install Python dependencies
./gradlew moquiJepSetup -Djep_python_path=/path/to/miniconda3/bin

# 3. Export the shared library path (Linux/macOS)
export LD_LIBRARY_PATH=/path/to/miniconda3/lib:$LD_LIBRARY_PATH

# 4. Run Moqui or the moqui-jep tests
./gradlew :runtime:component:moqui-jep:test -Djep_python_path=/path/to/miniconda3/bin
```

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
