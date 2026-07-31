package bd.sammalani.alumni.storage;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around MinIO that owns all object-storage concerns.
 * <p>
 * The bucket is created and set to public-read on first startup. This means
 * the photo URLs stored in {@code person.photo_url} can be loaded directly
 * by the browser without going through the API — which is intentional, because
 * serving binary blobs through a Spring controller is wasteful and slow.
 * <p>
 * The object key is always {@code photos/{personId}/profile.jpg} — fixed, so
 * MinIO overwrites the previous photo on every upload and storage never
 * accumulates stale files. Cache-busting is handled by a {@code ?v=} timestamp
 * appended to the URL stored in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 5 * 1024 * 1024L; // 5 MB

    private final MinioClient minio;
    private final AppProperties props;

    @PostConstruct
    void ensureBucket() {
        String bucket = props.storage().bucket();
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
            // Public-read policy so browsers can load photos directly from MinIO.
            String policy = publicReadPolicy(bucket);
            minio.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("Applied public-read policy to bucket: {}", bucket);
        } catch (Exception e) {
            // Not fatal at startup — if MinIO is temporarily down the bucket will
            // be created on the first upload attempt. Log a warning so ops knows.
            log.warn("Could not initialise MinIO bucket '{}': {}", bucket, e.getMessage());
        }
    }

    /**
     * Validates and uploads a profile photo, returning the public URL to store.
     * <p>
     * The object key is fixed at {@code photos/{personId}/profile.jpg}. MinIO
     * silently overwrites the previous object, so there is always exactly one
     * photo per person and storage never accumulates stale files.
     * <p>
     * A {@code ?v=} timestamp is appended to the returned URL so that browsers
     * bust their cache on every update without needing a query-string on the
     * MinIO side.
     *
     * @param personId owner of the photo
     * @param file     the uploaded file (client-side compression is expected to
     *                 have already reduced this to a small JPEG)
     * @return full public URL with cache-busting stamp
     */
    public String upload(@NonNull UUID personId, @NonNull MultipartFile file) {
        validate(file);
        // Fixed key — one object per person, overwritten on every upload.
        String objectKey = "photos/" + personId + "/profile.jpg";
        AppProperties.Storage s = props.storage();
        try (InputStream stream = file.getInputStream()) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(s.bucket())
                    .object(objectKey)
                    .stream(stream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO upload failed for {}: {}", objectKey, e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error",
                    "Photo upload failed. Please try again.",
                    "ছবি আপলোড ব্যর্থ হয়েছে। আবার চেষ্টা করুন।");
        }
        // ?v= busts the browser cache; MinIO ignores unknown query params.
        return s.publicBaseUrl() + "/" + s.bucket() + "/" + objectKey + "?v=" + System.currentTimeMillis();
    }

    /**
     * Removes a person's photo from MinIO.
     * <p>
     * The stored URL may carry a {@code ?v=} cache-busting suffix — it is
     * stripped before the object key is derived. A missing object is silently
     * ignored so that a partial failure (object already gone) never prevents
     * the DB row from being cleared.
     *
     * @param publicUrl as stored in {@code person.photo_url}
     */
    public void delete(@NonNull String publicUrl) {
        AppProperties.Storage s = props.storage();
        String prefix = s.publicBaseUrl() + "/" + s.bucket() + "/";
        if (!publicUrl.startsWith(prefix)) {
            log.warn("delete() URL does not match bucket prefix — skipping: {}", publicUrl);
            return;
        }
        // Strip prefix, then strip any cache-busting query param.
        String withoutPrefix = publicUrl.substring(prefix.length());
        String objectKey = withoutPrefix.contains("?")
                ? withoutPrefix.substring(0, withoutPrefix.indexOf('?'))
                : withoutPrefix;
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(s.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO delete failed for {}: {}", objectKey, e.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("no_file", "No file was uploaded.", "কোনো ফাইল আপলোড করা হয়নি।");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("invalid_type",
                    "Only JPEG, PNG and WebP images are accepted.",
                    "শুধুমাত্র JPEG, PNG এবং WebP ছবি গ্রহণযোগ্য।");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("file_too_large",
                    "Photo must be smaller than 5 MB.",
                    "ছবির আকার ৫ MB-এর বেশি হওয়া যাবে না।");
        }
    }

    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }""".formatted(bucket);
    }
}
