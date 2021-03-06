package nxt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nxt.computation.CommandNewWork;
import nxt.computation.ComputationConstants;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import org.json.simple.JSONObject;

/******************************************************************************
 * Copyright © 2017 The XEL Core Developers.                                  *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

public final class Work {

    private static final Listeners<Work, Event> listeners = new Listeners<>();
    private static final DbKey.LongKeyFactory<Work> workDbKeyFactory = new DbKey.LongKeyFactory<Work>("id") {

        @Override
        public DbKey newKey(final Work shuffling) {
            return shuffling.dbKey;
        }

    };
    private static final VersionedEntityDbTable<Work> workTable = new VersionedEntityDbTable<Work>("work",
            Work.workDbKeyFactory) {

        @Override
        protected Work load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
            return new Work(rs, dbKey);
        }

        @Override
        protected void save(final Connection con, final Work shuffling) throws SQLException {
            shuffling.save(con);
        }

    };

    // this will check whether work needs to be closed after applying each block
    // Later, close work if users balance drops before the estimated remaning balances or if payouts are not
    // performed at all
    static {
        Nxt.getBlockchainProcessor().addListener(block -> {
            final List<Work> shufflings = new ArrayList<>();
            try (DbIterator<Work> iterator = Work.getActiveWork()) {
                for (final Work shuffling : iterator) shufflings.add(shuffling);
            }
            shufflings.forEach(shuffling -> {
                shuffling.CheckForAutoClose(block);
            });
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    public String getSource_code() {
        return source_code;
    }

    private final long id;
    private final DbKey dbKey;
    private final long block_id;
    private final int cap_number_pow;
    private final long sender_account_id;
    private final long xel_per_pow;
    private final int iterations;
    private final long xel_per_bounty;
    private final int bounty_limit_per_iteration;
    private final int originating_height;
    private boolean closed;
    private boolean cancelled;
    private boolean timedout;
    private int iterations_left;
    private int received_bounties;
    private int received_pows;
    private short blocksRemaining;
    private int closing_timestamp;

    public String getVerifyFunction() {
        return verifyFunction;
    }

    private int[] combined_storage;
    private int storage_size;
    private String verifyFunction;
    private String source_code;

    public int getStorage_size() {
        return storage_size;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setTimedout(boolean timedout) {
        this.timedout = timedout;
    }

    public void setIterations_left(int iterations_left) {
        this.iterations_left = iterations_left;
    }

    public void setReceived_bounties(int received_bounties) {
        this.received_bounties = received_bounties;
    }

    public void setReceived_pows(int received_pows) {
        this.received_pows = received_pows;
    }

    public void setBlocksRemaining(short blocksRemaining) {
        this.blocksRemaining = blocksRemaining;
    }

    public void setClosing_timestamp(int closing_timestamp) {
        this.closing_timestamp = closing_timestamp;
    }

    public void setCombined_storage(int[] combined_storage) {
        this.combined_storage = combined_storage;
    }

    private Work(final ResultSet rs, final DbKey dbKey) throws SQLException {

        this.id = rs.getLong("id");
        this.block_id = rs.getLong("block_id");
        this.dbKey = dbKey;
        this.xel_per_pow = rs.getLong("xel_per_pow");
        this.cap_number_pow = rs.getInt("cap_number_pow");
        this.blocksRemaining = rs.getShort("blocks_remaining");
        this.closed = rs.getBoolean("closed");
        this.cancelled = rs.getBoolean("cancelled");
        this.timedout = rs.getBoolean("timedout");
        this.xel_per_bounty = rs.getLong("xel_per_bounty");
        this.iterations = rs.getInt("iterations");
        this.iterations_left = rs.getInt("iterations_left");
        this.received_bounties = rs.getInt("received_bounties");
        this.received_pows = rs.getInt("received_pows");
        this.bounty_limit_per_iteration = rs.getInt("bounty_limit_per_iteration");
        this.sender_account_id = rs.getLong("sender_account_id");
        this.originating_height = rs.getInt("originating_height");
        this.closing_timestamp = rs.getInt("closing_timestamp");
        this.combined_storage = Convert.byte2int(rs.getBytes("combined_storage"));
        this.storage_size = rs.getInt("storage_size");
        this.verifyFunction = rs.getString("verify_function");
        this.source_code = rs.getString("source_code");
    }
    private Work(final Transaction transaction, final CommandNewWork attachment) {
        this.id = transaction.getId();
        this.block_id = transaction.getBlockId();
        this.dbKey = Work.workDbKeyFactory.newKey(this.id);
        this.xel_per_pow = attachment.getXelPerPow();
        this.cap_number_pow = attachment.getCap_number_pow();
        this.iterations = attachment.getNumberOfIterations();
        this.iterations_left = iterations;
        this.blocksRemaining = attachment.getDeadline();
        this.closed = false;
        this.xel_per_bounty = attachment.getXelPerBounty();
        this.received_bounties = 0;
        this.received_pows = 0;
        this.bounty_limit_per_iteration = attachment.getBountiesPerIteration();
        this.combined_storage = new int[ComputationConstants.BOUNTY_STORAGE_INTS*this.bounty_limit_per_iteration];
        this.sender_account_id = transaction.getSenderId();
        this.cancelled = false;
        this.timedout = false;
        this.originating_height = transaction.getBlock().getHeight();
        this.closing_timestamp = 0;
        this.storage_size = attachment.getStorageSize();
        this.verifyFunction = attachment.getVerifyFunction();
        this.source_code = new String(attachment.getSourceCode());
    }

    public static boolean addListener(final Listener<Work> listener, final Event eventType) {
        return Work.listeners.addListener(listener, eventType);
    }

    public static void addWork(final Transaction transaction, final CommandNewWork attachment) {
        final Work shuffling = new Work(transaction, attachment);
        Work.workTable.insert(shuffling);
        Work.listeners.notify(shuffling, Event.WORK_CREATED);
    }


    public static List<Work> getWork(final long accountId, final boolean includeFinished, final int from,
                                            final int to, final long onlyOneId) {
        final List<Work> ret = new ArrayList<>();

        try (Connection con = Db.db.getConnection();) {

            PreparedStatement pstmt = null;
            if(accountId != 0)
                pstmt = con.prepareStatement("SELECT work.* FROM work WHERE work.sender_account_id = ? "
                             + (includeFinished ? "" : "AND work.blocks_remaining IS NOT NULL ")
                             + (onlyOneId == 0 ? "" : "AND work.work_id = ? ")
                             + "AND work.latest = TRUE ORDER BY closed, originating_height DESC "
                             + DbUtils.limitsClause(from, to));
            else
                pstmt = con.prepareStatement("SELECT work.* FROM work WHERE work.sender_account_id != 0 "
                        + (includeFinished ? "" : "AND work.blocks_remaining IS NOT NULL ")
                        + (onlyOneId == 0 ? "" : "AND work.work_id = ? ")
                        + "AND work.latest = TRUE ORDER BY closed, originating_height DESC "
                        + DbUtils.limitsClause(from, to));
            int i = 0;
            if(accountId != 0)
                pstmt.setLong(++i, accountId);
            if (onlyOneId != 0) pstmt.setLong(++i, onlyOneId);
            DbUtils.setLimits(++i, pstmt, from, to);
            try (DbIterator<Work> w_it = Work.workTable.getManyBy(con, pstmt, true)) {
                while (w_it.hasNext()) ret.add(w_it.next());
            } catch (final Exception ignored) {

            }
            return ret;
        } catch (final SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static int getActiveCount() {
        return Work.workTable.getCount(new DbClause.BooleanClause("closed", false));
    }

    public static DbIterator<Work> getActiveWork(final int from, final int to) {
        return Work.workTable.getManyBy(
                new DbClause.BooleanClause("closed", false).and(new DbClause.BooleanClause("latest", true)),
                from, to, " ORDER BY blocks_remaining, height DESC ");
    }

    public static DbIterator<Work> getAll(final int from, final int to) {
        return Work.workTable.getAll(from, to, " ORDER BY blocks_remaining NULLS LAST, height DESC ");
    }

    public static DbIterator<Work> getActiveWork() {
        return Work.workTable.getManyBy(
                new DbClause.BooleanClause("closed", false), 0,
                Integer.MAX_VALUE);
    }

    public static int getCount() {
        return Work.workTable.getCount();
    }

    public static Work getWork(final long id) {
        return Work.workTable.get(Work.workDbKeyFactory.newKey(id));
    }

    public static Work getWorkById(final long work_id) {

        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con
                     .prepareStatement("SELECT work.* FROM work WHERE work.id = ? AND work.latest = TRUE")) {
            int i = 0;
            pstmt.setLong(++i, work_id);
            final DbIterator<Work> it = Work.workTable.getManyBy(con, pstmt, true);
            Work w = null;
            if (it.hasNext()) w = it.next();
            it.close();
            return w;
        } catch (final SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void init() {
    }

    public static boolean removeListener(final Listener<Work> listener, final Event eventType) {
        return Work.listeners.removeListener(listener, eventType);
    }

    public DbKey getDbKey() {
        return this.dbKey;
    }

    private byte[] getFullHash() {
        return TransactionDb.getFullHash(this.id);
    }

    public long getId() {
        return id;
    }

    public long getBlock_id() {
        return block_id;
    }

    public long getSender_account_id() {
        return sender_account_id;
    }

    public long getXel_per_pow() {
        return xel_per_pow;
    }

    public int getIterations() {
        return iterations;
    }

    public long getXel_per_bounty() {
        return xel_per_bounty;
    }

    public int getBounty_limit_per_iteration() {
        return bounty_limit_per_iteration;
    }

    public int getOriginating_height() {
        return originating_height;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isTimedout() {
        return timedout;
    }

    public int getIterations_left() {
        return iterations_left;
    }

    public int getReceived_bounties() {
        return received_bounties;
    }

    public int getReceived_pows() {
        return received_pows;
    }

    public short getBlocksRemaining() {
        return blocksRemaining;
    }

    public int getClosing_timestamp() {
        return closing_timestamp;
    }

    public int[] getCombined_storage() {
        return combined_storage;
    }

    private void save(final Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement(
                "MERGE INTO work (id, cap_number_pow, closing_timestamp, block_id, sender_account_id, xel_per_pow, " +
                        "iterations, iterations_left, blocks_remaining, closed, cancelled, timedout, xel_per_bounty, received_bounties, received_pows, bounty_limit_per_iteration, originating_height, height, combined_storage, storage_size, verify_function, source_code, latest) "
                        + "KEY (id, height) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setInt(++i, this.cap_number_pow);
            pstmt.setInt(++i, this.closing_timestamp);
            pstmt.setLong(++i, this.block_id);
            pstmt.setLong(++i, this.sender_account_id);
            pstmt.setLong(++i, this.xel_per_pow);
            pstmt.setInt(++i, this.iterations);
            pstmt.setInt(++i, this.iterations_left);
            pstmt.setShort(++i, this.blocksRemaining);
            pstmt.setBoolean(++i, this.closed);
            pstmt.setBoolean(++i, this.cancelled);
            pstmt.setBoolean(++i, this.timedout);
            pstmt.setLong(++i, this.xel_per_bounty);
            pstmt.setInt(++i, this.received_bounties);
            pstmt.setInt(++i, this.received_pows);
            pstmt.setInt(++i, this.bounty_limit_per_iteration);
            pstmt.setInt(++i, this.originating_height);
            pstmt.setInt(++i,Nxt.getBlockchain().getHeight());
            pstmt.setBytes(++i, Convert.int2byte(this.combined_storage));
            pstmt.setInt(++i, this.storage_size);
            pstmt.setString(++i, this.verifyFunction);
            pstmt.setString(++i, this.source_code);
            pstmt.executeUpdate();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void CloseManual(Block bl) {
        if(this.closed == false) {
            this.closed = true;
            this.cancelled = true;
            this.closing_timestamp = bl.getTimestamp();
            Work.workTable.insert(this);
            Work.listeners.notify(this, Event.WORK_CANCELLED);
            Logger.logInfoMessage("work closed on user request: id=" + Long.toUnsignedString(this.id));
        }
    }

    public void JustSave(){
        Work.workTable.insert(this);
        if(this.isClosed()) {
            Logger.logInfoMessage("work closed by pow/bty handler: id=" + Long.toUnsignedString(this.id));
            Work.listeners.notify(this, Event.WORK_CANCELLED);
        }
    }

    public void EmitPow(){
        Work.listeners.notify(this, Event.WORK_POW_RECEIVED);
    }

    public void EmitBty(){
        Work.listeners.notify(this, Event.WORK_BOUNTY_RECEIVED);
    }

    private void CheckForAutoClose(Block bl) {
        if(this.closed == false) {
            if(this.originating_height + this.blocksRemaining == bl.getHeight()){
                this.closed = true;
                this.timedout = true;
                this.closing_timestamp = bl.getTimestamp();
                Work.workTable.insert(this);
                Work.listeners.notify(this, Event.WORK_TIMEOUTED);
                Logger.logInfoMessage("work automatically closed due to timeout: id=" + Long.toUnsignedString(this.id));
            }
        }
    }

    public int getCap_number_pow() {
        return cap_number_pow;
    }

    public static JSONObject toJson(Work work) {
        final JSONObject response = new JSONObject();
        response.put("id", Long.toUnsignedString(work.id));
        response.put("work_at_height",Nxt.getBlockchain().getHeight());
        response.put("block_id", Long.toUnsignedString(work.block_id));
        response.put("xel_per_pow", work.xel_per_pow);
        response.put("iterations", work.iterations);
        response.put("iterations_left", work.iterations_left);
        response.put("originating_height", work.originating_height);
        response.put("max_closing_height", work.originating_height + work.blocksRemaining);
        response.put("closed", work.closed);
        response.put("closing_timestamp", work.closing_timestamp);
        response.put("cancelled", work.cancelled);
        response.put("timedout", work.timedout);
        response.put("xel_per_bounty", work.getXel_per_bounty());
        response.put("received_bounties", work.received_bounties);
        response.put("received_pows", work.received_pows);
        response.put("bounty_limit_per_iteration", work.bounty_limit_per_iteration);
        response.put("cap_number_pow", work.cap_number_pow);
        response.put("sender_account_id", Long.toUnsignedString(work.sender_account_id));
        response.put("storage_size", work.storage_size);
        return response;
    }

    public static JSONObject toJsonWithSource(Work work, boolean with_source) {
        final JSONObject response = toJson(work);
        if(with_source)
            response.put("source", work.source_code);
        return response;
    }

    public static int[] getStorage(Work work, int storage_slot){
        int[] storage_area = new int[work.storage_size];
        if (storage_slot>=0 && storage_slot < work.bounty_limit_per_iteration) {
            for (int i = 0; i < work.storage_size; ++i) {
                storage_area[i] = work.combined_storage[storage_slot * work.storage_size + i];
            }
        }
        return storage_area;
    }
    public static JSONObject toJsonWithStorage(Work work, int storage_slot, boolean with_source) {
        final JSONObject response = toJsonWithSource(work,with_source);

        if (storage_slot>=0 && storage_slot < work.bounty_limit_per_iteration) {
            int[] storage_area = getStorage(work, storage_slot);
            response.put("storage_id", storage_slot);
            response.put("storage", Convert.toHexString(Convert.int2byte(storage_area)));
        }
        return response;
    }

    public enum Event {
        WORK_CREATED, WORK_POW_RECEIVED, WORK_BOUNTY_RECEIVED, WORK_CANCELLED, WORK_TIMEOUTED
    }

}