DROP TABLE
    IF EXISTS `table_index`;

CREATE TABLE `table_index`
(
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (`id`)
) ENGINE = INNODB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- 删除存储过程
DROP PROCEDURE
    IF EXISTS sum2;

CREATE PROCEDURE sum2(a INT)
BEGIN

DECLARE
i INT DEFAULT 1;

loop_name
:
LOOP
	-- 循环开始
IF i > a THEN
	LEAVE loop_name;
END
IF;
INSERT INTO table_index (id)
VALUES (NULL);
SET
i = i + 1;
END
LOOP
;

-- 输出结果
END;

-- 执行存储过程
CALL sum2 (10000);

-- 删除存储过程
DROP PROCEDURE
    IF EXISTS sum2;