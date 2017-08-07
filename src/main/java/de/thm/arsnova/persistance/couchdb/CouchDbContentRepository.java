package de.thm.arsnova.persistance.couchdb;

import de.thm.arsnova.entities.Content;
import de.thm.arsnova.entities.User;
import de.thm.arsnova.persistance.AnswerRepository;
import de.thm.arsnova.persistance.ContentRepository;
import de.thm.arsnova.persistance.LogEntryRepository;
import org.ektorp.ComplexKey;
import org.ektorp.CouchDbConnector;
import org.ektorp.DbAccessException;
import org.ektorp.DocumentNotFoundException;
import org.ektorp.UpdateConflictException;
import org.ektorp.ViewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CouchDbContentRepository extends CouchDbCrudRepository<Content> implements ContentRepository {
	private static final Logger logger = LoggerFactory.getLogger(CouchDbContentRepository.class);

	@Autowired
	private LogEntryRepository dbLogger;

	@Autowired
	private AnswerRepository answerRepository;

	public CouchDbContentRepository(final CouchDbConnector db, final boolean createIfNotExists) {
		super(Content.class, db, "by_sessionid", createIfNotExists);
	}

	@Cacheable("skillquestions")
	@Override
	public List<Content> findBySessionIdForUsers(final String sessionId) {
		final List<Content> contents = new ArrayList<>();
		final List<Content> questions1 = findBySessionIdAndVariantAndActive(sessionId, "lecture", true);
		final List<Content> questions2 = findBySessionIdAndVariantAndActive(sessionId, "preparation", true);
		final List<Content> questions3 = findBySessionIdAndVariantAndActive(sessionId, "flashcard", true);
		contents.addAll(questions1);
		contents.addAll(questions2);
		contents.addAll(questions3);

		return contents;
	}

	@Cacheable("skillquestions")
	@Override
	public List<Content> findBySessionIdForSpeaker(final String sessionId) {
		return findBySessionIdAndVariantAndActive(new Object[] {sessionId}, sessionId);
	}

	@Override
	public int countBySessionId(final String sessionId) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId))
				.endKey(ComplexKey.of(sessionId, ComplexKey.emptyObject())));

		return result.getSize();
	}

	/* TODO: Move to service layer. */
	@Caching(evict = {@CacheEvict(value = "skillquestions", key = "#sessionId"),
			@CacheEvict(value = "lecturequestions", key = "#sessionId", condition = "#content.getQuestionVariant().equals('lecture')"),
			@CacheEvict(value = "preparationquestions", key = "#sessionId", condition = "#content.getQuestionVariant().equals('preparation')"),
			@CacheEvict(value = "flashcardquestions", key = "#sessionId", condition = "#content.getQuestionVariant().equals('flashcard')") },
			put = {@CachePut(value = "questions", key = "#content.id")})
	@Override
	public Content save(final String sessionId, final Content content) {
		/* TODO: This should be done on the service level. */
		content.setSessionId(sessionId);
		try {
			db.create(content);

			return content;
		} catch (final IllegalArgumentException e) {
			logger.error("Could not save content {}.", content, e);
		}

		return null;
	}

	/* TODO: Move to service layer. */
	/* TODO: Only evict cache entry for the content's session. This requires some refactoring. */
	@Caching(evict = {@CacheEvict(value = "skillquestions", allEntries = true),
			@CacheEvict(value = "lecturequestions", allEntries = true, condition = "#content.getQuestionVariant().equals('lecture')"),
			@CacheEvict(value = "preparationquestions", allEntries = true, condition = "#content.getQuestionVariant().equals('preparation')"),
			@CacheEvict(value = "flashcardquestions", allEntries = true, condition = "#content.getQuestionVariant().equals('flashcard')") },
			put = {@CachePut(value = "questions", key = "#content.id")})
	@Override
	public void update(final Content content) {
		try {
			/* TODO: This should be done on the service level. Make sure that
			 * sessionId is valid before so the content does not need to be retrieved. */
			final Content oldContent = get(content.getId());
			content.setId(oldContent.getId());
			content.setRevision(oldContent.getRevision());
			content.updateRoundManagementState();
			super.update(content);
		} catch (final UpdateConflictException e) {
			logger.error("Could not update content {}.", content, e);
		}
	}

	/* TODO: Move to service layer. */
	@Cacheable("questions")
	@Override
	public Content findOne(final String id) {
		try {
			final Content content = get(id);
			content.updateRoundManagementState();
			//content.setSessionKeyword(sessionRepository.getSessionFromId(content.getSessionId()).getKeyword());

			return content;
		} catch (final DocumentNotFoundException e) {
			logger.error("Could not get question {}.", id, e);
		}

		return null;
	}

	@Override
	public List<String> findIdsBySessionId(final String sessionId) {
		return collectQuestionIds(db.queryView(createQuery("by_sessionid_variant_active").key(sessionId)));
	}

	/* TODO: Move to service layer. */
	/* TODO: Only evict cache entry for the content's session. This requires some refactoring. */
	@Caching(evict = { @CacheEvict(value = "questions", key = "#content.id"),
			@CacheEvict(value = "skillquestions", allEntries = true),
			@CacheEvict(value = "lecturequestions", allEntries = true, condition = "#content.getQuestionVariant().equals('lecture')"),
			@CacheEvict(value = "preparationquestions", allEntries = true, condition = "#content.getQuestionVariant().equals('preparation')"),
			@CacheEvict(value = "flashcardquestions", allEntries = true, condition = "#content.getQuestionVariant().equals('flashcard')") })
	@Override
	public int deleteQuestionWithAnswers(final String contentId) {
		try {
			final int count = answerRepository.deleteByContentId(contentId);
			db.delete(contentId);
			dbLogger.log("delete", "type", "content", "answerCount", count);

			return count;
		} catch (final IllegalArgumentException e) {
			logger.error("Could not delete content {}.", contentId, e);
		}

		return 0;
	}

	/* TODO: Move to service layer. */
	@Caching(evict = { @CacheEvict(value = "questions", allEntries = true),
			@CacheEvict(value = "skillquestions", key = "#sessionId"),
			@CacheEvict(value = "lecturequestions", key = "#sessionId"),
			@CacheEvict(value = "preparationquestions", key = "#sessionId"),
			@CacheEvict(value = "flashcardquestions", key = "#sessionId") })
	@Override
	public int[] deleteAllQuestionsWithAnswers(final String sessionId) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId))
				.endKey(ComplexKey.of(sessionId, ComplexKey.emptyObject()))
				.reduce(false));

		return deleteAllQuestionDocumentsWithAnswers(result);
	}

	/* TODO: Move to service layer. */
	private int[] deleteAllQuestionDocumentsWithAnswers(final ViewResult viewResult) {
		List<Content> contents = new ArrayList<>();
		for (final ViewResult.Row row : viewResult.getRows()) {
			final Content q = new Content();
			q.setId(row.getId());
			q.setRevision(row.getValueAsNode().get("_rev").asText());
			contents.add(q);
		}

		final int[] count = answerRepository.deleteAllAnswersWithQuestions(contents);
		dbLogger.log("delete", "type", "question", "questionCount", count[0]);
		dbLogger.log("delete", "type", "answer", "answerCount", count[1]);

		return count;
	}

	@Override
	public List<String> findUnansweredIdsBySessionIdAndUser(final String sessionId, final User user) {
		final ViewResult result = db.queryView(createQuery("questionid_by_user_sessionid_variant")
				.designDocId("_design/Answer")
				.startKey(ComplexKey.of(user.getUsername(), sessionId))
				.endKey(ComplexKey.of(user.getUsername(), sessionId, ComplexKey.emptyObject())));
		final List<String> answeredIds = new ArrayList<>();
		for (final ViewResult.Row row : result.getRows()) {
			answeredIds.add(row.getId());
		}
		return collectUnansweredQuestionIds(findIdsBySessionId(sessionId), answeredIds);
	}

	@Override
	public List<String> findUnansweredIdsBySessionIdAndUserOnlyLectureVariant(final String sessionId, final User user) {
		final ViewResult result = db.queryView(createQuery("questionid_piround_by_user_sessionid_variant")
				.designDocId("_design/Answer")
				.key(ComplexKey.of(user.getUsername(), sessionId, "lecture")));
		final Map<String, Integer> answeredQuestions = new HashMap<>();
		for (final ViewResult.Row row : result.getRows()) {
			answeredQuestions.put(row.getId(), row.getKeyAsNode().get(2).asInt());
		}

		return collectUnansweredQuestionIdsByPiRound(findBySessionIdOnlyLectureVariantAndActive(sessionId), answeredQuestions);
	}

	@Override
	public List<String> findUnansweredIdsBySessionIdAndUserOnlyPreparationVariant(final String sessionId, final User user) {
		final ViewResult result = db.queryView(createQuery("questionid_piround_by_user_sessionid_variant")
				.designDocId("_design/Answer")
				.key(ComplexKey.of(user.getUsername(), sessionId, "preparation")));
		final Map<String, Integer> answeredQuestions = new HashMap<>();
		for (final ViewResult.Row row : result.getRows()) {
			answeredQuestions.put(row.getId(), row.getKeyAsNode().get(2).asInt());
		}

		return collectUnansweredQuestionIdsByPiRound(findBySessionIdOnlyPreparationVariantAndActive(sessionId), answeredQuestions);
	}

	@Cacheable("lecturequestions")
	@Override
	public List<Content> findBySessionIdOnlyLectureVariantAndActive(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "lecture", true);
	}

	@Override
	public List<Content> findBySessionIdOnlyLectureVariant(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "lecture");
	}

	@Cacheable("flashcardquestions")
	@Override
	public List<Content> findBySessionIdOnlyFlashcardVariantAndActive(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "flashcard", true);
	}

	@Override
	public List<Content> findBySessionIdOnlyFlashcardVariant(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "flashcard");
	}

	@Cacheable("preparationquestions")
	@Override
	public List<Content> findBySessionIdOnlyPreparationVariantAndActive(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "preparation", true);
	}

	@Override
	public List<Content> findBySessionIdOnlyPreparationVariant(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId, "preparation");
	}

	@Override
	public List<Content> findBySessionId(final String sessionId) {
		return findBySessionIdAndVariantAndActive(sessionId);
	}

	@Override
	public List<Content> findBySessionIdAndVariantAndActive(final Object... keys) {
		final Object[] endKeys = Arrays.copyOf(keys, keys.length + 1);
		endKeys[keys.length] = ComplexKey.emptyObject();
		final List<Content> contents = db.queryView(createQuery("by_sessionid_variant_active")
						.includeDocs(true)
						.reduce(false)
						.startKey(ComplexKey.of(keys))
						.endKey(ComplexKey.of(endKeys)),
				Content.class);
		for (final Content content : contents) {
			content.updateRoundManagementState();
			//content.setSessionKeyword(session.getKeyword());
		}

		return contents;
	}

	@Override
	public int countLectureVariantBySessionId(final String sessionId) {
		/* TODO: reduce code duplication */
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "lecture"))
				.endKey(ComplexKey.of(sessionId, "lecture", ComplexKey.emptyObject())));

		return result.isEmpty() ? 0 : result.getRows().get(0).getValueAsInt();
	}

	@Override
	public int countFlashcardVariantBySessionId(final String sessionId) {
		/* TODO: reduce code duplication */
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "flashcard"))
				.endKey(ComplexKey.of(sessionId, "flashcard", ComplexKey.emptyObject())));

		return result.isEmpty() ? 0 : result.getRows().get(0).getValueAsInt();
	}

	@Override
	public int countPreparationVariantBySessionId(final String sessionId) {
		/* TODO: reduce code duplication */
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "preparation"))
				.endKey(ComplexKey.of(sessionId, "preparation", ComplexKey.emptyObject())));

		return result.isEmpty() ? 0 : result.getRows().get(0).getValueAsInt();
	}

	/* TODO: Move to service layer. */
	/* TODO: Only evict cache entry for the answer's question. This requires some refactoring. */
	@Caching(evict = { @CacheEvict(value = "questions", allEntries = true),
			@CacheEvict("skillquestions"),
			@CacheEvict("lecturequestions"),
			@CacheEvict(value = "answers", allEntries = true)})
	@Override
	public int[] deleteAllLectureQuestionsWithAnswers(final String sessionId) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "lecture"))
				.endKey(ComplexKey.of(sessionId, "lecture", ComplexKey.emptyObject()))
				.reduce(false));

		return deleteAllQuestionDocumentsWithAnswers(result);
	}

	/* TODO: Move to service layer. */
	/* TODO: Only evict cache entry for the answer's question. This requires some refactoring. */
	@Caching(evict = { @CacheEvict(value = "questions", allEntries = true),
			@CacheEvict("skillquestions"),
			@CacheEvict("flashcardquestions"),
			@CacheEvict(value = "answers", allEntries = true)})
	@Override
	public int[] deleteAllFlashcardsWithAnswers(final String sessionId) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "flashcard"))
				.endKey(ComplexKey.of(sessionId, "flashcard", ComplexKey.emptyObject()))
				.reduce(false));

		return deleteAllQuestionDocumentsWithAnswers(result);
	}

	/* TODO: Move to service layer. */
	/* TODO: Only evict cache entry for the answer's question. This requires some refactoring. */
	@Caching(evict = { @CacheEvict(value = "questions", allEntries = true),
			@CacheEvict("skillquestions"),
			@CacheEvict("preparationquestions"),
			@CacheEvict(value = "answers", allEntries = true)})
	@Override
	public int[] deleteAllPreparationQuestionsWithAnswers(final String sessionId) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, "preparation"))
				.endKey(ComplexKey.of(sessionId, "preparation", ComplexKey.emptyObject()))
				.reduce(false));

		return deleteAllQuestionDocumentsWithAnswers(result);
	}

	private List<String> collectUnansweredQuestionIds(
			final List<String> contentIds,
			final List<String> answeredContentIds
	) {
		final List<String> unanswered = new ArrayList<>();
		for (final String contentId : contentIds) {
			if (!answeredContentIds.contains(contentId)) {
				unanswered.add(contentId);
			}
		}
		return unanswered;
	}

	private List<String> collectUnansweredQuestionIdsByPiRound(
			final List<Content> contents,
			final Map<String, Integer> answeredQuestions
	) {
		final List<String> unanswered = new ArrayList<>();

		for (final Content content : contents) {
			if (!"slide".equals(content.getQuestionType()) && (!answeredQuestions.containsKey(content.getId())
					|| (answeredQuestions.containsKey(content.getId()) && answeredQuestions.get(content.getId()) != content.getPiRound()))) {
				unanswered.add(content.getId());
			}
		}

		return unanswered;
	}

	private List<String> collectQuestionIds(final ViewResult viewResult) {
		final List<String> ids = new ArrayList<>();
		for (final ViewResult.Row row : viewResult.getRows()) {
			ids.add(row.getId());
		}
		return ids;
	}

	/* TODO: Move to service layer. */
	@Override
	public List<Content> publishAllQuestions(final String sessionId, final boolean publish) {
		final List<Content> contents = db.queryView(createQuery("by_sessionid_variant_active")
						.startKey(ComplexKey.of(sessionId))
						.endKey(ComplexKey.of(sessionId, ComplexKey.emptyObject())),
				Content.class);
		/* FIXME: caching */
		publishQuestions(sessionId, publish, contents);

		return contents;
	}

	/* TODO: Move to service layer. */
	@Caching(evict = { @CacheEvict(value = "contents", allEntries = true),
			@CacheEvict(value = "skillquestions", key = "#sessionId"),
			@CacheEvict(value = "lecturequestions", key = "#sessionId"),
			@CacheEvict(value = "preparationquestions", key = "#sessionId"),
			@CacheEvict(value = "flashcardquestions", key = "#sessionId") })
	@Override
	public void publishQuestions(final String sessionId, final boolean publish, final List<Content> contents) {
		for (final Content content : contents) {
			content.setActive(publish);
		}
		try {
			db.executeBulk(contents);
		} catch (final DbAccessException e) {
			logger.error("Could not bulk publish all contents.", e);
		}
	}

	/* TODO: Move to service layer. */
	@Override
	public List<Content> setVotingAdmissionForAllQuestions(final String sessionId, final boolean disableVoting) {
		final List<Content> contents = db.queryView(createQuery("by_sessionid_variant_active")
						.startKey(ComplexKey.of(sessionId))
						.endKey(ComplexKey.of(sessionId, ComplexKey.emptyObject()))
						.includeDocs(true),
				Content.class);
		/* FIXME: caching */
		setVotingAdmissions(sessionId, disableVoting, contents);

		return contents;
	}

	@Caching(evict = { @CacheEvict(value = "contents", allEntries = true),
			@CacheEvict(value = "skillquestions", key = "#sessionId"),
			@CacheEvict(value = "lecturequestions", key = "#sessionId"),
			@CacheEvict(value = "preparationquestions", key = "#sessionId"),
			@CacheEvict(value = "flashcardquestions", key = "#sessionId") })
	@Override
	public void setVotingAdmissions(final String sessionId, final boolean disableVoting, final List<Content> contents) {
		for (final Content q : contents) {
			if (!"flashcard".equals(q.getQuestionType())) {
				q.setVotingDisabled(disableVoting);
			}
		}

		try {
			db.executeBulk(contents);
		} catch (final DbAccessException e) {
			logger.error("Could not bulk set voting admission for all contents.", e);
		}
	}

	/* TODO: remove if this method is no longer used */
	@Override
	public List<String> findIdsBySessionIdAndVariantAndSubject(final String sessionId, final String questionVariant, final String subject) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, questionVariant, 1, subject))
				.endKey(ComplexKey.of(sessionId, questionVariant, 1, subject, ComplexKey.emptyObject())));

		final List<String> qids = new ArrayList<>();

		for (final ViewResult.Row row : result.getRows()) {
			final String s = row.getId();
			qids.add(s);
		}

		return qids;
	}

	@Override
	public List<String> findSubjectsBySessionIdAndVariant(final String sessionId, final String questionVariant) {
		final ViewResult result = db.queryView(createQuery("by_sessionid_variant_active")
				.startKey(ComplexKey.of(sessionId, questionVariant))
				.endKey(ComplexKey.of(sessionId, questionVariant, ComplexKey.emptyObject())));

		final Set<String> uniqueSubjects = new HashSet<>();

		for (final ViewResult.Row row : result.getRows()) {
			uniqueSubjects.add(row.getKeyAsNode().get(3).asText());
		}

		return new ArrayList<>(uniqueSubjects);
	}

	/* TODO: Move to service layer. */
	@Caching(evict = { @CacheEvict(value = "contents", allEntries = true),
			@CacheEvict(value = "skillquestions", key = "#sessionId"),
			@CacheEvict(value = "lecturequestions", key = "#sessionId"),
			@CacheEvict(value = "preparationquestions", key = "#sessionId"),
			@CacheEvict(value = "flashcardquestions", key = "#sessionId") })
	@Override
	public void resetQuestionsRoundState(final String sessionId, final List<Content> contents) {
		for (final Content q : contents) {
			q.setSessionId(sessionId);
			q.resetQuestionState();
		}
		try {
			db.executeBulk(contents);
		} catch (final DbAccessException e) {
			logger.error("Could not bulk reset all contents round state.", e);
		}
	}
}