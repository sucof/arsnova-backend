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
package de.thm.arsnova.persistance;

import de.thm.arsnova.entities.Answer;
import de.thm.arsnova.entities.Content;
import de.thm.arsnova.entities.Session;
import de.thm.arsnova.entities.User;

import java.util.List;

public interface AnswerRepository {
	Answer get(String id);
	Answer getMyAnswer(User me, String questionId, int piRound);
	List<Answer> getAnswers(Content content, int piRound);
	List<Answer> getAnswers(Content content);
	List<Answer> getAllAnswers(Content content);
	int getAnswerCount(Content content, int piRound);
	int getTotalAnswerCountByQuestion(Content content);
	int getAbstentionAnswerCount(String questionId);
	List<Answer> getFreetextAnswers(String questionId, final int start, final int limit);
	List<Answer> getMyAnswers(User me, Session session);
	int getTotalAnswerCount(String sessionKey);
	int deleteAnswers(Content content);
	Answer saveAnswer(Answer answer, User user, Content content, Session session);
	Answer updateAnswer(Answer answer);
	void deleteAnswer(String answerId);
	int countLectureQuestionAnswers(Session session);
	int countPreparationQuestionAnswers(Session session);
	int deleteAllQuestionsAnswers(Session session);
	int deleteAllPreparationAnswers(Session session);
	int deleteAllLectureAnswers(Session session);
	int[] deleteAllAnswersWithQuestions(List<Content> contents);
}
