INSERT INTO services (id, name, duration_minutes, required_skill) VALUES
(1, 'Battery replacement', 60, 'BATTERY_FIT'),
(2, 'Jump-start', 30, 'JUMPSTART'),
(3, 'Battery health check', 30, 'DIAGNOSTICS');

INSERT INTO zones (id, name) VALUES
(1, 'Mumbai Central'),
(2, 'Andheri West');

INSERT INTO zone_pincodes (zone_id, pincode) VALUES
(1, '400001'), (1, '400002'), (1, '400003'),
(2, '400058'), (2, '400059'), (2, '400060');

INSERT INTO technicians (id, name, zone_id, shift_start_minute, shift_end_minute) VALUES
(1, 'Ravi Kumar', 1, 480, 1200),
(2, 'Priya Shah', 1, 540, 1080),
(3, 'Amit Patel', 1, 480, 1200),
(4, 'Suresh Nair', 2, 480, 1200),
(5, 'Neha Desai', 2, 600, 1140);

INSERT INTO technician_skills (technician_id, skill) VALUES
(1, 'BATTERY_FIT'), (1, 'JUMPSTART'),
(2, 'DIAGNOSTICS'),
(3, 'BATTERY_FIT'), (3, 'JUMPSTART'), (3, 'DIAGNOSTICS'),
(4, 'BATTERY_FIT'),
(5, 'JUMPSTART'), (5, 'DIAGNOSTICS');
