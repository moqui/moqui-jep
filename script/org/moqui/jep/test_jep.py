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

import sys

out = result
out["pythonVersion"] = sys.version.split()[0]
out["siteFound"] = next((path for path in sys.path if path.endswith("site-packages")), "")

try:
    import numpy as np

    out["numpyVersion"] = np.__version__
    out["ok"] = True
except Exception as e:
    out["numpyVersion"] = f"ERROR: {e}"
    out["error"] = repr(e)
    out["ok"] = False
