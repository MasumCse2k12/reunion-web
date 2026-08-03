package bd.sammalani.alumni.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import bd.sammalani.alumni.domain.admin.AdminRole;

class AdminSessionTest {

    @Test
    @DisplayName("batch years come out ascending, whatever order they went in")
    void batchesAreSorted() {
        Set<Integer> shuffled = new LinkedHashSet<>(java.util.List.of(2015, 1998, 2007, 1974));

        assertThat(AdminSession.sorted(shuffled)).containsExactly(1974, 1998, 2007, 2015);
    }

    @Test
    @DisplayName("the ends of a sorted scope are the ends of the assignment")
    void endsAreTheEnds() {
        // The portal reads batches[0] and batches[last] as "from" and "to" on the
        // account card and the edit form. Set.copyOf would randomise those.
        Set<Integer> years = AdminSession.sorted(Set.of(2011, 1969, 1992));

        assertThat(years.iterator().next()).isEqualTo(1969);
        assertThat(years).containsExactly(1969, 1992, 2011);
    }

    @Test
    @DisplayName("a super admin covers every batch and carries no scope")
    void superAdminHasNoScope() {
        AdminSession session = session(AdminRole.SUPER_ADMIN, Set.of());

        assertThat(session.isSuperAdmin()).isTrue();
        assertThat(session.scopeOrNull()).isNull();
        assertThat(session.covers(1974)).isTrue();
        assertThat(session.covers(null)).isTrue();
    }

    @Test
    @DisplayName("a group admin covers exactly its assigned years")
    void groupAdminCoversItsYears() {
        AdminSession session = session(AdminRole.GROUP_ADMIN, Set.of(2009, 2010));

        assertThat(session.scopeOrNull()).containsExactlyInAnyOrder(2009, 2010);
        assertThat(session.covers(2010)).isTrue();
        assertThat(session.covers(2011)).isFalse();
        assertThat(session.covers(null)).isFalse();
    }

    @Test
    @DisplayName("a group admin with no batches sees nothing, not everything")
    void emptyScopeIsNotAllBatches() {
        AdminSession session = session(AdminRole.GROUP_ADMIN, Set.of());

        // Empty, not null: null is the query layer's "no restriction".
        assertThat(session.scopeOrNull()).isNotNull().isEmpty();
        assertThat(session.covers(2010)).isFalse();
    }

    private static AdminSession session(AdminRole role, Set<Integer> batches) {
        return new AdminSession(UUID.randomUUID(), "Coordinator", null, "coordinator",
                role, AdminSession.sorted(batches), true, false);
    }
}
