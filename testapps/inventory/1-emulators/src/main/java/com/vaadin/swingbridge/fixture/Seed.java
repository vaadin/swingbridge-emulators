package com.vaadin.swingbridge.fixture;

import com.ca.db.model.ApplicationLog;
import com.ca.db.model.BranchOffice;
import com.ca.db.model.Category;
import com.ca.db.model.CategorySpecifications;
import com.ca.db.model.Item;
import com.ca.db.model.ItemReturn;
import com.ca.db.model.LoginUser;
import com.ca.db.model.Specification;
import com.ca.db.model.Transfer;
import com.ca.db.model.UnitsString;
import com.ca.db.model.Vendor;
import com.ca.db.service.DBUtils;
import com.ca.db.service.ItemReturnServiceImpl;
import com.ca.db.service.TransferServiceImpl;
import com.ca.db.service.dto.ReturnedItemDTO;
import com.ca.ui.panels.ItemReceiverPanel.ReceiverType;
import com.gt.common.utils.DateTimeUtils;
import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.IMMUTABLE_CONSTANT;

/**
 * Seeds the inventory app to a known state, and prints what it left behind.
 *
 * <p>Ours, not upstream's — the only non-upstream class under {@code src/main}. It lives in main
 * sources, and {@link com.ca.ui.Main} calls {@link #seedIfEmpty()} before the UI opens, so every
 * launch of every migration stage starts on identical rows without a launcher of its own. The
 * migration copies it along with the rest of {@code src}, which is deliberate: a migrated app on an
 * empty database shows empty grids and empty combo boxes, and those are exactly the widgets whose
 * migration most needs judging.
 *
 * <p>The seed goes through the app's <em>own</em> services rather than SQL. Two reasons: it
 * exercises the same insert path the UI uses, so a broken service layer fails here instead of
 * three journeys later; and those service classes survive the stage-2 import swap unchanged, so
 * the same seed populates the migrated app and both stages compare on identical rows.
 *
 * <p>Changing which rows it inserts, or the values on them, is a measurement change, not a tweak —
 * cross-stage comparison only means something on identical data. A calendar date's <em>time of
 * day</em> is not one of those values: the reference is the day, so a stage may move the stored
 * instant within it, as the migration guide's {@code dates.md} midday rule tells it to. The row
 * table in {@code ../PROVENANCE.md} is the reference, and the {@code FIXTURE:} counts printed on
 * every launch are how a drifted stage gets caught.
 */
public final class Seed {

    /** Fixed so every run produces byte-identical rows — a rubric can name dates and ids. */
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date PURCHASE_1 = date(2026, 1, 15);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date PURCHASE_2 = date(2026, 2, 3);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date PURCHASE_3 = date(2026, 3, 21);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date TRANSFER_1 = date(2026, 4, 10);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date TRANSFER_2 = date(2026, 5, 5);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date TRANSFER_3 = date(2026, 6, 1);
    @IntentionallyStatic(IMMUTABLE_CONSTANT)
    private static final Date TRANSFER_4 = date(2026, 6, 18);

    /**
     * Seeds the database if it holds no data, then prints the row counts either way.
     *
     * <p>The emptiness check is what makes a file-backed database reusable across launches:
     * seeding it twice would produce duplicate reference rows and drifting ids. The in-memory
     * default always starts virgin, so there it always seeds.
     */
    public static void seedIfEmpty() throws Exception {
        if (DBUtils.readAll(Category.class).isEmpty()) {
            System.out.println("FIXTURE: seeding via the app's own service layer");
            seed();
        } else {
            System.out.println("FIXTURE: database already populated — seeding skipped");
        }
        report();
    }

    private static void seed() throws Exception {
        // Reference tables get three rows each, not one: a single-entry combo box looks identical
        // whether or not selection, ordering and value-binding actually work.
        Category laptops = category("Laptops", Category.TYPE_RETURNABLE, "CPU", "RAM", "Screen");
        Category printers = category("Printers", Category.TYPE_RETURNABLE, "Type", "DPI");
        Category stationery = category("Stationery", Category.TYPE_NON_RETURNABLE, "Colour");

        Vendor acme = vendor("Acme Supplies", "12 Trade Park, Brno", "+420 555 0101");
        Vendor globex = vendor("Globex Trading", "8 Harbour Road, Turku", "+358 555 0102");
        Vendor initech = vendor("Initech Hardware", "3 Mill Lane, Leeds", "+44 555 0103");

        UnitsString pieces = unit("pcs");
        UnitsString box = unit("box");
        UnitsString kg = unit("kg");

        BranchOffice head = office("Head Office", "1 Central Square", "Praha 1", "+420 555 0200");
        BranchOffice north = office("North Branch", "44 Lake Street", "Oulu", "+358 555 0201");
        BranchOffice south = office("South Depot", "9 Dock Way", "Bristol", "+44 555 0202");

        // Each item takes a distinct category/vendor/unit, so the three inserts stand on their own
        // regardless of how Hibernate constrains Item's @OneToOne FKs; sharing is probed below.
        Item laptop = item("ThinkPad T14", laptops, acme, pieces, "A-01", "1200.00", 10, PURCHASE_1,
                spec("i5-1345U", "16 GB", "14 inch"));
        Item printer = item("LaserJet 400", printers, globex, box, "B-02", "350.50", 5, PURCHASE_2,
                spec("Mono laser", "1200"));
        Item paper = item("A4 Paper", stationery, initech, kg, "C-03", "4.25", 100, PURCHASE_3,
                spec("White"));

        // Transfer and return go through the real services so their side effects happen too:
        // saveTransfer decrements the item's stock, saveReturnedItem walks remainingQtyToReturn and
        // adds the quantity back. Both were left empty by a manual happy-path pass, and both are
        // SwingWorker paths.
        //
        // Four transfers, because the state a Transfer row is in decides which screens can see it,
        // and one row can only be in one state. Each carries its own request number and its own
        // cart of exactly one item: saveTransfer commits inside its per-entry loop, so a two-entry
        // cart would commit the same Transaction twice — that is upstream's bug to expose from a
        // journey, not something to hide a seeded row behind.
        transfer("TR-0001", laptop, 3, TRANSFER_1, head);    // partially returned below
        transfer("TR-0002", printer, 3, TRANSFER_2, north);  // outstanding, nothing returned
        transfer("TR-0003", paper, 20, TRANSFER_3, south);   // non-returnable category
        transfer("TR-0004", printer, 1, TRANSFER_4, head);   // returned in full below

        // TR-0001 twice over, one unit each, so it lands in the *partially* returned state with two
        // ItemReturn rows behind it — the interesting one, and the only way to get more than one
        // return row against a single transfer. The second return uses a damaged condition, which
        // takes saveReturnedItem's else branch (the one that builds a replacement Item and then
        // never saves it — upstream's, ported not fixed).
        returned("RT-0001", "TR-0001", 1, ItemReturn.RETURN_ITEM_CONDITION_GOOD);
        returned("RT-0002", "TR-0001", 1, ItemReturn.RETURN_NEEDS_REPAIR);
        // Full quantity, so saveReturnedItem flips the transfer to STATUS_RETURNED_ALL and
        // remainingQtyToReturn hits 0 — which drops it out of the Return panel's search.
        returned("RT-0003", "TR-0004", 1, ItemReturn.RETURN_ITEM_CONDITION_GOOD);

        // Left unseeded on purpose, all three for their own reason:
        //   LoginUser — Main.addUserForFirstTime only creates ADMIN/ADMIN when the table is empty,
        //     so seeding a user here would take the app's only known credentials away.
        //   ApplicationLog — the app appends "Application Started" on every launch; seeded history
        //     would just be rows nothing reads.
        //   CategorySpecifications — an orphan @Entity: no code in the tree reads or writes it, so
        //     a row there is invisible to every screen. Category's own specification1..3 labels are
        //     what the entry form uses, and those are seeded.

        // Item's category/vendor/unit associations are @OneToOne, which Hibernate may back with a
        // unique FK — in which case two items cannot share one Category row even though the UI's
        // dropdown implies they can. Probed rather than assumed: informative either way, and not
        // load-bearing for the rest of the fixture.
        try {
            item("ThinkPad T16", laptops, acme, pieces, "A-04", "1450", 3, PURCHASE_1,
                    spec("i7-1355U", "32 GB", "16 inch"));
            System.out.println("FIXTURE-NOTE: a second Item may share a Category/Vendor/Unit row");
        } catch (Exception e) {
            System.out.println("FIXTURE-NOTE: sharing a Category/Vendor/Unit row across Items is "
                    + "rejected by the schema (@OneToOne unique FK): " + e.getMessage());
        }
    }

    /** One transfer of one item, the way ItemTransferPanel's cart of a single line would save it. */
    private static void transfer(String requestNumber, Item item, int quantity, Date date,
            BranchOffice receiver) throws Exception {
        Map<Integer, Integer> cart = new LinkedHashMap<>();
        cart.put(item.getId(), quantity);
        TransferServiceImpl.saveTransfer(cart, date, ReceiverType.OFFICIAL, receiver.getId(),
                requestNumber);
    }

    /** Returns {@code quantity} units of the named transfer, as ItemReturnPanel's cart would. */
    private static void returned(String returnNumber, String transferRequestNumber, int quantity,
            int condition) throws Exception {
        Transfer transfer = transferByRequestNumber(transferRequestNumber);
        Map<Integer, ReturnedItemDTO> cart = new LinkedHashMap<>();
        // Keyed by TRANSFER id despite saveReturnedItem naming its local variable itemId — it
        // queries Transfer with that key. Upstream naming, not ours.
        cart.put(transfer.getId(),
                new ReturnedItemDTO(quantity, condition, transfer.getItem().getRackNo()));
        ItemReturnServiceImpl.saveReturnedItem(cart, returnNumber);
    }

    private static Transfer transferByRequestNumber(String requestNumber) throws Exception {
        for (Object row : DBUtils.readAll(Transfer.class)) {
            Transfer t = (Transfer) row;
            if (requestNumber.equals(t.getTransferRequestNumber())) return t;
        }
        throw new IllegalStateException("FIXTURE-FAIL: no transfer " + requestNumber);
    }

    private static Category category(String name, int type, String... specLabels) throws Exception {
        Category c = new Category();
        c.setCategoryName(name);
        c.setCategoryType(type);
        c.setdFlag(1);
        c.setLastModifiedDate(new Date());
        // The specificationN fields on Category are the *labels* the entry form shows for that
        // category's spec inputs, not values.
        if (specLabels.length > 0) c.setSpecification1(specLabels[0]);
        if (specLabels.length > 1) c.setSpecification2(specLabels[1]);
        if (specLabels.length > 2) c.setSpecification3(specLabels[2]);
        DBUtils.saveOrUpdate(c);
        return requireSaved(c, c.getId(), "Category " + name);
    }

    private static Vendor vendor(String name, String address, String phone) throws Exception {
        Vendor v = new Vendor();
        v.setName(name);
        v.setAddress(address);
        v.setPhoneNumber(phone);
        v.setdFlag(1);
        v.setLastModifiedDate(new Date());
        DBUtils.saveOrUpdate(v);
        return requireSaved(v, v.getId(), "Vendor " + name);
    }

    private static UnitsString unit(String value) throws Exception {
        UnitsString u = new UnitsString();
        u.setValue(value);
        u.setdFlag(1);
        u.setDateTime(new Date());
        u.setLastModifiedDate(new Date());
        DBUtils.saveOrUpdate(u);
        return requireSaved(u, u.getId(), "UnitsString " + value);
    }

    private static BranchOffice office(String name, String address, String district, String phone)
            throws Exception {
        BranchOffice o = new BranchOffice();
        o.setName(name);
        o.setAddress(address);
        o.setDistrict(district);
        o.setPhoneNumber(phone);
        o.setdFlag(1);
        o.setLastModifiedDate(new Date());
        DBUtils.saveOrUpdate(o);
        return requireSaved(o, o.getId(), "BranchOffice " + name);
    }

    private static Specification spec(String... values) {
        Specification s = new Specification();
        s.setdFlag(1);
        s.setActiveStatus(1);
        s.setLastModifiedDate(new Date());
        if (values.length > 0) s.setSpecification1(values[0]);
        if (values.length > 1) s.setSpecification2(values[1]);
        if (values.length > 2) s.setSpecification3(values[2]);
        return s;  // cascaded from Item, so never saved on its own
    }

    /** Mirrors what {@code ItemEntryPanel}'s save-new branch sets, field for field. */
    private static Item item(String name, Category category, Vendor vendor, UnitsString unit,
            String rackNo, String rate, int quantity, Date purchaseDate, Specification spec)
            throws Exception {
        Item i = new Item();
        i.setName(name);
        i.setCategory(category);
        i.setVendor(vendor);
        i.setUnitsString(unit);
        i.setSpecification(spec);
        i.setRackNo(rackNo);
        i.setRate(new BigDecimal(rate));
        i.setQuantity(quantity);
        i.setOriginalQuantity(quantity);
        i.setPartsNumber("PN-" + rackNo);
        i.setSerialNumber("SN-" + rackNo);
        i.setPurchaseOrderNo("PO-" + rackNo);
        i.setPurchaseDate(purchaseDate);
        i.setAddedType(Item.ADD_TYPE_NEW_ENTRY);
        i.setdFlag(1);
        i.setAddedDate(new Date());
        i.setCurrentFiscalYear(DateTimeUtils.getCurrentFiscalYear());
        DBUtils.saveOrUpdate(i);
        return requireSaved(i, i.getId(), "Item " + name);
    }

    /**
     * A generated id of 0 means the insert silently did nothing — worth catching here rather than
     * as an empty dropdown later.
     */
    private static <T> T requireSaved(T entity, int id, String what) {
        if (id <= 0) throw new IllegalStateException("FIXTURE-FAIL: " + what + " got no id");
        return entity;
    }

    private static void report() throws Exception {
        System.out.println("FIXTURE: row counts after seeding");
        count("Category", Category.class);
        count("Vendor", Vendor.class);
        count("UnitsString", UnitsString.class);
        count("BranchOffice", BranchOffice.class);
        count("Item", Item.class);
        count("Specification", Specification.class);
        count("Transfer", Transfer.class);
        count("ItemReturn", ItemReturn.class);
        // 0 on a fresh database is correct: Main.addUserForFirstTime creates ADMIN/ADMIN after
        // this returns. A reused file database reports 1.
        count("LoginUser", LoginUser.class);
        // The three tables the seed deliberately leaves alone, counted anyway so a stage that
        // grew rows in them gets noticed. Unfiltered, because ApplicationLog's own writer never
        // sets dflag, so the filtered count would read 0 however many rows are there.
        countUnfiltered("ApplicationLog", ApplicationLog.class);
        countUnfiltered("CategorySpecifications", CategorySpecifications.class);
        reachableFromReturnPanel();
    }

    /**
     * Runs the query behind the Return panel's Search button with the arguments an untouched form
     * supplies, and prints what it finds.
     *
     * <p>Row counts alone do not prove a seeded row is <em>usable</em>: this query filters on
     * {@code remainingQtyToReturn > 0}, {@code dFlag == 1} and a returnable category, so a transfer
     * can exist and still be invisible to the only screen that consumes it. Worth asserting here
     * because the panel's own result table is starved to header height at the app's default window
     * size, which makes an empty result and an unreadable one look identical on screen.
     */
    private static void reachableFromReturnPanel() throws Exception {
        List<?> hits = TransferServiceImpl.notReturnedTransferItemQuery(
                "", -1, -1, -1, -1, "", null, null);
        System.out.println("FIXTURE:   Transfer rows the Return panel's search returns = "
                + hits.size());
    }

    private static void count(String label, Class<?> clazz) throws Exception {
        List<?> rows = DBUtils.readAll(clazz);
        System.out.println("FIXTURE:   " + label + " = " + rows.size());
    }

    /** Counts every row, {@code dflag} included — {@link DBUtils#readAll} only sees {@code 1}. */
    private static void countUnfiltered(String label, Class<?> clazz) throws Exception {
        List<?> rows = DBUtils.readAllNoStatus(clazz);
        System.out.println("FIXTURE:   " + label + " = " + rows.size() + " (unseeded)");
    }

    private static Date date(int year, int month, int day) {
        // Server zone on purpose: the seed runs from main(), before any browser exists. Midday, not
        // midnight, so browsers a few zones west of the server still show the same calendar day.
        return Date.from(LocalDate.of(year, month, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private Seed() {
    }
}
