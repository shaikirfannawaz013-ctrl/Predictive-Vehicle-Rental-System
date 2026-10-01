"""Trains all four models and writes them to MODEL_DIR. Run: python -m app.train"""
import json
import os
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.ensemble import GradientBoostingClassifier, GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, r2_score, roc_auc_score
from sklearn.model_selection import train_test_split

from app import synthetic

MODEL_DIR = os.environ.get("MODEL_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def _fit(name, X, y, model, classifier):
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=7)
    model.fit(X_tr, y_tr)
    if classifier:
        metrics = {"auc": round(float(roc_auc_score(y_te, model.predict_proba(X_te)[:, 1])), 3)}
    else:
        pred = model.predict(X_te)
        metrics = {"r2": round(float(r2_score(y_te, pred)), 3), "mae": round(float(mean_absolute_error(y_te, pred)), 3)}
    joblib.dump(model, os.path.join(MODEL_DIR, f"{name}.joblib"))
    print(f"{name:12s} {metrics}")
    return metrics


def train_all() -> dict:
    os.makedirs(MODEL_DIR, exist_ok=True)
    rng = np.random.default_rng(2026)
    gbr = dict(n_estimators=200, max_depth=3, learning_rate=0.08, random_state=7)
    metrics = {
        "demand": _fit("demand", *synthetic.demand(20_000, rng), GradientBoostingRegressor(**gbr), False),
        "forecast": _fit("forecast", *synthetic.forecast(20_000, rng), GradientBoostingRegressor(**gbr), False),
        "maintenance": _fit("maintenance", *synthetic.maintenance(20_000, rng), GradientBoostingClassifier(**gbr), True),
        "risk": _fit("risk", *synthetic.risk(20_000, rng), GradientBoostingClassifier(**gbr), True),
    }
    info = {"version": "v1-" + datetime.now(timezone.utc).strftime("%Y%m%d"),
            "trainedAt": datetime.now(timezone.utc).isoformat(), "metrics": metrics}
    with open(os.path.join(MODEL_DIR, "info.json"), "w") as f:
        json.dump(info, f, indent=2)
    return info


if __name__ == "__main__":
    train_all()
