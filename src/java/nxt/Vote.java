package nxt;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Vote {
    private static final DbKey.LongKeyFactory<Vote> voteDbKeyFactory = new DbKey.LongKeyFactory<Vote>("id") {
        @Override
        public DbKey newKey(Vote vote) {
            return vote.dbKey;
        }
    };

    private static final EntityDbTable<Vote> voteTable = new EntityDbTable<Vote>("vote", voteDbKeyFactory) {

        @Override
        protected Vote load(Connection con, ResultSet rs) throws SQLException {
            return new Vote(rs);
        }

        @Override
        protected void save(Connection con, Vote vote) throws SQLException {
            vote.save(con);
        }
    };

    static void init() {}


    private final long id;
    private final DbKey dbKey;
    private final long pollId;
    private final long voterId;
    private final byte[] voteBytes;

    private Vote(Transaction transaction, Attachment.MessagingVoteCasting attachment) {
        this.id = transaction.getId();
        this.dbKey = voteDbKeyFactory.newKey(this.id);
        this.pollId = attachment.getPollId();
        this.voterId = transaction.getSenderId();
        this.voteBytes = attachment.getPollVote();
    }

    private Vote(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = voteDbKeyFactory.newKey(this.id);
        this.pollId = rs.getLong("poll_id");
        this.voterId = rs.getLong("voter_id");
        this.voteBytes = rs.getBytes("vote_bytes");
    }

    static Vote addVote(Transaction transaction, Attachment.MessagingVoteCasting attachment) {
        Vote vote = new Vote(transaction, attachment);
        voteTable.insert(vote);
        return vote;
    }

    protected void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO vote (id, poll_id, voter_id, "
                + "vote_bytes, height) VALUES (?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.getId());
            pstmt.setLong(++i, this.getPollId());
            pstmt.setLong(++i, this.getVoterId());
            pstmt.setBytes(++i, this.getVote());
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public static int getCount() {
        return voteTable.getCount();
    }

    public static Vote getVote(long id) {
        return voteTable.get(voteDbKeyFactory.newKey(id));
    }

    public static DbIterator<Vote> getVotes(long pollId, int from, int to) {
        return voteTable.getManyBy(new DbClause.LongClause("poll_id", pollId), from, to);
    }

    static boolean isVoteGiven(long pollId, long voterId){
        DbClause clause = new DbClause.LongLongClause("poll_id", pollId, "voter_id", voterId);
        return voteTable.getCount(clause) > 0;
    }
    
    public long getId() {
        return id;
    }

    public long getPollId() { return pollId; }

    public long getVoterId() { return voterId; }

    public byte[] getVote() { return voteBytes; }

}
