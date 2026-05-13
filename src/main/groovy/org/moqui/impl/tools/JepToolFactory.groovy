/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl.tools

import groovy.transform.CompileStatic
import jep.Interpreter
import jep.JepConfig
import jep.MainInterpreter
import jep.SharedInterpreter
import jep.SubInterpreterOptions
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class JepToolFactory implements ToolFactory<JepToolFactory> {
    protected final static Logger logger = LoggerFactory.getLogger(JepToolFactory.class)
    public final static String TOOL_NAME = "Jep"

    protected ExecutionContextFactory ecf = null
    protected JepConfig baseCfg = null
    protected boolean sandboxedInterpreter = false
    protected boolean jepInitialized = false
    protected String unavailableReason = "JEP is not initialized."

    @Override
    String getName() { return TOOL_NAME }

    @Override
    void init(ExecutionContextFactory ecf) { }

    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        this.ecf = ecf
        sandboxedInterpreter = Boolean.parseBoolean(System.getProperty("jep_sandboxed_interpreter", "false"))

        JepEnvironment env = resolveEnvironment()
        if (!env.available) {
            unavailableReason = env.message
            logger.info("JEP disabled: {}", unavailableReason)
            return
        }

        try {
            MainInterpreter.setJepLibraryPath(env.jepLib.absolutePath)

            JepConfig cfg = new JepConfig()
            if (env.sitePkgs != null) cfg.addIncludePaths(env.sitePkgs.absolutePath)
            cfg.setClassLoader(Thread.currentThread().getContextClassLoader())

            String sharedCsv = (System.getProperty("jep_shared") ?: "").trim()
            if (sharedCsv) {
                for (String mod in sharedCsv.split(",")) {
                    String moduleName = mod?.trim()
                    if (moduleName) cfg.addSharedModules(moduleName)
                }
            }

            String optsPreset = (System.getProperty("jep_sub_opts") ?: "legacy").trim()
            if (optsPreset.equalsIgnoreCase("legacy")) {
                cfg.setSubInterpreterOptions(SubInterpreterOptions.legacy())
            } else if (optsPreset.equalsIgnoreCase("isolated")) {
                cfg.setSubInterpreterOptions(SubInterpreterOptions.isolated())
            }

            try {
                SharedInterpreter.setConfig(cfg)
            } catch (Throwable t) {
                logger.warn("Jep SharedInterpreter.setConfig failed.", t)
            }

            if (sharedCsv) {
                SharedInterpreter py = null
                try {
                    py = new SharedInterpreter()
                    sharedCsv.split(",")*.trim().findAll { it }.each { String mod ->
                        try {
                            py.exec("import " + mod)
                        } catch (Throwable t) {
                            logger.warn("SharedInterpreter warm import failed for module ${mod}.", t)
                        }
                    }
                } finally {
                    try { py?.close() } catch (Throwable ignore) { }
                }
            }

            baseCfg = cfg
            jepInitialized = true
            unavailableReason = null
            logger.info("Jep interpreters ready (lib: {}, site: {}, shared: {}, opts: {})",
                    env.jepLib.absolutePath, env.sitePkgs?.absolutePath ?: "-", sharedCsv ?: "-", optsPreset)
        } catch (Throwable t) {
            unavailableReason = "Error initializing JEP: ${t.class.simpleName}: ${t.message}"
            logger.error(unavailableReason, t)
        }
    }

    @Override
    JepToolFactory getInstance(Object... parameters) {
        if (!jepInitialized) throw new IllegalStateException(getUnavailableMessage())
        return this
    }

    @Override
    void destroy() { }

    ExecutionContextFactory getEcf() { return ecf }

    Interpreter getInterpreter() {
        if (!jepInitialized) throw new IllegalStateException(getUnavailableMessage())
        return sandboxedInterpreter ? (baseCfg.createSubInterpreter() as Interpreter) : (new SharedInterpreter() as Interpreter)
    }

    Interpreter getInterpreter(boolean sandboxed) {
        if (!jepInitialized) throw new IllegalStateException(getUnavailableMessage())
        return sandboxed ? (baseCfg.createSubInterpreter() as Interpreter) : (new SharedInterpreter() as Interpreter)
    }

    String getUnavailableMessage() {
        return unavailableReason ?: "JEP is not initialized. Run './gradlew moquiJepSetup' to prepare runtime/python_venv."
    }

    protected JepEnvironment resolveEnvironment() {
        File jepLib = resolveConfiguredFile("jep.lib")
        File sitePkgs = resolveConfiguredFile("jep_site_pkgs")

        if (sitePkgs == null && jepLib != null) {
            File jepDir = jepLib.parentFile
            if (jepDir != null) sitePkgs = jepDir.parentFile
        }

        if (jepLib == null || sitePkgs == null) {
            JepEnvironment runtimeEnv = resolveFromRuntimeVenv()
            if (jepLib == null) jepLib = runtimeEnv.jepLib
            if (sitePkgs == null) sitePkgs = runtimeEnv.sitePkgs
        }

        if (jepLib == null || !jepLib.exists()) {
            return new JepEnvironment(null, null,
                    "JEP native library not found. Prepare runtime/python_venv with './gradlew moquiJepSetup' or set -Djep.lib.")
        }
        if (sitePkgs == null || !sitePkgs.exists()) {
            return new JepEnvironment(jepLib, null,
                    "Python site-packages not found for JEP. Prepare runtime/python_venv or set -Djep_site_pkgs.")
        }

        return new JepEnvironment(jepLib, sitePkgs, null)
    }

    protected File resolveConfiguredFile(String propertyName) {
        String propValue = System.getProperty(propertyName)
        if (!propValue) return null
        File resolvedFile = new File(propValue)
        return resolvedFile.exists() ? resolvedFile : null
    }

    protected JepEnvironment resolveFromRuntimeVenv() {
        String runtimePath = System.getProperty("moqui.runtime")
        if (!runtimePath) return new JepEnvironment(null, null, "moqui.runtime is not set.")

        File venvDir = new File(runtimePath, "python_venv")
        if (!venvDir.exists()) {
            return new JepEnvironment(null, null, "Python venv not found at ${venvDir.absolutePath}.")
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win")
        File sitePkgs = null
        if (isWindows) {
            File candidate = new File(venvDir, "Lib/site-packages")
            if (candidate.exists()) sitePkgs = candidate
        } else {
            File libDir = new File(venvDir, "lib")
            File pyDir = libDir.listFiles()?.find { File file -> file.isDirectory() && file.name.startsWith("python") }
            if (pyDir != null) {
                File candidate = new File(pyDir, "site-packages")
                if (candidate.exists()) sitePkgs = candidate
            }
        }

        File jepLib = null
        if (sitePkgs != null) {
            File jepDir = new File(sitePkgs, "jep")
            List<File> candidates = isWindows ?
                    [new File(jepDir, "jep.dll")] :
                    [new File(jepDir, "libjep.so"), new File(jepDir, "libjep.dylib")]
            jepLib = candidates.find { File file -> file.exists() }
        }

        return new JepEnvironment(jepLib, sitePkgs, null)
    }

    @CompileStatic
    protected static class JepEnvironment {
        final File jepLib
        final File sitePkgs
        final String message

        JepEnvironment(File jepLib, File sitePkgs, String message) {
            this.jepLib = jepLib
            this.sitePkgs = sitePkgs
            this.message = message
        }

        boolean isAvailable() { return jepLib != null && sitePkgs != null }
    }
}
