-- Run once on the existing creatorhub schema before starting the new backend.
ALTER TABLE `creatorhub`.`works`
    ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL;
