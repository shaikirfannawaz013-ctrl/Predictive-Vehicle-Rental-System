"""Synthetic training data.

Real deployments retrain from the backend's tables (search_events, bookings, payments,
maintenance_records). Until enough history exists, these generators encode the domain
assumptions so the models behave sensibly from day one.
"""
import numpy as np

VEHICLE_TYPES = ["SUV", "SEDAN", "HATCHBACK", "EV", "BIKE"]


def demand(n: int, rng: np.random.Generator):
    searches = rng.poisson(rng.uniform(0, 40, n))
    supply = rng.integers(0, 12, n)
    upcoming = rng.poisson(rng.uniform(0, 6, n))
    hour = rng.integers(0, 24, n)
    dow = rng.integers(1, 8, n)                      # ISO: 1 = Monday
    weekend = (dow >= 6).astype(int)

    pressure = (searches + 2 * upcoming) / (supply + 1)
    y = 1 - np.exp(-pressure / 2)
    y += np.where(((hour >= 7) & (hour <= 11)) | ((hour >= 16) & (hour <= 20)), 0.05, 0)
    y += weekend * 0.08
    y += rng.normal(0, 0.03, n)
    X = np.column_stack([searches, supply, upcoming, hour, dow, weekend])
    return X, np.clip(y, 0, 1)


def forecast(n: int, rng: np.random.Generator):
    dow = rng.integers(1, 8, n)
    type_idx = rng.integers(0, len(VEHICLE_TYPES), n)
    recent_avg = rng.uniform(0, 15, n)
    weekend = (dow >= 5).astype(int)                 # Fri-Sun pilgrim/tourist traffic
    season = np.where(weekend == 1, np.where(type_idx == 0, 1.8, 1.35), np.where(dow == 1, 0.9, 1.0))
    y = rng.poisson(np.maximum(recent_avg * season, 0.3))
    X = np.column_stack([dow, weekend, type_idx, recent_avg])
    return X, y


def maintenance(n: int, rng: np.random.Generator):
    odometer = rng.integers(2_000, 180_000, n)
    km_since = rng.integers(0, 15_000, n)
    days_since = rng.integers(0, 300, n)
    age_months = rng.integers(1, 96, n)
    trips = rng.integers(0, 25, n)
    electric = rng.integers(0, 2, n)

    logit = (-5.0 + km_since / 3_000 + days_since / 90 + odometer / 90_000
             + trips * 0.03 + age_months / 60 - electric * 0.4)
    p = 1 / (1 + np.exp(-logit))
    y = rng.binomial(1, p)
    X = np.column_stack([odometer, km_since, days_since, age_months, trips, electric])
    return X, y


def risk(n: int, rng: np.random.Generator):
    total = rng.integers(0, 30, n)
    late_rate = rng.beta(1.2, 6, n)
    late = rng.binomial(total, late_rate)
    damage = rng.binomial(total, rng.beta(1, 25, n))
    failures = rng.poisson(rng.uniform(0, 1.2, n))
    avg_overdue = np.where(late > 0, rng.gamma(2, 1.8, n), 0)

    observed_rate = np.where(total > 0, late / np.maximum(total, 1), 0)
    logit = -3.2 + observed_rate * 7 + damage * 0.9 + failures * 0.7 + np.minimum(avg_overdue, 12) * 0.12
    y = rng.binomial(1, 1 / (1 + np.exp(-logit)))
    X = np.column_stack([total, late, damage, failures, avg_overdue, observed_rate])
    return X, y
