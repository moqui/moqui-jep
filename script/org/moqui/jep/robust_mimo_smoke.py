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

"""Robust MIMO smoke test inspired by python-control robust_mimo example.

Based on the official example:
https://python-control.readthedocs.io/en/latest/examples/robust_mimo.html

The analysis part is always exercised. H-infinity synthesis is attempted when
the local python-control install supports it; otherwise the reason is returned
in synthesisError without failing the overall smoke test.
"""

import sys
import warnings

try:
    import numpy as np
    import control as ct
except Exception as e:
    result["ok"] = False
    result["analysisOk"] = False
    result["pythonVersion"] = sys.version.split()[0]
    result["error"] = repr(e)
else:
    def weighting(wb, m, a):
        s = ct.tf([1, 0], [1])
        return (s / m + wb) / (s + wb * a)


    def plant():
        # Direct state-space realization of the plant used in the
        # python-control robust_mimo example. This avoids the MIMO tf->ss
        # conversion path that requires the optional slycot package.
        a = np.array([
            [0.0, 1.0, 0.0, 0.0],
            [-5.0, -6.0, 0.0, 0.0],
            [0.0, 0.0, 0.0, 1.0],
            [0.0, 0.0, -5.0, -6.0],
        ])
        b = np.array([
            [0.0, 0.0],
            [1.0, 0.0],
            [0.0, 0.0],
            [0.0, 1.0],
        ])
        c = np.array([
            [5.0, 0.0, 5.0, 0.0],
            [5.0, 10.0, 10.0, 0.0],
        ])
        d = np.zeros((2, 2))
        return ct.ss(a, b, c, d)


    def triv_sigma(g, w):
        magnitude, phase, _ = g.frequency_response(w)
        sjw = (magnitude * np.exp(1j * phase)).transpose(2, 0, 1)
        return np.linalg.svd(sjw, compute_uv=False)


    def step_opposite(g, t):
        _, yu1 = ct.step_response(g, t, input=0, squeeze=True)
        _, yu2 = ct.step_response(g, t, input=1, squeeze=True)
        return yu1 - yu2


    g = plant()
    t = np.linspace(0.0, 10.0, 101)
    _, yu1 = ct.step_response(g, t, input=0, squeeze=True)
    _, yu2 = ct.step_response(g, t, input=1, squeeze=True)
    yuz = yu1 - yu2

    w = np.logspace(-2, 2, 21)
    sv = triv_sigma(g, w)

    result["pythonVersion"] = sys.version.split()[0]
    result["numpyVersion"] = np.__version__
    result["controlVersion"] = getattr(ct, "__version__", "unknown")
    result["input10Final"] = [float(yu1[0, -1]), float(yu1[1, -1])]
    result["input01Final"] = [float(yu2[0, -1]), float(yu2[1, -1])]
    result["oppositeFinal"] = [float(yuz[0, -1]), float(yuz[1, -1])]
    result["sigmaAtLowFreq"] = [float(sv[0, 0]), float(sv[0, 1])]
    result["sigmaAtHighFreq"] = [float(sv[-1, 0]), float(sv[-1, 1])]
    result["analysisOk"] = bool(np.all(np.isfinite(sv)))

    try:
        identity = ct.ss([], [], [], np.eye(2))
        wu = ct.ss([], [], [], np.eye(2))
        wp1 = ct.ss(weighting(wb=0.25, m=1.5, a=1e-4))
        wp2 = ct.ss(weighting(wb=0.25, m=1.5, a=1e-4))
        wp_fast = ct.ss(weighting(wb=25, m=1.5, a=1e-4))

        with warnings.catch_warnings():
            warnings.filterwarnings(
                "ignore",
                message=r"connect\(\) is deprecated; use interconnect\(\)",
                category=FutureWarning,
            )
            _, _, info1 = ct.mixsyn(g, wp1.append(wp2), wu)
            k2, _, info2 = ct.mixsyn(g, wp1.append(wp_fast), wu)

        closed_loop = (g * k2).feedback(identity)
        opposite_fast = step_opposite(closed_loop, t)

        result["synthesisAvailable"] = True
        result["gamma1"] = float(info1[0])
        result["gamma2"] = float(info2[0])
        result["oppositeFastFinal"] = [float(opposite_fast[0, -1]), float(opposite_fast[1, -1])]
    except Exception as e:
        result["synthesisAvailable"] = False
        result["synthesisError"] = repr(e)

    result["ok"] = True
