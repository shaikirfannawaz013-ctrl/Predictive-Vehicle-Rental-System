"""FleetIQ ML service: demand, 7-day forecast, predictive maintenance and customer risk.

JSON uses camelCase to match the Spring Boot client (com.fleetiq.ml.MlDtos).
"""
import json
from contextlib import asynccontextmanager
import os
from datetime import date, timedelta
from typing import Dict, List, Optional

import joblib
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel

from app.synthetic import VEHICLE_TYPES
from app.train import MODEL_DIR, train_all

models: dict = {}
info: dict = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not os.path.exists(os.path.join(MODEL_DIR, "info.json")):
        train_all()
    for name in ("demand", "forecast", "maintenance", "risk"):
        models[name] = joblib.load(os.path.join(MODEL_DIR, f"{name}.joblib"))
    with open(os.path.join(MODEL_DIR, "info.json")) as f:
        info.update(json.load(f))
    yield


app = FastAPI(title="FleetIQ ML service", version="1.0", lifespan=lifespan)


def version(name: str) -> str:
    return f"{name}-gbm-{info.get('version', 'v1')}"


# ---------- schemas ----------
class DemandFeatures(BaseModel):
    zoneId: str
    searchesLastHour: int
    availableSupply: int
    upcomingBookings: int
    hour: int
    dayOfWeek: int
    weekend: bool


class DemandRequest(BaseModel):
    zones: List[DemandFeatures]


class ForecastRequest(BaseModel):
    startDate: date
    days: int = 7
    recentDailyAverage: Dict[str, float] = {}


class MaintenanceFeatures(BaseModel):
    vehicleId: int
    odometerKm: int
    kmSinceService: int
    daysSinceService: int
    ageMonths: int
    tripsLast30Days: int
    electric: bool


class MaintenanceRequest(BaseModel):
    vehicles: List[MaintenanceFeatures]


class RiskFeatures(BaseModel):
    customerId: int
    totalBookings: int
    lateReturns: int
    damageClaims: int
    paymentFailures: int
    avgOverdueHours: float = 0.0


class RiskRequest(BaseModel):
    customers: List[RiskFeatures]


# ---------- endpoints ----------
@app.get("/health")
def health():
    return {"status": "UP", "models": sorted(models), "version": info.get("version")}


@app.get("/model/info")
def model_info():
    return info


@app.post("/predict/demand")
def predict_demand(req: DemandRequest):
    if not req.zones:
        return {"predictions": [], "modelVersion": version("demand")}
    X = np.array([[z.searchesLastHour, z.availableSupply, z.upcomingBookings, z.hour, z.dayOfWeek, int(z.weekend)]
                  for z in req.zones])
    y = np.clip(models["demand"].predict(X), 0, 1)
    return {"predictions": [{"zoneId": z.zoneId, "demand": round(float(d), 3)} for z, d in zip(req.zones, y)],
            "modelVersion": version("demand")}


@app.post("/predict/forecast")
def predict_forecast(req: ForecastRequest):
    days = []
    for i in range(max(1, min(req.days, 14))):
        d = req.startDate + timedelta(days=i)
        dow = d.isoweekday()
        weekend = int(dow >= 5)
        X = np.array([[dow, weekend, t, req.recentDailyAverage.get(name, 0.0)]
                      for t, name in enumerate(VEHICLE_TYPES)])
        pred = np.maximum(models["forecast"].predict(X), 0)
        days.append({"date": d.isoformat(), "day": d.strftime("%a"),
                     "bookings": {name: int(round(p)) for name, p in zip(VEHICLE_TYPES, pred)}})
    return {"days": days, "modelVersion": version("forecast")}


def _component(v: MaintenanceFeatures) -> str:
    if v.electric:
        return "Battery cooling" if v.odometerKm > 20_000 else "Tyres"
    if v.kmSinceService > 9_000:
        return "Engine oil"
    if v.odometerKm > 90_000:
        return "Brake pads"
    if v.daysSinceService > 120:
        return "Clutch plate"
    return "Tyres"


@app.post("/predict/maintenance")
def predict_maintenance(req: MaintenanceRequest):
    if not req.vehicles:
        return {"predictions": [], "modelVersion": version("maintenance")}
    X = np.array([[v.odometerKm, v.kmSinceService, v.daysSinceService, v.ageMonths, v.tripsLast30Days,
                   int(v.electric)] for v in req.vehicles])
    probs = models["maintenance"].predict_proba(X)[:, 1]
    out = []
    for v, p in zip(req.vehicles, probs):
        p = float(np.clip(p, 0.01, 0.98))
        out.append({"vehicleId": v.vehicleId, "failureProbability": round(p, 2),
                    "healthScore": int(round((1 - p) * 100)), "component": _component(v),
                    "dueInDays": max(1, int(round((1 - p) * 45)))})
    return {"predictions": out, "modelVersion": version("maintenance")}


@app.post("/predict/risk")
def predict_risk(req: RiskRequest):
    if not req.customers:
        return {"predictions": [], "modelVersion": version("risk")}
    X = np.array([[c.totalBookings, c.lateReturns, c.damageClaims, c.paymentFailures, c.avgOverdueHours,
                   c.lateReturns / c.totalBookings if c.totalBookings else 0.0] for c in req.customers])
    probs = models["risk"].predict_proba(X)[:, 1]
    return {"predictions": [{"customerId": c.customerId, "riskProbability": round(float(p), 3),
                             "score": int(round(float(p) * 100))} for c, p in zip(req.customers, probs)],
            "modelVersion": version("risk")}
