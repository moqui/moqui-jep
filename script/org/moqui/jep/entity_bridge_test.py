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

out = result

try:
    entity_facade = ec.getEntity()
    current_user_id = ec.getUser().getUserId()
    user = entity_facade.find("moqui.security.UserAccount").condition("userId", current_user_id).useCache(False).one()
    user_groups = entity_facade.find("moqui.security.UserGroupMember").condition("userId", current_user_id).list()

    out["ok"] = user is not None
    out["userId"] = user.getString("userId") if user is not None else None
    out["locale"] = user.getString("locale") if user is not None else None
    out["userGroupCount"] = user_groups.size() if user_groups is not None else 0
    out["userGroupIds"] = [group.getString("userGroupId") for group in user_groups] if user_groups is not None else []
except Exception as e:
    out["ok"] = False
    out["error"] = repr(e)
