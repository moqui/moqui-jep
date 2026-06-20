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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.impl.tools.JepToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Stepwise
class JepServiceTests extends Specification {
    @Shared protected static final Logger logger = LoggerFactory.getLogger(JepServiceTests)

    @Shared ExecutionContext ec
    @Shared JepToolFactory jepTool

    def setupSpec() {
        Moqui.getExecutionContextFactory().checkEmptyDb()
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
        ec.transaction.begin(null)
    }

    def cleanup() {
        try {
            ec.transaction.commit()
        } finally {
            ec.artifactExecution.enableAuthz()
            ec.user.logoutUser()
        }
    }

    def "python service runner reports Python and NumPy versions"() {
        when:
        Map result = runService("org.moqui.jep.JepServices.test#Jep")

        then:
        !ec.message.hasError()
        result.ok
        result.pythonVersion
        result.numpyVersion
        !(result.numpyVersion as String).startsWith("ERROR:")
        (result.siteFound as String).contains("site-packages")
    }

    def "python script runner executes .py scripts from service actions"() {
        when:
        Map result = runService("org.moqui.jep.JepServices.test#PythonScript")

        then:
        !ec.message.hasError()
        result.ok
        result.runner == "script"
    }

    def "python service can run NumPy tensor operations"() {
        when:
        Map result = runService("org.moqui.jep.JepServices.test#NumpyTensor")

        then:
        !ec.message.hasError()
        result.ok
        result.add == [[6.0d, 8.0d], [10.0d, 12.0d]]
        result.dot == [[19.0d, 22.0d], [43.0d, 50.0d]]
        result.matmul == [[19.0d, 22.0d], [43.0d, 50.0d]]
    }

    def "python service can access Moqui entities and lists"() {
        when:
        Map result = runService("org.moqui.jep.JepServices.test#EntityBridge")

        then:
        !ec.message.hasError()
        result.ok
        result.userId == ec.user.userId
        result.userGroupCount instanceof Number
        (result.userGroupCount as Number).intValue() > 0
        (result.userGroupIds as List).size() == (result.userGroupCount as Number).intValue()
    }

    @spock.lang.IgnoreIf({
        try {
            Class.forName("mantle.party.PartyServices")
            return false
        } catch (ClassNotFoundException e) {
            return true
        }
    })
    def "python service can mirror Mantle findParty Groovy logic"() {
        given:
        Map parameters = [
                combinedName   : "John Doe",
                orderByField   : "partyId",
                pageIndex      : 0,
                pageSize       : 20,
                leadingWildcard: false
        ]

        when:
        Map pythonResult = runService("org.moqui.jep.JepServices.find#PartyPy", parameters)
        Map groovyResult = runService("mantle.party.PartyServices.find#Party", parameters)

        then:
        !ec.message.hasError()
        (pythonResult.partyIdList as List) == (groovyResult.partyIdList as List)
        pythonResult.partyIdListCount == groovyResult.partyIdListCount
        pythonResult.partyIdListPageIndex == groovyResult.partyIdListPageIndex
        pythonResult.partyIdListPageSize == groovyResult.partyIdListPageSize
        pythonResult.partyIdListPageMaxIndex == groovyResult.partyIdListPageMaxIndex
        pythonResult.partyIdListPageRangeLow == groovyResult.partyIdListPageRangeLow
        pythonResult.partyIdListPageRangeHigh == groovyResult.partyIdListPageRangeHigh
        (pythonResult.partyIdList as List).contains("EX_JOHN_DOE")
    }

    def "python-control robust mimo smoke test runs inside JEP"() {
        when:
        Map result = runService("org.moqui.jep.JepServices.test#ControlRobustMimo")

        then:
        !ec.message.hasError()
        result.ok
        result.analysisOk
        result.controlVersion
        (result.input10Final as List).size() == 2
        (result.input01Final as List).size() == 2
        (result.oppositeFinal as List).size() == 2
        (result.sigmaAtLowFreq as List).size() == 2
        (result.sigmaAtHighFreq as List).size() == 2

        and:
        if (result.synthesisAvailable) {
            assert result.gamma1 instanceof Number
            assert result.gamma2 instanceof Number
            assert (result.gamma1 as Number).doubleValue() > 0.0d
            assert (result.gamma2 as Number).doubleValue() > 0.0d
        } else {
            assert result.synthesisError
        }
    }

    private Map runService(String serviceName, Map parameters = [:]) {
        logger.info("Calling JEP test service {}", serviceName)
        return ec.service.sync().name(serviceName).parameters(parameters).disableAuthz().call()
    }
}
