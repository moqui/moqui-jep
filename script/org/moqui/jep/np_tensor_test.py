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

try:
    import numpy as np
except Exception as e:
    result["ok"] = False
    result["pythonVersion"] = sys.version.split()[0]
    result["numpyVersion"] = f"ERROR: {e}"
    result["error"] = repr(e)
else:
    a = np.array([[1.0, 2.0], [3.0, 4.0]], dtype=np.float64)
    b = np.array([[5.0, 6.0], [7.0, 8.0]], dtype=np.float64)

    result["ok"] = True
    result["pythonVersion"] = sys.version.split()[0]
    result["numpyVersion"] = np.__version__
    result["a"] = a.tolist()
    result["b"] = b.tolist()
    result["add"] = (a + b).tolist()
    result["dot"] = np.dot(a, b).tolist()
    result["matmul"] = (a @ b).tolist()
