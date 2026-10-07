-- Reference data only: the API still filters channels by configured provider beans.
-- Without tj.pay.simulated=true, this row cannot expose a simulated payment channel.
INSERT INTO pay_channel(name,channel_code,channel_priority,channel_icon,status,creater,updater,create_time,update_time)
SELECT '本地模拟支付','mockPay',0,'local-simulation',1,0,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM pay_channel WHERE channel_code='mockPay');
