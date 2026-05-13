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
package org.moqui.impl.context.runner

import groovy.transform.CompileStatic
import jep.Interpreter
import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ScriptRunner
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.tools.JepToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.cache.Cache
import java.util.concurrent.locks.ReentrantLock

@CompileStatic
class PythonScriptRunner implements ScriptRunner {
    protected final static Logger logger = LoggerFactory.getLogger(PythonScriptRunner.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Cache<String, String> scriptPythonLocationCache
    private final ReentrantLock loadPythonLock = new ReentrantLock()

    @Override
    ScriptRunner init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.scriptPythonLocationCache = ecfi.cacheFacade.getCache("resource.python.location", String.class, String.class)
        return this
    }

    @Override
    void destroy() { }

    @Override
    Object run(String location, String method, ExecutionContext ec) {
        if (method) {
            logger.warn("Tried to invoke Python script at [${location}] with method [${method}], but Jep ignores method names.",
                    new BaseException("Python Script Run Location"))
        }

        Interpreter interpreter = null
        try {
            interpreter = getJepToolFactory().getInterpreter()
            for (Map.Entry<String, Object> ce in ec.getContext().entrySet()) interpreter.set(ce.getKey(), ce.getValue())
            interpreter.set("ec", ec)
            interpreter.set("context", ec.getContext())
            interpreter.set("ecfi", ecfi)
            interpreter.exec(getPythonByLocation(location))
            return interpreter.getValue("result", Object.class)
        } catch (IllegalStateException e) {
            throw new BaseException("Python/JEP is not available for script [${location}]: ${e.message}", e)
        } finally {
            try { interpreter?.close() } catch (Throwable ignore) { }
        }
    }

    protected JepToolFactory getJepToolFactory() {
        def toolFactory = ecfi.getToolFactory(JepToolFactory.TOOL_NAME)
        if (toolFactory == null) {
            throw new IllegalStateException("ToolFactory '${JepToolFactory.TOOL_NAME}' not found. Make sure the moqui-jep component is built and on the runtime classpath.")
        }
        return (JepToolFactory) toolFactory.getInstance()
    }

    String getPythonByLocation(String location) {
        String scriptText = scriptPythonLocationCache.get(location)
        if (scriptText == null) scriptText = loadPython(location)
        return scriptText
    }

    private String loadPython(String location) {
        loadPythonLock.lock()
        try {
            String scriptText = scriptPythonLocationCache.get(location)
            if (scriptText == null) {
                scriptText = ecfi.resourceFacade.getLocationText(location, false)
                scriptPythonLocationCache.put(location, scriptText)
            }
            return scriptText
        } finally {
            loadPythonLock.unlock()
        }
    }
}
