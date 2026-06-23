package com.agentledger;

import com.agentledger.model.TxnType;
import com.agentledger.repo.TxnTypeRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Batch E — custom transaction-type MANAGEMENT edges not covered by CustomTxnTypeTest:
 *  reorder persistence, append-at-end on create, rename propagation, displayNameFor fallback. */
class TxnTypeManagementTest extends TestBase {

    /** position of a type id within the active, ordered list; -1 if absent. */
    private int positionOf(int id) {
        List<TxnType> list = TxnTypeRepo.listForBranch(1);
        for (int i = 0; i < list.size(); i++) if (list.get(i).id() == id) return i;
        return -1;
    }

    @Test
    void reorder_persistsAndReordersList() throws Exception {
        // insertCustom assigns MAX(sort_order)+1, so B sorts after A
        int a = TxnTypeRepo.insertCustom(1, "Alpha", "out", "in");
        int b = TxnTypeRepo.insertCustom(1, "Beta", "out", "in");

        int sa = TxnTypeRepo.sortOrderOf(a);
        int sb = TxnTypeRepo.sortOrderOf(b);
        assertTrue(sa < sb, "freshly created B sorts after A");
        assertTrue(positionOf(a) < positionOf(b), "list shows A before B initially");

        // swap their sort orders
        TxnTypeRepo.setSortOrder(a, sb);
        TxnTypeRepo.setSortOrder(b, sa);

        // persisted?
        assertEquals(sb, TxnTypeRepo.sortOrderOf(a), "A's new sort order persisted");
        assertEquals(sa, TxnTypeRepo.sortOrderOf(b), "B's new sort order persisted");

        // and the list reflects the swap
        assertTrue(positionOf(b) < positionOf(a), "after swap, list shows B before A");
    }

    @Test
    void insertCustom_appendsAtEnd() throws Exception {
        List<TxnType> before = TxnTypeRepo.listForBranch(1);
        int newId = TxnTypeRepo.insertCustom(1, "Last One", "in", "out");

        List<TxnType> after = TxnTypeRepo.listForBranch(1);
        assertEquals(before.size() + 1, after.size(), "one more active type");
        assertEquals(newId, after.get(after.size() - 1).id(),
                "a newly created custom type lands at the end of the ordered list");
    }

    @Test
    void rename_persistsAndShowsInDisplayNameMapping() throws Exception {
        int id = TxnTypeRepo.insertCustom(1, "Old Name", "out", "in");
        String canonical = TxnTypeRepo.byId(id).name();   // auto-generated CUSTOM_<ts>

        TxnTypeRepo.updateDisplayName(id, "New Name");

        // verified through the cross-screen mapping other screens actually use —
        // reads display_name straight from the row, so no reliance on an unconfirmed record accessor name
        assertEquals("New Name", TxnTypeRepo.displayNameFor(1, canonical),
                "displayNameFor reflects the rename across screens");
    }

    @Test
    void displayNameFor_unknownName_fallsBackToCanonical() {
        String unknown = "NOPE_NOT_A_REAL_TYPE";
        assertEquals(unknown, TxnTypeRepo.displayNameFor(1, unknown),
                "unknown canonical name falls back to itself");
    }
}