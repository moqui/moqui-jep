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

Examples and tests:

- `src/test/groovy/JepServiceTests.groovy` covers Moqui service/script integration
- `src/test/groovy/JepDocsExamplesTests.groovy` contains small examples inspired by the Jep wiki:
  - How Jep Works
  - Numpy Usage
  - Interactive Console
- Run them with `./gradlew :runtime:component:moqui-jep:test`
