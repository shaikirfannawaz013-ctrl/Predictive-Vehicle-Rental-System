# FleetIQ — Frontend (React)

React frontend for the **Predictive Vehicle Rental & Fleet Intelligence System**.
It talks to the Spring Boot API over REST (JWT auth) and receives live notifications over
Server-Sent Events that the backend fans out from Kafka.

## Run it

```bash
npm install
cp .env.example .env      # VITE_USE_MOCKS=true runs without any backend
npm run dev               # http://localhost:5173
```

Demo mode: any password works; an email containing `admin` (e.g. `admin@fleetiq.in`)
opens the fleet manager screens, anything else opens the customer screens.

To use the real backend set `VITE_USE_MOCKS=false`. In dev, `/api` is proxied to
`http://localhost:8080` (see `vite.config.js`), so no CORS config is needed.

## Screens

| Role | Route | What it does |
|---|---|---|
| Customer | `/search` | Search by area, type and dates. Shows live demand multiplier and predicted availability |
| Customer | `/vehicles/:id` | Live price quote with the reasons for surge pricing; reserve |
| Customer | `/checkout/:bookingId` | Payment (UPI / card / net banking) |
| Customer | `/bookings` | Booking history, pay pending, cancel |
| Admin | `/admin` | Utilisation KPIs, demand map, 14-day trend, utilisation by type |
| Admin | `/admin/demand` | Zone-by-zone demand, multiplier breakdown, 7-day forecast by type |
| Admin | `/admin/allocation` | Model-suggested vehicle moves between zones; dispatch |
| Admin | `/admin/maintenance` | Health score, failure probability, schedule service |
| Admin | `/admin/late-returns` | Overdue bookings with running penalty (polls every minute) |
| Admin | `/admin/risk` | Customer risk scores and recommended action |

All users get the notification bell and pop-up toasts.

## Project layout

```
src/
  api/        client.js (axios + JWT), services.js (all endpoints), mock.js (demo backend)
  context/    AuthContext (login/session), NotificationContext (SSE stream)
  hooks/      useAsync (loading / error / reload)
  components/ Layout, DemandMap, SurgePlate, NotificationBell, Toast, Badge, Status...
  pages/      customer pages; pages/admin/ fleet manager pages
  styles/     global.css (design tokens + all styles)
```

## REST contract the backend must implement

All routes are under `/api`. Authenticated routes expect `Authorization: Bearer <jwt>`.
Errors should return `{ "message": "..." }` — the UI shows that text directly.

| Method | Path | Body / query | Returns |
|---|---|---|---|
| POST | `/auth/login` | `{email, password}` | `{token, user:{id,name,email,role}}` (`role`: `CUSTOMER` \| `ADMIN`) |
| POST | `/auth/register` | `{name,email,phone,licenseNumber,password}` | same as login |
| GET | `/auth/me` | | `user` |
| GET | `/vehicles/search` | `zoneId?, type?, startTime, endTime` | `[{id,name,type,seats,fuel,transmission,baseRate,currentRate,demandMultiplier,zoneId,zoneName,availabilityProbability}]` |
| GET | `/vehicles/{id}` | | vehicle incl. `odometer`, `zoneName` |
| GET | `/pricing/quote` | `vehicleId, startTime, endTime` | `{days,baseRate,multiplier,subtotal,tax,total,deposit,reasons:[string]}` |
| POST | `/bookings` | `{vehicleId,startTime,endTime}` | `{id,vehicleId,vehicleName,startTime,endTime,total,status}` |
| GET | `/bookings/me` | | `[booking]` |
| POST | `/bookings/{id}/cancel` | | booking |
| GET | `/bookings/late` | ADMIN | `[booking + customer, overdueHours, penalty]` |
| POST | `/payments` | `{bookingId, method, upiId?}` | `{paymentId, status}` |
| POST | `/payments/{paymentId}/confirm` | | `{paymentId, status:"SUCCESS"\|"FAILED"}` |
| GET | `/predictions/demand` | ADMIN, `days?` | `{zones:[{id,name,x,y,demand,supply,searches,multiplier}], forecast:[{day,SUV,SEDAN,HATCHBACK,EV,BIKE}], modelVersion, generatedAt}` |
| GET | `/predictions/maintenance` | ADMIN | `[{vehicleId,vehicleName,status,odometer,healthScore,failureProbability,component,dueInDays}]` |
| POST | `/maintenance/{vehicleId}/schedule` | ADMIN | `{vehicleId,status}` |
| GET | `/risk/customers` | ADMIN | `[{customerId,name,totalBookings,lateReturns,damageClaims,paymentFailures,score}]` |
| GET | `/analytics/utilization` | ADMIN | `{fleetSize,rented,inMaintenance,utilizationRate,revenueToday,byType:[{type,utilization}],trend:[{date,utilization,revenue}]}` |
| GET | `/allocation/recommendations` | ADMIN | `[{id,vehicleId,vehicleName,type,fromZone,toZone,expectedGain,reason}]` |
| POST | `/allocation/{id}/apply` | ADMIN | `{id,status}` |
| GET | `/notifications` | | `[{id,type,message,createdAt,read}]` |
| PATCH | `/notifications/{id}/read` | | |
| GET | `/notifications/stream?token=<jwt>` | SSE, event name `notification` | JSON notification per event |

`zones[].x / y` are 0–100 positions on the schematic map (not lat/lng).
Notification `type`: `DEMAND_SURGE`, `LATE_RETURN`, `MAINTENANCE`, `BOOKING`, `PAYMENT`.

**SSE note:** `EventSource` can't send headers, so the stream takes the JWT as a query
parameter. Add a small filter in Spring Security that reads `token` for that one path.
Spring side: a Kafka `@KafkaListener` on the `notifications` topic writes to the user's `SseEmitter`.

## Docker

```bash
docker build -t fleetiq-frontend .
```

`docker-compose.yml` snippet (the nginx config forwards `/api` to a service named `backend`):

```yaml
  frontend:
    build: ./fleetiq-frontend
    ports: ["3000:80"]
    depends_on: [backend]
```
