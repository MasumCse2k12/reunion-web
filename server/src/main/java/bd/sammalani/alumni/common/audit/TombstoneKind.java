package bd.sammalani.alumni.common.audit;

import java.util.Arrays;
import java.util.Locale;

import bd.sammalani.alumni.common.error.ApiException;

/**
 * The soft-deletable tables, and how to describe a tombstone in each.
 * <p>
 * This enum is also the security boundary. The recycle bin has to build SQL with
 * a table name in it — there is no other way to read a column the ORM has been
 * told to hide — so the table name may only ever come from a constant defined
 * here. A path variable is resolved to one of these five values or the request is
 * a 404; nothing a caller sends reaches a query.
 */
public enum TombstoneKind {

    PERSON("person", "t.name", "t.batch_year"),
    /** No name of its own, so it borrows the applicant's — nobody recognises a uuid. */
    REGISTRATION("registration", "(select p.name from person p where p.id = t.person_id)", "t.batch_year"),
    PAYMENT("payment", "coalesce(t.reference, t.amount_bdt::text)", "null::int"),
    NOTICE("notice", "t.title", "null::int"),
    REFERRAL("referral", "t.name", "t.batch_year");

    private final String table;
    private final String labelSql;
    private final String batchYearSql;

    TombstoneKind(String table, String labelSql, String batchYearSql) {
        this.table = table;
        this.labelSql = labelSql;
        this.batchYearSql = batchYearSql;
    }

    /** Matches the {@code entity} column of an audit row, which is the table name. */
    public String table() {
        return table;
    }

    String labelSql() {
        return labelSql;
    }

    String batchYearSql() {
        return batchYearSql;
    }

    /**
     * @throws ApiException 404 for anything that is not one of the five, rather
     *                      than 400: a caller has no business knowing which table
     *                      names exist and which do not
     */
    public static TombstoneKind of(String name) {
        return Arrays.stream(values())
                .filter(kind -> kind.name().equalsIgnoreCase(name) || kind.table.equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "Nothing deleted of that kind.", "এই ধরনের কিছু মুছে ফেলা হয়নি।"));
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
