package de.thm.arsnova.persistance.couchdb;

import com.google.common.collect.Lists;
import de.thm.arsnova.entities.VisitedSession;
import de.thm.arsnova.persistance.LogEntryRepository;
import de.thm.arsnova.persistance.VisitedSessionRepository;
import org.ektorp.BulkDeleteDocument;
import org.ektorp.CouchDbConnector;
import org.ektorp.DbAccessException;
import org.ektorp.DocumentOperationResult;
import org.ektorp.ViewResult;
import org.ektorp.support.CouchDbRepositorySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class CouchDbVisitedSessionRepository extends CouchDbRepositorySupport<VisitedSession> implements VisitedSessionRepository {
	private static final int BULK_PARTITION_SIZE = 500;

	private static final Logger logger = LoggerFactory.getLogger(CouchDbVisitedSessionRepository.class);

	@Autowired
	private LogEntryRepository dbLogger;

	public CouchDbVisitedSessionRepository(CouchDbConnector db, boolean createIfNotExists) {
		super(VisitedSession.class, db, createIfNotExists);
	}

	@Override
	public int deleteInactiveGuestVisitedSessionLists(long lastActivityBefore) {
		try {
			ViewResult result = db.queryView(createQuery("by_last_activity_for_guests").endKey(lastActivityBefore));

			int count = 0;
			List<List<ViewResult.Row>> partitions = Lists.partition(result.getRows(), BULK_PARTITION_SIZE);
			for (List<ViewResult.Row> partition: partitions) {
				final List<BulkDeleteDocument> newDocs = new ArrayList<>();
				for (final ViewResult.Row oldDoc : partition) {
					final BulkDeleteDocument newDoc = new BulkDeleteDocument(oldDoc.getId(), oldDoc.getValueAsNode().get("_rev").asText());
					newDocs.add(newDoc);
					logger.debug("Marked logged_in document {} for deletion.", oldDoc.getId());
					/* Use log type 'user' since effectively the user is deleted in case of guests */
					dbLogger.log("delete", "type", "user", "id", oldDoc.getId());
				}

				if (!newDocs.isEmpty()) {
					List<DocumentOperationResult> results = db.executeBulk(newDocs);
					count += newDocs.size() - results.size();
					if (!results.isEmpty()) {
						logger.error("Could not bulk delete some visited session lists.");
					}
				}
			}

			if (count > 0) {
				logger.info("Deleted {} visited session lists of inactive users.", count);
				dbLogger.log("cleanup", "type", "visitedsessions", "count", count);
			}

			return count;
		} catch (DbAccessException e) {
			logger.error("Could not delete visited session lists of inactive users.", e);
		}

		return 0;
	}
}
