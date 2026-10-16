-- Mỗi service một user + một database riêng (chỉ chạy lần đầu khi volume còn trống)
CREATE USER auth_user WITH PASSWORD 'auth_pass';
CREATE DATABASE auth_db OWNER auth_user;

CREATE USER prescription_user WITH PASSWORD 'prescription_pass';
CREATE DATABASE prescription_db OWNER prescription_user;

CREATE USER order_user WITH PASSWORD 'order_pass';
CREATE DATABASE order_db OWNER order_user;

CREATE USER payment_user WITH PASSWORD 'payment_pass';
CREATE DATABASE payment_db OWNER payment_user;

CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
CREATE DATABASE inventory_db OWNER inventory_user;
