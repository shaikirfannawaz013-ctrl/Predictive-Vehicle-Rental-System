-- Pickup zones around Tirupati. map_x / map_y place zones on the frontend's schematic map (0-100).
INSERT INTO zones (id, name, latitude, longitude, map_x, map_y) VALUES
  ('TPT-C', 'Tirupati Central',   13.6288, 79.4192, 48, 52),
  ('TML',   'Tirumala',           13.6833, 79.3474, 30, 20),
  ('REN',   'Renigunta Airport',  13.6325, 79.5433, 74, 44),
  ('CHG',   'Chandragiri',        13.5856, 79.3170, 22, 70),
  ('SKH',   'Srikalahasti',       13.7496, 79.6984, 88, 22),
  ('PTR',   'Puttur',             13.4417, 79.5510, 70, 84);

INSERT INTO vehicles (registration_number, name, type, seats, fuel, transmission, base_rate, zone_id, status,
                      odometer_km, purchase_date, last_service_date, last_service_odometer) VALUES
  ('AP03AB1201', 'Mahindra XUV700',           'SUV',       7, 'Diesel',   'Automatic', 4200, 'TPT-C', 'AVAILABLE',    38120, '2023-02-10', CURRENT_DATE - 40,  34500),
  ('AP03AB1202', 'Toyota Innova Crysta',      'SUV',       7, 'Diesel',   'Manual',    3900, 'TML',   'AVAILABLE',    91240, '2020-06-18', CURRENT_DATE - 150, 80100),
  ('AP03AB1203', 'Hyundai Creta',             'SUV',       5, 'Petrol',   'Automatic', 3300, 'REN',   'AVAILABLE',    22410, '2024-01-05', CURRENT_DATE - 20,  21000),
  ('AP03AB1204', 'Kia Seltos',                'SUV',       5, 'Diesel',   'Automatic', 3400, 'CHG',   'AVAILABLE',    55800, '2022-03-22', CURRENT_DATE - 95,  48200),
  ('AP03AB1205', 'Mahindra Scorpio-N',        'SUV',       7, 'Diesel',   'Manual',    3700, 'TPT-C', 'AVAILABLE',    41900, '2023-05-14', CURRENT_DATE - 60,  37400),
  ('AP03AB1206', 'Tata Harrier',              'SUV',       5, 'Diesel',   'Automatic', 3600, 'PTR',   'AVAILABLE',    47350, '2022-11-02', CURRENT_DATE - 70,  42100),
  ('AP03AB1207', 'Toyota Fortuner',           'SUV',       7, 'Diesel',   'Automatic', 5200, 'REN',   'AVAILABLE',    63400, '2021-09-30', CURRENT_DATE - 110, 55200),
  ('AP03CD2201', 'Honda City',                'SEDAN',     5, 'Petrol',   'Manual',    2600, 'TPT-C', 'AVAILABLE',    47300, '2022-04-11', CURRENT_DATE - 55,  43000),
  ('AP03CD2202', 'Maruti Dzire',              'SEDAN',     5, 'CNG',      'Manual',    1900, 'CHG',   'AVAILABLE',   120550, '2019-07-01', CURRENT_DATE - 190, 106000),
  ('AP03CD2203', 'Hyundai Verna',             'SEDAN',     5, 'Petrol',   'Automatic', 2700, 'REN',   'AVAILABLE',    29800, '2023-08-19', CURRENT_DATE - 30,  27500),
  ('AP03CD2204', 'Skoda Slavia',              'SEDAN',     5, 'Petrol',   'Automatic', 2900, 'TML',   'AVAILABLE',    18600, '2024-02-26', CURRENT_DATE - 25,  16900),
  ('AP03CD2205', 'Maruti Ciaz',               'SEDAN',     5, 'Petrol',   'Manual',    2200, 'SKH',   'AVAILABLE',    77200, '2020-12-15', CURRENT_DATE - 130, 69000),
  ('AP03EF3201', 'Maruti Swift',              'HATCHBACK', 5, 'Petrol',   'Manual',    1500, 'PTR',   'AVAILABLE',    30210, '2023-03-09', CURRENT_DATE - 45,  27800),
  ('AP03EF3202', 'Hyundai i20',               'HATCHBACK', 5, 'Petrol',   'Automatic', 1800, 'TPT-C', 'AVAILABLE',    41200, '2022-10-21', CURRENT_DATE - 65,  36900),
  ('AP03EF3203', 'Tata Altroz',               'HATCHBACK', 5, 'Diesel',   'Manual',    1700, 'CHG',   'AVAILABLE',    52800, '2021-12-12', CURRENT_DATE - 100, 45100),
  ('AP03EF3204', 'Maruti Baleno',             'HATCHBACK', 5, 'Petrol',   'Manual',    1600, 'SKH',   'AVAILABLE',    26400, '2023-06-30', CURRENT_DATE - 35,  24200),
  ('AP03EF3205', 'Renault Kwid',              'HATCHBACK', 5, 'Petrol',   'Manual',    1200, 'PTR',   'AVAILABLE',    68900, '2020-08-08', CURRENT_DATE - 160, 60200),
  ('AP03GH4201', 'Tata Nexon EV',             'EV',        5, 'Electric', 'Automatic', 2800, 'TPT-C', 'MAINTENANCE',  18900, '2023-04-17', CURRENT_DATE - 85,  12500),
  ('AP03GH4202', 'MG ZS EV',                  'EV',        5, 'Electric', 'Automatic', 3100, 'REN',   'AVAILABLE',    12050, '2024-03-03', CURRENT_DATE - 30,  10100),
  ('AP03GH4203', 'Tata Punch EV',             'EV',        5, 'Electric', 'Automatic', 2300, 'TPT-C', 'AVAILABLE',     8400, '2024-06-12', CURRENT_DATE - 15,   7600),
  ('AP03GH4204', 'Mahindra XUV400',           'EV',        5, 'Electric', 'Automatic', 2900, 'TML',   'AVAILABLE',    24700, '2023-01-28', CURRENT_DATE - 75,  20400),
  ('AP03JK5201', 'Royal Enfield Classic 350', 'BIKE',      2, 'Petrol',   'Manual',     900, 'TML',   'AVAILABLE',    15320, '2023-07-07', CURRENT_DATE - 50,  12800),
  ('AP03JK5202', 'Honda Activa 6G',           'BIKE',      2, 'Petrol',   'Automatic',  450, 'SKH',   'AVAILABLE',    26700, '2022-05-25', CURRENT_DATE - 120, 21100),
  ('AP03JK5203', 'TVS Jupiter',               'BIKE',      2, 'Petrol',   'Automatic',  420, 'TPT-C', 'AVAILABLE',    19800, '2022-09-14', CURRENT_DATE - 80,  16200),
  ('AP03JK5204', 'Bajaj Pulsar NS200',        'BIKE',      2, 'Petrol',   'Manual',     700, 'REN',   'AVAILABLE',    33100, '2021-11-03', CURRENT_DATE - 140, 26300),
  ('AP03JK5205', 'Ather 450X',                'BIKE',      2, 'Electric', 'Automatic',  600, 'TPT-C', 'AVAILABLE',     9700, '2024-04-20', CURRENT_DATE - 28,   8800);

INSERT INTO maintenance_records (vehicle_id, component, predicted_failure_probability, scheduled_for, status)
SELECT id, 'Battery cooling', 0.41, CURRENT_DATE, 'SCHEDULED' FROM vehicles WHERE registration_number = 'AP03GH4201';
