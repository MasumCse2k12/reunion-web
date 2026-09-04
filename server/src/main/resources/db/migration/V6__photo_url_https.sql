-- photo_url holds the full public URL, so moving photos behind TLS orphans every
-- row written before the switch: the stored http://<ip>:9000/... links are blocked
-- as cleartext by the Android app and as mixed content by browsers, and
-- StorageService.delete() skips them because they no longer match the bucket prefix.
--
-- Rebuild each URL onto the current public base, keeping everything from the
-- bucket segment onwards (the object key, plus any ?v= cache-buster). Placeholders
-- come from spring.flyway.placeholders in application.yml, so this follows
-- MINIO_PUBLIC_BASE_URL wherever it points.
update person
   set photo_url = '${minioPublicBaseUrl}'
                || substring(photo_url from position('/${minioBucket}/' in photo_url))
 where photo_url is not null
   and photo_url not like '${minioPublicBaseUrl}/%'
   and position('/${minioBucket}/' in photo_url) > 0;
