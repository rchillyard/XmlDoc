#!/usr/bin/env python3
"""Summarize raw benchmark data; standard library only. No threshold adjustments."""
import json
import math
import statistics
import sys
from pathlib import Path


def solve(a, b):
    matrix = [list(row) + [value] for row, value in zip(a, b)]
    pivots = []
    for col in range(3):
        pivot = max(range(col, 3), key=lambda row: abs(matrix[row][col]))
        matrix[col], matrix[pivot] = matrix[pivot], matrix[col]
        divisor = matrix[col][col]
        if abs(divisor) < 1e-10:
            return None
        pivots.append(abs(divisor))
        matrix[col] = [x / divisor for x in matrix[col]]
        for row in range(3):
            if row != col:
                factor = matrix[row][col]
                matrix[row] = [x - factor * y for x, y in zip(matrix[row], matrix[col])]
    return [row[-1] for row in matrix], max(pivots) / min(pivots)


def summary(document):
    groups = []
    x, y = [], []
    for run in document["runs"]:
        row = {key: run[key] for key in ("nodes", "budget", "seed", "textBytesPerLeaf", "operations", "rejections")}
        for metric in ("parseMs", "matchMs", "mergeMs", "writeMs", "totalMs", "sampledHeapPeakMiB", "poolHeapPeakMiB"):
            values = sorted(m[metric] for m in run["measurements"])
            row[metric] = dict(min=values[0], median=statistics.median(values),
                               p95=values[math.ceil(.95 * len(values)) - 1], max=values[-1])
        row["E"] = sorted({m["E"] for m in run["measurements"]})
        assert all(m["correct"] for m in run["measurements"])
        groups.append(row)
        if run["textBytesPerLeaf"] == 0:
            for m in run["measurements"]:
                x.append([1., run["nodes"] / 512., m["E"] / 128.])
                y.append(m["mergeMs"])
    fit = solve([[sum(row[i] * row[j] for row in x) for j in range(3)] for i in range(3)],
                [sum(row[i] * value for row, value in zip(x, y)) for i in range(3)])
    model = {"status": "rank-deficient"}
    if fit:
        coefficients, pivot_ratio = fit
        predicted = [sum(a * b for a, b in zip(row, coefficients)) for row in x]
        sse = sum((a - b) ** 2 for a, b in zip(y, predicted))
        sst = sum((value - statistics.mean(y)) ** 2 for value in y)
        model = dict(status="fit", c0=coefficients[0], c1=coefficients[1] / 512.,
                     c2=coefficients[2] / 128., residualRMSE=math.sqrt(sse / len(y)),
                     rSquared=1 - sse / sst if sst else None, scaledPivotRatio=pivot_ratio,
                     samples=len(y), note="merge/rebuild only; long text excluded; observational fit, not complexity proof")
    return dict(sourceCommit=document["sourceCommit"], sourceSha256=document["sourceSha256"],
                environment=document["environment"], model=model, scenarios=groups)


if __name__ == "__main__":
    source, target = map(Path, sys.argv[1:])
    target.write_text(json.dumps(summary(json.loads(source.read_text())), indent=2) + "\n")
