# This software is in the public domain under CC0 1.0 Universal plus a
# Grant of Patent License.
#
# To the extent possible under law, the author(s) have dedicated all
# copyright and related and neighboring rights to this software to the
# public domain worldwide. This software is distributed without any
# warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication
# along with this software (see the LICENSE.md file). If not, see
# <http://creativecommons.org/publicdomain/zero/1.0/>.

from jep import findClass

LoggerFactory = findClass('org.slf4j.LoggerFactory')
ComparisonOperator = findClass('org.moqui.entity.EntityCondition$ComparisonOperator')
JoinOperator = findClass('org.moqui.entity.EntityCondition$JoinOperator')
ArrayList = findClass('java.util.ArrayList')
BigDecimal = findClass('java.math.BigDecimal')

logger = LoggerFactory.getLogger("python.findPartyView")


def ctx(name, default=None):
    return context.get(name) if ('context' in globals() and context is not None) else default


def like_value(val, leading_wildcard):
    if val is None:
        return None
    return (("%" if leading_wildcard else "") + str(val) + "%")


def like_cond(cf, field, value, ignore_case=True, leading_wildcard=False):
    if value in (None, ""):
        return None
    cond = cf.makeCondition(field, ComparisonOperator.LIKE, like_value(value, leading_wildcard))
    if ignore_case:
        try:
            cond = cond.ignoreCase()
        except Exception:
            pass
    return cond


try:
    ec_local = context.get("ec") if context is not None and context.containsKey("ec") else ec
    efac = ec_local.getEntity()
    cf = efac.getConditionFactory()

    leadingWildcard = bool(ctx('leadingWildcard', False))
    partyId = ctx('partyId')
    pseudoId = ctx('pseudoId')
    partyTypeEnumId = ctx('partyTypeEnumId')
    disabled = ctx('disabled')
    customerStatusId = ctx('customerStatusId')
    hasDuplicates = ctx('hasDuplicates')
    roleTypeId = ctx('roleTypeId')
    username = ctx('username')

    combinedName = ctx('combinedName')
    organizationName = ctx('organizationName')
    firstName = ctx('firstName')
    lastName = ctx('lastName')
    suffix = ctx('suffix')

    address1 = ctx('address1')
    address2 = ctx('address2')
    city = ctx('city')
    stateProvinceGeoId = ctx('stateProvinceGeoId')
    postalCode = ctx('postalCode')

    countryCode = ctx('countryCode')
    areaCode = ctx('areaCode')
    contactNumber = ctx('contactNumber')
    emailAddress = ctx('emailAddress')
    assetSerialNumber = ctx('assetSerialNumber')

    orderByField = ctx('orderByField')
    pageNoLimit = bool(ctx('pageNoLimit', False))
    pageIndex = int(ctx('pageIndex', 0) or 0)
    pageSize = int(ctx('pageSize', 20) or 20)

    ef = efac.find("mantle.party.FindPartyView").distinct(True)
    ef.selectField("partyId")

    if partyId:
        ef.condition(like_cond(cf, "partyId", partyId, True, leadingWildcard))
    if pseudoId:
        ef.condition(like_cond(cf, "pseudoId", pseudoId, True, leadingWildcard))
    if partyTypeEnumId:
        ef.condition("partyTypeEnumId", partyTypeEnumId)
    if disabled:
        ef.condition("disabled", disabled)
    if customerStatusId:
        ef.condition("customerStatusId", customerStatusId)
    if hasDuplicates:
        ef.condition("hasDuplicates", hasDuplicates)
    if roleTypeId:
        ef.condition("roleTypeId", roleTypeId)
    if username:
        ef.condition(like_cond(cf, "username", username, True, leadingWildcard))

    if combinedName:
        fnSplit = combinedName
        lnSplit = combinedName
        space_idx = combinedName.find(" ")
        if space_idx >= 0:
            fnSplit = combinedName[:space_idx]
            lnSplit = combinedName[space_idx + 1:]
        cn_list = ArrayList()
        c1 = like_cond(cf, "organizationName", combinedName, True, leadingWildcard)
        c2 = like_cond(cf, "firstName", fnSplit, True, leadingWildcard)
        c3 = like_cond(cf, "lastName", lnSplit, True, leadingWildcard)
        if c1:
            cn_list.add(c1)
        if c2:
            cn_list.add(c2)
        if c3:
            cn_list.add(c3)
        if not cn_list.isEmpty():
            ef.condition(cf.makeCondition(cn_list, JoinOperator.OR))

    if organizationName:
        ef.condition(like_cond(cf, "organizationName", organizationName, True, leadingWildcard))
    if firstName:
        ef.condition(like_cond(cf, "firstName", firstName, True, leadingWildcard))
    if lastName:
        ef.condition(like_cond(cf, "lastName", lastName, True, leadingWildcard))
    if suffix:
        ef.condition(like_cond(cf, "suffix", suffix, True, leadingWildcard))

    if address1:
        ef.condition(like_cond(cf, "address1", address1, True, leadingWildcard))
    if address2:
        ef.condition(like_cond(cf, "address2", address2, True, leadingWildcard))
    if city:
        ef.condition(like_cond(cf, "city", city, True, leadingWildcard))
    if stateProvinceGeoId:
        ef.condition("stateProvinceGeoId", stateProvinceGeoId)
    if postalCode:
        ef.condition(like_cond(cf, "postalCode", postalCode, True, leadingWildcard))

    if countryCode:
        ef.condition("countryCode", countryCode)
    if areaCode:
        ef.condition("areaCode", areaCode)
    if contactNumber:
        ef.condition(cf.makeCondition("contactNumber", ComparisonOperator.LIKE, like_value(contactNumber, leadingWildcard)))
    if emailAddress:
        ef.condition(like_cond(cf, "emailAddress", emailAddress, True, leadingWildcard))
    if assetSerialNumber:
        ef.condition(like_cond(cf, "assetSerialNumber", assetSerialNumber, True, leadingWildcard))

    if orderByField:
        obf = str(orderByField)
        if "combinedName" in obf:
            if "-" in obf:
                ef.orderBy("-organizationName,-firstName,-lastName")
            else:
                ef.orderBy("organizationName,firstName,lastName")
        else:
            ef.orderBy(orderByField)

    if not pageNoLimit:
        ef.offset(pageIndex, pageSize)
        ef.limit(pageSize)

    partyIdList = ArrayList()
    el = ef.list()
    it = el.iterator()
    while it.hasNext():
        ev = it.next()
        partyIdList.add(ev.get("partyId"))

    partyIdListCount = ef.count()
    partyIdListPageIndex = ef.getPageIndex()
    partyIdListPageSize = ef.getPageSize()
    bd_count_minus_1 = BigDecimal.valueOf(partyIdListCount - 1 if partyIdListCount > 0 else 0)
    bd_page_size = BigDecimal.valueOf(partyIdListPageSize if partyIdListPageSize > 0 else 1)
    partyIdListPageMaxIndex = int(bd_count_minus_1.divide(bd_page_size, 0, BigDecimal.ROUND_DOWN).intValue())

    partyIdListPageRangeLow = partyIdListPageIndex * partyIdListPageSize + 1
    partyIdListPageRangeHigh = partyIdListPageIndex * partyIdListPageSize + partyIdListPageSize
    if partyIdListPageRangeHigh > partyIdListCount:
        partyIdListPageRangeHigh = partyIdListCount

    context.put("partyIdList", partyIdList)
    context.put("partyIdListCount", partyIdListCount)
    context.put("partyIdListPageIndex", partyIdListPageIndex)
    context.put("partyIdListPageSize", partyIdListPageSize)
    context.put("partyIdListPageMaxIndex", partyIdListPageMaxIndex)
    context.put("partyIdListPageRangeLow", partyIdListPageRangeLow)
    context.put("partyIdListPageRangeHigh", partyIdListPageRangeHigh)

    result.put("partyIdList", partyIdList)
    result.put("partyIdListCount", partyIdListCount)
    result.put("partyIdListPageIndex", partyIdListPageIndex)
    result.put("partyIdListPageSize", partyIdListPageSize)
    result.put("partyIdListPageMaxIndex", partyIdListPageMaxIndex)
    result.put("partyIdListPageRangeLow", partyIdListPageRangeLow)
    result.put("partyIdListPageRangeHigh", partyIdListPageRangeHigh)
except Exception as e:
    logger.error(f"Error in test_find_party.py: {e}")
    raise
