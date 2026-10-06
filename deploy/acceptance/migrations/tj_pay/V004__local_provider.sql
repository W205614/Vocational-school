CREATE TABLE simulated_provider_payment(pay_order_no BIGINT PRIMARY KEY,amount INT NOT NULL,status INT NOT NULL DEFAULT 1,success_time DATETIME(3));
CREATE TABLE simulated_provider_refund(refund_order_no BIGINT PRIMARY KEY,pay_order_no BIGINT NOT NULL,amount INT NOT NULL,status INT NOT NULL DEFAULT 2);
