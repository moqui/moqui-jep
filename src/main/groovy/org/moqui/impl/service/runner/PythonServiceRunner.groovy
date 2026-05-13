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
package org.moqui.impl.service.runner

import groovy.transform.CompileStatic
import org.moqui.BaseException
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import org.moqui.service.ServiceException
import org.moqui.util.ContextStack
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class PythonServiceRunner implements ServiceRunner {
    protected static final Logger logger = LoggerFactory.getLogger(PythonServiceRunner.class)

    private ExecutionContextFactoryImpl ecfi = null

    PythonServiceRunner() { }

    @Override
    ServiceRunner init(ServiceFacadeImpl sfi) {
        ecfi = sfi.ecfi
        return this
    }

    @Override
    Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        if (!sd.location) throw new ServiceException("Service [${sd.serviceName}] is missing location attribute and it is required for running a Python service.")

        ExecutionContextImpl ec = ecfi.getEci()
        ContextStack cs = ec.contextStack

        cs.pushContext()
        try {
            cs.put("ec", ec)
            cs.putAll(parameters)
            Map<String, Object> autoResult = new HashMap<>()
            cs.put("result", autoResult)

            Object result = ec.getResource().script(sd.location, sd.method)

            if (result instanceof Map) {
                return (Map<String, Object>) result
            } else {
                combineResults(sd, autoResult, cs.getCombinedMap())
                return autoResult
            }
        } catch (BaseException e) {
            throw new ServiceException("Error running Python service [${sd.serviceName}].", e)
        } catch (Throwable t) {
            throw new ServiceException("Error or unknown exception in Python service [${sd.serviceName}].", t)
        } finally {
            cs.popContext()
        }
    }

    static void combineResults(ServiceDefinition sd, Map<String, Object> autoResult, Map<String, Object> csMap) {
        boolean autoResultUsed = autoResult.size() > 0
        String[] outParameterNames = sd.outParameterNameArray
        int outParameterNamesSize = outParameterNames.length
        for (int i = 0; i < outParameterNamesSize; i++) {
            String outParameterName = outParameterNames[i]
            Object outValue = csMap.get(outParameterName)
            if ((!autoResultUsed || !autoResult.containsKey(outParameterName)) && outValue != null) {
                autoResult.put(outParameterName, outValue)
            }
        }
    }

    @Override
    void destroy() { }
}
