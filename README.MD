FleetIQ — Predictive Vehicle Rental & Fleet Intelligence System

A vehicle rental platform that predicts demand and maintenance instead of only recording rentals. Example: 20 people search for SUVs around Tirupati this weekend → the demand score for that zone rises → SUV prices there go up, and the allocation screen suggests moving idle SUVs in from quieter zones.

fleetiq/
├── backend/      Spring Boot 3.3 · Java 21 · Spring Security (JWT) · JPA + Flyway · PostgreSQL · Redis · Kafka
├── ml-service/   FastAPI · scikit-learn gradient-boosting models (demand, forecast, maintenance, risk)
├── frontend/     React 18 + Vite (customer app + fleet-manager dashboard)
└── docker-compose.yml
Run it

Everything in Docker (needs Docker Desktop):

bash
docker compose up --build

Open http://localhost:3000. API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html · ML service docs: http://localhost:8000/docs

Login	Password	Role
admin@fleetiq.in	admin123	Fleet manager (all admin screens)
demo@fleetiq.in	demo1234	Customer
ravi@, anitha@, suresh@, meena@, karthik@, lakshmi@example.in	password123	Customers with rental history

Backend from your IDE (IntelliJ or mvn spring-boot:run): start the infrastructure only, then run the app.

bash
docker compose up postgres redis kafka ml-service
cd backend && mvn spring-boot:run          # defaults point at localhost:5432 / 6379 / 29092 / 8000
cd frontend && npm install && npm run dev  # set VITE_USE_MOCKS=false in frontend/.env

ML service alone: cd ml-service && pip install -r requirements.txt && uvicorn app.main:app --reload (models train on first start if models/ is empty).

Architecture
 React ──REST/JWT──▶ Spring Boot ──JDBC──▶ PostgreSQL (Flyway-managed schema)
   ▲                  │   │   │
   │ SSE              │   │   └──HTTP──▶ Python ML service (falls back to rules if down)
   │                  │   └──────────▶ Redis (search windows, caches, alert de-duplication)
   └──────────────────┴──produce/consume──▶ Kafka
Kafka topics
Topic	Produced when	Consumed by
fleetiq.vehicle-searches	every vehicle search	DemandEventsConsumer → Redis search window + search_events table (training data)
fleetiq.booking-events	booking confirmed / cancelled / started / returned, hold expired	DemandEventsConsumer → invalidates the demand cache
fleetiq.notifications	any notification is saved	NotificationSseListener on every instance (unique consumer group) → pushes to that instance's SSE connections

Events are sent after the database transaction commits, so consumers never see rolled-back data. If Kafka is unreachable, a search is recorded straight into Redis instead, so pricing keeps working.

Redis keys
Key	Type	Purpose
demand:searches:{zone} / :{zone}:{type}	sorted set (score = time)	sliding 1-hour search window per zone and vehicle type
demand:zones	JSON, 30 s	live demand, supply and price multiplier per zone
demand:forecast	JSON, 30 min	7-day booking forecast
maintenance:predictions	JSON, 10 min	fleet health predictions
risk:customers	JSON, 10 min	customer risk scores
analytics:utilization	JSON, 1 min	dashboard numbers
maintenance:alerted:{vehicleId}	flag, 24 h	one maintenance alert per vehicle per day

If Redis is down, every cache falls through to the database/ML call.

How the intelligence works
Dynamic pricing — demand score (0–1) per zone from: searches in the last hour, vehicles parked there, bookings starting in the next 24 h, hour and day of week. multiplier = 1 + 1.1 × (demand − 0.40), capped at 2.0×, plus 10 % for Fri–Sun pickups, plus 18 % GST. Prices are held for 10 minutes while paying.
Availability prediction — search results show the chance a vehicle is still free if the customer waits, from demand pressure in its zone.
Predictive maintenance — failure probability in the next 30 days from odometer, km and days since service, age and recent trips. Recomputed every 6 h; managers get one alert per at-risk vehicle per day.
Customer risk — one SQL aggregation (late returns, damage, failed payments, average hours overdue) scored by the risk model. High-risk customers (≥ 70) pay a double security deposit.
Late-return detection — scheduled job every 5 minutes flags active bookings past their end time plus 30 minutes' grace; ₹250/hour late fee is charged at return.
Location-based allocation — greedy rebalancing: idle vehicles from low-demand zones go to zones where searches far exceed parked vehicles, preferring the type being searched for, ranked by expected extra revenue.
Utilisation analytics — booked vehicle-hours ÷ available vehicle-hours per day (PostgreSQL generate_series + range overlap, in IST).

Every ML call has a rule-based fallback in Java, so the rental flow never depends on the model being up.

Concurrency

Creating a booking locks the vehicle row (SELECT … FOR UPDATE) while checking for overlapping bookings, so two customers can't book the same car for overlapping dates. Unpaid holds expire after 10 minutes and release the car.

API

All endpoints are under /api. Send Authorization: Bearer <token> except for login/register. Errors always return {"message": "..."}.

Method	Path	Who	Purpose
POST	/auth/login, /auth/register	anyone	returns {token, user}
GET	/auth/me	signed in	current user
GET	/vehicles/search?zoneId&type&startTime&endTime	signed in	free vehicles with live price
GET	/vehicles/{id}	signed in	vehicle detail
GET / POST	/vehicles	admin	list the whole fleet / add a vehicle
GET	/pricing/quote?vehicleId&startTime&endTime	signed in	price breakdown with reasons
POST	/bookings	signed in	create (PENDING_PAYMENT, price held 10 min)
GET	/bookings/me	signed in	my bookings
GET	/bookings/{id}	owner / admin	one booking
POST	/bookings/{id}/cancel	owner / admin	cancel, refunds if paid
POST	/bookings/{id}/return	admin	record the return {odometerKm, damageReported}, charges late fee
GET	/bookings/late	admin	overdue rentals with penalty so far
POST	/payments	owner	start payment {bookingId, method, upiId}
POST	/payments/{paymentId}/confirm	owner	verify with the gateway, confirms booking
GET	/predictions/demand	signed in	zones (demand, supply, multiplier) + 7-day forecast
GET	/predictions/maintenance	admin	fleet health, worst first
POST	/maintenance/{vehicleId}/schedule, /complete	admin	take a vehicle into / out of service
GET	/risk/customers	admin	customer risk scores
GET	/analytics/utilization	admin	fleet size, utilisation, revenue, 14-day trend
GET	/allocation/recommendations	admin	suggested vehicle moves
POST	/allocation/{id}/apply	admin	dispatch a move
GET	/notifications · PATCH /notifications/{id}/read	signed in	notification list
GET	/notifications/stream?token=<jwt>	signed in	live notifications (SSE, event name notification)
Notes
Database: PostgreSQL. Several queries use PostgreSQL features (FILTER, generate_series, AT TIME ZONE). Moving to MySQL means rewriting those in RiskService and AnalyticsService and the migrations.
Payments use SimulatedPaymentGateway, which always succeeds (set app.payment.simulated-failure-rate to test declines). PaymentGateway documents what a Razorpay implementation needs.
Demo data: SEED_DATA=true creates users, 30 days of rental history, one overdue booking and a burst of recent searches (Tirupati Central, mostly SUVs). The searches expire after an hour; restart or search from the UI to regenerate demand.
Scaling out: scheduled jobs run on every instance; add ShedLock (Redis or JDBC provider) before running more than one backend instance. SSE already works across instances through Kafka.
Models are trained on synthetic data that encodes the domain assumptions (ml-service/app/synthetic.py). Retrain from the search_events, bookings and payments tables once real history exists.
Set a real JWT_SECRET (base64, 32+ bytes) outside development.