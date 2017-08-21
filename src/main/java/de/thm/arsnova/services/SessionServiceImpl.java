/*
 * This file is part of ARSnova Backend.
 * Copyright (C) 2012-2017 The ARSnova Team
 *
 * ARSnova Backend is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ARSnova Backend is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.thm.arsnova.services;

import de.thm.arsnova.util.ImageUtils;
import de.thm.arsnova.connector.client.ConnectorClient;
import de.thm.arsnova.connector.model.Course;
import de.thm.arsnova.services.score.ScoreCalculatorFactory;
import de.thm.arsnova.services.score.ScoreCalculator;
import de.thm.arsnova.entities.ScoreOptions;
import de.thm.arsnova.entities.Session;
import de.thm.arsnova.entities.SessionFeature;
import de.thm.arsnova.entities.SessionInfo;
import de.thm.arsnova.entities.User;
import de.thm.arsnova.entities.transport.ImportExportSession;
import de.thm.arsnova.entities.transport.ScoreStatistics;
import de.thm.arsnova.events.DeleteSessionEvent;
import de.thm.arsnova.events.FeatureChangeEvent;
import de.thm.arsnova.events.FlipFlashcardsEvent;
import de.thm.arsnova.events.LockFeedbackEvent;
import de.thm.arsnova.events.NewSessionEvent;
import de.thm.arsnova.events.StatusSessionEvent;
import de.thm.arsnova.exceptions.BadRequestException;
import de.thm.arsnova.exceptions.ForbiddenException;
import de.thm.arsnova.exceptions.NotFoundException;
import de.thm.arsnova.exceptions.PayloadTooLargeException;
import de.thm.arsnova.exceptions.UnauthorizedException;
import de.thm.arsnova.persistance.SessionRepository;
import de.thm.arsnova.persistance.VisitedSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Performs all session related operations.
 */
@Service
public class SessionServiceImpl extends EntityService<Session> implements SessionService, ApplicationEventPublisherAware {
	private static final long SESSION_INACTIVITY_CHECK_INTERVAL_MS = 30 * 60 * 1000L;

	private static final Logger logger = LoggerFactory.getLogger(SessionServiceImpl.class);

	private SessionRepository sessionRepository;

	private VisitedSessionRepository visitedSessionRepository;

	private UserService userService;

	private FeedbackService feedbackService;

	private ScoreCalculatorFactory scoreCalculatorFactory;

	private ConnectorClient connectorClient;

	private ImageUtils imageUtils;

	@Value("${session.guest-session.cleanup-days:0}")
	private int guestSessionInactivityThresholdDays;

	@Value("${pp.logofilesize_b}")
	private int uploadFileSizeByte;

	private ApplicationEventPublisher publisher;

	public SessionServiceImpl(
			SessionRepository repository,
			VisitedSessionRepository visitedSessionRepository,
			UserService userService,
			FeedbackService feedbackService,
			ScoreCalculatorFactory scoreCalculatorFactory,
			ImageUtils imageUtils,
			@Qualifier("defaultJsonMessageConverter") MappingJackson2HttpMessageConverter jackson2HttpMessageConverter) {
		super(Session.class, repository, jackson2HttpMessageConverter.getObjectMapper());
		this.sessionRepository = repository;
		this.visitedSessionRepository = visitedSessionRepository;
		this.userService = userService;
		this.feedbackService = feedbackService;
		this.scoreCalculatorFactory = scoreCalculatorFactory;
		this.imageUtils = imageUtils;
	}

	public static class SessionNameComparator implements Comparator<Session>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final Session session1, final Session session2) {
			return session1.getName().compareToIgnoreCase(session2.getName());
		}
	}

	public static class SessionInfoNameComparator implements Comparator<SessionInfo>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final SessionInfo session1, final SessionInfo session2) {
			return session1.getName().compareToIgnoreCase(session2.getName());
		}
	}

	public static class SessionShortNameComparator implements Comparator<Session>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final Session session1, final Session session2) {
			return session1.getShortName().compareToIgnoreCase(session2.getShortName());
		}
	}

	public static class SessionInfoShortNameComparator implements Comparator<SessionInfo>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final SessionInfo session1, final SessionInfo session2) {
			return session1.getShortName().compareToIgnoreCase(session2.getShortName());
		}
	}

	@Autowired(required = false)
	public void setConnectorClient(ConnectorClient connectorClient) {
		this.connectorClient = connectorClient;
	}

	@Scheduled(fixedDelay = SESSION_INACTIVITY_CHECK_INTERVAL_MS)
	public void deleteInactiveSessions() {
		if (guestSessionInactivityThresholdDays > 0) {
			logger.info("Delete inactive sessions.");
			long unixTime = System.currentTimeMillis();
			long lastActivityBefore = unixTime - guestSessionInactivityThresholdDays * 24 * 60 * 60 * 1000L;
			sessionRepository.deleteInactiveGuestSessions(lastActivityBefore);
		}
	}

	@Scheduled(fixedDelay = SESSION_INACTIVITY_CHECK_INTERVAL_MS)
	public void deleteInactiveVisitedSessionLists() {
		if (guestSessionInactivityThresholdDays > 0) {
			logger.info("Delete lists of visited session for inactive users.");
			long unixTime = System.currentTimeMillis();
			long lastActivityBefore = unixTime - guestSessionInactivityThresholdDays * 24 * 60 * 60 * 1000L;
			visitedSessionRepository.deleteInactiveGuestVisitedSessionLists(lastActivityBefore);
		}
	}

	@Override
	public Session join(final String keyword, final UUID socketId) {
		/* Socket.IO solution */

		Session session = null != keyword ? sessionRepository.findByKeyword(keyword) : null;

		if (null == session) {
			userService.removeUserFromSessionBySocketId(socketId);
			return null;
		}
		final User user = userService.getUser2SocketId(socketId);

		userService.addUserToSessionBySocketId(socketId, keyword);

		if (session.getCreator().equals(user.getUsername())) {
			sessionRepository.updateSessionOwnerActivity(session);
		}
		sessionRepository.registerAsOnlineUser(user, session);

		if (connectorClient != null && session.isCourseSession()) {
			final String courseid = session.getCourseId();
			if (!connectorClient.getMembership(user.getUsername(), courseid).isMember()) {
				throw new ForbiddenException("User is no course member.");
			}
		}

		return session;
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public Session getByKey(final String keyword) {
		final User user = userService.getCurrentUser();
		return Session.anonymizedCopy(this.getInternal(keyword, user));
	}

	@PreAuthorize("isAuthenticated() and hasPermission(#sessionkey, 'session', 'owner')")
	public Session getForAdmin(final String keyword) {
		return sessionRepository.findByKeyword(keyword);
	}

	/*
	 * The "internal" suffix means it is called by internal services that have no authentication!
	 * TODO: Find a better way of doing this...
	 */
	@Override
	public Session getInternal(final String keyword, final User user) {
		final Session session = sessionRepository.findByKeyword(keyword);
		if (session == null) {
			throw new NotFoundException();
		}
		if (!session.isActive()) {
			if (user.hasRole(UserSessionService.Role.STUDENT)) {
				throw new ForbiddenException("User is not session creator.");
			} else if (user.hasRole(UserSessionService.Role.SPEAKER) && !session.isCreator(user)) {
				throw new ForbiddenException("User is not session creator.");
			}
		}
		if (connectorClient != null && session.isCourseSession()) {
			final String courseid = session.getCourseId();
			if (!connectorClient.getMembership(user.getUsername(), courseid).isMember()) {
				throw new ForbiddenException("User is no course member.");
			}
		}
		return session;
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(#sessionkey, 'session', 'owner')")
	public List<Session> getUserSessions(String username) {
		return sessionRepository.findByUsername(username, 0, 0);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<Session> getMySessions(final int offset, final int limit) {
		return sessionRepository.findByUser(userService.getCurrentUser(), offset, limit);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<SessionInfo> getPublicPoolSessionsInfo() {
		return sessionRepository.findInfosForPublicPool();
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<SessionInfo> getMyPublicPoolSessionsInfo() {
		return sessionRepository.findInfosForPublicPoolByUser(userService.getCurrentUser());
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<SessionInfo> getMySessionsInfo(final int offset, final int limit) {
		final User user = userService.getCurrentUser();
		return sessionRepository.getMySessionsInfo(user, offset, limit);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<Session> getMyVisitedSessions(final int offset, final int limit) {
		return sessionRepository.findVisitedByUsername(userService.getCurrentUser().getUsername(), offset, limit);
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(1, 'motd', 'admin')")
	public List<Session> getUserVisitedSessions(String username) {
		return sessionRepository.findVisitedByUsername(username, 0, 0);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public List<SessionInfo> getMyVisitedSessionsInfo(final int offset, final int limit) {
		return sessionRepository.findInfoForVisitedByUser(userService.getCurrentUser(), offset, limit);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public Session save(final Session session) {
		if (connectorClient != null && session.getCourseId() != null) {
			if (!connectorClient.getMembership(
					userService.getCurrentUser().getUsername(), session.getCourseId()).isMember()
					) {
				throw new ForbiddenException();
			}
		}
		handleLogo(session);

		// set some default values
		ScoreOptions lpo = new ScoreOptions();
		lpo.setType("questions");
		session.setLearningProgressOptions(lpo);

		SessionFeature sf = new SessionFeature();
		sf.setLecture(true);
		sf.setFeedback(true);
		sf.setInterposed(true);
		sf.setJitt(true);
		sf.setLearningProgress(true);
		sf.setPi(true);
		session.setFeatures(sf);

		final Session result = sessionRepository.save(userService.getCurrentUser(), session);
		this.publisher.publishEvent(new NewSessionEvent(this, result));
		return result;
	}

	@Override
	public boolean isKeyAvailable(final String keyword) {
		return sessionRepository.sessionKeyAvailable(keyword);
	}

	@Override
	public String generateKey() {
		final int low = 10000000;
		final int high = 100000000;
		final String keyword = String
				.valueOf((int) (Math.random() * (high - low) + low));

		if (isKeyAvailable(keyword)) {
			return keyword;
		}
		return generateKey();
	}

	@Override
	public int countSessionsByCourses(final List<Course> courses) {
		final List<Session> sessions = sessionRepository.findSessionsByCourses(courses);
		if (sessions == null) {
			return 0;
		}
		return sessions.size();
	}

	@Override
	public int activeUsers(final String sessionkey) {
		return userService.getUsersBySessionKey(sessionkey).size();
	}

	@Override
	public Session setActive(final String sessionkey, final Boolean lock) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		final User user = userService.getCurrentUser();
		if (!session.isCreator(user)) {
			throw new ForbiddenException("User is not session creator.");
		}
		session.setActive(lock);
		this.publisher.publishEvent(new StatusSessionEvent(this, session));
		sessionRepository.update(session);

		return session;
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(#session, 'owner')")
	public Session update(final String sessionkey, final Session session) {
		final Session existingSession = sessionRepository.findByKeyword(sessionkey);

		existingSession.setActive(session.isActive());
		existingSession.setShortName(session.getShortName());
		existingSession.setPpAuthorName(session.getPpAuthorName());
		existingSession.setPpAuthorMail(session.getPpAuthorMail());
		existingSession.setShortName(session.getShortName());
		existingSession.setPpAuthorName(session.getPpAuthorName());
		existingSession.setPpFaculty(session.getPpFaculty());
		existingSession.setName(session.getName());
		existingSession.setPpUniversity(session.getPpUniversity());
		existingSession.setPpDescription(session.getPpDescription());
		existingSession.setPpLevel(session.getPpLevel());
		existingSession.setPpLicense(session.getPpLicense());
		existingSession.setPpSubject(session.getPpSubject());
		existingSession.setFeedbackLock(session.getFeedbackLock());

		handleLogo(session);
		existingSession.setPpLogo(session.getPpLogo());

		sessionRepository.update(existingSession);

		return session;
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(1,'motd','admin')")
	public Session updateCreator(String sessionkey, String newCreator) {
		final Session existingSession = sessionRepository.findByKeyword(sessionkey);
		if (existingSession == null) {
			throw new NullPointerException("Could not load session " + sessionkey + ".");
		}
		return sessionRepository.changeSessionCreator(existingSession, newCreator);
	}

	/*
	 * The "internal" suffix means it is called by internal services that have no authentication!
	 * TODO: Find a better way of doing this...
	 */
	@Override
	public Session updateInternal(final Session session, final User user) {
		if (session.isCreator(user)) {
			sessionRepository.update(session);
			return session;
		}
		return null;
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(#sessionkey, 'session', 'owner')")
	public void delete(final String sessionkey) {
		final Session session = sessionRepository.findByKeyword(sessionkey);

		sessionRepository.deleteSession(session);

		this.publisher.publishEvent(new DeleteSessionEvent(this, session));
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public ScoreStatistics getLearningProgress(final String sessionkey, final String progressType, final String questionVariant) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		ScoreCalculator learningProgress = scoreCalculatorFactory.create(progressType, questionVariant);
		return learningProgress.getCourseProgress(session);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public ScoreStatistics getMyLearningProgress(final String sessionkey, final String progressType, final String questionVariant) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		final User user = userService.getCurrentUser();
		ScoreCalculator learningProgress = scoreCalculatorFactory.create(progressType, questionVariant);
		return learningProgress.getMyProgress(session, user);
	}

	@Override
	@PreAuthorize("isAuthenticated()")
	public SessionInfo importSession(ImportExportSession importSession) {
		final User user = userService.getCurrentUser();
		final SessionInfo info = sessionRepository.importSession(user, importSession);
		if (info == null) {
			throw new NullPointerException("Could not import session.");
		}
		return info;
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(#sessionkey, 'session', 'owner')")
	public ImportExportSession exportSession(String sessionkey, Boolean withAnswerStatistics, Boolean withFeedbackQuestions) {
		return sessionRepository.exportSession(sessionkey, withAnswerStatistics, withFeedbackQuestions);
	}

	@Override
	@PreAuthorize("isAuthenticated() and hasPermission(#sessionkey, 'session', 'owner')")
	public SessionInfo copySessionToPublicPool(String sessionkey, de.thm.arsnova.entities.transport.ImportExportSession.PublicPool pp) {
		ImportExportSession temp = sessionRepository.exportSession(sessionkey, false, false);
		temp.getSession().setPublicPool(pp);
		temp.getSession().setSessionType("public_pool");
		final User user = userService.getCurrentUser();
		return sessionRepository.importSession(user, temp);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public SessionFeature getFeatures(String sessionkey) {
		return sessionRepository.findByKeyword(sessionkey).getFeatures();
	}

	@Override
	public SessionFeature updateFeatures(String sessionkey, SessionFeature features) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		final User user = userService.getCurrentUser();
		if (!session.isCreator(user)) {
			throw new UnauthorizedException("User is not session creator.");
		}
		session.setFeatures(features);
		this.publisher.publishEvent(new FeatureChangeEvent(this, session));
		sessionRepository.update(session);

		return session.getFeatures();
	}

	@Override
	public boolean lockFeedbackInput(String sessionkey, Boolean lock) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		final User user = userService.getCurrentUser();
		if (!session.isCreator(user)) {
			throw new UnauthorizedException("User is not session creator.");
		}
		if (!lock) {
			feedbackService.cleanFeedbackVotesBySessionKey(sessionkey, 0);
		}

		session.setFeedbackLock(lock);
		this.publisher.publishEvent(new LockFeedbackEvent(this, session));
		sessionRepository.update(session);

		return session.getFeedbackLock();
	}

	@Override
	public boolean flipFlashcards(String sessionkey, Boolean flip) {
		final Session session = sessionRepository.findByKeyword(sessionkey);
		final User user = userService.getCurrentUser();
		if (!session.isCreator(user)) {
			throw new UnauthorizedException("User is not session creator.");
		}
		session.setFlipFlashcards(flip);
		this.publisher.publishEvent(new FlipFlashcardsEvent(this, session));
		sessionRepository.update(session);

		return session.getFlipFlashcards();
	}

	private void handleLogo(Session session) {
		if (session.getPpLogo() != null) {
			if (session.getPpLogo().startsWith("http")) {
				final String base64ImageString = imageUtils.encodeImageToString(session.getPpLogo());
				if (base64ImageString == null) {
					throw new BadRequestException("Could not encode image.");
				}
				session.setPpLogo(base64ImageString);
			}

			// base64 adds offset to filesize, formula taken from: http://en.wikipedia.org/wiki/Base64#MIME
			final int fileSize = (int) ((session.getPpLogo().length() - 814) / 1.37);
			if (fileSize > uploadFileSizeByte) {
				throw new PayloadTooLargeException("Could not save file. File is too large with " + fileSize + " Byte.");
			}
		}
	}
}
