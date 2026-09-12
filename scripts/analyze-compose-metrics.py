import csv
import glob
import os
from collections import Counter, defaultdict

per_module = Counter()
lambdas = Counter()
worst = []
for f in glob.glob(os.path.join("build", "compose-reports", "*", "*-composables.csv")):
    module = os.path.basename(os.path.dirname(f))
    with open(f, encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            per_module[module] += 1
            try:
                skippable = int(row.get("skippable", "1"))
                restartable = int(row.get("restartable", "0"))
                is_lambda = int(row.get("isLambda", "0"))
            except Exception:
                continue
            if is_lambda:
                lambdas[module] += 1
            if restartable and not skippable:
                worst.append((module, row.get("name", "?"), row.get("package", "?"), is_lambda))

print("per module scanned:", dict(per_module))
print("lambdas:", dict(lambdas))
print("restartable+unskippable (incl lambdas):", len(worst))
for w in worst[:20]:
    print(w)
