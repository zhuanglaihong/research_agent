"""Reproducible CPU baseline; human-authored, no LLM or network request."""
import argparse
import copy
import hashlib
import json
import platform
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import sklearn
from sklearn.datasets import load_digits
from sklearn.linear_model import SGDClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, log_loss
from sklearn.model_selection import train_test_split


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default=".")
    parser.add_argument("--demo-json", default=None)
    args = parser.parse_args()
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    dataset = load_digits()
    x = dataset.data.astype(np.float64) / 16.0
    y = dataset.target
    indices = np.arange(len(y))
    train_val, test = train_test_split(indices, test_size=0.2, stratify=y, random_state=2026)
    train, validation = train_test_split(train_val, test_size=0.25, stratify=y[train_val], random_state=2026)
    classes = np.arange(10)
    runs = []
    curves = []
    for seed in (42, 43, 44):
        model = SGDClassifier(loss="log_loss", alpha=0.0001, learning_rate="constant",
                              eta0=0.01, random_state=seed)
        rng = np.random.default_rng(seed)
        best_loss, best_model, best_epoch, stale = float("inf"), None, 0, 0
        rows = []
        with (output / f"metrics_seed{seed}.jsonl").open("w", encoding="utf-8") as log:
            for epoch in range(1, 61):
                order = rng.permutation(train)
                model.partial_fit(x[order], y[order], classes=classes)
                validation_loss = float(log_loss(y[validation], model.predict_proba(x[validation]), labels=classes))
                row = {"step": epoch, "loss": validation_loss,
                       "accuracy": float(accuracy_score(y[validation], model.predict(x[validation])))}
                rows.append(row)
                log.write(json.dumps(row) + "\n")
                log.flush()
                if seed == 42:
                    (output / "metrics.jsonl").write_text("".join(json.dumps(item) + "\n" for item in rows), encoding="utf-8")
                if validation_loss < best_loss - 0.0001:
                    best_loss, best_model, best_epoch, stale = validation_loss, copy.deepcopy(model), epoch, 0
                else:
                    stale += 1
                if stale >= 5:
                    break
        prediction = best_model.predict(x[test])
        test_accuracy = float(accuracy_score(y[test], prediction))
        runs.append({"seed": seed, "epochs": len(rows), "bestEpoch": best_epoch,
                     "validationLoss": best_loss, "testAccuracy": test_accuracy})
        curves.append({"seed": seed, "points": rows})
        if seed == 42:
            fig, ax = plt.subplots(figsize=(6, 5))
            image = ax.imshow(confusion_matrix(y[test], prediction, labels=classes), cmap="Blues")
            ax.set(xlabel="Predicted class", ylabel="True class", xticks=classes, yticks=classes)
            fig.colorbar(image, ax=ax)
            fig.tight_layout()
            fig.savefig(output / "confusion-matrix.png", dpi=180)
            plt.close(fig)
    accuracies = np.array([run["testAccuracy"] for run in runs])
    result = {"dataset": "scikit-learn Digits", "samples": len(y), "classes": 10,
              "split": {"train": len(train), "validation": len(validation), "test": len(test), "seed": 2026},
              "model": "SGDClassifier(log_loss)", "runs": runs, "curves": curves,
              "testAccuracyMean": float(accuracies.mean()), "testAccuracyStd": float(accuracies.std(ddof=1)),
              "environment": {"python": platform.python_version(), "numpy": np.__version__,
                              "scikitLearn": sklearn.__version__, "matplotlib": matplotlib.__version__},
              "codeSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "datasetSha256": hashlib.sha256(dataset.data.tobytes() + y.tobytes()).hexdigest(),
              "provenance": "Human-authored baseline executed locally. No LLM generation or paper reproduction claim."}
    (output / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    if args.demo_json:
        destination = Path(args.demo_json)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    fig, axes = plt.subplots(1, 2, figsize=(10, 4))
    for curve in curves:
        rows = curve["points"]
        axes[0].plot([r["step"] for r in rows], [r["loss"] for r in rows], label=f"seed {curve['seed']}")
        axes[1].plot([r["step"] for r in rows], [r["accuracy"] for r in rows], label=f"seed {curve['seed']}")
    for ax, title in zip(axes, ("Validation log loss", "Validation accuracy")):
        ax.set(xlabel="Epoch", title=title)
        ax.legend()
        ax.grid(alpha=0.2)
    fig.tight_layout()
    fig.savefig(output / "training-curves.svg")
    plt.close(fig)
    report = "# Digits baseline: measured results\n\n"
    report += f"Test accuracy mean: {accuracies.mean():.6f}; sample std: {accuracies.std(ddof=1):.6f}.\n\n"
    report += "Train/validation/test: " + str(result["split"]) + "\n\n"
    report += "\n".join(str(run) for run in runs)
    report += "\n\nThree optimizer seeds share one fixed data split. This does not estimate generalization across datasets.\n"
    (output / "report.md").write_text(report, encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("runs", "testAccuracyMean", "testAccuracyStd", "environment")}, indent=2))


if __name__ == "__main__":
    main()
