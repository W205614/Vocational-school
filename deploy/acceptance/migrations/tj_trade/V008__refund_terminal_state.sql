-- Preserve legacy status 6 as pending; make successful refunded details and all-refunded orders terminal.
UPDATE order_detail SET status=7 WHERE status=6 AND refund_status=5;
UPDATE `order` o SET o.status=7,o.message='退款完成'
WHERE o.status=6 AND EXISTS(SELECT 1 FROM order_detail d WHERE d.order_id=o.id)
AND NOT EXISTS(SELECT 1 FROM order_detail d WHERE d.order_id=o.id AND d.status<>7);
UPDATE `order` o SET o.status=2,o.message='已支付'
WHERE o.status=6 AND EXISTS(SELECT 1 FROM order_detail d WHERE d.order_id=o.id AND d.status=2)
AND NOT EXISTS(SELECT 1 FROM order_detail d WHERE d.order_id=o.id AND d.status=6 AND d.refund_status IN(1,3));
