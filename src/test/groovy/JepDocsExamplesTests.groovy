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

import jep.DirectNDArray
import jep.Interpreter
import jep.NDArray
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.impl.tools.JepToolFactory
import spock.lang.Shared
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer

class JepDocsExamplesTests extends Specification {
    @Shared ExecutionContext ec
    @Shared JepToolFactory jepTool

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("john.doe", "moqui")
        jepTool = ec.getTool(JepToolFactory.TOOL_NAME, JepToolFactory.class)
        assert jepTool != null
    }

    def cleanupSpec() {
        if (ec != null) ec.destroy()
    }

    def setup() {
        if (!ec.user.getUserId()) ec.user.loginUser("john.doe", "moqui")
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
        ec.user.logoutUser()
    }

    def "how jep works example supports exec eval getValue invoke and Java objects"() {
        given:
        Number result
        Number invoked
        String userId
        boolean expressionMatches

        when:
        try (Interpreter py = jepTool.getInterpreter(false)) {
            py.exec("import math")
            py.exec("""
def hypotenuse(a, b):
    return math.sqrt(a * a + b * b)
""")
            py.set("left", 5)
            py.set("right", 12)
            py.set("ec", ec)
            py.exec("result = hypotenuse(left, right)")
            py.exec("current_user = ec.getUser().getUserId()")

            result = py.getValue("result", Number.class)
            invoked = py.invoke("hypotenuse", 8, 15) as Number
            userId = py.getValue("current_user", String.class)
            expressionMatches = py.eval("result == 13.0 and current_user is not None")
        }

        then:
        expressionMatches
        Math.abs(result.doubleValue() - 13.0d) < 0.0001d
        Math.abs(invoked.doubleValue() - 17.0d) < 0.0001d
        userId == ec.user.userId
    }

    def "how jep works example keeps globals isolated across interpreter sessions"() {
        given:
        Number firstValue
        String secondValue

        when:
        try (Interpreter py = jepTool.getInterpreter(true)) {
            py.exec("counter = 42")
            firstValue = py.getValue("counter", Number.class)
        }
        try (Interpreter py = jepTool.getInterpreter(true)) {
            secondValue = py.getValue("globals().get('counter', 'missing')", String.class)
        }

        then:
        firstValue.intValue() == 42
        secondValue == "missing"
    }

    def "numpy usage example converts NDArray into numpy ndarray"() {
        given:
        int[] values = [1, 2, 3, 4, 5, 6] as int[]
        NDArray matrix = new NDArray(values, 2, 3)
        List shape
        List doubled
        Number total
        String dtypeName

        when:
        try (Interpreter py = jepTool.getInterpreter(false)) {
            py.exec("import numpy as np")
            py.set("matrix", matrix)

            shape = py.getValue("list(matrix.shape)", List.class)
            doubled = py.getValue("(matrix * 2).tolist()", List.class)
            total = py.getValue("int(matrix.sum())", Number.class)
            dtypeName = py.getValue("matrix.dtype.name", String.class)
        }

        then:
        shape == [2, 3]
        doubled == [[2, 4, 6], [8, 10, 12]]
        total.intValue() == 21
        dtypeName
    }

    def "numpy usage example shares a direct buffer with python through DirectNDArray"() {
        given:
        double[] values = [1.0d, 2.0d, 3.0d, 4.0d] as double[]
        DoubleBuffer buffer = ByteBuffer.allocateDirect(values.length * Double.BYTES)
                .order(ByteOrder.nativeOrder())
                .asDoubleBuffer()
        buffer.put(values)
        DirectNDArray matrix = new DirectNDArray(buffer.rewind() as DoubleBuffer, 2, 2)
        Number pythonSum

        when:
        try (Interpreter py = jepTool.getInterpreter(false)) {
            py.exec("import numpy as np")
            py.set("matrix", matrix)

            pythonSum = py.getValue("float(matrix.sum())", Number.class)
            py.exec("matrix[1, 1] = 99.0")
        }

        then:
        Math.abs(pythonSum.doubleValue() - 10.0d) < 0.0001d
        Math.abs(buffer.get(3) - 99.0d) < 0.0001d
    }

    def "interactive console style example preserves state across commands"() {
        given:
        Number area
        List history
        boolean evalResult

        when:
        try (Interpreter py = jepTool.getInterpreter(false)) {
            py.exec("history = []")
            py.exec("import math")
            py.exec("history.append('import math')")
            py.exec("radius = 3")
            py.exec("history.append('radius = 3')")
            py.exec("""
def circle_area(r):
    return round(math.pi * r * r, 4)
""")
            py.exec("history.append('def circle_area(r): ...')")

            area = py.getValue("circle_area(radius)", Number.class)
            history = py.getValue("history", List.class)
            evalResult = py.eval("circle_area(radius) > 28 and len(history) == 3")
        }

        then:
        evalResult
        Math.abs(area.doubleValue() - 28.2743d) < 0.0001d
        history == ["import math", "radius = 3", "def circle_area(r): ..."]
    }
}
