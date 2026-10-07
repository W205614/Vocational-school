USE tj_course;
INSERT INTO category(id,name,parent_id,level,status) VALUES(1,'软件开发',0,1,1),(2,'Java后端',1,2,1),(3,'Spring应用',2,3,1);
INSERT INTO course(id,name,cover_url,first_cate_id,second_cate_id,third_cate_id,price,status,purchase_start_time,purchase_end_time,step,valid_duration,section_num,media_duration,dep_id,publish_time,creater,updater) VALUES(1,'Java 演示课程','',1,2,3,100,2,NOW()-INTERVAL 1 DAY,NOW()+INTERVAL 1 YEAR,5,12,2,2,0,NOW(),800000000000000002,800000000000000002);
INSERT INTO course_teacher(id,course_id,teacher_id,is_show,c_index,dep_id,create_time,update_time,creater,updater,deleted) VALUES(1,1,800000000000000003,1,1,0,NOW(),NOW(),800000000000000002,800000000000000002,0);
USE tj_pay;
INSERT INTO pay_channel(id,name,channel_code,channel_priority,channel_icon,status,creater,updater) VALUES(1,'本地模拟支付','mockPay',1,'',1,800000000000000002,800000000000000002);
