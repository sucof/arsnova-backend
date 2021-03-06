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
package de.thm.arsnova.entities.transport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import de.thm.arsnova.entities.Content;
import de.thm.arsnova.entities.User;
import de.thm.arsnova.entities.serialization.View;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * A user's answer to a question.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@ApiModel(value = "session/answer", description = "the Answer API")
public class Answer implements Serializable {

	private String answerSubject;

	private String answerSubjectRaw;

	private String answerText;
	private String answerTextRaw;
	private double freeTextScore;
	private boolean successfulFreeTextAnswer;

	private String answerImage;

	private boolean abstention;

	public Answer() {

	}

	public Answer(de.thm.arsnova.entities.Answer a) {
		answerSubject = a.getAnswerSubject();
		answerText = a.getAnswerText();
		answerImage = a.getAnswerImage();
		abstention = a.isAbstention();
		successfulFreeTextAnswer = a.isSuccessfulFreeTextAnswer();
	}

	@ApiModelProperty(required = true, value = "used to display text answer")
	@JsonView(View.Public.class)
	public String getAnswerText() {
		return answerText;
	}

	public void setAnswerText(String answerText) {
		this.answerText = answerText;
	}

	@ApiModelProperty(required = true, value = "used to display subject answer")
	@JsonView(View.Public.class)
	public String getAnswerSubject() {
		return answerSubject;
	}

	public void setAnswerSubject(String answerSubject) {
		this.answerSubject = answerSubject;
	}

	@JsonView(View.Public.class)
	public final String getAnswerTextRaw() {
		return this.answerTextRaw;
	}

	public final void setAnswerTextRaw(final String answerTextRaw) {
		this.answerTextRaw = answerTextRaw;
	}

	@JsonView(View.Public.class)
	public final String getAnswerSubjectRaw() {
		return this.answerSubjectRaw;
	}

	public final void setAnswerSubjectRaw(final String answerSubjectRaw) {
		this.answerSubjectRaw = answerSubjectRaw;
	}

	@JsonView(View.Public.class)
	public final double getFreeTextScore() {
		return this.freeTextScore;
	}

	public final void setFreeTextScore(final double freeTextScore) {
		this.freeTextScore = freeTextScore;
	}

	@ApiModelProperty(required = true, value = "successfulFreeTextAnswer")
	public final boolean isSuccessfulFreeTextAnswer() {
		return this.successfulFreeTextAnswer;
	}

	public final void setSuccessfulFreeTextAnswer(final boolean successfulFreeTextAnswer) {
		this.successfulFreeTextAnswer = successfulFreeTextAnswer;
	}

	@ApiModelProperty(required = true, value = "abstention")
	@JsonView(View.Public.class)
	public boolean isAbstention() {
		return abstention;
	}

	public void setAbstention(boolean abstention) {
		this.abstention = abstention;
	}

	public de.thm.arsnova.entities.Answer generateAnswerEntity(final User user, final Content content) {
		// rewrite all fields so that no manipulated data gets written
		// only answerText, answerSubject, and abstention are allowed
		de.thm.arsnova.entities.Answer theAnswer = new de.thm.arsnova.entities.Answer();
		theAnswer.setAnswerSubject(this.getAnswerSubject());
		theAnswer.setAnswerText(this.getAnswerText());
		theAnswer.setAnswerTextRaw(this.getAnswerTextRaw());
		theAnswer.setSessionId(content.getSessionId());
		theAnswer.setUser(user.getUsername());
		theAnswer.setQuestionId(content.getId());
		theAnswer.setTimestamp(new Date().getTime());
		theAnswer.setQuestionVariant(content.getQuestionVariant());
		theAnswer.setAbstention(this.isAbstention());
		// calculate learning progress value after all properties are set
		theAnswer.setQuestionValue(content.calculateValue(theAnswer));
		theAnswer.setAnswerImage(this.getAnswerImage());
		theAnswer.setSuccessfulFreeTextAnswer(this.isSuccessfulFreeTextAnswer());

		if ("freetext".equals(content.getQuestionType())) {
			theAnswer.setPiRound(0);
		} else {
			theAnswer.setPiRound(content.getPiRound());
		}

		return theAnswer;
	}

	@ApiModelProperty(required = true, value = "used to display image answer")
	@JsonView(View.Public.class)
	public String getAnswerImage() {
		return answerImage;
	}

	public void setAnswerImage(String answerImage) {
		this.answerImage = answerImage;
	}
}
